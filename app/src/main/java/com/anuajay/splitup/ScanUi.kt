package com.anuajay.splitup

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class Row3(val id: String, title: String, amount: String, include: Boolean) {
    var title by mutableStateOf(title)
    var amount by mutableStateOf(amount)
    var include by mutableStateOf(include)
}

/** Reads a screenshot (WhatsApp expense list or a UPI payment screen) and lets you confirm the entries before they are added. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportDialog(g: Group, uri: Uri, onDismiss: () -> Unit, onUpdate: (Group) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf("") }
    var isPayment by remember { mutableStateOf(false) }
    val rows = remember { mutableStateOf(listOf<Row3>()) }
    var paidBy by remember { mutableStateOf(g.members.first().id) }
    var sharedBy by remember { mutableStateOf(g.members.map { it.id }.toSet()) }
    var date by remember { mutableStateOf(todayUtc()) }
    var pickDate by remember { mutableStateOf(false) }
    var payTo by remember { mutableStateOf(g.members.last().id) }
    var payAmount by remember { mutableStateOf("") }
    var showRaw by remember { mutableStateOf(false) }
    var billTotal by remember { mutableStateOf<Long?>(null) }
    var billNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        val t = Scan.ocr(ctx, uri)
        if (t.isNullOrBlank()) { failed = true; loading = false; return@LaunchedEffect }
        raw = t
        Scan.dateOf(t)?.let { date = it }
        val bill = Scan.billTotal(t)
        if (Scan.looksLikePayment(t)) {
            // a UPI screen: if it is to someone in the group it is a settlement, otherwise it is a spend (shop, hotel…)
            val amount = Scan.paymentAmount(t)
            val cp = Scan.paymentCounterparty(t)
            val toMember = Scan.matchMember(g.members, cp)
            val fromMember = Scan.matchMember(g.members, Scan.paymentFrom(t))
            if (toMember != null) {
                isPayment = true
                payTo = toMember.id
                fromMember?.let { paidBy = it.id }
                if (paidBy == payTo) paidBy = g.members.first { it.id != payTo }.id
                amount?.let { payAmount = Money.plain(it) }
            } else {
                fromMember?.let { paidBy = it.id }
                if (amount != null) rows.value = listOf(Row3(newId(), cp ?: "Payment", Money.plain(amount), true))
                amount?.let { payAmount = Money.plain(it) }
            }
        } else if (bill != null || Scan.isBill(t)) {
            val (drafts, note) = Scan.billDrafts(t)
            rows.value = drafts.map { Row3(newId(), it.title, Money.plain(it.amount), it.include) }
            billTotal = bill
            billNote = note
        } else {
            rows.value = Scan.expenses(t).map { Row3(newId(), it.title, Money.plain(it.amount), it.include) }
            Scan.matchMember(g.members, Scan.payerName(t))?.let { paidBy = it.id }
        }
        loading = false
    }

    val chosen = rows.value.filter { it.include && Money.parse(it.amount) != null && it.title.isNotBlank() }
    val payTotal = Money.parse(payAmount)
    val canSave = !loading && sharedBy.isNotEmpty() && if (isPayment) payTotal != null && paidBy != payTo else chosen.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPayment) "Payment found" else "Entries found") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) { Text("Reading the picture on your phone…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (failed) Text("Could not read any text in that picture. Try a clearer screenshot.")
                if (!loading && !failed) {
                    Text("Check everything, fix anything that is wrong, then add.", fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Type: ", fontWeight = FontWeight.SemiBold)
                        FilterChip(selected = !isPayment, onClick = { isPayment = false; if (rows.value.isEmpty()) rows.value = Scan.expenses(raw).map { Row3(newId(), it.title, Money.plain(it.amount), it.include) } }, label = { Text("Expenses") })
                        Text(" ")
                        FilterChip(selected = isPayment, onClick = { isPayment = true }, label = { Text("A payment") })
                    }
                    if (isPayment) {
                        OutlinedTextField(payAmount, { payAmount = it }, label = { Text("Amount (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        Text("Who paid", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { g.members.forEach { m -> FilterChip(selected = paidBy == m.id, onClick = { paidBy = m.id }, label = { Text(m.name) }) } }
                        Text("Paid to", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { g.members.forEach { m -> FilterChip(selected = payTo == m.id, onClick = { payTo = m.id }, label = { Text(m.name) }) } }
                        if (paidBy == payTo) Text("Choose two different people.", fontSize = 12.sp)
                    } else {
                        if (rows.value.isEmpty()) Text("No amounts were found. Check the screenshot, or add the expense by hand.")
                        rows.value.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = r.include, onCheckedChange = { r.include = it })
                                Column(Modifier.weight(1f)) {
                                    OutlinedTextField(r.title, { r.title = it }, singleLine = true, label = { Text("What for") })
                                    OutlinedTextField(r.amount, { r.amount = it }, singleLine = true, label = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                }
                            }
                        }
                        billTotal?.let { bt ->
                            val sel = chosen.sumOf { Money.parse(it.amount) ?: 0L }
                            val ok = kotlin.math.abs(sel - bt) <= 100
                            Text("Ticked ${Money.format(sel)} · bill total ${Money.format(bt)}" + if (ok) "  ✓" else "  (does not match)", fontSize = 12.sp,
                                color = if (ok) androidx.compose.ui.graphics.Color(0xFF15803D) else androidx.compose.ui.graphics.Color(0xFFB91C1C))
                        }
                        billNote?.let { Text(it, fontSize = 12.sp) }
                        Text("Paid by", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { g.members.forEach { m -> FilterChip(selected = paidBy == m.id, onClick = { paidBy = m.id }, label = { Text(m.name) }) } }
                        Text("Shared equally by", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            g.members.forEach { m -> FilterChip(selected = m.id in sharedBy, onClick = { sharedBy = if (m.id in sharedBy) sharedBy - m.id else sharedBy + m.id }, label = { Text(m.name) }) }
                        }
                    }
                    TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "Hide what the phone read" else "Show what the phone read") }
                    if (showRaw) Text(raw, fontSize = 11.sp)
                    OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Date: ${fmtDate(date)}  (tap to change)") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                if (isPayment) {
                    onUpdate(g.copy(payments = g.payments + Payment(newId(), paidBy, payTo, payTotal!!, date)))
                } else {
                    val ids = g.members.map { it.id }.filter { it in sharedBy }
                    val added = chosen.map { r -> val a = Money.parse(r.amount)!!; Expense(newId(), r.title.trim(), a, paidBy, Split.equal(a, ids), date) }
                    onUpdate(g.copy(expenses = g.expenses + added))
                }
                onDismiss()
            }) { Text(if (isPayment) "Add payment" else "Add ${chosen.size} expense${if (chosen.size == 1) "" else "s"}") }
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
