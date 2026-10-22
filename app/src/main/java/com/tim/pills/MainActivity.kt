package com.tim.pills

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Bg = Color(0xFF121616)
private val CardDone = Color(0xFF213B38)
private val CardTodo = Color(0xFF1B2B2A)
private val Accent = Color(0xFF4DB8A4)
private val Muted = Color(0xFF9AA5A3)
private val Warn = Color(0xFFE0A050)
private val Red = Color(0xFFE5484D)
private val White = Color(0xFFF2F2F2)
private val RU: Locale = Locale.forLanguageTag("ru")

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        Reminders.ensureChannel(this)
        Reminders.scheduleAll(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg)) {
                App(resumeTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@Composable
fun App(resumeTick: Int) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val pills = remember(resumeTick, version) { Store.loadPills(ctx) }
    val history = remember(resumeTick, version) { Store.loadHistory(ctx) }
    val today = LocalDate.now()
    val takenToday = history[today.toString()] ?: emptySet()
    val done = pills.count { it.id in takenToday }

    var editMode by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pill?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    val dateText = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU))
    val sub = if (pills.isEmpty()) "$dateText · нажми + чтобы добавить" else "$dateText · выпито $done из ${pills.size}"

    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Таблетки", color = White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Muted, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { if (pills.isEmpty()) 0f else done.toFloat() / pills.size },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = Accent,
                trackColor = CardTodo
            )
            Spacer(Modifier.height(20.dp))

            pills.forEach { p ->
                PillCard(
                    p = p,
                    taken = p.id in takenToday,
                    editMode = editMode,
                    onToggle = {
                        Store.setTaken(ctx, p.id, today, p.id !in takenToday)
                        Reminders.schedule(ctx, p)
                        ctx.getSystemService(android.app.NotificationManager::class.java).cancel(p.id.toInt())
                        version++
                    },
                    onEdit = { editing = p; showDialog = true },
                    onDelete = {
                        Store.savePills(ctx, Store.loadPills(ctx).filter { it.id != p.id })
                        Reminders.cancel(ctx, p.id)
                        version++
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(4.dp))
            Text(
                if (editMode) "Готово" else "Изменить список",
                color = Muted,
                fontSize = 20.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { editMode = !editMode }
            )
            Spacer(Modifier.height(32.dp))

            Text("История", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            CalendarView(
                month = month,
                history = history,
                onPrev = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) }
            )
            Spacer(Modifier.height(120.dp))
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(68.dp)
                .clip(CircleShape)
                .background(Accent)
                .clickable { editing = null; showDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        }

        if (showDialog) {
            PillDialog(
                initial = editing,
                onDismiss = { showDialog = false },
                onSave = { pill ->
                    val list = Store.loadPills(ctx).filter { it.id != pill.id } + pill
                    Store.savePills(ctx, list)
                    Reminders.schedule(ctx, pill)
                    showDialog = false
                    version++
                }
            )
        }
    }
}

@Composable
fun PillCard(
    p: Pill,
    taken: Boolean,
    editMode: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val days = if (p.perDose > 0) p.left / p.perDose else 0
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (taken) CardDone else CardTodo)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%02d:%02d".format(p.hour, p.minute), color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    p.name,
                    color = if (taken) Muted else White,
                    fontSize = 20.sp,
                    textDecoration = if (taken) TextDecoration.LineThrough else null
                )
                Text(
                    "осталось ${p.left} шт · хватит на $days дн.",
                    color = if (days <= 3) Warn else Muted,
                    fontSize = 14.sp
                )
            }
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (taken) Accent else Color.Transparent)
                    .border(2.dp, Accent, CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = if (taken) White else Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (editMode) {
            Row {
                TextButton(onClick = onEdit) { Text("Изменить", color = Accent) }
                TextButton(onClick = onDelete) { Text("Удалить", color = Red) }
            }
        }
    }
}

@Composable
fun CalendarView(
    month: YearMonth,
    history: Map<String, Set<Long>>,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val today = LocalDate.now()
    val title = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", RU))
        .replaceFirstChar { it.uppercase() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(CardTodo).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrev) { Text("‹", color = Accent, fontSize = 28.sp) }
            Text(
                title,
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onNext) { Text("›", color = Accent, fontSize = 28.sp) }
        }
        Row {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        val offset = month.atDay(1).dayOfWeek.value - 1
        val len = month.lengthOfMonth()
        val rows = (offset + len + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val d = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (d in 1..len) {
                            val date = month.atDay(d)
                            val took = history[date.toString()]?.isNotEmpty() == true
                            val isToday = date == today
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .then(if (isToday) Modifier.border(1.5.dp, Accent, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$d", color = if (took) Muted else White, fontSize = 14.sp)
                                if (took) {
                                    Text("✕", color = Red, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White,
    unfocusedTextColor = White,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Muted,
    focusedLabelColor = Accent,
    unfocusedLabelColor = Muted,
    cursorColor = Accent
)

@Composable
fun PillDialog(initial: Pill?, onDismiss: () -> Unit, onSave: (Pill) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var hour by remember { mutableIntStateOf(initial?.hour ?: 12) }
    var minute by remember { mutableIntStateOf(initial?.minute ?: 0) }
    var left by remember { mutableStateOf((initial?.left ?: 30).toString()) }
    var perDose by remember { mutableStateOf((initial?.perDose ?: 1).toString()) }
    var repeat by remember { mutableStateOf((initial?.repeatMin ?: 120).toString()) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardTodo,
        title = { Text(if (initial == null) "Новая таблетка" else "Изменить", color = White) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    name, { name = it }, label = { Text("Название") },
                    singleLine = true, colors = fieldColors()
                )
                OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                }) {
                    Text("Время приёма: %02d:%02d".format(hour, minute), color = Accent)
                }
                OutlinedTextField(
                    left, { left = it.filter(Char::isDigit) },
                    label = { Text("Таблеток в упаковке") },
                    singleLine = true, keyboardOptions = num, colors = fieldColors()
                )
                OutlinedTextField(
                    perDose, { perDose = it.filter(Char::isDigit) },
                    label = { Text("Таблеток за один приём") },
                    singleLine = true, keyboardOptions = num, colors = fieldColors()
                )
                OutlinedTextField(
                    repeat, { repeat = it.filter(Char::isDigit) },
                    label = { Text("Повторять каждые (мин), 0 = не повторять") },
                    singleLine = true, keyboardOptions = num, colors = fieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    Pill(
                        id = initial?.id ?: (System.currentTimeMillis() % 1_000_000_000L),
                        name = name.trim().ifEmpty { "Таблетка" },
                        hour = hour,
                        minute = minute,
                        left = left.toIntOrNull() ?: 0,
                        perDose = (perDose.toIntOrNull() ?: 1).coerceAtLeast(1),
                        repeatMin = repeat.toIntOrNull() ?: 0
                    )
                )
            }) { Text("Сохранить", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Muted) } }
    )
}
