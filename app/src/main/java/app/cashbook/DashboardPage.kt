package app.cashbook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
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

private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM")
private val MONTH_LETTERS = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

@Composable
fun DashboardPage(expenses: List<Expense>) {
    val p = LocalPalette.current
    val now = YearMonth.now()
    var yearMode by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(now) }
    var year by remember { mutableStateOf(now.year) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModeSwitch(yearMode) { yearMode = it }
        if (yearMode) {
            YearView(expenses, year, onYear = { year = it }, onOpenMonth = { month = it; yearMode = false })
        } else {
            MonthView(expenses, month) { month = it }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Swipe right to add an expense", color = p.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            PageDots(1)
        }
    }
}

@Composable
private fun ModeSwitch(yearMode: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.line).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(false to "Month", true to "Year").forEach { (mode, label) ->
            val on = mode == yearMode
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) p.paper else Color.Transparent)
                    .clickable { onChange(mode) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) p.ink else p.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

/** The outlined box that shows the chosen month or year and opens its picker. */
@Composable
private fun PickerBox(text: String, open: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).border(1.5.dp, p.accent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = numStyle(),
            modifier = Modifier.weight(1f))
        Text(if (open) "▴" else "▾", color = p.accent, fontSize = 14.sp)
    }
}

@Composable
private fun MonthView(expenses: List<Expense>, month: YearMonth, onMonth: (YearMonth) -> Unit) {
    val p = LocalPalette.current
    val now = YearMonth.now()
    var selectedDay by remember(month) { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf(false) }

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

    PickerBox(month.format(MONTH_TITLE), picking, Modifier.fillMaxWidth()) { picking = true }

    Column {
        Label(if (isCurrent) "Spent this month" else "Spent in ${month.format(MONTH_NAME)}")
        BigTotal(total)
        Delta(total, prevTotal, prevMonth.format(MONTH_NAME), isCurrent)
    }
    Splits(buckets)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Spending per day", Modifier.weight(1f))
            val sd = selectedDay
            if (sd != null) {
                Text("$sd ${month.format(MONTH_SHORT)}: ${formatRupees(perDay[sd - 1].sum())}",
                    color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Tap a bar", color = p.muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            EmptyNote("No expenses recorded in ${month.format(MONTH_NAME)}.")
        } else {
            StackedBars(
                stacks = perDay,
                axis = listOf(1, 8, 15, 22, days).map { it - 1 to it.toString() },
                mark = if (isCurrent) today - 1 else null,
                selected = selectedDay?.minus(1),
                onSelect = { i -> selectedDay = i?.plus(1) },
            )
        }
    }

    if (picking) {
        MonthPicker(expenses, month, onDismiss = { picking = false }) { onMonth(it); picking = false }
    }
}

@Composable
private fun MonthPicker(expenses: List<Expense>, current: YearMonth, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit) {
    val p = LocalPalette.current
    val now = YearMonth.now()
    val firstYear = minOf(expenses.minOfOrNull { it.date.year } ?: now.year, now.year)
    var shownYear by remember { mutableStateOf(current.year) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.paper).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton("‹", enabled = shownYear > firstYear) { shownYear-- }
                Text(shownYear.toString(), color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                RoundButton("›", enabled = shownYear < now.year) { shownYear++ }
            }
            for (r in 0 until 4) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0 until 3) {
                        val ym = YearMonth.of(shownYear, r * 3 + c + 1)
                        val future = ym > now
                        val on = ym == current
                        val sum = expenses.filter { YearMonth.from(it.date) == ym }.sumOf { it.paise }
                        val shape = RoundedCornerShape(12.dp)
                        Column(
                            Modifier.weight(1f).clip(shape)
                                .background(if (on) p.accent else Color.Transparent)
                                .border(1.dp, if (on) p.accent else p.line, shape)
                                .clickable(enabled = !future) { onPick(ym) }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val fg = when { on -> p.onAccent; future -> p.ghost; else -> p.ink }
                            Text(ym.format(MONTH_SHORT), color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(if (future) "—" else formatRupees(sum, alwaysWhole = true),
                                color = if (on) p.onAccent.copy(alpha = 0.85f) else if (future) p.ghost else p.muted,
                                fontSize = 11.sp, style = numStyle())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearView(expenses: List<Expense>, year: Int, onYear: (Int) -> Unit, onOpenMonth: (YearMonth) -> Unit) {
    val p = LocalPalette.current
    val nowDate = LocalDate.now()
    val isCurrent = year == nowDate.year
    var menu by remember { mutableStateOf(false) }
    var selected by remember(year) { mutableStateOf<Int?>(null) }

    val rows = expenses.filter { it.date.year == year }
    val total = rows.sumOf { it.paise }
    // For the running year, compare with the same stretch of last year ("so far").
    val prevTotal = expenses.filter {
        it.date.year == year - 1 && (!isCurrent || !it.date.isAfter(nowDate.minusYears(1)))
    }.sumOf { it.paise }
    val buckets = LongArray(3).also { b -> rows.forEach { b[bucketOf(it)] += it.paise } }
    val perMonth = Array(12) { LongArray(3) }
    rows.forEach { perMonth[it.date.monthValue - 1][bucketOf(it)] += it.paise }
    val monthsCounted = if (isCurrent) nowDate.monthValue else 12
    val costliest = (0 until 12).maxByOrNull { perMonth[it].sum() }?.takeIf { perMonth[it].sum() > 0 }
    val years = (expenses.map { it.date.year } + nowDate.year).distinct().sortedDescending()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Label("Spent in the year", Modifier.weight(1f))
        Box {
            PickerBox(year.toString(), menu, Modifier.width(150.dp)) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.width(170.dp),
                containerColor = p.paper) {
                years.forEach { y ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(y.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    color = if (y == year) p.accent else p.ink, modifier = Modifier.weight(1f))
                                Text(formatRupees(expenses.filter { it.date.year == y }.sumOf { it.paise }, alwaysWhole = true),
                                    color = p.muted, fontSize = 12.sp, style = numStyle())
                            }
                        },
                        onClick = { onYear(y); menu = false },
                    )
                }
            }
        }
    }
    Column {
        BigTotal(total)
        Delta(total, prevTotal, (year - 1).toString(), isCurrent)
    }
    Splits(buckets)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SoftTile("Per month", formatRupees(if (total == 0L) 0 else total / monthsCounted, alwaysWhole = true), Modifier.weight(1f))
        SoftTile("Costliest",
            costliest?.let { "${YearMonth.of(year, it + 1).format(MONTH_SHORT)} · ${formatRupees(perMonth[it].sum(), alwaysWhole = true)}" } ?: "—",
            Modifier.weight(1f))
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Spending per month", Modifier.weight(1f))
            val s = selected
            if (s != null) {
                Text("${YearMonth.of(year, s + 1).format(MONTH_SHORT)}: ${formatRupees(perMonth[s].sum(), alwaysWhole = true)} · tap again to open",
                    color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Tap a bar", color = p.muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            EmptyNote("No expenses recorded in $year.")
        } else {
            StackedBars(
                stacks = perMonth,
                axis = MONTH_LETTERS.mapIndexed { i, l -> i to l },
                mark = null,
                selected = selected,
                onSelect = { i ->
                    if (i != null && i == selected) onOpenMonth(YearMonth.of(year, i + 1)) else selected = i
                },
            )
        }
    }
}

@Composable
private fun BigTotal(paise: Long) {
    Text(formatRupees(paise, alwaysWhole = true), color = LocalPalette.current.ink, fontSize = 38.sp,
        fontWeight = FontWeight.Bold, style = numStyle())
}

@Composable
private fun Delta(total: Long, prevTotal: Long, prevName: String, soFar: Boolean) {
    val p = LocalPalette.current
    val so = if (soFar) " so far" else ""
    val (text, color) = if (prevTotal == 0L) {
        "No entries in $prevName to compare" to p.muted
    } else {
        val pct = ((total - prevTotal) * 100.0 / prevTotal).roundToInt()
        when {
            pct > 0 -> "▲ $pct% more than $prevName$so" to p.warn
            pct < 0 -> "▼ ${abs(pct)}% less than $prevName$so" to p.accent
            else -> "Same as $prevName$so" to p.accent
        }
    }
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Splits(buckets: LongArray) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SplitTile("Food", buckets[0], p.bar, Modifier.weight(1f))
        SplitTile("Laundry", buckets[1], p.bar2, Modifier.weight(1f))
        SplitTile("Other", buckets[2], p.ghost, Modifier.weight(1f))
    }
}

@Composable
private fun SoftTile(label: String, value: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(p.accentSoft).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Label(label)
        Text(value, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = numStyle(), maxLines = 1)
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = LocalPalette.current.muted, fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = TextAlign.Center)
}

/** Stacked bars (Food / Laundry / Other) for each slot: days of a month or months of a year. */
@Composable
private fun StackedBars(
    stacks: Array<LongArray>, axis: List<Pair<Int, String>>, mark: Int?, selected: Int?, onSelect: (Int?) -> Unit,
) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = p.muted, fontSize = 10.sp)
    val boldStyle = TextStyle(color = p.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val n = stacks.size
    val maxRupees = (stacks.maxOf { it.sum() } / 100.0).coerceAtLeast(1.0)
    val step = niceStep(maxRupees / 3)
    val top = ceil(maxRupees / step) * step
    val colors = listOf(p.bar, p.bar2, p.ghost)

    Canvas(
        Modifier.fillMaxWidth().height(190.dp).pointerInput(n, selected) {
            detectTapGestures { pos ->
                val left = 36.dp.toPx()
                val bw = (size.width - left) / n
                val i = ((pos.x - left) / bw).toInt()
                onSelect(if (pos.x >= left && i in 0 until n && stacks[i].sum() > 0) i else null)
            }
        },
    ) {
        val left = 36.dp.toPx()
        val bottom = 18.dp.toPx()
        val topPad = 6.dp.toPx()
        val ph = size.height - bottom - topPad
        val bw = (size.width - left) / n
        fun y(v: Double) = (topPad + ph - v / top * ph).toFloat()

        var v = 0.0
        while (v <= top + 0.001) {
            drawLine(p.line, Offset(left, y(v)), Offset(size.width, y(v)), strokeWidth = 1.dp.toPx())
            val t = measurer.measure(shortRupees(v), labelStyle)
            drawText(t, topLeft = Offset(left - 6.dp.toPx() - t.size.width, y(v) - t.size.height / 2f))
            v += step
        }
        mark?.let {
            val x = left + (it + 0.5f) * bw
            drawLine(p.muted, Offset(x, topPad), Offset(x, topPad + ph), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        }
        for (i in 0 until n) {
            var base = 0.0
            val x = left + i * bw + bw * 0.17f
            val w = bw * 0.66f
            val dim = selected != null && selected != i
            for (b in 0..2) {
                val amt = stacks[i][b] / 100.0
                if (amt <= 0) continue
                val yTop = y(base + amt)
                drawRect(colors[b].copy(alpha = if (dim) 0.35f else 1f), topLeft = Offset(x, yTop), size = Size(w, y(base) - yTop))
                base += amt
            }
        }
        axis.forEach { (i, label) ->
            val t = measurer.measure(label, if (i == selected) boldStyle else labelStyle)
            drawText(t, topLeft = Offset(left + (i + 0.5f) * bw - t.size.width / 2f, size.height - t.size.height))
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
