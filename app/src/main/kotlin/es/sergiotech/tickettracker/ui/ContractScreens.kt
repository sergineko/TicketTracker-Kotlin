package es.sergiotech.tickettracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.domain.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractsScreen(
    kind: ContractKind,
    records: List<TrackedContract>,
    back: () -> Unit,
    add: () -> Unit,
    edit: (TrackedContract) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        label(
                            if (kind == ContractKind.SUBSCRIPTION) R.string.subscriptions
                            else R.string.my_insurance
                        )
                    )
                },
                navigationIcon = {
                    IconButton(back) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, label(R.string.back))
                    }
                },
                actions = {
                    IconButton(add, Modifier.testTag("add-contract")) {
                        Icon(Icons.Outlined.Add, label(R.string.add_record))
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    label(R.string.contract_local_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (records.isEmpty())
                item {
                    EmptyState(
                        label(R.string.empty_contracts),
                        label(R.string.contract_help),
                        label(R.string.add_record),
                        add,
                    )
                }
            items(records.sortedBy { it.nextDate }, key = { it.id }) {
                ContractRow(it) { edit(it) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractEditorScreen(
    kind: ContractKind,
    initial: TrackedContract?,
    isNew: Boolean,
    back: () -> Unit,
    save: suspend (TrackedContract) -> Unit,
    archive: suspend (TrackedContract) -> Unit,
) {
    val original = remember { initial }
    val expectedRevision = rememberSaveable { initial?.revision ?: 0L }
    val ownerId = rememberSaveable { initial?.ownerId.orEmpty() }
    val scope = rememberCoroutineScope()
    val id = rememberSaveable { original?.id ?: newId() }
    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var provider by rememberSaveable { mutableStateOf(original?.provider.orEmpty()) }
    var amount by rememberSaveable {
        mutableStateOf(
            original?.amount?.let { BigDecimal.valueOf(it.minorUnits, 2).toPlainString() }.orEmpty()
        )
    }
    var currency by rememberSaveable { mutableStateOf(original?.amount?.currency ?: "EUR") }
    var period by rememberSaveable { mutableStateOf(original?.period ?: BillingPeriod.MONTHLY) }
    var date by rememberSaveable {
        mutableStateOf(original?.nextDate?.toString() ?: LocalDate.now().plusMonths(1).toString())
    }
    var active by rememberSaveable { mutableStateOf(original?.active ?: true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableIntStateOf(0) }
    var discard by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val requestBack = { if (!busy) discard = true }
    BackHandler { requestBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        label(
                            if (kind == ContractKind.SUBSCRIPTION) R.string.subscription_record
                            else R.string.insurance_record
                        )
                    )
                },
                navigationIcon = {
                    IconButton(requestBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, label(R.string.back))
                    }
                },
                actions = {
                    if (!isNew && original != null)
                        IconButton({ deleting = true }, enabled = !busy) {
                            Icon(Icons.Outlined.DeleteOutline, label(R.string.delete))
                        }
                    TextButton(
                        onClick = {
                            val parsed = Money.parse(amount, currency)
                            val day = runCatching { LocalDate.parse(date) }.getOrNull()
                            if (name.isBlank() || parsed == null || day == null)
                                error = R.string.contract_validation
                            else {
                                busy = true
                                error = 0
                                scope.launch(Dispatchers.Main.immediate) {
                                    try {
                                        save(
                                            TrackedContract(
                                                id,
                                                ownerId,
                                                kind,
                                                name,
                                                provider,
                                                parsed,
                                                period,
                                                day,
                                                active,
                                                expectedRevision,
                                            )
                                        )
                                        back()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: RevisionConflictException) {
                                        error = R.string.contract_conflict
                                    } catch (_: Exception) {
                                        error = R.string.save_error
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        enabled = !busy && (isNew || original != null),
                        modifier = Modifier.testTag("save-contract"),
                    ) {
                        Text(label(if (busy) R.string.saving else R.string.save))
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!isNew && original == null) Text(label(R.string.missing_contract))
            Text(
                label(R.string.contract_local_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error != 0)
                Text(
                    label(error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("contract-error"),
                )
            OutlinedTextField(
                name,
                { name = it.take(200) },
                label = { Text(label(R.string.contract_name)) },
                modifier = Modifier.fillMaxWidth().testTag("contract-name"),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(
                provider,
                { provider = it.take(200) },
                label = { Text(label(R.string.contract_provider)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(
                amount,
                { amount = it },
                label = { Text(label(R.string.contract_amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("contract-amount"),
                enabled = !busy,
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("EUR", "USD", "GBP").forEach { code ->
                    FilterChip(
                        currency == code,
                        { currency = code },
                        { Text(code) },
                        enabled = !busy,
                    )
                }
            }
            Text(label(R.string.billing_period), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BillingPeriod.entries.forEach { value ->
                    FilterChip(
                        period == value,
                        { period = value },
                        {
                            Text(
                                label(
                                    when (value) {
                                        BillingPeriod.MONTHLY -> R.string.monthly
                                        BillingPeriod.YEARLY -> R.string.yearly
                                        BillingPeriod.OTHER -> R.string.other
                                    }
                                )
                            )
                        },
                        enabled = !busy,
                    )
                }
            }
            DateField(
                date,
                { if (!busy) date = it },
                if (kind == ContractKind.SUBSCRIPTION) R.string.next_renewal
                else R.string.coverage_end,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label(R.string.record_active), Modifier.weight(1f))
                Switch(active, { active = it }, enabled = !busy)
            }
            Text(
                label(R.string.contract_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(label(R.string.discard_title)) },
            text = { Text(label(R.string.discard_contract)) },
            confirmButton = { TextButton(back) { Text(label(R.string.leave)) } },
            dismissButton = { TextButton({ discard = false }) { Text(label(R.string.stay)) } },
        )
    if (deleting && original != null)
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = false },
            title = { Text(label(R.string.archive_contract)) },
            text = { Text(label(R.string.archive_contract_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        busy = true
                        scope.launch(Dispatchers.Main.immediate) {
                            try {
                                archive(
                                    original.copy(revision = expectedRevision, ownerId = ownerId)
                                )
                                back()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                error = R.string.contract_conflict
                                deleting = false
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text(label(R.string.delete))
                }
            },
            dismissButton = {
                TextButton({ deleting = false }, enabled = !busy) { Text(label(R.string.cancel)) }
            },
        )
}
