package es.sergiotech.tickettracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.domain.*

@Composable
fun PurchaseEditorScreen(
    draft: EditorState,
    documents: List<ReceiptDocument>,
    vm: TrackerViewModel,
    back: () -> Unit,
    import: () -> Unit,
    scan: () -> Unit,
    openDocument: (ReceiptDocument) -> Unit = {},
) =
    PurchaseForm(
        draft,
        documents,
        EditorActions(
            vm::edit,
            vm::line,
            vm::addLine,
            vm::removeLine,
            vm::attach,
            vm::applyDeadline,
            vm::save,
        ),
        back,
        import,
        scan,
        openDocument,
    )

internal class EditorActions(
    val edit: ((EditorState) -> EditorState) -> Unit,
    val line: (Int, (LineDraft) -> LineDraft) -> Unit,
    val add: () -> Unit,
    val remove: (Int) -> Unit,
    val attach: (String) -> Unit,
    val deadline: (Int, PrintedDeadline) -> Unit,
    val save: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PurchaseForm(
    draft: EditorState,
    documents: List<ReceiptDocument>,
    actions: EditorActions,
    back: () -> Unit,
    import: () -> Unit,
    scan: () -> Unit,
    openDocument: (ReceiptDocument) -> Unit = {},
) {
    var discard by rememberSaveable { mutableStateOf(false) }
    var chooser by remember { mutableStateOf(false) }
    var details by
        rememberSaveable(draft.original?.id) {
            mutableStateOf(
                draft.order.isNotBlank() ||
                    draft.delivered.isNotBlank() ||
                    draft.notes.isNotBlank() ||
                    draft.channel != PurchaseChannel.IN_STORE
            )
        }
    var currencyMenu by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val requester = remember { BringIntoViewRequester() }
    val problems = if (draft.error != null) draft.problems() else emptyList()
    fun anchor(field: DraftField, index: Int? = null): Modifier =
        if (problems.firstOrNull() == DraftProblem(field, index))
            Modifier.bringIntoViewRequester(requester)
        else Modifier
    fun error(field: DraftField, index: Int? = null): Int? =
        if (DraftProblem(field, index) in problems)
            when (field) {
                DraftField.NAME -> R.string.form_name_error
                DraftField.QUANTITY -> R.string.form_quantity_error
                DraftField.PRICE -> R.string.form_price_error
                else -> errorResource(DraftProblem(field, index).error)
            }
        else null
    val sum = draft.lineSum()
    val total = Money.parse(draft.total, draft.currency)
    val extraOpen = details || problems.any { it.field == DraftField.DELIVERY }
    LaunchedEffect(draft.error) {
        if (problems.any { it.field == DraftField.DELIVERY }) details = true
    }
    LaunchedEffect(draft.order, draft.delivered) {
        if (draft.extracted && (draft.order.isNotBlank() || draft.delivered.isNotBlank()))
            details = true
    }
    val linked = documents.filter { it.id in draft.documentIds }
    val requestBack = {
        if (!draft.busy) {
            val hasContent =
                draft.original != null ||
                    draft.merchant.isNotBlank() ||
                    draft.total.isNotBlank() ||
                    draft.documentIds.isNotEmpty() ||
                    draft.notes.isNotBlank() ||
                    draft.order.isNotBlank() ||
                    draft.delivered.isNotBlank() ||
                    draft.channel != PurchaseChannel.IN_STORE ||
                    draft.currency != "EUR" ||
                    draft.dateEdited ||
                    draft.lines.any {
                        it.name.isNotBlank() ||
                            it.price.isNotBlank() ||
                            it.quantity != "1" ||
                            it.warrantyEnd.isNotBlank() ||
                            it.returnEnd.isNotBlank()
                    }
            if (hasContent) discard = true else back()
        }
    }
    BackHandler { requestBack() }
    LaunchedEffect(draft.error) {
        if (draft.error != null) {
            withFrameNanos {}
            if (problems.isNotEmpty()) requester.bringIntoView() else scroll.animateScrollTo(0)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        label(
                            if (draft.original == null) R.string.form_title
                            else R.string.edit_purchase
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestBack, enabled = !draft.busy) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, label(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = actions.save,
                    enabled = !draft.busy,
                    modifier =
                        Modifier.fillMaxWidth()
                            .imePadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .testTag("save"),
                ) {
                    Icon(Icons.Outlined.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(label(if (draft.busy) R.string.saving else R.string.form_save))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (draft.error != null)
                Text(
                    label(errorResource(draft.error)),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("form-error"),
                )
            run {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            label(
                                if (draft.extracted) R.string.form_review_title
                                else R.string.form_import_title
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            label(
                                if (draft.extracted) R.string.form_review_body
                                else R.string.form_import_body
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = scan,
                                enabled = !draft.busy,
                                modifier = Modifier.testTag("form-scan"),
                            ) {
                                Icon(Icons.Outlined.CameraAlt, null)
                                Spacer(Modifier.width(6.dp))
                                Text(label(R.string.scan))
                            }
                            OutlinedButton(
                                onClick = import,
                                enabled = !draft.busy,
                                modifier = Modifier.testTag("form-import"),
                            ) {
                                Icon(Icons.Outlined.AttachFile, null)
                                Spacer(Modifier.width(6.dp))
                                Text(label(R.string.form_file))
                            }
                        }
                        linked.forEach { document ->
                            TextButton(
                                onClick = { openDocument(document) },
                                enabled = !draft.busy,
                            ) {
                                Icon(Icons.Outlined.Description, null)
                                Spacer(Modifier.width(6.dp))
                                Text(document.displayName)
                            }
                        }
                        if (documents.any { it.purchaseId == null && it.id !in draft.documentIds })
                            TextButton(onClick = { chooser = true }, enabled = !draft.busy) {
                                Text(label(R.string.attach_existing))
                            }
                        if (draft.partialRead)
                            Text(
                                label(R.string.ocr_partial),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        if (draft.unreadableRows)
                            Text(
                                label(R.string.form_unreadable_rows),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                    }
                }
            }
            FormSection(label(R.string.form_basics)) {
                EditorField(
                    draft.merchant,
                    { v -> actions.edit { it.copy(merchant = v.take(200)) } },
                    R.string.merchant,
                    "merchant",
                    modifier = anchor(DraftField.MERCHANT),
                    error = error(DraftField.MERCHANT),
                    enabled = !draft.busy,
                )
                DateField(
                    draft.date,
                    { v -> actions.edit { it.copy(date = v, dateEdited = true) } },
                    R.string.purchase_date,
                    modifier = anchor(DraftField.DATE).testTag("purchase-date"),
                    error = error(DraftField.DATE),
                    enabled = !draft.busy,
                    hint = R.string.form_date_hint,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditorField(
                        draft.total,
                        { v -> actions.edit { it.copy(total = v) } },
                        R.string.total,
                        "total",
                        KeyboardType.Decimal,
                        Modifier.weight(1f).then(anchor(DraftField.TOTAL)),
                        error(DraftField.TOTAL),
                        !draft.busy,
                    )
                    Box {
                        OutlinedButton(
                            onClick = { currencyMenu = true },
                            enabled = !draft.busy,
                            modifier = Modifier.testTag("currency"),
                        ) {
                            Text(draft.currency)
                            Icon(Icons.Outlined.ArrowDropDown, label(R.string.currency))
                        }
                        DropdownMenu(
                            expanded = currencyMenu,
                            onDismissRequest = { currencyMenu = false },
                        ) {
                            listOf("EUR", "USD", "GBP").forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(code) },
                                    onClick = {
                                        actions.edit { it.copy(currency = code) }
                                        currencyMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (sum != null) {
                    Text(
                        stringResource(R.string.form_line_sum, money(sum)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (total != sum) {
                        if (total != null)
                            Text(
                                label(R.string.form_total_difference),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        TextButton(
                            onClick = {
                                actions.edit {
                                    it.copy(total = TrackerViewModel.decimal(sum.minorUnits))
                                }
                            },
                            enabled = !draft.busy,
                            modifier = Modifier.testTag("use-line-total"),
                        ) {
                            Text(label(R.string.form_use_sum))
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.form_item_count, draft.lines.size),
                style = MaterialTheme.typography.titleMedium,
            )
            draft.lines.forEachIndexed { index, line ->
                key(line.id) {
                    var expanded by
                        rememberSaveable(line.id) { mutableStateOf(line.name.isBlank()) }
                    var coverage by
                        rememberSaveable(line.id) {
                            mutableStateOf(
                                line.warrantyEnd.isNotBlank() || line.returnEnd.isNotBlank()
                            )
                        }
                    LaunchedEffect(line.warrantyEnd, line.returnEnd) {
                        if (line.warrantyEnd.isNotBlank() || line.returnEnd.isNotBlank())
                            coverage = true
                    }
                    val lineProblems = problems.filter { it.line == index }
                    LaunchedEffect(lineProblems.isNotEmpty()) {
                        if (lineProblems.isNotEmpty()) expanded = true
                    }
                    val coverageOpen =
                        coverage ||
                            lineProblems.any {
                                it.field == DraftField.WARRANTY || it.field == DraftField.RETURN
                            }
                    OutlinedCard {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { expanded = !expanded },
                                    enabled = !draft.busy,
                                    modifier = Modifier.weight(1f).testTag("expand-item-" + index),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            line.name.ifBlank {
                                                stringResource(R.string.item_number, index + 1)
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        val price = Money.parse(line.price, draft.currency)
                                        Text(
                                            stringResource(
                                                R.string.form_item_summary,
                                                line.quantity,
                                                price?.let(::money)
                                                    ?: label(R.string.form_price_unknown),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Icon(
                                        if (expanded || lineProblems.isNotEmpty())
                                            Icons.Outlined.ExpandLess
                                        else Icons.Outlined.ExpandMore,
                                        null,
                                    )
                                }
                                if (draft.lines.size > 1)
                                    IconButton(
                                        onClick = { actions.remove(index) },
                                        enabled = !draft.busy,
                                        modifier = Modifier.testTag("remove-item-" + index),
                                    ) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            label(R.string.remove_item),
                                        )
                                    }
                            }
                            if (expanded || lineProblems.isNotEmpty()) {
                                EditorField(
                                    line.name,
                                    { v -> actions.line(index) { it.copy(name = v.take(200)) } },
                                    R.string.product_name,
                                    "product-" + index,
                                    modifier = anchor(DraftField.NAME, index),
                                    error = error(DraftField.NAME, index),
                                    enabled = !draft.busy,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    EditorField(
                                        line.quantity,
                                        { v -> actions.line(index) { it.copy(quantity = v) } },
                                        R.string.quantity,
                                        "quantity-" + index,
                                        KeyboardType.Number,
                                        Modifier.weight(.35f)
                                            .then(anchor(DraftField.QUANTITY, index)),
                                        error(DraftField.QUANTITY, index),
                                        !draft.busy,
                                    )
                                    EditorField(
                                        line.price,
                                        { v -> actions.line(index) { it.copy(price = v) } },
                                        R.string.form_unit_price,
                                        "price-" + index,
                                        KeyboardType.Decimal,
                                        Modifier.weight(.65f).then(anchor(DraftField.PRICE, index)),
                                        error(DraftField.PRICE, index),
                                        !draft.busy,
                                    )
                                }
                                TextButton(
                                    onClick = { coverage = !coverage },
                                    enabled = !draft.busy,
                                    modifier = Modifier.testTag("coverage-" + index),
                                ) {
                                    Icon(Icons.Outlined.VerifiedUser, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(label(R.string.form_coverage))
                                }
                                if (coverageOpen) {
                                    Text(
                                        label(R.string.deadline_help),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DateField(
                                        line.warrantyEnd,
                                        { v -> actions.line(index) { it.copy(warrantyEnd = v) } },
                                        R.string.warranty_end,
                                        true,
                                        anchor(DraftField.WARRANTY, index)
                                            .testTag("warranty-" + index),
                                        error(DraftField.WARRANTY, index),
                                        !draft.busy,
                                        R.string.form_date_hint,
                                    )
                                    DateField(
                                        line.returnEnd,
                                        { v -> actions.line(index) { it.copy(returnEnd = v) } },
                                        R.string.return_end,
                                        true,
                                        anchor(DraftField.RETURN, index).testTag("return-" + index),
                                        error(DraftField.RETURN, index),
                                        !draft.busy,
                                        R.string.form_date_hint,
                                    )
                                }
                                draft.printedDeadlines.forEach { deadline ->
                                    if (
                                        if (deadline.kind == PrintedDeadlineKind.WARRANTY)
                                            line.warrantyEnd.isBlank()
                                        else line.returnEnd.isBlank()
                                    )
                                        OutlinedButton(
                                            onClick = { actions.deadline(index, deadline) },
                                            enabled = !draft.busy,
                                            modifier =
                                                Modifier.fillMaxWidth()
                                                    .testTag(
                                                        "ocr-" + deadline.kind.name + "-" + index
                                                    ),
                                        ) {
                                            Text(
                                                stringResource(
                                                    if (
                                                        deadline.kind ==
                                                            PrintedDeadlineKind.WARRANTY
                                                    )
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
            }
            OutlinedButton(
                onClick = actions.add,
                enabled = !draft.busy && draft.lines.size < 100,
                modifier = Modifier.fillMaxWidth().testTag("add-item"),
            ) {
                Icon(Icons.Outlined.Add, null)
                Text(label(R.string.add_item))
            }
            OutlinedCard {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        onClick = { details = !details },
                        enabled = !draft.busy,
                        modifier = Modifier.testTag("purchase-details"),
                    ) {
                        Text(label(R.string.form_details))
                        Icon(
                            if (extraOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            null,
                        )
                    }
                    if (extraOpen) {
                        Text(label(R.string.channel), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PurchaseChannel.entries.forEach { channel ->
                                FilterChip(
                                    selected = draft.channel == channel,
                                    onClick = { actions.edit { it.copy(channel = channel) } },
                                    enabled = !draft.busy,
                                    label = {
                                        Text(
                                            label(
                                                when (channel) {
                                                    PurchaseChannel.IN_STORE -> R.string.in_store
                                                    PurchaseChannel.ONLINE -> R.string.online
                                                    PurchaseChannel.OTHER -> R.string.other
                                                }
                                            )
                                        )
                                    },
                                )
                            }
                        }
                        EditorField(
                            draft.order,
                            { v -> actions.edit { it.copy(order = v.take(200)) } },
                            R.string.order_number,
                            "order",
                            enabled = !draft.busy,
                        )
                        DateField(
                            draft.delivered,
                            { v -> actions.edit { it.copy(delivered = v) } },
                            R.string.delivery_date,
                            true,
                            anchor(DraftField.DELIVERY).testTag("delivery"),
                            error(DraftField.DELIVERY),
                            !draft.busy,
                            R.string.form_date_hint,
                        )
                        OutlinedTextField(
                            draft.notes,
                            { v -> actions.edit { it.copy(notes = v.take(10000)) } },
                            Modifier.fillMaxWidth().testTag("notes"),
                            label = { Text(label(R.string.notes)) },
                            enabled = !draft.busy,
                            minLines = 2,
                        )
                    }
                }
            }
            if (draft.documentIds.isNotEmpty())
                Text(
                    label(R.string.document_saved_separately),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                                    actions.attach(doc.id)
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
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun EditorField(
    value: String,
    change: (String) -> Unit,
    labelId: Int,
    tag: String,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    error: Int? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value,
        change,
        modifier.fillMaxWidth().testTag(tag),
        label = { Text(label(labelId)) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { message -> { Text(label(message)) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
    )
}
