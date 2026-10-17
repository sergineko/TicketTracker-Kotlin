package es.sergiotech.tickettracker.data.cloud

import android.content.Context
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import es.sergiotech.tickettracker.domain.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncDependencies {
    fun accounts(): AccountGateway

    fun sync(): SyncController

    fun repository(): PurchaseRepository
}

class CloudSyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val dependencies =
            EntryPointAccessors.fromApplication(applicationContext, SyncDependencies::class.java)
        if (!dependencies.accounts().configured || dependencies.accounts().accounts.first() == null)
            return Result.success()
        return try {
            dependencies.sync().synchronize()
            val pending = dependencies.repository().observePendingCount().first()
            val conflicts = dependencies.sync().conflicts().first().size
            if (pending > conflicts && runAttemptCount < 5) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val code = (failure as? CloudOperationException)?.errorCode
            if (
                code in
                    setOf(
                        "authentication_required",
                        "session_expired",
                        "rate_limited",
                        "quota_exceeded",
                    ) || runAttemptCount >= 5
            )
                Result.failure()
            else Result.retry()
        }
    }

    companion object {
        private val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork("cloud-sync")
                manager.cancelUniqueWork("cloud-sync-periodic")
                return
            }
            manager.enqueueUniquePeriodicWork(
                "cloud-sync-periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CloudSyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build(),
            )
            manager.enqueueUniqueWork(
                "cloud-sync",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CloudSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }
}
