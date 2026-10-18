package es.sergiotech.tickettracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseEditorScreen(
    draft: EditorState,
    documents: List<ReceiptDocument>,
    vm: TrackerViewModel,
    back: () -> Unit,
    import: () -> Unit,
    scan: () -> Unit,
) {
    var discard by rememberSaveable { mutableStateOf(false) }
    var chooser by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(draft.error) { if (draft.error != null) scroll.animateScrollTo(0) }
    val requestBack = { if (!draft.busy) discard = true }
    BackHandler { requestBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        label(
                            if (draft.original == null) R.string.new_purchase
                            else R.string.edit_purchase
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestBack, enabled = !draft.busy) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, label(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = vm::save,
                        enabled = !draft.busy,
                        modifier = Modifier.testTag("save"),
                    ) {
                        Text(label(if (draft.busy) R.string.saving else R.string.save))
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scroll)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            draft.error?.let {
                Text(
                    label(errorResource(it)),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("form-error"),
                )
            }
            if (draft.extracted)
                Text(label(R.string.extracted_notice), color = MaterialTheme.colorScheme.primary)
            Field(
                draft.merchant,
                { value -> vm.edit { it.copy(merchant = value.take(200)) } },
                R.string.merchant,
                "merchant",
            )
            DateField(
                draft.date,
                { value -> vm.edit { it.copy(date = value) } },
                R.string.purchase_date,
            )
            Field(
                draft.total,
                { value -> vm.edit { it.copy(total = value) } },
                R.string.total,
                "total",
                KeyboardType.Decimal,
            )
            Text(label(R.string.currency), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("EUR", "USD", "GBP").forEach { code ->
                    FilterChip(
                        draft.currency == code,
                        { vm.edit { it.copy(currency = code) } },
                        { Text(code) },
                    )
                }
            }
            Text(label(R.string.channel), style = MaterialTheme.typography.labelLarge)
            Column {
                PurchaseChannel.entries.forEach { channel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            draft.channel == channel,
                            { vm.edit { it.copy(channel = channel) } },
                        )
                        Text(
                            label(
                                when (channel) {
                                    PurchaseChannel.IN_STORE -> R.string.in_store
                                    PurchaseChannel.ONLINE -> R.string.online
                                    PurchaseChannel.OTHER -> R.string.other
                                }
                            )
                        )
                    }
                }
            }
            DateField(
                draft.delivered,
                { value -> vm.edit { it.copy(delivered = value) } },
                R.string.delivery_date,
                optional = true,
            )
            Field(
                draft.order,
                { value -> vm.edit { it.copy(order = value.take(200)) } },
                R.string.order_number,
                "order",
            )
            HorizontalDivider()
            Text(label(R.string.items), style = MaterialTheme.typography.titleLarge)
            Text(
                label(R.string.deadline_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draft.printedDeadlines.isNotEmpty()) {
                OutlinedCard {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            label(R.string.ocr_deadlines_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            label(R.string.ocr_deadlines_review),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        draft.printedDeadlines.forEach {
                            Text(it.evidence, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            draft.lines.forEachIndexed { index, line ->
                key(line.id) {
                    OutlinedCard {
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.item_number, index + 1),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (draft.lines.size > 1)
                                    IconButton(onClick = { vm.removeLine(index) }) {
                                        Icon(
                                            Icons.Outlined.RemoveCircleOutline,
                                            label(R.string.remove_item),
                                        )
                                    }
                            }
                            Field(
                                line.name,
                                { value -> vm.line(index) { it.copy(name = value.take(200)) } },
                                R.string.product_name,
                                "product-$index",
                            )
                            Field(
                                line.quantity,
                                { value -> vm.line(index) { it.copy(quantity = value) } },
                                R.string.quantity,
                                "quantity-$index",
                                KeyboardType.Number,
                            )
                            Field(
                                line.price,
                                { value -> vm.line(index) { it.copy(price = value) } },
                                R.string.unit_price,
                                "price-$index",
                                KeyboardType.Decimal,
                            )
                            DateField(
                                line.warrantyEnd,
                                { value -> vm.line(index) { it.copy(warrantyEnd = value) } },
                                R.string.warranty_end,
                                optional = true,
                            )
                            DateField(
                                line.returnEnd,
                                { value -> vm.line(index) { it.copy(returnEnd = value) } },
                                R.string.return_end,
                                optional = true,
                            )
                            draft.printedDeadlines.forEach { deadline ->
                                val empty =
                                    if (deadline.kind == PrintedDeadlineKind.WARRANTY)
                                        line.warrantyEnd.isBlank()
                                    else line.returnEnd.isBlank()
                                if (empty)
                                    OutlinedButton(
                                        onClick = { vm.applyDeadline(index, deadline) },
                                        enabled = !draft.busy,
                                        modifier =
                                            Modifier.fillMaxWidth()
                                                .testTag("ocr-${deadline.kind.name}-$index"),
                                    ) {
                                        Text(
                                            stringResource(
                                                if (deadline.kind == PrintedDeadlineKind.WARRANTY)
                                                    R.string.ocr_use_warranty
                                                else R.string.ocr_use_return,
                                                deadline.date.toString(),
                                            )
                                        )
                                    }
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = vm::addLine,
                enabled = draft.lines.size < 100,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, null)
                Text(label(R.string.add_item))
            }
            HorizontalDivider()
            Text(label(R.string.receipt_section), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.document_count, draft.documentIds.size))
            documents
                .filter { it.id in draft.documentIds }
                .forEach { Text(it.displayName, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = import, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FileUpload, null)
                Spacer(Modifier.width(8.dp))
                Text(label(R.string.import_document))
            }
            OutlinedButton(onClick = scan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text(label(R.string.scan))
            }
            if (documents.any { it.purchaseId == null && it.id !in draft.documentIds })
                TextButton(onClick = { chooser = true }) { Text(label(R.string.attach_existing)) }
            Text(
                label(R.string.document_saved_separately),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                draft.notes,
                { value -> vm.edit { it.copy(notes = value.take(10_000)) } },
                Modifier.fillMaxWidth(),
                label = { Text(label(R.string.notes)) },
                minLines = 3,
            )
            Button(onClick = vm::save, enabled = !draft.busy, modifier = Modifier.fillMaxWidth()) {
                Text(label(if (draft.busy) R.string.saving else R.string.save))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(label(R.string.discard_title)) },
            text = { Text(label(R.string.discard_body)) },
            confirmButton = { TextButton(onClick = back) { Text(label(R.string.leave)) } },
            dismissButton = {
                TextButton(onClick = { discard = false }) { Text(label(R.string.stay)) }
            },
        )
    if (chooser)
        AlertDialog(
            onDismissRequest = { chooser = false },
            title = { Text(label(R.string.attach_existing)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    documents
                        .filter { it.purchaseId == null && it.id !in draft.documentIds }
                        .forEach { doc ->
                            TextButton(
                                onClick = {
                                    vm.attach(doc.id)
                                    chooser = false
                                }
                            ) {
                                Text(doc.displayName)
                            }
                        }
                }
            },
            confirmButton = {
                TextButton(onClick = { chooser = false }) { Text(label(R.string.close)) }
            },
        )
}

@Composable
private fun Field(
    value: String,
    change: (String) -> Unit,
    labelId: Int,
    tag: String,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value,
        change,
        Modifier.fillMaxWidth().testTag(tag),
        label = { Text(label(labelId)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    value: String,
    change: (String) -> Unit,
    labelId: Int,
    optional: Boolean = false,
) {
    var show by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value,
            change,
            Modifier.fillMaxWidth(),
            label = { Text(label(labelId)) },
            placeholder = { Text(label(R.string.date_hint)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { show = true }) {
                    Icon(Icons.Outlined.CalendarMonth, label(R.string.select_date))
                }
            },
        )
        if (optional && value.isNotBlank())
            TextButton(onClick = { change("") }) { Text(label(R.string.clear_date)) }
    }
    if (show) {
        val date =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    runCatching {
                            LocalDate.parse(value)
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

private fun errorResource(error: EditorError): Int =
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
