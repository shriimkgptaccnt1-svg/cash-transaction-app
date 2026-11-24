package app.cashbook

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** One cash expense. Amounts are stored in paise so totals never pick up rounding errors. */
data class Expense(val id: Long, val date: LocalDate, val desc: String, val paise: Long)

/** The descriptions that autofill from their first letters. */
val QUICK_DESCRIPTIONS = listOf("Food", "Laundry")

/** Saves expenses as a small JSON file in the app's private storage. */
class ExpenseStore(context: Context) {
    private val file = File(context.filesDir, "expenses.json")

    fun load(): List<Expense> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Expense(o.getLong("id"), LocalDate.ofEpochDay(o.getLong("day")), o.getString("desc"), o.getLong("paise"))
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<Expense>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("day", it.date.toEpochDay()).put("desc", it.desc).put("paise", it.paise))
        }
        // Write to a temp file and rename, so a crash mid-write cannot corrupt the saved data.
        val tmp = File(file.parentFile, "expenses.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) { file.writeText(arr.toString()); tmp.delete() }
    }
}

private val inrFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))

fun formatRupees(paise: Long, alwaysWhole: Boolean = false): String {
    val whole = paise / 100
    val frac = paise % 100
    return if (frac == 0L || alwaysWhole) "₹" + inrFormat.format(if (alwaysWhole) Math.round(paise / 100.0) else whole)
    else "₹" + inrFormat.format(whole) + "." + frac.toString().padStart(2, '0')
}

/** "250", "250.5", "250.50" → paise. Returns null for anything that isn't a positive amount. */
fun parsePaise(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty() || t == ".") return null
    val parts = t.split(".")
    val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    val frac = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: return null else 0L
    val p = whole * 100 + frac
    return if (p > 0) p else null
}

/** Which bucket the dashboard puts an expense in. */
fun bucketOf(e: Expense): Int = when {
    e.desc.equals("Food", ignoreCase = true) -> 0
    e.desc.equals("Laundry", ignoreCase = true) -> 1
    else -> 2
}
