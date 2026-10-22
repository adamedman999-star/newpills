package com.tim.pills

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "pills_channel"
    const val EXTRA_ID = "pill_id"

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Напоминания о таблетках", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    // Когда напомнить в следующий раз
    fun nextTime(p: Pill, now: LocalDateTime, takenToday: Boolean): LocalDateTime {
        val first = now.toLocalDate().atTime(p.hour, p.minute)
        if (takenToday) return first.plusDays(1)
        if (now.isBefore(first)) return first
        if (p.repeatMin <= 0) return first.plusDays(1)
        val passed = Duration.between(first, now).toMinutes()
        val k = passed / p.repeatMin + 1
        val next = first.plusMinutes(k * p.repeatMin)
        return if (next.toLocalDate() == now.toLocalDate()) next else first.plusDays(1)
    }

    private fun alarmIntent(c: Context, id: Long): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            c, id.toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(c: Context, p: Pill) {
        val am = c.getSystemService(AlarmManager::class.java)
        val taken = Store.isTaken(c, p.id, LocalDate.now())
        val at = nextTime(p, LocalDateTime.now(), taken)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = alarmIntent(c, p.id)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun cancel(c: Context, id: Long) {
        c.getSystemService(AlarmManager::class.java).cancel(alarmIntent(c, id))
        c.getSystemService(NotificationManager::class.java).cancel(id.toInt())
    }

    fun scheduleAll(c: Context) {
        Store.loadPills(c).forEach { schedule(c, it) }
    }

    fun notify(c: Context, p: Pill) {
        ensureChannel(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val takeIntent = Intent(c, TakeReceiver::class.java).putExtra(EXTRA_ID, p.id)
        val take = PendingIntent.getBroadcast(
            c, p.id.toInt() + 1, takeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(c, R.drawable.ic_stat_pill), "Выпил", take
        ).build()
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle("Пора выпить таблетку")
            .setContentText("${p.name} · осталось ${p.left} шт.")
            .setContentIntent(open)
            .addAction(action)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(p.id.toInt(), n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra(Reminders.EXTRA_ID, -1)
        val p = Store.loadPills(c).find { it.id == id } ?: return
        if (!Store.isTaken(c, id, LocalDate.now())) Reminders.notify(c, p)
        Reminders.schedule(c, p)
    }
}

class TakeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra(Reminders.EXTRA_ID, -1)
        Store.setTaken(c, id, LocalDate.now(), true)
        c.getSystemService(NotificationManager::class.java).cancel(id.toInt())
        Store.loadPills(c).find { it.id == id }?.let { Reminders.schedule(c, it) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Reminders.scheduleAll(c)
    }
}
