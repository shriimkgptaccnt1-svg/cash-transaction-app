package app.cashbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = ExpenseStore(applicationContext)
        setContent { CashBookTheme { App(store) } }
    }
}

@Composable
fun App(store: ExpenseStore) {
    val p = LocalPalette.current
    val expenses = remember { mutableStateListOf<Expense>().apply { addAll(store.load()) } }
    // Page 0 is the sheet, page 1 the dashboard: swiping finger right-to-left opens the dashboard.
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = pager.currentPage == 1) { scope.launch { pager.animateScrollToPage(0) } }

    // safeDrawingPadding includes the keyboard (IME) inset, so the whole screen shrinks to the space above
    // the keyboard. The entry row is the last thing in the sheet's column, so it always sits just above it.
    Box(Modifier.fillMaxSize().background(p.paper).safeDrawingPadding()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            if (page == 0) {
                EntriesPage(
                    expenses = expenses,
                    isActive = pager.settledPage == 0,
                    onAdd = { date: LocalDate, desc: String, paise: Long ->
                        val id = (expenses.maxOfOrNull { it.id } ?: 0L) + 1
                        expenses.add(Expense(id, date, desc, paise))
                        store.save(expenses)
                    },
                    onDelete = { e ->
                        expenses.removeAll { it.id == e.id }
                        store.save(expenses)
                    },
                )
            } else {
                DashboardPage(expenses)
            }
        }
    }
}
