package es.sergiotech.tickettracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.domain.ReceiptDocument
import es.sergiotech.tickettracker.domain.SyncController
import es.sergiotech.tickettracker.ui.AccountViewModel
import es.sergiotech.tickettracker.ui.TicketTrackerApp
import es.sergiotech.tickettracker.ui.TrackerViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: TrackerViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    @Inject lateinit var store: PrivateDocumentStore
    @Inject lateinit var sync: SyncController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TicketTrackerApp(viewModel, accountViewModel, store, ::openDocument) }
        if (savedInstanceState == null) receiveShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShare(intent)
    }

    @Suppress("DEPRECATION")
    private fun receiveShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri =
            if (android.os.Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        uri?.takeIf { it.scheme == "content" }?.let { viewModel.importDocument(it) }
    }

    private fun openDocument(document: ReceiptDocument) {
        lifecycleScope.launch {
            try {
                sync.ensureDocument(document)
                val original = requireNotNull(store.availableVariant(document))
                val uri =
                    FileProvider.getUriForFile(
                        this@MainActivity,
                        "$packageName.documents",
                        store.file(original),
                    )
                startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, original.mimeType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                android.widget.Toast.makeText(
                        this@MainActivity,
                        R.string.open_error,
                        android.widget.Toast.LENGTH_LONG,
                    )
                    .show()
            }
        }
    }
}
