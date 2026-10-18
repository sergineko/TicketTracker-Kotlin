package es.sergiotech.tickettracker.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentsScreen(
    documents: List<ReceiptDocument>,
    store: PrivateDocumentStore,
    import: () -> Unit,
    open: (ReceiptDocument) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { PageHeading(label(R.string.documents)) }
        item {
            OutlinedButton(onClick = import, modifier = Modifier.fillMaxWidth()) {
                Text(label(R.string.import_document))
            }
        }
        if (documents.isEmpty())
            item {
                EmptyState(label(R.string.empty_documents), label(R.string.empty_documents_body))
            }
        items(documents, key = { it.id }) { document ->
            Card(onClick = { open(document) }, shape = RoundedCornerShape(24.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DocumentThumbnail(document, store)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(document.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            label(
                                if (document.state == DocumentState.SYNCED_VERIFIED)
                                    R.string.cloud_document
                                else R.string.local_document
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (document.purchaseId == null)
                            Text(
                                label(R.string.unlinked_document),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        Text(
                            "${document.variants.first { it.kind != VariantKind.THUMBNAIL }.byteSize / 1024} KB",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            label(R.string.open_original),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun DocumentThumbnail(document: ReceiptDocument, store: PrivateDocumentStore) {
    val bitmap by
        produceState<ImageBitmap?>(null, document.id, document.state) {
            value =
                withContext(Dispatchers.IO) {
                    store.preview(document)?.let {
                        BitmapFactory.decodeFile(it.path)?.asImageBitmap()
                    }
                }
        }
    bitmap?.let {
        Image(
            it,
            null,
            Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}
