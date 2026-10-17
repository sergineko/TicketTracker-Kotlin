package es.sergiotech.tickettracker.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import es.sergiotech.tickettracker.core.database.TicketDatabase
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.data.*
import es.sergiotech.tickettracker.data.cloud.*
import es.sergiotech.tickettracker.domain.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TicketDatabase =
        Room.databaseBuilder(context, TicketDatabase::class.java, "tickettracker.db")
            .addMigrations(
                TicketDatabase.MIGRATION_1_2,
                TicketDatabase.MIGRATION_2_3,
                TicketDatabase.MIGRATION_3_4,
            )
            .build()

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context) = LocalPreferences(context)

    @Provides
    @Singleton
    fun sessionStore(
        @ApplicationContext context: Context,
        db: TicketDatabase,
        preferences: LocalPreferences,
    ) = CloudSessionStore(context, db, preferences)

    @Provides fun identity(sessions: CloudSessionStore): IdentityGateway = sessions

    @Provides @Singleton fun cloudApi(): RemoteApi = CloudflareApi()

    @Provides
    @Singleton
    fun accounts(api: RemoteApi, sessions: CloudSessionStore): AccountGateway =
        CloudflareAccountGateway(api, sessions)

    @Provides
    @Singleton
    fun sync(
        db: TicketDatabase,
        api: RemoteApi,
        sessions: CloudSessionStore,
        store: PrivateDocumentStore,
    ): SyncController = CloudflareSyncController(db, api, sessions, store)

    @Provides
    @Singleton
    fun repository(db: TicketDatabase, identity: IdentityGateway): PurchaseRepository =
        RoomPurchaseRepository(db, identity)

    @Provides
    @Singleton
    fun coverage(db: TicketDatabase, identity: IdentityGateway): CoverageRepository =
        RoomCoverageRepository(db, identity)

    @Provides
    @Singleton
    fun documents(@ApplicationContext context: Context) = PrivateDocumentStore(context)

    @Provides
    fun exporter(
        @ApplicationContext context: Context,
        repository: PurchaseRepository,
        store: PrivateDocumentStore,
        coverage: CoverageRepository,
        identity: IdentityGateway,
    ) = VaultExporter(context, repository, store, coverage, identity)

    @Provides fun savePurchase(repository: PurchaseRepository) = SavePurchase(repository)
}
