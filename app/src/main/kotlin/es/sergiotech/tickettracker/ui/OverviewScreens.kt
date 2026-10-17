package es.sergiotech.tickettracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.domain.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    state: TrackerState,
    add: () -> Unit,
    scan: () -> Unit,
    import: () -> Unit,
    all: () -> Unit,
    detail: (String) -> Unit,
) {
    val today = LocalDate.now()
    val alerts = upcomingDeadlines(state.purchases, today)
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Text(
                label(R.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item { PageHeading(label(R.string.welcome), label(R.string.welcome_body)) }
        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(36.dp))
                    Text(label(R.string.tagline), style = MaterialTheme.typography.titleLarge)
                    Button(onClick = scan, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text(label(R.string.scan))
                    }
                    OutlinedButton(onClick = import, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.FileUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(label(R.string.import_document))
                    }
                }
            }
        }
        if (alerts.isNotEmpty()) {
            item { Text(label(R.string.upcoming), style = MaterialTheme.typography.titleLarge) }
            items(alerts.take(5)) { alert ->
                Card(
                    onClick = { detail(alert.purchaseId) },
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(alert.productName, style = MaterialTheme.typography.titleMedium)
                        DeadlineLabel(alert.end, alert.isReturn)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(
                    state.purchases.size.toString(),
                    label(R.string.saved_purchases),
                    Modifier.weight(1f),
                )
                Metric(
                    state.purchases
                        .sumOf { p ->
                            p.lines.count {
                                it.product.warrantyEnd?.let { end -> end >= today } == true
                            }
                        }
                        .toString(),
                    label(R.string.active_warranties),
                    Modifier.weight(1f),
                )
            }
        }
        if (state.purchases.isEmpty())
            item {
                EmptyState(
                    label(R.string.empty_title),
                    label(R.string.empty_body),
                    label(R.string.manual_entry),
                    add,
                )
            }
        else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label(R.string.recent),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(onClick = all) { Text(label(R.string.see_all)) }
                }
            }
            items(state.purchases.take(5), key = { it.id }) { PurchaseCard(it) { detail(it.id) } }
        }
        item {
            Text(
                label(R.string.local_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun Metric(value: String, title: String, modifier: Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PurchasesScreen(state: TrackerState, add: () -> Unit, detail: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val filtered by
        produceState<List<Purchase>>(
            emptyList(),
            state.purchases,
            state.documents,
            query,
            filter,
            today,
        ) {
            value =
                withContext(Dispatchers.Default) {
                    state.purchases.filter { purchase ->
                        val searchable = buildString {
                            append(purchase.merchant)
                            append(' ')
                            append(purchase.orderNumber)
                            append(' ')
                            append(purchase.notes)
                            purchase.lines.forEach {
                                append(' ')
                                append(it.product.name)
                            }
                            state.documents
                                .filter { it.purchaseId == purchase.id }
                                .forEach {
                                    append(' ')
                                    append(it.extractedText)
                                }
                        }
                        searchable.contains(query.trim(), ignoreCase = true) &&
                            when (filter) {
                                1 ->
                                    purchase.lines.any {
                                        deadlineState(it.product.returnEnd, today, 7) ==
                                            DeadlineState.ENDING_SOON
                                    }
                                2 ->
                                    purchase.lines.any {
                                        it.product.warrantyEnd?.let { end -> end >= today } == true
                                    }
                                else -> true
                            }
                    }
                }
        }
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageHeading(label(R.string.purchases)) }
        item {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().testTag("search"),
                label = { Text(label(R.string.search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(R.string.all_filter, R.string.returns_filter, R.string.warranties_filter)
                    .forEachIndexed { index, id ->
                        FilterChip(filter == index, { filter = index }, { Text(label(id)) })
                    }
            }
        }
        if (state.purchases.isEmpty())
            item {
                EmptyState(
                    label(R.string.empty_title),
                    label(R.string.empty_body),
                    label(R.string.add_purchase),
                    add,
                )
            }
        else if (filtered.isEmpty())
            item { Text(label(R.string.no_results), Modifier.padding(vertical = 32.dp)) }
        items(filtered, key = { it.id }) { PurchaseCard(it) { detail(it.id) } }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun ProfileScreen(
    state: TrackerState,
    viewModel: TrackerViewModel,
    account: AccountUiState,
    accountViewModel: AccountViewModel,
    export: () -> Unit,
    reminders: (Boolean) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { PageHeading(label(R.string.profile)) }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        Icons.Outlined.PersonOutline,
                        null,
                        Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    AccountPanel(account, accountViewModel)
                    Text(
                        stringResource(R.string.outbox_count, state.pendingCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item { Text(label(R.string.appearance), style = MaterialTheme.typography.titleLarge) }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                        "SYSTEM" to R.string.system_theme,
                        "LIGHT" to R.string.light_theme,
                        "DARK" to R.string.dark_theme,
                    )
                    .forEach { (value, id) ->
                        FilterChip(
                            state.preferences.theme == value,
                            { viewModel.theme(value) },
                            { Text(label(id)) },
                        )
                    }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label(R.string.dynamic_colors), Modifier.weight(1f))
                Switch(state.preferences.dynamicColor, viewModel::dynamic)
            }
        }
        item { HorizontalDivider() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label(R.string.reminders), Modifier.weight(1f))
                    Switch(state.preferences.reminders, reminders)
                }
                Text(
                    label(R.string.reminders_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(label(R.string.integrations), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(label(R.string.gmail_body))
        }
        item { HorizontalDivider() }
        item {
            Text(label(R.string.export_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = export) {
                Icon(Icons.Outlined.FileDownload, null)
                Spacer(Modifier.width(8.dp))
                Text(label(R.string.export))
            }
        }
        item {
            Text(
                label(R.string.local_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                stringResource(
                    R.string.about,
                    es.sergiotech.tickettracker.BuildConfig.VERSION_NAME,
                ),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
