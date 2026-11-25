package app.cashbook

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.foundation.shape.RoundedCornerShape
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
private val DEL_COL = 44.dp
private const val TABULAR = "tnum"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntriesPage(
    expenses: List<Expense>,
    isActive: Boolean,
    onAdd: (LocalDate, String, Long) -> Unit,
    onDelete: (Expense) -> Unit,
    onRestore: (Expense) -> Unit,
    onUpdate: (Expense) -> Unit,
) {
    val p = LocalPalette.current
    // Newest first, drawn bottom-up (reverseLayout), so the newest row sits directly above the entry row
    // and the list stays anchored to the bottom when the keyboard opens.
    val sorted = remember(expenses.toList()) {
        expenses.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The last deleted row, kept for 5 seconds so it can be restored with Undo.
    var undo by remember { mutableStateOf<Expense?>(null) }
    LaunchedEffect(undo) { if (undo != null) { delay(5000); undo = null } }
    // Which saved row is being edited, and which cell was tapped (1 = description, 2 = amount).
    var editing by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var datePickFor by remember { mutableStateOf<Expense?>(null) }
    // Bumped to send the cursor back to the entry row after an edit is finished.
    var refocusEntry by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        // Column headers
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(DEL_COL))
            HeaderText("Date", Modifier.width(DATE_COL))
            HeaderText("Description", Modifier.weight(1f))
            HeaderText("Amount", Modifier.width(AMT_COL), TextAlign.End)
        }
        HorizontalDivider(color = p.line)

        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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
                            .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Bin at the far left, away from the thumb. Deletes at once; Undo brings it back.
                        IconButton(onClick = { onDelete(e); undo = e }, modifier = Modifier.size(DEL_COL)) {
                            Icon(BinIcon, contentDescription = "Delete ${e.desc}",
                                tint = p.del, modifier = Modifier.size(21.dp))
                        }
                        // Every cell is tappable: date opens the calendar, the others become editable.
                        Text(e.date.format(DAY_FMT), color = p.muted, fontSize = 13.sp, style = numStyle(),
                            modifier = Modifier.width(DATE_COL).clip(RoundedCornerShape(6.dp))
                                .clickable { datePickFor = e }.padding(vertical = 10.dp))
                        val ed = editing
                        if (ed != null && ed.first == e.id) {
                            EditCells(
                                e = e, startField = ed.second, onUpdate = onUpdate,
                                modifier = Modifier.weight(1f),
                                onFinish = { byKeyboard ->
                                    if (editing?.first == e.id) editing = null
                                    if (byKeyboard) refocusEntry++
                                },
                            )
                        } else {
                            Text(e.desc, color = p.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).clickable { editing = e.id to 1 }.padding(vertical = 10.dp))
                            Text(formatRupees(e.paise), color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                style = numStyle(), textAlign = TextAlign.End,
                                modifier = Modifier.width(AMT_COL).clickable { editing = e.id to 2 }.padding(vertical = 10.dp))
                        }
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
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp + DEL_COL, top = 14.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
            undo?.let { e ->
                UndoBar(e, Modifier.align(Alignment.BottomCenter)) { onRestore(e); undo = null }
            }
        }

        EntryRow(isActive = isActive, refocus = refocusEntry, onAdd = { d, desc, paise ->
            onAdd(d, desc, paise)
            scope.launch { listState.scrollToItem(0) }
        })

        // Footer: page hint
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Swipe left for dashboard", color = p.muted, fontSize = 11.sp,
                modifier = Modifier.weight(1f))
            PageDots(0)
        }
    }

    datePickFor?.let { e ->
        DatePickDialog(e.date) { d ->
            if (d != null && d != e.date) onUpdate(e.copy(date = d))
            datePickFor = null
            refocusEntry++
        }
    }
}

/** Description and amount of a saved row, opened for typing. Valid changes are saved as you type. */
@Composable
private fun EditCells(
    e: Expense, startField: Int, onUpdate: (Expense) -> Unit, modifier: Modifier, onFinish: (byKeyboard: Boolean) -> Unit,
) {
    val p = LocalPalette.current
    val descFocus = remember { FocusRequester() }
    val amtFocus = remember { FocusRequester() }
    var desc by remember { mutableStateOf(TextFieldValue(e.desc, TextRange(e.desc.length))) }
    val startAmt = (e.paise / 100).toString() + if (e.paise % 100 != 0L) "." + (e.paise % 100).toString().padStart(2, '0') else ""
    var amount by remember { mutableStateOf(TextFieldValue(startAmt, TextRange(startAmt.length))) }
    var hadFocus by remember { mutableStateOf(false) }
    var descHas by remember { mutableStateOf(false) }
    var amtHas by remember { mutableStateOf(false) }
    val latest by androidx.compose.runtime.rememberUpdatedState(e)

    LaunchedEffect(Unit) { (if (startField == 2) amtFocus else descFocus).requestFocus() }
    // The edit ends only when neither field has had the cursor for a moment. Moving between the two
    // fields (Next) passes through a brief no-focus state, which must not count as leaving.
    LaunchedEffect(descHas, amtHas) {
        if (descHas || amtHas) hadFocus = true
        else if (hadFocus) { delay(300); onFinish(false) }
    }

    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(p.accentSoft).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = TextStyle(color = p.ink, fontSize = 15.sp)
        BasicTextField(
            value = desc,
            onValueChange = {
                desc = it
                val t = it.text.trim()
                if (t.isNotEmpty() && t != latest.desc) onUpdate(latest.copy(desc = t))
            },
            singleLine = true, textStyle = style, cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = false, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { amtFocus.requestFocus() }),
            modifier = Modifier.weight(1f).padding(end = 6.dp).focusRequester(descFocus)
                .onFocusChanged { descHas = it.isFocused },
        )
        Text("₹", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            value = amount,
            onValueChange = { v ->
                if (v.text.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) {
                    amount = v
                    parsePaise(v.text)?.let { if (it != latest.paise) onUpdate(latest.copy(paise = it)) }
                }
            },
            singleLine = true,
            textStyle = style.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, fontFeatureSettings = TABULAR),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onFinish(true) }),
            modifier = Modifier.width(AMT_COL - 22.dp).focusRequester(amtFocus)
                .onFocusChanged { amtHas = it.isFocused },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(initial: LocalDate, onClose: (LocalDate?) -> Unit) {
    val utcMillis = { d: LocalDate -> d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = utcMillis(initial),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= utcMillis(LocalDate.now())
        },
    )
    DatePickerDialog(
        onDismissRequest = { onClose(null) },
        confirmButton = {
            TextButton(onClick = {
                onClose(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text("OK") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onClose(LocalDate.now().minusDays(1)) }) { Text("Yesterday") }
                TextButton(onClick = { onClose(LocalDate.now()) }) { Text("Today") }
            }
        },
    ) { DatePicker(state = state) }
}

@Composable
private fun UndoBar(e: Expense, modifier: Modifier, onUndo: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier.padding(8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.ink)
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${e.desc} ${formatRupees(e.paise)} deleted", color = p.paper, fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(onClick = onUndo) {
            Text("UNDO", color = p.undo, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

/** Solid bin: lid with handle, tapered can, two cut-out ribs. */
val BinIcon: ImageVector = ImageVector.Builder("Bin", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M9.3 3.5h5.4c.6 0 1.1.5 1.1 1.1V6H20a1 1 0 1 1 0 2h-.8l-.9 10.6A2.6 2.6 0 0 1 15.7 21H8.3a2.6 2.6 0 0 1-2.6-2.4L4.8 8H4a1 1 0 1 1 0-2h4.2V4.6c0-.6.5-1.1 1.1-1.1Zm.9 2.5h3.6v-.6h-3.6V6Zm-.1 5a.9.9 0 0 0-.9.9v4.2a.9.9 0 1 0 1.8 0v-4.2a.9.9 0 0 0-.9-.9Zm3.8 0a.9.9 0 0 0-.9.9v4.2a.9.9 0 1 0 1.8 0v-4.2a.9.9 0 0 0-.9-.9Z"
    ),
    fill = SolidColor(Color.Black),
).build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryRow(isActive: Boolean, refocus: Int, onAdd: (LocalDate, String, Long) -> Unit) {
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
    LaunchedEffect(isActive, refocus) {
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
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(DEL_COL))
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
                modifier = Modifier.padding(start = 4.dp + DEL_COL, end = 16.dp, bottom = 8.dp))
        }
    }

    if (showPicker) {
        DatePickDialog(date) { d ->
            if (d != null) pickedDay = if (d == LocalDate.now()) Long.MIN_VALUE else d.toEpochDay()
            showPicker = false
            descFocus.requestFocus()
            keyboard?.show()
        }
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
