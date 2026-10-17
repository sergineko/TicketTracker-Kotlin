package es.sergiotech.tickettracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import es.sergiotech.tickettracker.data.cloud.CloudSyncWorker
import es.sergiotech.tickettracker.domain.AccountGateway
import es.sergiotech.tickettracker.domain.PurchaseRepository
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltAndroidApp
class TicketTrackerApplication : Application() {
    @Inject lateinit var accounts: AccountGateway
    @Inject lateinit var repository: PurchaseRepository
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            combine(
                    accounts.accounts,
                    repository.observePurchases(),
                    repository.observeDocuments(),
                    repository.observePendingCount(),
                ) { account, _, _, _ ->
                    account != null && accounts.configured
                }
                .debounce(750)
                .catch { failure -> if (failure is CancellationException) throw failure }
                .collect { enabled ->
                    CloudSyncWorker.schedule(this@TicketTrackerApplication, enabled)
                }
        }
    }
}
