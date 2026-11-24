package app.cashbook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy")
private val MONTH_NAME = DateTimeFormatter.ofPattern("MMMM")

@Composable
fun DashboardPage(expenses: List<Expense>) {
    val p = LocalPalette.current
    val now = YearMonth.now()
    var month by remember { mutableStateOf(now) }
    var selectedDay by remember(month) { mutableStateOf<Int?>(null) }

    val isCurrent = month == now
    val today = LocalDate.now().dayOfMonth
    val rows = expenses.filter { YearMonth.from(it.date) == month }
    val total = rows.sumOf { it.paise }
    val prevMonth = month.minusMonths(1)
    // For the running month, compare with the same days of last month ("so far").
    val prevTotal = expenses.filter {
        YearMonth.from(it.date) == prevMonth && (!isCurrent || it.date.dayOfMonth <= today)
    }.sumOf { it.paise }
    val buckets = LongArray(3).also { b -> rows.forEach { b[bucketOf(it)] += it.paise } }

    val days = month.lengthOfMonth()
    val perDay = Array(days) { LongArray(3) }
    rows.forEach { perDay[it.date.dayOfMonth - 1][bucketOf(it)] += it.paise }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Month switcher
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton("‹", enabled = true) { month = month.minusMonths(1) }
            Text(month.format(MONTH_TITLE), color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            RoundButton("›", enabled = month < now) { month = month.plusMonths(1) }
        }

        Column {
            Label(if (isCurrent) "Spent this month" else "Spent in ${month.format(MONTH_NAME)}")
            Text(formatRupees(total, alwaysWhole = true), color = p.ink, fontSize = 38.sp, fontWeight = FontWeight.Bold,
                style = numStyle())
            val deltaText: String
            val deltaColor = when {
                prevTotal == 0L -> p.muted
                total > prevTotal -> p.warn
                else -> p.accent
            }
            if (prevTotal == 0L) {
                deltaText = "No entries in ${prevMonth.format(MONTH_NAME)} to compare"
            } else {
                val pct = ((total - prevTotal) * 100.0 / prevTotal).roundToInt()
                val so = if (isCurrent) " so far" else ""
                deltaText = when {
                    pct > 0 -> "▲ $pct% more than ${prevMonth.format(MONTH_NAME)}$so"
                    pct < 0 -> "▼ ${abs(pct)}% less than ${prevMonth.format(MONTH_NAME)}$so"
                    else -> "Same as ${prevMonth.format(MONTH_NAME)}$so"
                }
            }
            Text(deltaText, color = deltaColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SplitTile("Food", buckets[0], p.bar, Modifier.weight(1f))
            SplitTile("Laundry", buckets[1], p.bar2, Modifier.weight(1f))
            SplitTile("Other", buckets[2], p.ghost, Modifier.weight(1f))
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Spending per day", Modifier.weight(1f))
                val sd = selectedDay
                if (sd != null) {
                    Text("$sd ${month.format(DateTimeFormatter.ofPattern("MMM"))}: ${formatRupees(perDay[sd - 1].sum())}",
                        color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Tap a bar", color = p.muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (rows.isEmpty()) {
                Text("No expenses recorded in ${month.format(MONTH_NAME)}.", color = p.muted, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            } else {
                DailyChart(perDay, if (isCurrent) today else null, selectedDay) { selectedDay = it }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Swipe right to add an expense", color = p.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            PageDots(1)
        }
    }
}

@Composable
private fun DailyChart(perDay: Array<LongArray>, todayMark: Int?, selected: Int?, onSelect: (Int?) -> Unit) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = p.muted, fontSize = 10.sp)
    val days = perDay.size
    val maxRupees = (perDay.maxOf { it.sum() } / 100.0).coerceAtLeast(1.0)
    val step = niceStep(maxRupees / 3)
    val top = ceil(maxRupees / step) * step
    val colors = listOf(p.bar, p.bar2, p.ghost)

    Canvas(
        Modifier.fillMaxWidth().height(190.dp).pointerInput(days) {
            detectTapGestures { pos ->
                val left = 36.dp.toPx()
                val bw = (size.width - left) / days
                val d = ((pos.x - left) / bw).toInt() + 1
                onSelect(if (d in 1..days && perDay[d - 1].sum() > 0) d else null)
            }
        },
    ) {
        val left = 36.dp.toPx()
        val bottom = 18.dp.toPx()
        val topPad = 6.dp.toPx()
        val ph = size.height - bottom - topPad
        val pw = size.width - left
        val bw = pw / days
        fun y(v: Double) = (topPad + ph - v / top * ph).toFloat()

        var v = 0.0
        while (v <= top + 0.001) {
            drawLine(p.line, Offset(left, y(v)), Offset(size.width, y(v)), strokeWidth = 1.dp.toPx())
            val t = measurer.measure(shortRupees(v), labelStyle)
            drawText(t, topLeft = Offset(left - 6.dp.toPx() - t.size.width, y(v) - t.size.height / 2f))
            v += step
        }
        todayMark?.let {
            val x = left + (it - 0.5f) * bw
            drawLine(p.muted, Offset(x, topPad), Offset(x, topPad + ph), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        }
        for (d in 0 until days) {
            var base = 0.0
            val x = left + d * bw + bw * 0.17f
            val w = bw * 0.66f
            val dim = selected != null && selected != d + 1
            for (b in 0..2) {
                val amt = perDay[d][b] / 100.0
                if (amt <= 0) continue
                val yTop = y(base + amt)
                val c = colors[b].copy(alpha = if (dim) 0.35f else 1f)
                drawRect(c, topLeft = Offset(x, yTop), size = Size(w, y(base) - yTop))
                base += amt
            }
        }
        listOf(1, 8, 15, 22, days).forEach { d ->
            val t = measurer.measure(d.toString(), labelStyle)
            drawText(t, topLeft = Offset(left + (d - 0.5f) * bw - t.size.width / 2f, size.height - t.size.height))
        }
    }
}

private fun niceStep(x: Double): Double {
    val pw = 10.0.pow(floor(log10(x)))
    val f = x / pw
    return (if (f <= 1) 1.0 else if (f <= 2) 2.0 else if (f <= 5) 5.0 else 10.0) * pw
}

private fun shortRupees(v: Double): String = when {
    v >= 1000 -> { val k = v / 1000; if (k % 1.0 == 0.0) "${k.toInt()}k" else "%.1fk".format(k) }
    else -> v.toInt().toString()
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = LocalPalette.current.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, modifier = modifier)
}

@Composable
private fun SplitTile(name: String, paise: Long, swatch: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.border(1.dp, p.line, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(swatch))
            Spacer(Modifier.width(5.dp))
            Label(name)
        }
        Text(formatRupees(paise, alwaysWhole = true), color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            style = numStyle())
    }
}

@Composable
private fun RoundButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    TextButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(40.dp).border(1.dp, p.line, CircleShape),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Text(text, fontSize = 20.sp, color = if (enabled) p.ink else p.line)
    }
}
