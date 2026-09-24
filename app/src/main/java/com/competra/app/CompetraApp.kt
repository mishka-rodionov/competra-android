package com.competra.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Configuration
import com.competra.analytics.di.analyticsCoreModule
import com.competra.analytics.initAppMetrica
import com.competra.center.di.centerModule
import com.competra.clubs.di.clubsModule
import com.competra.diary.di.diaryModule
import com.competra.core.sync.NetworkAvailabilityObserver
import com.competra.core.sync.SyncBootstrap
import com.competra.core.sync.WorkoutSyncBootstrap
import com.competra.core.sync.di.syncModule
import com.competra.app.service.CompetitionForegroundService
import com.competra.app.service.WorkoutTrackingService
import com.competra.app.service.CompetitionTrackingService
import com.competra.core.tracking.LiveTrackFlushWorker
import com.competra.core.tracking.di.liveTrackingModule
import com.competra.remote.di.liveTrackDataModule
import com.competra.data.navigation.di.navigationModule
import com.competra.eventdetails.di.eventDetailsModule
import com.competra.events.di.eventsModule
import com.competra.local.di.databaseModule
import com.competra.local.di.localModule
import com.competra.nfchelper.di.nfcModule
import com.competra.onboarding.di.onboardingModule
import com.competra.profile.di.profileModule
import com.competra.rating.di.ratingModule
import com.competra.remote.di.authModule
import com.competra.remote.di.clubsDataModule
import com.competra.remote.di.deviceModule
import com.competra.remote.di.diaryDataModule
import com.competra.remote.di.eventsDataModule
import com.competra.remote.di.orienteeringModule
import com.competra.remote.di.ratingDataModule
import com.competra.remote.di.retrofitModule
import com.competra.remote.di.uploadModule
import com.competra.resources.di.resourceModule
import com.competra.app.BuildConfig
import com.competra.app.di.firebaseModule
import com.competra.app.di.mainModule
import com.competra.app.fcm.CompetraMessagingService
import com.competra.resources.R as ResR
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

/**
 * Класс приложения Competra.
 * Выполняет инициализацию Koin (Dependency Injection) и создание каналов уведомлений.
 */
class CompetraApp : Application(), Configuration.Provider {

    private val networkObserver: NetworkAvailabilityObserver by inject()

    /**
     * On-demand Configuration: WorkManagerInitializer (отключённый в Manifest) больше не
     * вызывается, поэтому WorkManager при первом обращении подтягивает эту конфигурацию.
     * setWorkerFactory ставится через workManagerFactory() в Koin DSL.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@CompetraApp)
            workManagerFactory()
            // core modules
            modules(
                retrofitModule, databaseModule, navigationModule, resourceModule, nfcModule,
                localModule, syncModule, liveTrackingModule
            )

            // data modules
            modules(
                authModule, orienteeringModule, eventsDataModule, uploadModule, deviceModule, diaryDataModule,
                clubsDataModule, ratingDataModule, liveTrackDataModule
            )

            // feature modules
            modules(
                mainModule, centerModule, eventsModule, eventDetailsModule, profileModule, onboardingModule,
                diaryModule, clubsModule, ratingModule
            )

            // firebase
            modules(firebaseModule)

            // analytics
            modules(analyticsCoreModule)
        }

        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
        initAppMetrica(this, BuildConfig.APPMETRICA_API_KEY)

        createNotificationChannel()
        createWorkoutTrackingNotificationChannel()
        createLiveTrackNotificationChannel()
        createPushNotificationChannel()

        // Подписываемся на появление сети — каждое появление триггерит SyncCenterWorker и
        // WorkoutSyncWorker (тренировочный дневник синкается независимо от :feature:center).
        networkObserver.start {
            SyncBootstrap.enqueue(this)
            WorkoutSyncBootstrap.enqueue(this)
            LiveTrackFlushWorker.enqueue(this)
        }
        // Дополнительный enqueue на старте — на случай, если сеть уже есть и есть unsynced.
        SyncBootstrap.enqueue(this)
        WorkoutSyncBootstrap.enqueue(this)
        // Недосланный хвост онлайн-трека (стоп без сети, сервис убит системой).
        LiveTrackFlushWorker.enqueue(this)
    }

    /**
     * Создает канал уведомлений для работы сервиса соревнований.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CompetitionForegroundService.CHANNEL_ID,
            "Соревнование",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Информация о текущем соревновании" }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Создаёт канал уведомлений для сервиса live-трекинга тренировки.
     */
    private fun createWorkoutTrackingNotificationChannel() {
        val channel = NotificationChannel(
            WorkoutTrackingService.CHANNEL_ID,
            "Тренировка",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Live-трекинг тренировки: время, дистанция" }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Создаёт канал уведомлений для сервиса онлайн-трекинга бегуна на соревновании.
     */
    private fun createLiveTrackNotificationChannel() {
        val channel = NotificationChannel(
            CompetitionTrackingService.CHANNEL_ID,
            "Онлайн-трек",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Запись и передача трека на соревновании" }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createPushNotificationChannel() {
        val channel = NotificationChannel(
            CompetraMessagingService.PUSH_CHANNEL_ID,
            getString(ResR.string.notif_channel_push_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(ResR.string.notif_channel_push_desc) }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
