package es.sergiotech.tickettracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.domain.*
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    value: String,
    change: (String) -> Unit,
    labelId: Int,
    optional: Boolean = false,
    modifier: Modifier = Modifier,
    error: Int? = null,
    enabled: Boolean = true,
    hint: Int = R.string.date_hint,
) {
    var show by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value,
            change,
            modifier.fillMaxWidth(),
            label = { Text(label(labelId)) },
            placeholder = { Text(label(hint)) },
            isError = error != null,
            supportingText = error?.let { message -> { Text(label(message)) } },
            enabled = enabled,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { show = true }, enabled = enabled) {
                    Icon(Icons.Outlined.CalendarMonth, label(R.string.select_date))
                }
            },
        )
        if (optional && value.isNotBlank())
            TextButton(onClick = { change("") }, enabled = enabled) {
                Text(label(R.string.clear_date))
            }
    }
    if (show) {
        val date =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    runCatching {
                            requireNotNull(editorDate(value))
                                .atStartOfDay(ZoneOffset.UTC)
                                .toInstant()
                                .toEpochMilli()
                        }
                        .getOrNull()
            )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        date.selectedDateMillis?.let {
                            change(
                                Instant.ofEpochMilli(it)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                    .toString()
                            )
                        }
                        show = false
                    }
                ) {
                    Text(label(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text(label(R.string.cancel)) }
            },
        ) {
            DatePicker(date)
        }
    }
}

internal fun errorResource(error: EditorError): Int =
    when (error) {
        EditorError.MERCHANT -> R.string.merchant_error
        EditorError.TOTAL -> R.string.total_error
        EditorError.DATE -> R.string.date_error
        EditorError.PRODUCT -> R.string.product_error
        EditorError.DEADLINE -> R.string.deadline_error
        EditorError.DELIVERY -> R.string.delivery_error
        EditorError.SAVE -> R.string.save_error
        EditorError.CONFLICT -> R.string.conflict_error
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(
    purchase: Purchase?,
    documents: List<ReceiptDocument>,
    back: () -> Unit,
    edit: () -> Unit,
    delete: () -> Unit,
    openDocument: (ReceiptDocument) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(purchase?.merchant.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, label(R.string.back))
                    }
                },
                actions = {
                    if (purchase != null) {
                        IconButton(onClick = edit) {
                            Icon(Icons.Outlined.Edit, label(R.string.edit))
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.DeleteOutline, label(R.string.delete))
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (purchase == null) Text(label(R.string.missing_purchase))
            else {
                Text(
                    money(purchase.total),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(day(purchase.purchasedOn), style = MaterialTheme.typography.titleMedium)
                purchase.deliveredOn?.let { Text("${label(R.string.delivery_date)}: ${day(it)}") }
                if (purchase.orderNumber.isNotBlank()) Text(purchase.orderNumber)
                Text(label(R.string.items), style = MaterialTheme.typography.titleLarge)
                purchase.lines.forEach { line ->
                    Card {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(line.product.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${label(R.string.quantity)}: ${line.quantity}${line.unitPrice?.let { " · ${money(it)}" }.orEmpty()}"
                            )
                            DeadlineLabel(line.product.warrantyEnd, false)
                            DeadlineLabel(line.product.returnEnd, true)
                            Text(
                                label(R.string.confirmed_date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                val linked = documents.filter { it.purchaseId == purchase.id }
                if (linked.isNotEmpty()) {
                    Text(
                        label(R.string.receipt_section),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    linked.forEach { doc ->
                        OutlinedButton(
                            onClick = { openDocument(doc) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(doc.displayName)
                        }
                    }
                }
                if (purchase.notes.isNotBlank()) {
                    Text(label(R.string.notes), style = MaterialTheme.typography.titleLarge)
                    Text(purchase.notes)
                }
                Text(
                    label(R.string.local_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmDelete)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(label(R.string.delete_title)) },
            text = { Text(label(R.string.delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        delete()
                    }
                ) {
                    Text(label(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(label(R.string.cancel)) }
            },
        )
}
