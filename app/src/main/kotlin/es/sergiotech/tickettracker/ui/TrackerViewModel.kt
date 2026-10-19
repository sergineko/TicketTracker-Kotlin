package es.sergiotech.tickettracker.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.sergiotech.tickettracker.core.documents.ImportResult
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.data.*
import es.sergiotech.tickettracker.domain.*
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrackerState(
    val purchases: List<Purchase> = emptyList(),
    val documents: List<ReceiptDocument> = emptyList(),
    val pendingCount: Int = 0,
    val preferences: AppPreferences = AppPreferences(),
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val contracts: List<TrackedContract> = emptyList(),
)

data class LineDraft(
    val id: String = newId(),
    val productId: String = newId(),
    val name: String = "",
    val quantity: String = "1",
    val price: String = "",
    val warrantyEnd: String = "",
    val returnEnd: String = "",
)

data class SharedAttachment(val uri: Uri, val name: String?)

data class EditorState(
    val original: Purchase? = null,
    val merchant: String = "",
    val date: String = LocalDate.now().toString(),
    val delivered: String = "",
    val total: String = "",
    val currency: String = "EUR",
    val channel: PurchaseChannel = PurchaseChannel.IN_STORE,
    val order: String = "",
    val notes: String = "",
    val lines: List<LineDraft> = listOf(LineDraft()),
    val documentIds: List<String> = emptyList(),
    val error: EditorError? = null,
    val busy: Boolean = false,
    val extracted: Boolean = false,
    val printedDeadlines: List<PrintedDeadline> = emptyList(),
    val dateEdited: Boolean = false,
)

enum class EditorError {
    MERCHANT,
    TOTAL,
    DATE,
    PRODUCT,
    DEADLINE,
    DELIVERY,
    SAVE,
    CONFLICT,
}

sealed interface UiEvent {
    data object Saved : UiEvent

    data object Deleted : UiEvent

    data class Imported(
        val duplicate: Boolean,
        val ocrFailed: Boolean,
        val optimizationFailed: Boolean = false,
        val review: Boolean = true,
    ) : UiEvent

    data class Message(val key: String) : UiEvent
}

@HiltViewModel
class TrackerViewModel
@Inject
constructor(
    private val repository: PurchaseRepository,
    private val savePurchase: SavePurchase,
    private val preferences: LocalPreferences,
    private val documents: PrivateDocumentStore,
    private val exporter: VaultExporter,
    private val coverage: CoverageRepository,
    private val identity: IdentityGateway,
) : ViewModel() {
    private val _sharedAttachments = MutableStateFlow<List<SharedAttachment>>(emptyList())
    val sharedAttachments = _sharedAttachments.asStateFlow()
    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText = _sharedText.asStateFlow()
    private var pendingText: String? = null

    fun offerShared(shared: SharedReceipt) {
        if (shared.uris.isEmpty() && shared.text == null) return
        viewModelScope.launch {
            _sharedAttachments.value =
                withContext(Dispatchers.IO) {
                    shared.uris.map { uri ->
                        val name =
                            try {
                                documents.displayName(uri)
                            } catch (_: Exception) {
                                null
                            }
                        SharedAttachment(uri, name)
                    }
                }
            _sharedText.value = shared.text
        }
    }

    fun dismissShared() {
        _sharedAttachments.value = emptyList()
        _sharedText.value = null
    }

    fun useSharedText(intoEditor: Boolean) {
        val text = _sharedText.value ?: return
        if (intoEditor)
            _editor.update {
                it.withExtraction(ReceiptExtractor().extract(text)).copy(extracted = true)
            }
        else pendingText = text
        dismissShared()
    }

    private val _state = MutableStateFlow(TrackerState())
    val state = _state.asStateFlow()
    private val _editor = MutableStateFlow(EditorState())
    val editor = _editor.asStateFlow()
    private val _processing = MutableStateFlow(false)
    val processing = _processing.asStateFlow()
    private val eventsChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private var observeJob: Job? = null
    private var importJob: Job? = null
    private var pendingImport: ImportResult? = null

    init {
        retry()
    }

    fun retry() {
        observeJob?.cancel()
        observeJob =
            viewModelScope.launch {
                _state.update { it.copy(loading = true, loadError = false) }
                combine(
                        repository.observePurchases(),
                        repository.observeDocuments(),
                        repository.observePendingCount(),
                        preferences.preferences,
                        coverage.observe(),
                    ) { purchases, documents, pending, prefs, protection ->
                        TrackerState(
                            purchases,
                            documents,
                            pending,
                            prefs,
                            loading = false,
                            contracts = protection.contracts,
                        )
                    }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        _state.update { it.copy(loading = false, loadError = true) }
                    }
                    .collect { _state.value = it }
            }
    }

    suspend fun saveContract(contract: TrackedContract) = coverage.save(contract)

    suspend fun archiveContract(contract: TrackedContract) =
        coverage.archive(contract.id, contract.revision)

    fun startEditor(id: String?) {
        val purchase = state.value.purchases.find { it.id == id }
        _editor.value =
            purchase?.let {
                EditorState(
                    it,
                    it.merchant,
                    it.purchasedOn.toString(),
                    it.deliveredOn?.toString().orEmpty(),
                    decimal(it.total.minorUnits),
                    it.total.currency,
                    it.channel,
                    it.orderNumber,
                    it.notes,
                    it.lines.map { line ->
                        LineDraft(
                            line.id,
                            line.product.id,
                            line.product.name,
                            line.quantity.toString(),
                            line.unitPrice?.let { p -> decimal(p.minorUnits) }.orEmpty(),
                            line.product.warrantyEnd?.toString().orEmpty(),
                            line.product.returnEnd?.toString().orEmpty(),
                        )
                    },
                    state.value.documents
                        .filter { doc -> doc.purchaseId == it.id }
                        .map { doc -> doc.id },
                )
            } ?: EditorState()
        pendingImport?.let { applyImport(it) }
        pendingImport = null
        pendingText?.let { text ->
            _editor.update {
                it.withExtraction(ReceiptExtractor().extract(text)).copy(extracted = true)
            }
        }
        pendingText = null
    }

    fun edit(transform: (EditorState) -> EditorState) {
        if (!editor.value.busy) _editor.update { transform(it).copy(error = null) }
    }

    fun line(index: Int, transform: (LineDraft) -> LineDraft) = edit { old ->
        old.copy(
            lines = old.lines.mapIndexed { i, line -> if (i == index) transform(line) else line }
        )
    }

    fun addLine() = edit {
        if (it.lines.size >= 100) it else it.copy(lines = it.lines + LineDraft())
    }

    fun removeLine(index: Int) = edit {
        if (it.lines.size == 1) it
        else it.copy(lines = it.lines.filterIndexed { i, _ -> i != index })
    }

    fun attach(id: String) = edit { old ->
        val document = state.value.documents.find { it.id == id }
        val filled =
            document?.let { old.withExtraction(ReceiptExtractor().extract(it.extractedText)) }
                ?: old
        filled.copy(
            documentIds = (old.documentIds + id).distinct(),
            extracted = document != null || old.extracted,
        )
    }

    fun applyDeadline(index: Int, deadline: PrintedDeadline) =
        line(index) {
            if (deadline !in editor.value.printedDeadlines) it
            else
                when (deadline.kind) {
                    PrintedDeadlineKind.WARRANTY ->
                        if (it.warrantyEnd.isBlank())
                            it.copy(warrantyEnd = deadline.date.toString())
                        else it
                    PrintedDeadlineKind.RETURN ->
                        if (it.returnEnd.isBlank()) it.copy(returnEnd = deadline.date.toString())
                        else it
                }
        }

    fun save() {
        if (editor.value.busy) return
        val draft = editor.value
        fun fail(error: EditorError) {
            _editor.update { it.copy(error = error) }
        }
        if (draft.merchant.isBlank() || draft.merchant.length > 200)
            return fail(EditorError.MERCHANT)
        val total = Money.parse(draft.total, draft.currency) ?: return fail(EditorError.TOTAL)
        val date = date(draft.date) ?: return fail(EditorError.DATE)
        val delivered =
            if (draft.delivered.isBlank()) null
            else date(draft.delivered) ?: return fail(EditorError.DELIVERY)
        if (delivered != null && delivered < date) return fail(EditorError.DELIVERY)
        if (
            draft.lines.any {
                it.name.isBlank() ||
                    it.name.length > 200 ||
                    it.quantity.toIntOrNull() !in 1..999 ||
                    (it.price.isNotBlank() && Money.parse(it.price, draft.currency) == null)
            }
        )
            return fail(EditorError.PRODUCT)
        if (
            draft.lines.any {
                (it.warrantyEnd.isNotBlank() && date(it.warrantyEnd) == null) ||
                    (it.returnEnd.isNotBlank() && date(it.returnEnd) == null)
            }
        )
            return fail(EditorError.DEADLINE)
        val purchase =
            (draft.original
                    ?: Purchase(
                        merchant = draft.merchant,
                        purchasedOn = date,
                        total = total,
                        lines = emptyList(),
                    ))
                .copy(
                    merchant = draft.merchant,
                    purchasedOn = date,
                    deliveredOn = delivered,
                    total = total,
                    channel = draft.channel,
                    orderNumber = draft.order.take(200),
                    notes = draft.notes.take(10_000),
                    lines =
                        draft.lines.map {
                            PurchaseLine(
                                it.id,
                                Product(
                                    it.productId,
                                    it.name.trim(),
                                    date(it.warrantyEnd),
                                    date(it.returnEnd),
                                ),
                                it.quantity.toInt(),
                                it.price.takeIf(String::isNotBlank)?.let { price ->
                                    Money.parse(price, draft.currency)
                                },
                            )
                        },
                )
        _editor.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                savePurchase(purchase, draft.documentIds)
                eventsChannel.send(UiEvent.Saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RevisionConflictException) {
                fail(EditorError.CONFLICT)
            } catch (_: Exception) {
                fail(EditorError.SAVE)
            } finally {
                _editor.update { it.copy(busy = false) }
            }
        }
    }

    fun delete(purchase: Purchase) =
        viewModelScope.launch {
            try {
                repository.delete(purchase.id, purchase.revision)
                eventsChannel.send(UiEvent.Deleted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                eventsChannel.send(UiEvent.Message("delete_error"))
            }
        }

    fun importDocument(uri: Uri, intoEditor: Boolean = false, expectedOwner: String? = null) {
        if (_processing.value) return
        _processing.value = true
        importJob =
            viewModelScope.launch {
                var result: ImportResult? = null
                var committed = false
                try {
                    val owner = expectedOwner ?: identity.identity.first().id
                    check(identity.identity.first().id == owner)
                    result = documents.import(uri)
                    val imported = result
                    check(identity.identity.first().id == owner)
                    val saved =
                        withContext(NonCancellable + Dispatchers.IO) {
                            repository.addDocument(imported.document.copy(ownerId = owner)).also {
                                committed = true
                            }
                        }
                    val duplicate = saved.id != imported.document.id
                    if (
                        duplicate &&
                            saved.variants.none {
                                it.relativePath.startsWith("${imported.document.id}/")
                            }
                    ) {
                        val existingOriginal =
                            saved.variants.first { it.kind == VariantKind.ORIGINAL }
                        if (!documents.verify(existingOriginal)) {
                            val source =
                                imported.document.variants.first { it.kind == VariantKind.ORIGINAL }
                            documents.file(source).inputStream().use {
                                documents.saveDownloaded(existingOriginal, it)
                            }
                        }
                        documents.discardUncommitted(imported.document)
                    }
                    val accepted = imported.copy(document = saved)
                    if (saved.purchaseId == null || saved.purchaseId == editor.value.original?.id) {
                        if (intoEditor) applyImport(accepted) else pendingImport = accepted
                    }
                    eventsChannel.send(
                        UiEvent.Imported(
                            duplicate,
                            imported.ocrFailed,
                            imported.optimizationFailed,
                            saved.purchaseId == null,
                        )
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    eventsChannel.send(UiEvent.Message("import_error"))
                } finally {
                    if (!committed)
                        result?.let {
                            withContext(NonCancellable) {
                                documents.discardUncommitted(it.document)
                            }
                        }
                    _processing.value = false
                }
            }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    private fun applyImport(result: ImportResult) = edit { old ->
        old.withExtraction(result.extraction)
            .copy(documentIds = (old.documentIds + result.document.id).distinct(), extracted = true)
    }

    fun export(uri: Uri) =
        viewModelScope.launch {
            try {
                exporter.export(uri)
                eventsChannel.send(UiEvent.Message("export_success"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                eventsChannel.send(UiEvent.Message("export_error"))
            }
        }

    fun theme(value: String) = setting { preferences.theme(value) }

    fun dynamic(value: Boolean) = setting { preferences.dynamic(value) }

    fun reminders(value: Boolean) = setting { preferences.reminders(value) }

    private fun setting(block: suspend () -> Unit) =
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                eventsChannel.send(UiEvent.Message("settings_error"))
            }
        }

    companion object {
        private fun date(value: String): LocalDate? =
            runCatching { LocalDate.parse(value) }.getOrNull()

        fun decimal(minor: Long) = java.math.BigDecimal.valueOf(minor, 2).toPlainString()
    }
}

internal fun EditorState.withExtraction(extraction: ReceiptExtraction): EditorState {
    val emptyLines =
        lines.all {
            it.name.isBlank() &&
                it.price.isBlank() &&
                it.warrantyEnd.isBlank() &&
                it.returnEnd.isBlank() &&
                it.quantity == "1"
        }
    val adoptCurrency = original == null && total.isBlank() && emptyLines
    val extractedTotal =
        extraction.total?.value?.takeIf { adoptCurrency || it.currency == currency }
    return copy(
        merchant = merchant.ifBlank { extraction.merchant?.value.orEmpty() },
        date =
            if (
                original == null &&
                    !dateEdited &&
                    (date.isBlank() || date == LocalDate.now().toString())
            )
                extraction.date?.value?.toString().orEmpty()
            else date,
        total =
            total.ifBlank {
                extractedTotal?.let { TrackerViewModel.decimal(it.minorUnits) }.orEmpty()
            },
        currency = if (adoptCurrency) extractedTotal?.currency ?: currency else currency,
        lines =
            if (original == null && emptyLines && extraction.items.isNotEmpty())
                extraction.items.map {
                    LineDraft(
                        name = it.name,
                        quantity = it.quantity.toString(),
                        price =
                            it.unitPrice
                                ?.takeIf { p ->
                                    p.currency == (extractedTotal?.currency ?: currency)
                                }
                                ?.let { p -> TrackerViewModel.decimal(p.minorUnits) }
                                .orEmpty(),
                    )
                }
            else lines,
        printedDeadlines =
            (printedDeadlines + extraction.deadlines)
                .distinct()
                .groupBy { it.kind }
                .values
                .mapNotNull { it.distinctBy { d -> d.date }.singleOrNull() },
    )
}
