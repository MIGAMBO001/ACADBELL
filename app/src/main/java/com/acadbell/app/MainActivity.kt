package com.acadbell.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.acadbell.app.alarm.InterruptedSessionRecovery
import com.acadbell.app.alarm.MaintenanceWorker
import com.acadbell.app.alarm.ReadingSessionService
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.repository.AcadBellRepository
import com.acadbell.app.ui.navigation.MainTab
import com.acadbell.app.ui.navigation.Routes
import com.acadbell.app.ui.navigation.fullScreenRoutes
import com.acadbell.app.ui.screens.courses.CourseDetailScreen
import com.acadbell.app.ui.screens.courses.CourseEditScreen
import com.acadbell.app.ui.screens.courses.CoursesScreen
import com.acadbell.app.ui.screens.home.HomeScreen
import com.acadbell.app.ui.screens.progress.ProgressScreen
import com.acadbell.app.ui.screens.session.ActiveSessionScreen
import com.acadbell.app.ui.screens.session.SessionSummaryScreen
import com.acadbell.app.ui.screens.settings.DiagnosticsScreen
import com.acadbell.app.ui.screens.settings.SettingsScreen
import com.acadbell.app.ui.screens.timetable.SessionDetailScreen
import com.acadbell.app.ui.screens.timetable.SessionEditScreen
import com.acadbell.app.ui.screens.timetable.TimetableScreen
import com.acadbell.app.ui.screens.topics.PasteTopicsScreen
import com.acadbell.app.ui.shell.AcadBellShell
import com.acadbell.app.ui.theme.AcadBellTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Result is surfaced on the Settings screen; nothing to handle here. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        askForNotificationPermission()

        setContent {
            val themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            AcadBellTheme(darkTheme = dark) {
                AcadBellRoot(
                    startRoute = intent?.getStringExtra(EXTRA_ROUTE)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Self-healing every time the student opens the app.
        val app = application
        kotlinx.coroutines.MainScope().launch {
            runCatching {
                val repo = ServiceLocator.repository(app)
                val today = TimeUtils.todayEpochDay()
                repo.materialiseRange(today, today + AcadBellRepository.MaterialiseAheadDays)
                repo.reconcileState()
                ServiceLocator.alarmScheduler(app).armAll()
                InterruptedSessionRecovery.run(app)
            }
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    companion object {
        const val EXTRA_ROUTE = "acadbell.route"
    }
}

class ThemeViewModel(app: android.app.Application) : androidx.lifecycle.AndroidViewModel(app) {
    private val prefs = ServiceLocator.preferences(app)
    val themeMode: StateFlow<String> =
        prefs.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")
}

@Composable
private fun AcadBellRoot(startRoute: String?) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        MaintenanceWorker.runNow(navController.context)
        // Deep link from an alarm notification (e.g. "session/12", "summary/7").
        startRoute?.let { route ->
            val target = when {
                route.startsWith("session/") -> Routes.sessionDetail(route.removePrefix("session/").toLongOrNull() ?: return@let)
                route.startsWith("summary/") -> Routes.summary(route.removePrefix("summary/").toLongOrNull() ?: return@let)
                route.startsWith("active/") -> Routes.activeSession(route.removePrefix("active/").toLongOrNull() ?: return@let)
                else -> null
            }
            target?.let { runCatching { navController.navigate(it) } }
        }
    }

    val showBottomBar = currentRoute == null || !fullScreenRoutes.contains(currentRoute)

    AcadBellShell(
        navController = navController,
        showBottomBar = showBottomBar
    ) { paddingModifier ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = paddingModifier
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenTimetable = { navController.navigateTab(MainTab.TIMETABLE) },
                    onOpenCourses = { navController.navigateTab(MainTab.COURSES) },
                    onCreateSession = { navController.navigate(Routes.sessionEdit()) },
                    onOpenSession = { navController.navigate(Routes.sessionDetail(it)) },
                    onOpenActiveSession = { navController.navigate(Routes.activeSession(it)) },
                    onOpenProgress = { navController.navigateTab(MainTab.PROGRESS) }
                )
            }

            composable(Routes.TIMETABLE) {
                TimetableScreen(
                    onOpenEntry = { navController.navigate(Routes.sessionDetail(it)) },
                    onCreateSession = { dateEpochDay: Long? ->
                        val preset: Long? = dateEpochDay?.takeIf { it > 0L }
                        navController.navigate(Routes.sessionEdit(dateEpochDay = preset))
                    },
                    onStartSession = { occurrenceId ->
                        val ctx = navController.context
                        ReadingSessionService.start(ctx, occurrenceId = occurrenceId)
                        navController.navigate(Routes.activeSession(-1L).replace("-1", "live"))
                    }
                )
            }

            composable(Routes.COURSES) {
                CoursesScreen(
                    onOpenCourse = { navController.navigate(Routes.courseDetail(it)) },
                    onCreateCourse = { navController.navigate(Routes.courseEdit()) }
                )
            }

            composable(Routes.PROGRESS) {
                ProgressScreen(
                    onOpenCourseProgress = { navController.navigate(Routes.progressDetail(it)) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) }
                )
            }

            // ── Course flows ──
            composable(
                Routes.COURSE_DETAIL,
                arguments = listOf(navArgument("courseId") { type = NavType.LongType })
            ) { entry ->
                val courseId = entry.arguments?.getLong("courseId") ?: 0L
                CourseDetailScreen(
                    courseId = courseId,
                    onBack = { navController.popBackStack() },
                    onEditCourse = { navController.navigate(Routes.courseEdit(it)) },
                    onPasteTopics = { navController.navigate(Routes.pasteTopics(it)) },
                    onEditTopic = { courseIdArg, topicId -> navController.navigate(Routes.topicEdit(courseIdArg, topicId)) },
                    onDeleted = { navController.popBackStack() }
                )
            }

            composable(
                Routes.COURSE_EDIT,
                arguments = listOf(navArgument("courseId") { type = NavType.LongType; defaultValue = -1L })
            ) { entry ->
                val courseId = entry.arguments?.getLong("courseId") ?: -1L
                CourseEditScreen(
                    courseId = courseId,
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.popBackStack()
                        if (courseId <= 0) navController.navigate(Routes.courseDetail(id))
                    }
                )
            }

            composable(
                Routes.PASTE_TOPICS,
                arguments = listOf(navArgument("courseId") { type = NavType.LongType })
            ) { entry ->
                val courseId = entry.arguments?.getLong("courseId") ?: 0L
                PasteTopicsScreen(
                    courseId = courseId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            // ── Timetable flows ──
            composable(
                Routes.SESSION_EDIT,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("dateEpochDay") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { entry ->
                SessionEditScreen(
                    sessionId = entry.arguments?.getLong("sessionId") ?: -1L,
                    presetDateEpochDay = entry.arguments?.getLong("dateEpochDay") ?: -1L,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(
                Routes.SESSION_DETAIL,
                arguments = listOf(navArgument("occurrenceId") { type = NavType.LongType })
            ) { entry ->
                val occurrenceId = entry.arguments?.getLong("occurrenceId") ?: 0L
                SessionDetailScreen(
                    occurrenceId = occurrenceId,
                    onBack = { navController.popBackStack() },
                    onEdit = { sessionId -> navController.navigate(Routes.sessionEdit(sessionId)) },
                    onStart = { occId ->
                        ReadingSessionService.start(navController.context, occurrenceId = occId)
                        navController.navigate(Routes.activeSession(-1L).replace("-1", "live"))
                    },
                    onStartAdHoc = {
                        ReadingSessionService.start(navController.context, occurrenceId = null)
                        navController.navigate(Routes.activeSession(-1L).replace("-1", "live"))
                    },
                    onDeleted = { navController.popBackStack() }
                )
            }

            // ── Live session ──
            composable(
                "active/{runId}",
                arguments = listOf(navArgument("runId") { type = NavType.StringType })
            ) { entry ->
                val raw = entry.arguments?.getString("runId") ?: "live"
                val runId = raw.toLongOrNull() ?: -1L
                ActiveSessionScreen(
                    runId = resolveRunId(runId),
                    onBack = { navController.popBackStack() },
                    onFinished = { id ->
                        navController.navigate(Routes.summary(id)) {
                            popUpTo(Routes.HOME)
                        }
                    }
                )
            }

            composable(
                Routes.SESSION_SUMMARY,
                arguments = listOf(navArgument("runId") { type = NavType.LongType })
            ) { entry ->
                val runId = entry.arguments?.getLong("runId") ?: 0L
                SessionSummaryScreen(
                    runId = runId,
                    onBack = { navController.popBackStack() },
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                Routes.PROGRESS_DETAIL,
                arguments = listOf(navArgument("courseId") { type = NavType.LongType })
            ) { entry ->
                val courseId = entry.arguments?.getLong("courseId") ?: 0L
                CourseDetailScreen(
                    courseId = courseId,
                    onBack = { navController.popBackStack() },
                    onEditCourse = { navController.navigate(Routes.courseEdit(it)) },
                    onPasteTopics = { navController.navigate(Routes.pasteTopics(it)) },
                    onEditTopic = { cId, topicId -> navController.navigate(Routes.topicEdit(cId, topicId)) },
                    onDeleted = { navController.popBackStack() }
                )
            }
        }
    }
}

/** Resolves "-1" (start-from-notification) to the currently active run id. */
@Composable
private fun resolveRunId(requested: Long): Long {
    if (requested > 0) return requested
    val live by ReadingSessionService.state.collectAsStateWithLifecycle()
    if (live.runId > 0) return live.runId
    // Fall back to whatever run is ACTIVE in the database; the screen will show a resume prompt.
    return -1L
}

private fun NavHostController.navigateTab(tab: MainTab) {
    navigate(tab.route) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}
