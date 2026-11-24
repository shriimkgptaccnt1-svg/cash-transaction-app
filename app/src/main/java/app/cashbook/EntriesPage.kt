package app.cashbook

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DAY_FMT = DateTimeFormatter.ofPattern("dd MMM")
private val MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DATE_COL = 58.dp
private val AMT_COL = 84.dp
private const val TABULAR = "tnum"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntriesPage(
    expenses: List<Expense>,
    isActive: Boolean,
    onAdd: (LocalDate, String, Long) -> Unit,
    onDelete: (Expense) -> Unit,
) {
    val p = LocalPalette.current
    // Newest first, drawn bottom-up (reverseLayout), so the newest row sits directly above the entry row
    // and the list stays anchored to the bottom when the keyboard opens.
    val sorted = remember(expenses.toList()) {
        expenses.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<Expense?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Column headers
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderText("Date", Modifier.width(DATE_COL))
            HeaderText("Description", Modifier.weight(1f))
            HeaderText("Amount", Modifier.width(AMT_COL), TextAlign.End)
        }
        HorizontalDivider(color = p.line)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
        ) {
            if (sorted.isEmpty()) {
                item {
                    Text(
                        "No expenses yet. Type your first one in the row below.",
                        color = p.muted, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center,
                    )
                }
            }
            sorted.forEachIndexed { i, e ->
                item(key = e.id) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {}, onLongClick = { toDelete = e })
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(e.date.format(DAY_FMT), color = p.muted, fontSize = 13.sp,
                            style = numStyle(), modifier = Modifier.width(DATE_COL))
                        Text(e.desc, color = p.ink, fontSize = 15.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(formatRupees(e.paise), color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            style = numStyle(), textAlign = TextAlign.End, modifier = Modifier.width(AMT_COL))
                    }
                    HorizontalDivider(color = p.line)
                }
                // In a reversed list, an item emitted later is drawn higher up: this puts a month label
                // above the oldest entry of each month.
                val next = sorted.getOrNull(i + 1)
                if (next == null || next.date.month != e.date.month || next.date.year != e.date.year) {
                    item(key = "m-${e.date.year}-${e.date.monthValue}") {
                        Text(
                            e.date.format(MONTH_FMT).uppercase(),
                            color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }

        EntryRow(isActive = isActive, onAdd = { d, desc, paise ->
            onAdd(d, desc, paise)
            scope.launch { listState.scrollToItem(0) }
        })

        // Footer: page hint
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Swipe left for dashboard  ·  Long-press a row to delete", color = p.muted, fontSize = 11.sp,
                modifier = Modifier.weight(1f))
            PageDots(0)
        }
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this expense?") },
            text = { Text("${e.date.format(DAY_FMT)} · ${e.desc} · ${formatRupees(e.paise)}") },
            confirmButton = { TextButton(onClick = { onDelete(e); toDelete = null }) { Text("Delete", color = p.warn) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryRow(isActive: Boolean, onAdd: (LocalDate, String, Long) -> Unit) {
    val p = LocalPalette.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val descFocus = remember { FocusRequester() }
    val amtFocus = remember { FocusRequester() }

    // Date stays "today" unless the user picks another day for this row.
    var pickedDay by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    val today = LocalDate.now()
    val date = if (pickedDay == Long.MIN_VALUE) today else LocalDate.ofEpochDay(pickedDay)
    var showPicker by remember { mutableStateOf(false) }

    var desc by remember { mutableStateOf(TextFieldValue("")) }
    var amount by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    // Autofill: "f" → Food, "l" → Laundry. Any other letter makes the suggestion disappear.
    val typed = desc.text
    val suggestion = QUICK_DESCRIPTIONS.firstOrNull {
        typed.isNotEmpty() && it.startsWith(typed, ignoreCase = true) && it != typed
    }
    fun acceptSuggestion() {
        suggestion?.let { desc = TextFieldValue(it, TextRange(it.length)) }
    }

    // Opening the sheet puts the cursor in Description and raises the keyboard.
    LaunchedEffect(isActive) {
        if (isActive) {
            delay(150)
            descFocus.requestFocus()
            keyboard?.show()
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    fun save() {
        val text = (suggestion ?: desc.text).trim()
        val paise = parsePaise(amount)
        when {
            text.isEmpty() -> { message = "Type a description first"; descFocus.requestFocus() }
            paise == null -> { message = "Enter an amount above 0"; amtFocus.requestFocus() }
            else -> {
                val day = if (pickedDay == Long.MIN_VALUE) LocalDate.now() else date
                onAdd(day, text, paise)
                message = "Saved ${formatRupees(paise)} for $text"
                desc = TextFieldValue("")
                amount = ""
                pickedDay = Long.MIN_VALUE
                descFocus.requestFocus()
            }
        }
    }

    Column(Modifier.fillMaxWidth().background(p.accentSoft)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.accent))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Date cell: autofilled with today, tap to change.
            Column(
                Modifier.width(DATE_COL).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .clickable { showPicker = true }.padding(vertical = 2.dp),
            ) {
                Text(date.format(DAY_FMT), color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, style = numStyle())
                Text(if (date == today) "Today ▾" else "Change ▾", color = p.accent.copy(alpha = 0.75f), fontSize = 10.sp)
            }

            // Description with inline autofill
            val textStyle = TextStyle(color = p.ink, fontSize = 16.sp)
            BasicTextField(
                value = desc,
                onValueChange = { desc = it; message = null },
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { acceptSuggestion(); amtFocus.requestFocus() }),
                modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(descFocus)
                    .onFocusChanged { if (!it.isFocused) acceptSuggestion() },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (typed.isEmpty()) Text("Description", style = textStyle.copy(color = p.ghost))
                        if (suggestion != null) {
                            // The typed part is drawn invisibly so the rest of the word lines up after it.
                            Text(buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.Transparent)) { append(typed) }
                                withStyle(SpanStyle(color = p.onAccent, background = p.accent.copy(alpha = 0.85f))) {
                                    append(suggestion.substring(typed.length))
                                }
                            }, style = textStyle, maxLines = 1)
                        }
                        inner()
                    }
                },
            )

            // Amount
            Row(Modifier.width(AMT_COL), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text("₹", color = if (amount.isEmpty()) p.ghost else p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(2.dp))
                BasicTextField(
                    value = amount,
                    onValueChange = { v ->
                        if (v.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) { amount = v; message = null }
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                        fontFeatureSettings = TABULAR),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.weight(1f).focusRequester(amtFocus),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (amount.isEmpty()) Text("0", color = p.ghost, fontSize = 16.sp)
                            inner()
                        }
                    },
                )
            }
        }
        message?.let {
            Text(it, color = if (it.startsWith("Saved")) p.accent else p.warn, fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        }
    }

    if (showPicker) {
        val utcMillis = { d: LocalDate -> d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = utcMillis(date),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= utcMillis(LocalDate.now())
            },
        )
        fun close(d: LocalDate?) {
            if (d != null) pickedDay = if (d == LocalDate.now()) Long.MIN_VALUE else d.toEpochDay()
            showPicker = false
            descFocus.requestFocus()
            keyboard?.show()
        }
        DatePickerDialog(
            onDismissRequest = { close(null) },
            confirmButton = {
                TextButton(onClick = {
                    close(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { close(LocalDate.now().minusDays(1)) }) { Text("Yesterday") }
                    TextButton(onClick = { close(LocalDate.now()) }) { Text("Today") }
                }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(text.uppercase(), color = LocalPalette.current.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, textAlign = align, modifier = modifier)
}

@Composable
fun PageDots(active: Int) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(2) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (it == active) p.accent else p.line))
        }
    }
}

fun numStyle() = TextStyle(fontFeatureSettings = TABULAR)
