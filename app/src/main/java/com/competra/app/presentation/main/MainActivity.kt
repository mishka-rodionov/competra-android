package com.competra.app.presentation.main

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.competra.analytics.AnalyticsTracker
import com.competra.analytics.TrackNavScreens
import com.competra.center.navigation.centerGraph
import com.competra.clubs.navigation.clubsGraph
import com.competra.rating.navigation.ratingGraph
import com.competra.data.navigation.ClubsNavigation
import com.competra.data.navigation.BackRoute
import com.competra.data.navigation.BaseNavigation
import com.competra.data.navigation.CenterNavigation
import com.competra.data.navigation.DiaryNavigation
import com.competra.data.navigation.EventsNavigation
import com.competra.data.navigation.PendingPushNavigationRepository
import com.competra.data.navigation.ProfileNavigation
import com.competra.diary.navigation.diaryGraph
import com.competra.events.navigation.eventsGraph
import com.competra.onboarding.presentation.OnboardingScreen
import com.competra.profile.navigation.profileNavigation
import com.competra.app.BottomNavItem
import com.competra.app.service.CompetitionForegroundService
import com.competra.app.service.WorkoutTrackingService
import com.competra.app.service.CompetitionTrackingService
import com.competra.core.tracking.CompetitionTrackingCommand
import com.competra.app.ui.theme.CompetraTheme
import com.competra.ui.CompetitionServiceCommand
import com.competra.ui.WorkoutTrackingCommand
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject

/**
 * Главная Activity приложения.
 * Является точкой входа, управляет жизненным циклом NFC-адаптера, foreground-сервисом соревнования
 * и запросом необходимых разрешений.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModel()

    private val nfcPendingIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            this, 0,
            Intent(
                this,
                this.javaClass
            ).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.setNfcAdapter(NfcAdapter.getDefaultAdapter(this))
        setContent {
            CompetraTheme {
                AppRoot(viewModel)
            }
        }
        observeServiceCommands()
        observeWorkoutTrackingCommands()
        observeCompetitionTrackingCommands()
        handlePushIntent(intent)
    }

    /**
     * Подписка на команды управления сервисом (запуск/остановка).
     */
    private fun observeServiceCommands() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.serviceCommands.collect { cmd ->
                    when (cmd) {
                        is CompetitionServiceCommand.Start ->
                            startForegroundService(
                                CompetitionForegroundService.startIntent(
                                    this@MainActivity,
                                    cmd.competitionId,
                                    cmd.startTimeMs
                                )
                            )
                        is CompetitionServiceCommand.Stop ->
                            stopService(
                                Intent(this@MainActivity, CompetitionForegroundService::class.java)
                            )
                    }
                }
            }
        }
    }

    /**
     * Подписка на команды управления сервисом live-трекинга тренировки. Пауза/резюм/стоп
     * доставляются в уже запущенный сервис обычным `startService` — новый `startForeground`
     * не требуется, сервис уже промоутирован в foreground командой Start.
     */
    private fun observeWorkoutTrackingCommands() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.workoutTrackingCommands.collect { cmd ->
                    when (cmd) {
                        is WorkoutTrackingCommand.Start ->
                            startForegroundService(WorkoutTrackingService.startIntent(this@MainActivity, cmd.sportType))
                        WorkoutTrackingCommand.Pause ->
                            startService(WorkoutTrackingService.pauseIntent(this@MainActivity))
                        WorkoutTrackingCommand.Resume ->
                            startService(WorkoutTrackingService.resumeIntent(this@MainActivity))
                        is WorkoutTrackingCommand.Stop ->
                            startService(WorkoutTrackingService.stopIntent(this@MainActivity, cmd.discard))
                    }
                }
            }
        }
    }

    /**
     * Подписка на команды сервиса онлайн-трекинга бегуна. Старт — через `startForegroundService`
     * (сервис сам промоутится в foreground с типом location), стоп — обычной командой в уже
     * работающий сервис: он прекращает GPS и досылает буфер.
     */
    private fun observeCompetitionTrackingCommands() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.competitionTrackingCommands.collect { cmd ->
                    when (cmd) {
                        is CompetitionTrackingCommand.StartRecording ->
                            startForegroundService(CompetitionTrackingService.startIntent(this@MainActivity, cmd.sessionId))
                        is CompetitionTrackingCommand.StopRecording ->
                            startService(CompetitionTrackingService.stopIntent(this@MainActivity, cmd.sessionId))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.enableReaderMode(this, viewModel::onNewTagDetected)
    }

    override fun onPause() {
        super.onPause()
        viewModel.disableReaderMode(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        }
        tag?.let(viewModel::onNewTagDetected)
        handlePushIntent(intent)
    }

    /** Извлекает extras, положенные [com.competra.app.fcm.CompetraMessagingService] при тапе на push. */
    private fun handlePushIntent(intent: Intent) {
        val competitionId = intent.getStringExtra(EXTRA_PUSH_COMPETITION_ID) ?: return
        val kind = intent.getStringExtra(EXTRA_PUSH_KIND)
        viewModel.onPushNavigation(competitionId, kind)
    }

    companion object {
        const val EXTRA_PUSH_KIND = "push_kind"
        const val EXTRA_PUSH_COMPETITION_ID = "push_competition_id"
    }
}

/**
 * Основной экран приложения с нижней навигацией.
 * 
 * @param viewModel Вьюмодель управления общим состоянием.
 * @param windowSizeClass Параметры размера экрана для адаптивной верстки.
 */
@Composable
internal fun MainScreen(viewModel: MainViewModel, windowSizeClass: WindowSizeClass) {
    BottomNavItem.all // не удалять, падает при первом tab.route
    var selectedTab by rememberSaveable { mutableStateOf<String>(BottomNavItem.CompetitionList.route) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanEvent by viewModel.currentScanEvent.collectAsState()
    val startAlertEvent by viewModel.startAlertEvent.collectAsState()
    val conflictEvent by viewModel.conflictEvent.collectAsState()
    val networkError by viewModel.networkErrorEvent.collectAsState()
    val isLoading by viewModel.loadingEvent.collectAsState()
    val onboardingRequest by viewModel.onboardingRequest.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.switchTabEffect.collect { tabRoute ->
            selectedTab = tabRoute
        }
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                BottomNavItem.all.forEach { tab ->
                    val isSelected = selectedTab == tab.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = ImageVector.vectorResource(tab.iconRes),
                                contentDescription = tab.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { 
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium
                            ) 
                        },
                        selected = isSelected,
                        onClick = { selectedTab = tab.route },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->

        Box(
            Modifier
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            BottomNavItem.all.forEach { tab ->

                val isSelected = tab.route == selectedTab

                AnimatedVisibility(visible = isSelected, enter = fadeIn(), exit = fadeOut()) {
                    saveableStateHolder.SaveableStateProvider(tab.route) {
                        val navController = rememberNavController()
                        val analyticsTracker = koinInject<AnalyticsTracker>()
                        val pendingPushNavigationRepository = koinInject<PendingPushNavigationRepository>()
                        TrackNavScreens(navController, analyticsTracker)

                        val isSelectedTab = selectedTab == tab.route
                        LaunchedEffect(navController, isSelectedTab) {
                            if (!isSelectedTab) return@LaunchedEffect
                            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                launch {
                                    viewModel.baseNavigationEffect.collectLatest { route ->
                                        if (route is BackRoute) {
                                            navController.popBackStack()
                                        }
                                    }
                                }

                                launch {
                                    viewModel.collectNavigationEffect(
                                        navigationHandler = { route ->
                                            val navBuilder = route.navOptionsBuilder
                                            if (navBuilder != null) {
                                                navController.navigate(route, navBuilder)
                                            } else {
                                                navController.navigate(route = route)
                                            }
                                            route.navOptionsBuilder = null
                                        },
                                        destination = checkNavigation(tab)
                                    )
                                }

                                // Отложенный переход по тапу на push-уведомление (см. MainViewModel.onPushNavigation).
                                // Идёт напрямую через navController, а не через Navigation/SharedFlow: этот блок
                                // выполняется, только когда таб уже активен и NavHost точно смонтирован.
                                if (tab == BottomNavItem.CompetitionList) {
                                    launch {
                                        pendingPushNavigationRepository.pending.collectLatest { route ->
                                            if (route == null) return@collectLatest
                                            pendingPushNavigationRepository.clear()
                                            val navBuilder = route.navOptionsBuilder
                                            if (navBuilder != null) {
                                                navController.navigate(route, navBuilder)
                                            } else {
                                                navController.navigate(route = route)
                                            }
                                            route.navOptionsBuilder = null
                                        }
                                    }
                                }
                            }
                        }
                        NavHost(
                            navController = navController,
                            startDestination = checkNavigation(tab),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when (tab) {
                                BottomNavItem.Profile -> profileNavigation()
                                BottomNavItem.CompetitionList -> eventsGraph()
                                BottomNavItem.CompetitionConstructor -> centerGraph(windowSizeClass, navController)
                                BottomNavItem.Diary -> diaryGraph()
                                BottomNavItem.Clubs -> {
                                    clubsGraph()
                                    ratingGraph()
                                }
                            }
                        }
                    }
                }
            }

            ParticipantStartBanner(
                event = startAlertEvent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(11f)
            )

            NfcScanBanner(
                event = scanEvent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(10f)
            )
        }
    }

    GlobalLoader(isLoading = isLoading)

    onboardingRequest?.let { request ->
        OnboardingScreen(
            source = request.source,
            viewModelKey = "onboarding_settings_${request.requestId}",
            onFinished = viewModel::dismissOnboarding
        )
    }

    conflictEvent?.let { event ->
        ResultConflictBottomSheet(
            event = event,
            onApply = viewModel::applyConflict,
            onCancel = viewModel::cancelConflict
        )
    }

    networkError?.let { event ->
        when (event.code) {
            401 -> {
                // TODO: принудительный logout / навигация на экран авторизации
                viewModel.dismissNetworkError()
            }
            else -> {
                NetworkErrorBottomSheet(
                    message = event.message ?: "Произошла ошибка",
                    onDismiss = viewModel::dismissNetworkError
                )
            }
        }
    }
}

/**
 * Определяет начальный роут для каждого таба нижней навигации.
 */
private fun checkNavigation(tab: BottomNavItem): BaseNavigation = when (tab) {
    BottomNavItem.Profile -> ProfileNavigation.MainProfileRoute
    BottomNavItem.CompetitionList -> EventsNavigation.EventsRoute
    BottomNavItem.CompetitionConstructor -> CenterNavigation.CenterRoute
    BottomNavItem.Diary -> DiaryNavigation.DiaryRoute
    BottomNavItem.Clubs -> ClubsNavigation.ClubsBaseRoute
}

/**
 * Глобальный индикатор загрузки, перекрывающий весь экран.
 * Используется для длительных операций, таких как сетевые запросы.
 *
 * @param isLoading Флаг отображения лоадера.
 */
@Composable
private fun GlobalLoader(isLoading: Boolean) {
    AnimatedVisibility(
        visible = isLoading,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.zIndex(100f) // Поверх всего, включая баннеры и диалоги
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Блокируем клики по контенту под лоадером
                ),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}
