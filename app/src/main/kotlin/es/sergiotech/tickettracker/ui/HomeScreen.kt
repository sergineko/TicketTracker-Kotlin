package es.sergiotech.tickettracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.domain.*
import java.time.LocalDate

@Composable
fun HomeScreen(
    state: TrackerState,
    add: () -> Unit,
    scan: () -> Unit,
    import: () -> Unit,
    all: () -> Unit,
    documents: () -> Unit,
    detail: (String) -> Unit,
) {
    val today = LocalDate.now()
    val products = state.purchases.flatMap { it.lines }.map { it.product }
    val alerts = upcomingDeadlines(state.purchases, today)
    val warranties = products.count { it.warrantyEnd?.let { end -> end >= today } == true }
    val returns = products.count { it.returnEnd?.let { end -> end >= today } == true }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { BrandHeading(all) }
        item { WarrantyOverview(products.size, state.purchases.size, warranties, alerts.size, all) }
        item {
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OverviewTile(
                    label(R.string.dashboard_returns),
                    returns,
                    label(R.string.dashboard_returns_body),
                    Icons.Outlined.EventAvailable,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    Modifier.weight(1f).fillMaxHeight(),
                    all,
                )
                OverviewTile(
                    label(R.string.documents),
                    state.documents.size,
                    label(R.string.dashboard_documents_body),
                    Icons.Outlined.Description,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                    Modifier.weight(1f).fillMaxHeight(),
                    documents,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = scan, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Outlined.DocumentScanner, null)
                    Spacer(Modifier.width(10.dp))
                    Text(label(R.string.scan))
                }
                OutlinedButton(onClick = import, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FileUpload, null)
                    Spacer(Modifier.width(10.dp))
                    Text(label(R.string.import_document))
                }
            }
        }
        if (alerts.isNotEmpty()) {
            item { Text(label(R.string.upcoming), style = MaterialTheme.typography.titleLarge) }
            items(alerts.take(5)) { alert ->
                OutlinedCard(onClick = { detail(alert.purchaseId) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(alert.productName, style = MaterialTheme.typography.titleMedium)
                            DeadlineLabel(alert.end, alert.isReturn)
                        }
                    }
                }
            }
        }
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
        if (state.purchases.isEmpty()) {
            item {
                EmptyState(
                    label(R.string.empty_title),
                    label(R.string.empty_body),
                    label(R.string.manual_entry),
                    add,
                )
            }
        } else {
            items(state.purchases.take(5), key = { it.id }) { PurchaseCard(it) { detail(it.id) } }
        }
        item {
            Text(
                label(R.string.local_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun BrandHeading(search: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painterResource(R.drawable.brand_icon),
            null,
            Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(label(R.string.brand_ticket))
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(label(R.string.brand_tracker))
                    }
                },
                fontSize = 23.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                label(R.string.brand_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = search) {
            Icon(Icons.Outlined.Search, label(R.string.search_purchases))
        }
    }
}

@Composable
private fun WarrantyOverview(
    products: Int,
    purchases: Int,
    warranties: Int,
    upcoming: Int,
    open: () -> Unit,
) {
    Card(
        onClick = open,
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Color(0xFF5DE4D5).copy(alpha = 0.5f)),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF005B68), Color(0xFF00777D), Color(0xFF006C74))
                    )
                )
        ) {
            Image(
                painterResource(R.drawable.brand_mark),
                null,
                Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 8.dp).size(90.dp),
                alpha = 0.08f,
            )
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label(R.string.dashboard_title),
                        Modifier.weight(1f),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(Icons.Outlined.ChevronRight, null, tint = Color.White)
                }
                Text(
                    pluralStringResource(R.plurals.dashboard_total, products, products),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OverviewMetric(
                        purchases,
                        label(R.string.dashboard_purchases),
                        Modifier.weight(1f),
                    )
                    OverviewMetric(
                        warranties,
                        label(R.string.dashboard_warranties),
                        Modifier.weight(1f),
                    )
                    OverviewMetric(
                        upcoming,
                        label(R.string.dashboard_upcoming),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewMetric(value: Int, title: String, modifier: Modifier) {
    Surface(
        modifier.fillMaxHeight(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.07f),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                value.toString(),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(title, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun OverviewTile(
    title: String,
    count: Int,
    body: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier,
    open: () -> Unit,
) {
    Card(
        onClick = open,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(22.dp))
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
            }
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}
