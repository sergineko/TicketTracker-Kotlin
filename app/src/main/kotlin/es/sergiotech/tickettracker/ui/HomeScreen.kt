package es.sergiotech.tickettracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency

private val Teal = Color(0xFF006B73)
private val Purple = Color(0xFF7118C9)
private val OrangeInk = Color(0xFF713100)

@Composable
fun HomeScreen(
    state: TrackerState,
    add: () -> Unit,
    all: () -> Unit,
    contracts: (ContractKind) -> Unit,
    editContract: (TrackedContract) -> Unit,
    detail: (String) -> Unit,
    account: AccountUiState,
    openAccount: () -> Unit,
) {
    val today = LocalDate.now()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(ProductFilter.ALL) }
    var alphabetical by rememberSaveable { mutableStateOf(false) }
    val products = state.purchases.flatMap { p -> p.lines.map { p to it.product } }
    val kind = if (tab == 1) ContractKind.SUBSCRIPTION else ContractKind.INSURANCE
    val selectedContracts = state.contracts.filter { it.kind == kind }
    LazyColumn(
        modifier = Modifier.testTag("home-list"),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item {
            BrandHeading(all) {
                tab = 0
                filter = ProductFilter.CRITICAL
            }
        }
        item { AccountBadge(account, state.pendingCount, openAccount) }
        item {
            WarrantyOverview(state, products.map { it.second }, today, all) {
                tab = 0
                filter = it
            }
        }
        item {
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContractKind.entries.forEach { type ->
                    ContractOverview(
                        type,
                        state.contracts.filter { it.kind == type },
                        today,
                        Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        contracts(type)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                listOf(R.string.my_products, R.string.subscriptions, R.string.my_insurance)
                    .forEachIndexed { index, title ->
                        Surface(
                            onClick = {
                                tab = index
                                filter = ProductFilter.ALL
                            },
                            modifier = Modifier.weight(1f).testTag("home-tab-$index"),
                            shape = CircleShape,
                            color = if (tab == index) Teal else MaterialTheme.colorScheme.surface,
                            contentColor =
                                if (tab == index) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            border =
                                BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
                                ),
                        ) {
                            Box(
                                Modifier.heightIn(min = 42.dp)
                                    .padding(horizontal = 3.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label(title),
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label(
                        if (tab == 0) filter.title()
                        else if (tab == 1) R.string.subscriptions else R.string.my_insurance
                    ),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { alphabetical = !alphabetical }, Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.SwapVert, label(R.string.change_sort), Modifier.size(20.dp))
                }
                TextButton(
                    onClick = { if (tab == 0) all() else contracts(kind) },
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    Text(label(R.string.view_all), fontSize = 12.sp)
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp))
                }
            }
        }
        if (tab == 0) {
            val visible =
                products
                    .filter { it.second.matches(filter, today) }
                    .let {
                        if (alphabetical) it.sortedBy { row -> row.second.name.lowercase() } else it
                    }
            if (visible.isEmpty())
                item {
                    CompactEmpty(
                        if (products.isEmpty()) R.string.empty_title
                        else R.string.no_products_filter,
                        add,
                    )
                }
            items(visible.take(8), key = { it.second.id }) { (p, product) ->
                ProductRow(p, product) { detail(p.id) }
            }
            if (filter == ProductFilter.RETURNS || filter == ProductFilter.CRITICAL)
                item {
                    Text(
                        label(
                            if (filter == ProductFilter.RETURNS) R.string.returns_explained
                            else R.string.critical_explained
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        } else {
            val visible =
                if (alphabetical) selectedContracts.sortedBy { it.name.lowercase() }
                else selectedContracts.sortedBy { it.nextDate }
            if (visible.isEmpty())
                item { CompactEmpty(R.string.empty_contracts) { contracts(kind) } }
            items(visible.take(8), key = { it.id }) { ContractRow(it) { editContract(it) } }
        }
    }
}

internal fun ProductFilter.title(): Int =
    when (this) {
        ProductFilter.ALL -> R.string.my_products
        ProductFilter.WARRANTY -> R.string.active_warranty
        ProductFilter.CRITICAL -> R.string.critical_warranty
        ProductFilter.RETURNS -> R.string.possible_returns
    }

@Composable
private fun BrandHeading(search: () -> Unit, alerts: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandSymbol(Modifier.size(45.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(label(R.string.brand_ticket))
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(label(R.string.brand_tracker))
                    }
                },
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                label(R.string.brand_subtitle),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(search, Modifier.size(38.dp)) {
            Icon(Icons.Outlined.Search, label(R.string.search_purchases), Modifier.size(23.dp))
        }
        IconButton(alerts, Modifier.size(38.dp)) {
            Icon(
                Icons.Outlined.NotificationsNone,
                label(R.string.critical_warranty),
                Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun WarrantyOverview(
    state: TrackerState,
    products: List<Product>,
    today: LocalDate,
    open: () -> Unit,
    select: (ProductFilter) -> Unit,
) {
    Card(
        onClick = open,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF6FF3E6)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF006570), Color(0xFF004D58), Color(0xFF00747A))
                    )
                )
        ) {
            Gloss(Modifier.matchParentSize())
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Capsule(label(R.string.dashboard_title), Teal)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Outlined.VerifiedUser,
                        null,
                        Modifier.size(34.dp),
                        tint = Color(0xFF9EF1EA),
                    )
                }
                Text(
                    stringResource(R.string.count_total, products.size),
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(ProductFilter.WARRANTY, ProductFilter.CRITICAL, ProductFilter.RETURNS)
                        .forEachIndexed { index, type ->
                            GlassMetric(
                                products.count { it.matches(type, today) },
                                label(type.title()),
                                listOf(
                                    Icons.Outlined.CheckCircle,
                                    Icons.Outlined.WarningAmber,
                                    Icons.AutoMirrored.Outlined.AssignmentReturn,
                                )[index],
                                Color.White,
                                Modifier.weight(
                                        when (type) {
                                            ProductFilter.RETURNS -> 1.3f
                                            ProductFilter.CRITICAL -> .75f
                                            else -> .95f
                                        }
                                    )
                                    .fillMaxHeight()
                                    .testTag("metric-${type.name}"),
                            ) {
                                select(type)
                            }
                        }
                    Column(
                        Modifier.weight(1.45f).fillMaxHeight().padding(start = 3.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            label(R.string.inventory_value),
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        val totals =
                            state.purchases
                                .groupBy { it.total.currency }
                                .map { (currency, purchases) ->
                                    NumberFormat.getCurrencyInstance()
                                        .apply { this.currency = Currency.getInstance(currency) }
                                        .format(
                                            purchases.fold(BigDecimal.ZERO) { sum, p ->
                                                sum + BigDecimal.valueOf(p.total.minorUnits, 2)
                                            }
                                        )
                                }
                        Text(
                            if (totals.isEmpty()) money(Money(0)) else totals.joinToString("\n"),
                            color = Color.White,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContractOverview(
    kind: ContractKind,
    records: List<TrackedContract>,
    today: LocalDate,
    modifier: Modifier,
    open: () -> Unit,
) {
    val subscription = kind == ContractKind.SUBSCRIPTION
    val ink = if (subscription) Color.White else OrangeInk
    Card(
        onClick = open,
        modifier = modifier.testTag("contract-card-${kind.name}"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (subscription) Color(0xFFCF9AFF) else Color(0xFFFFD18C)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.linearGradient(
                        if (subscription)
                            listOf(Color(0xFFAD50F5), Color(0xFF6815BC), Color(0xFF8C32D6))
                        else listOf(Color(0xFFFFBB51), Color(0xFFFF9C12), Color(0xFFFFAF33))
                    )
                )
        ) {
            Gloss(Modifier.matchParentSize())
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Capsule(
                        label(if (subscription) R.string.subscriptions else R.string.insurance),
                        if (subscription) Purple else OrangeInk,
                        Modifier.weight(1f),
                        showArrow = false,
                    )
                    Icon(
                        Icons.Outlined.ChevronRight,
                        null,
                        tint = ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    stringResource(R.string.count_total, records.size),
                    color = ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GlassMetric(
                        records.count { it.active },
                        label(
                            if (subscription) R.string.contract_active
                            else R.string.insurance_active
                        ),
                        null,
                        ink,
                        Modifier.weight(1f).fillMaxHeight(),
                        open,
                    )
                    GlassMetric(
                        records.count { it.needsRenewal(today) },
                        label(R.string.to_renew),
                        Icons.Outlined.Event,
                        ink,
                        Modifier.weight(1f).fillMaxHeight(),
                        open,
                    )
                }
            }
        }
    }
}

@Composable
private fun Gloss(modifier: Modifier) {
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = .18f),
                    Color.Transparent,
                    Color.White.copy(alpha = .06f),
                )
            )
        )
    )
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = .16f), Color.Transparent, Color.Transparent)
            )
        )
    )
}

@Composable
private fun Capsule(
    title: String,
    ink: Color,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true,
) {
    Surface(modifier, color = Color.White, shape = CircleShape) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = ink,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            if (showArrow) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = ink)
        }
    }
}

@Composable
private fun GlassMetric(
    count: Int,
    title: String,
    icon: ImageVector?,
    ink: Color,
    modifier: Modifier,
    open: () -> Unit,
) {
    Surface(
        onClick = open,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = .10f),
        border = BorderStroke(.5.dp, Color.White.copy(alpha = .18f)),
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    count.toString(),
                    color = ink,
                    fontSize = 28.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                icon?.let { Icon(it, null, Modifier.size(13.dp), tint = ink) }
            }
            Text(title, color = ink, fontSize = 10.sp, lineHeight = 12.sp, minLines = 2)
        }
    }
}

@Composable
internal fun ProductRow(purchase: Purchase, product: Product, open: () -> Unit) {
    val today = LocalDate.now()
    val status =
        when {
            product.matches(ProductFilter.CRITICAL, today) -> R.string.ending_soon
            product.matches(ProductFilter.WARRANTY, today) -> R.string.warranty_active
            product.warrantyEnd != null -> R.string.expired
            else -> R.string.unknown_deadline
        }
    CompactRecord(
        product.name,
        purchase.merchant,
        purchase.purchasedOn,
        Icons.Outlined.Inventory2,
        label(status),
        product.warrantyEnd?.let(::day).orEmpty(),
        when (status) {
            R.string.ending_soon -> Color(0xFFFFE9BF)
            R.string.warranty_active -> Color(0xFFDDF6EF)
            else -> Color(0xFFE5E9EF)
        },
        open,
    )
}

@Composable
internal fun ContractRow(record: TrackedContract, open: () -> Unit) {
    val due = record.needsRenewal(LocalDate.now())
    CompactRecord(
        record.name,
        record.provider.ifBlank { money(record.amount) },
        record.nextDate,
        if (record.kind == ContractKind.SUBSCRIPTION) Icons.Outlined.EventRepeat
        else Icons.Outlined.Shield,
        label(
            if (!record.active) R.string.contract_inactive
            else if (due) R.string.to_renew else R.string.record_active
        ),
        money(record.amount),
        if (record.kind == ContractKind.SUBSCRIPTION) Color(0xFFF0E3FF) else Color(0xFFFFE9BF),
        open,
    )
}

@Composable
private fun CompactRecord(
    name: String,
    provider: String,
    date: LocalDate,
    icon: ImageVector,
    status: String,
    note: String,
    badge: Color,
    open: () -> Unit,
) {
    Card(
        onClick = open,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFB9E9EA), Color(0xFFDAF5F1)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(29.dp), tint = Teal)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    provider,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        null,
                        Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        day(date),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    )
                }
            }
            Column(
                Modifier.widthIn(max = 115.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Surface(color = badge, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        status,
                        Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        color = Color(0xFF194D55),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 2,
                    )
                }
                if (note.isNotBlank())
                    Text(note, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactEmpty(title: Int, add: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label(title), style = MaterialTheme.typography.titleSmall)
            TextButton(add, contentPadding = PaddingValues(0.dp)) {
                Text(label(R.string.add_record))
                Icon(Icons.Outlined.Add, null)
            }
        }
    }
}
