package com.anuajay.splitup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions

class MainActivity : ComponentActivity() {
    private var groups by mutableStateOf<List<Group>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groups = Store.load(this)
        setContent { SplitTheme { App(groups) { groups = it; Store.save(this, it) } } }
    }
}

/** Dates are stored as UTC midnight of the chosen day, so the day never shifts with time zones. */
private fun todayUtc(): Long {
    val c = java.util.Calendar.getInstance()
    return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear(); set(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun fmtDate(ms: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))

/** True when the expense was an equal split, so it can be re-split when someone joins. */
private fun isEqualSplit(e: Expense) = e.shares.isNotEmpty() && (e.shares.values.max() - e.shares.values.min()) <= 1L

@Composable
fun SplitTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF5EEAD4), onPrimary = Color(0xFF042F2E), surface = Color(0xFF111418), onSurface = Color(0xFFE6EAEE), surfaceVariant = Color(0xFF1E252B), background = Color(0xFF111418), onBackground = Color(0xFFE6EAEE))
    else lightColorScheme(primary = Color(0xFF0F766E), onPrimary = Color.White, surface = Color(0xFFF7FAFA), onSurface = Color(0xFF111418), surfaceVariant = Color(0xFFE3EEEC), background = Color(0xFFF7FAFA), onBackground = Color(0xFF111418))
    MaterialTheme(colorScheme = scheme) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content) }
}

@Composable
private fun App(groups: List<Group>, onChange: (List<Group>) -> Unit) {
    var openId by remember { mutableStateOf<String?>(null) }
    val open = groups.firstOrNull { it.id == openId }
    BackHandler(enabled = open != null) { openId = null }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (open == null) Home(groups, onOpen = { openId = it }, onChange = onChange)
        else GroupScreen(open, onBack = { openId = null }, onUpdate = { g -> onChange(groups.map { if (it.id == g.id) g else it }) },
            onDelete = { onChange(groups.filter { it.id != open.id }); openId = null })
    }
}

@Composable
private fun Home(groups: List<Group>, onOpen: (String) -> Unit, onChange: (List<Group>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("SplitUp", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Split bills with friends. No limits, no ads, no account.", fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            if (groups.isEmpty()) Text("No groups yet. Tap + to make one (for example 'Goa trip' or 'Flat').")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(groups, key = { it.id }) { g ->
                    val total = g.expenses.sumOf { it.amount }
                    Card(Modifier.fillMaxWidth().clickable { onOpen(g.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(g.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("${g.members.size} people · ${g.expenses.size} expenses · ${Money.format(total)} total", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Text("+", fontSize = 26.sp) }
    }
    if (adding) NewGroupDialog(onDismiss = { adding = false }) { name, people ->
        val members = people.map { Member(newId(), it) }
        onChange(groups + Group(newId(), name, members))
        adding = false
    }
}

@Composable
private fun NewGroupDialog(onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var people by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Group name") }, singleLine = true)
                OutlinedTextField(people, { people = it }, label = { Text("People, separated by commas (include yourself)") }, minLines = 2)
            }
        },
        confirmButton = {
            val list = people.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            TextButton(enabled = name.isNotBlank() && list.size >= 2, onClick = { onCreate(name.trim(), list) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GroupScreen(g: Group, onBack: () -> Unit, onUpdate: (Group) -> Unit, onDelete: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var addingExpense by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var editMembers by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val names = g.members.associate { it.id to it.name }
    val balances = Split.balances(g)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back") }
                Text(g.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { editMembers = true }) { Text("People") }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Expenses") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Balances") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Settle up") })
            }
            when (tab) {
                0 -> LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (g.expenses.isEmpty()) item { Text("No expenses yet. Tap + to add the first one.") } else item { Text("Tap an expense to edit its date, amount or who shares it.", fontSize = 12.sp) }
                    items(g.expenses.sortedByDescending { it.date }, key = { it.id }) { e ->
                        Card(Modifier.fillMaxWidth().clickable { editing = e }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, fontWeight = FontWeight.SemiBold)
                                    Text("${fmtDate(e.date)} · ${names[e.paidBy] ?: "?"} paid · split between ${e.shares.size}", fontSize = 12.sp)
                                }
                                Text(Money.format(e.amount), fontWeight = FontWeight.Bold)
                                TextButton(onClick = { onUpdate(g.copy(expenses = g.expenses.filter { it.id != e.id })) }) { Text("Delete") }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
                1 -> Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.members.forEach { m ->
                        val b = balances[m.id] ?: 0L
                        Row(Modifier.fillMaxWidth()) {
                            Text(m.name, Modifier.weight(1f), fontSize = 16.sp)
                            Text(
                                when { b > 0 -> "is owed ${Money.format(b)}"; b < 0 -> "owes ${Money.format(-b)}"; else -> "settled up" },
                                color = when { b > 0 -> Color(0xFF15803D); b < 0 -> Color(0xFFB91C1C); else -> MaterialTheme.colorScheme.onSurface },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                else -> SettleTab(g, names, ctx, onUpdate)
            }
        }
        if (tab == 0) FloatingActionButton(onClick = { addingExpense = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Text("+", fontSize = 26.sp) }
    }
    if (addingExpense) ExpenseDialog(g, null, onDismiss = { addingExpense = false }) { e -> onUpdate(g.copy(expenses = g.expenses + e)); addingExpense = false }
    editing?.let { old -> ExpenseDialog(g, old, onDismiss = { editing = null }) { e -> onUpdate(g.copy(expenses = g.expenses.map { if (it.id == old.id) e else it })); editing = null } }
    if (editMembers) MembersDialog(g, onDismiss = { editMembers = false }, onUpdate = onUpdate, onDelete = { editMembers = false; confirmDelete = true })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete '${g.name}'?") },
        text = { Text("All its expenses are removed from this phone. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
    )
}

@Composable
private fun SettleTab(g: Group, names: Map<String, String>, ctx: Context, onUpdate: (Group) -> Unit) {
    val plan = Split.plan(g)
    var needUpi by remember { mutableStateOf<Member?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (plan.isEmpty()) Text("Everyone is settled up.")
        else Text("The fewest payments that settle everyone:", fontSize = 13.sp)
        plan.forEach { t ->
            val to = g.members.first { it.id == t.to }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${names[t.from]} pays ${names[t.to]}  ${Money.format(t.amount)}", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (to.upi.isBlank()) needUpi = to else payViaUpi(ctx, to, t.amount, g.name) }) { Text("Pay with UPI") }
                        OutlinedButton(onClick = { onUpdate(g.copy(payments = g.payments + Payment(newId(), t.from, t.to, t.amount, System.currentTimeMillis()))) }) { Text("Mark as paid") }
                    }
                }
            }
        }
        if (g.payments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Payments recorded", fontWeight = FontWeight.SemiBold)
            g.payments.sortedByDescending { it.date }.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${names[p.from]} → ${names[p.to]}  ${Money.format(p.amount)}", Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { onUpdate(g.copy(payments = g.payments.filter { it.id != p.id })) }) { Text("Undo") }
                }
            }
        }
        Text("Pay with UPI opens your UPI app with the amount filled in. You confirm the payment there. SplitUp never moves money. After paying, tap 'Mark as paid'.", fontSize = 12.sp)
    }
    needUpi?.let { m ->
        var upi by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { needUpi = null },
            title = { Text("${m.name}'s UPI ID") },
            text = { OutlinedTextField(upi, { upi = it }, label = { Text("for example name@bank") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = upi.contains("@"), onClick = {
                    onUpdate(g.copy(members = g.members.map { if (it.id == m.id) it.copy(upi = upi.trim()) else it }))
                    needUpi = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { needUpi = null }) { Text("Cancel") } },
        )
    }
}

private fun payViaUpi(ctx: Context, to: Member, paise: Long, note: String) {
    val uri = Uri.Builder().scheme("upi").authority("pay")
        .appendQueryParameter("pa", to.upi)
        .appendQueryParameter("pn", to.name)
        .appendQueryParameter("am", Money.plain(paise))
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", "SplitUp: $note")
        .build()
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    catch (_: ActivityNotFoundException) { Toast.makeText(ctx, "No UPI app found on this phone", Toast.LENGTH_LONG).show() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseDialog(g: Group, old: Expense?, onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var title by remember { mutableStateOf(old?.title ?: "") }
    var amount by remember { mutableStateOf(old?.let { Money.plain(it.amount) } ?: "") }
    var paidBy by remember { mutableStateOf(old?.paidBy?.takeIf { id -> g.members.any { it.id == id } } ?: g.members.first().id) }
    var chosen by remember { mutableStateOf(old?.shares?.keys?.toSet() ?: g.members.map { it.id }.toSet()) }
    var exact by remember { mutableStateOf(old != null && !isEqualSplit(old)) }
    var exactText by remember { mutableStateOf(if (old != null) old.shares.mapValues { Money.plain(it.value) } else mapOf<String, String>()) }
    var date by remember { mutableStateOf(old?.date?.takeIf { it > 0 } ?: todayUtc()) }
    var pickDate by remember { mutableStateOf(false) }

    val total = Money.parse(amount)
    val shares: Map<String, Long>? = if (total == null || chosen.isEmpty()) null
    else if (!exact) Split.equal(total, g.members.map { it.id }.filter { it in chosen })
    else {
        val parsed = chosen.associateWith { Money.parse(exactText[it].orEmpty()) }
        if (parsed.values.any { it == null }) null else parsed.mapValues { it.value!! }.takeIf { it.values.sum() == total }
    }
    val exactSum = chosen.sumOf { Money.parse(exactText[it].orEmpty()) ?: 0L }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (old == null) "Add expense" else "Edit expense") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("What was it for?") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text("Date: ${fmtDate(date)}  (tap to change)") }
                Text("Paid by", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.members.forEach { m -> FilterChip(selected = paidBy == m.id, onClick = { paidBy = m.id }, label = { Text(m.name) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Split", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    FilterChip(selected = !exact, onClick = { exact = false }, label = { Text("Equally") })
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    FilterChip(selected = exact, onClick = { exact = true }, label = { Text("Exact amounts") })
                }
                g.members.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = m.id in chosen, onCheckedChange = { on -> chosen = if (on) chosen + m.id else chosen - m.id })
                        Text(m.name, Modifier.weight(1f))
                        if (exact && m.id in chosen) OutlinedTextField(exactText[m.id].orEmpty(), { exactText = exactText + (m.id to it) }, modifier = Modifier.padding(start = 8.dp).weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), placeholder = { Text("₹") })
                        else if (!exact && total != null && m.id in chosen) Text(Money.format(shares?.get(m.id) ?: 0L), fontSize = 13.sp)
                    }
                }
                if (exact && total != null) Text("Entered ${Money.format(exactSum)} of ${Money.format(total)}", fontSize = 12.sp,
                    color = if (exactSum == total) Color(0xFF15803D) else Color(0xFFB91C1C))
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && total != null && shares != null, onClick = {
                onSave(Expense(old?.id ?: newId(), title.trim(), total!!, paidBy, shares!!, date))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { date = it }; pickDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = st) }
    }
}

@Composable
private fun MembersDialog(g: Group, onDismiss: () -> Unit, onUpdate: (Group) -> Unit, onDelete: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf<String?>(null) }
    joining?.let { name -> JoinDialog(g, name, onDismiss = { joining = null }) { ng -> onUpdate(ng); joining = null; newName = "" } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("People in ${g.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.members.forEach { m ->
                    val used = g.expenses.any { it.paidBy == m.id || m.id in it.shares } || g.payments.any { it.from == m.id || it.to == m.id }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(m.name); if (m.upi.isNotBlank()) Text(m.upi, fontSize = 12.sp) }
                        if (!used && g.members.size > 2) TextButton(onClick = { onUpdate(g.copy(members = g.members.filter { it.id != m.id })) }) { Text("Remove") }
                    }
                }
                OutlinedTextField(newName, { newName = it }, label = { Text("Add a person") }, singleLine = true)
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    val n = newName.trim()
                    if (g.members.any { it.name.equals(n, true) }) newName = ""
                    else if (g.expenses.isEmpty()) { onUpdate(g.copy(members = g.members + Member(newId(), n))); newName = "" }
                    else joining = n
                }) { Text("Add") }
                TextButton(onClick = onDelete) { Text("Delete this group") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}


/** Someone joins part-way: choose which earlier expenses they share. Equal splits are re-divided, so balances update at once. */
@Composable
private fun JoinDialog(g: Group, name: String, onDismiss: () -> Unit, onDone: (Group) -> Unit) {
    val sorted = g.expenses.sortedByDescending { it.date }
    var picked by remember { mutableStateOf(sorted.filter { isEqualSplit(it) }.map { it.id }.toSet()) }
    val me = remember { Member(newId(), name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $name") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Which earlier expenses should $name share? Their part is taken out equally from the others, and balances update automatically. Expenses added later include $name by default.", fontSize = 13.sp)
                Row {
                    TextButton(onClick = { picked = sorted.filter { isEqualSplit(it) }.map { it.id }.toSet() }) { Text("All") }
                    TextButton(onClick = { picked = emptySet() }) { Text("None") }
                }
                sorted.forEach { e ->
                    val eq = isEqualSplit(e)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = e.id in picked, enabled = eq, onCheckedChange = { on -> picked = if (on) picked + e.id else picked - e.id })
                        Column(Modifier.weight(1f)) {
                            Text("${e.title}  ${Money.format(e.amount)}", fontSize = 14.sp)
                            Text(fmtDate(e.date) + if (eq) "" else " · exact amounts, edit it yourself", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = g.expenses.map { e ->
                    if (e.id in picked) {
                        val ids = g.members.map { it.id }.filter { it in e.shares } + me.id
                        e.copy(shares = Split.equal(e.amount, ids))
                    } else e
                }
                onDone(g.copy(members = g.members + me, expenses = updated))
            }) { Text("Add $name") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
