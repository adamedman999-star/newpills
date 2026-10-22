package com.tim.pills

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Pill(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val left: Int,
    val perDose: Int,
    val repeatMin: Int
)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("pills", Context.MODE_PRIVATE)

    @Synchronized
    fun loadPills(c: Context): List<Pill> {
        val arr = JSONArray(prefs(c).getString("pills", "[]") ?: "[]")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Pill(
                o.getLong("id"), o.getString("name"),
                o.getInt("hour"), o.getInt("minute"),
                o.getInt("left"), o.getInt("perDose"), o.getInt("repeatMin")
            )
        }.sortedBy { it.hour * 60 + it.minute }
    }

    @Synchronized
    fun savePills(c: Context, list: List<Pill>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id).put("name", p.name)
                    .put("hour", p.hour).put("minute", p.minute)
                    .put("left", p.left).put("perDose", p.perDose)
                    .put("repeatMin", p.repeatMin)
            )
        }
        prefs(c).edit().putString("pills", arr.toString()).apply()
    }

    @Synchronized
    fun loadHistory(c: Context): Map<String, Set<Long>> {
        val o = JSONObject(prefs(c).getString("history", "{}") ?: "{}")
        val res = mutableMapOf<String, Set<Long>>()
        o.keys().forEach { k ->
            val a = o.getJSONArray(k)
            res[k] = (0 until a.length()).map { a.getLong(it) }.toSet()
        }
        return res
    }

    @Synchronized
    private fun saveHistory(c: Context, h: Map<String, Set<Long>>) {
        val o = JSONObject()
        h.forEach { (k, v) -> o.put(k, JSONArray(v.toList())) }
        prefs(c).edit().putString("history", o.toString()).apply()
    }

    fun isTaken(c: Context, pillId: Long, date: LocalDate): Boolean =
        loadHistory(c)[date.toString()]?.contains(pillId) == true

    @Synchronized
    fun setTaken(c: Context, pillId: Long, date: LocalDate, taken: Boolean) {
        val hist = loadHistory(c).toMutableMap()
        val key = date.toString()
        val set = (hist[key] ?: emptySet()).toMutableSet()
        val pills = loadPills(c).toMutableList()
        val i = pills.indexOfFirst { it.id == pillId }
        if (i < 0) return
        val p = pills[i]
        if (taken) {
            if (set.add(pillId)) {
                pills[i] = p.copy(left = (p.left - p.perDose).coerceAtLeast(0))
            }
        } else {
            if (set.remove(pillId)) {
                pills[i] = p.copy(left = p.left + p.perDose)
            }
        }
        if (set.isEmpty()) hist.remove(key) else hist[key] = set
        saveHistory(c, hist)
        savePills(c, pills)
    }
}
