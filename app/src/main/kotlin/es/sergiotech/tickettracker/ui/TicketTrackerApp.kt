package es.sergiotech.tickettracker.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.core.designsystem.TicketTheme
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.domain.ReceiptDocument
import es.sergiotech.tickettracker.notifications.DeadlineReminderWorker
import kotlinx.coroutines.launch

@Composable
fun TicketTrackerApp(
    vm: TrackerViewModel,
    accountVm: AccountViewModel,
    store: PrivateDocumentStore,
    openDocument: (ReceiptDocument) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val accountState by accountVm.state.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()
    val processing by vm.processing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val topRoutes = listOf("home", "purchases", "documents", "profile")
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                vm.importDocument(
                    it,
                    nav.currentBackStackEntry?.destination?.route?.startsWith("edit/") == true,
                )
            }
        }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) {
            it?.let(vm::export)
        }
    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.reminders(true)
            else
                scope.launch {
                    snackbar.showSnackbar(resources.getString(R.string.notification_denied))
                }
        }
    fun startImport() = import.launch(arrayOf("image/*", "application/pdf"))
    fun startEditor(id: String? = null) {
        vm.startEditor(id)
        nav.navigate("edit/${id ?: "new"}")
    }
    fun top(destination: String) {
        nav.navigate(destination) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(state.preferences.reminders, state.loading) {
        if (!state.loading) DeadlineReminderWorker.schedule(context, state.preferences.reminders)
    }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                UiEvent.Saved,
                UiEvent.Deleted -> nav.popBackStack()
                is UiEvent.Imported -> {
                    if (
                        nav.currentBackStackEntry?.destination?.route?.startsWith("edit/") !=
                            true && !event.duplicate
                    )
                        startEditor()
                    snackbar.showSnackbar(
                        resources.getString(
                            if (event.duplicate) R.string.duplicate_notice
                            else if (event.ocrFailed) R.string.ocr_failed
                            else R.string.import_success
                        )
                    )
                }
                is UiEvent.Message ->
                    snackbar.showSnackbar(
                        resources.getString(
                            when (event.key) {
                                "import_error" -> R.string.import_error
                                "export_success" -> R.string.export_success
                                "export_error" -> R.string.export_error
                                "delete_error" -> R.string.delete_error
                                else -> R.string.settings_error
                            }
                        )
                    )
            }
        }
    }

    TicketTheme(state.preferences.theme, state.preferences.dynamicColor) {
        val view = LocalView.current
        val lightSystemBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (route in topRoutes)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            val icons =
                                listOf(
                                    Icons.Outlined.Home,
                                    Icons.Outlined.ShoppingBag,
                                    Icons.Outlined.FolderOpen,
                                    Icons.Outlined.PersonOutline,
                                )
                            val titles =
                                listOf(
                                    R.string.home,
                                    R.string.nav_purchases,
                                    R.string.nav_documents,
                                    R.string.profile,
                                )
                            topRoutes.forEachIndexed { i, destination ->
                                if (i == 2) {
                                    Box(
                                        Modifier.width(68.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (!state.loading && !state.loadError)
                                            FloatingActionButton(
                                                onClick = { startEditor() },
                                                modifier =
                                                    Modifier.size(54.dp).testTag("add-purchase"),
                                                shape = CircleShape,
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Add,
                                                    label(R.string.add_purchase),
                                                    Modifier.size(30.dp),
                                                )
                                            }
                                    }
                                }
                                NavigationBarItem(
                                    route == destination,
                                    { top(destination) },
                                    icon = { Icon(icons[i], null) },
                                    label = {
                                        Text(
                                            label(titles[i]),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    colors =
                                        NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor =
                                                MaterialTheme.colorScheme.primaryContainer,
                                        ),
                                    modifier = Modifier.testTag("nav-$destination"),
                                )
                            }
                        }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    if (state.loading)
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    else if (state.loadError)
                        EmptyState(
                            label(R.string.load_error),
                            label(R.string.load_error_body),
                            label(R.string.retry),
                            vm::retry,
                        )
                    else
                        NavHost(nav, startDestination = "home") {
                            composable("home") {
                                HomeScreen(
                                    state,
                                    { startEditor() },
                                    { nav.navigate("scanner") },
                                    ::startImport,
                                    { top("purchases") },
                                    { top("documents") },
                                    { nav.navigate("detail/$it") },
                                )
                            }
                            composable("purchases") {
                                PurchasesScreen(
                                    state,
                                    { startEditor() },
                                    { nav.navigate("detail/$it") },
                                )
                            }
                            composable("documents") {
                                DocumentsScreen(state.documents, store, ::startImport, openDocument)
                            }
                            composable("profile") {
                                ProfileScreen(
                                    state,
                                    vm,
                                    accountState,
                                    accountVm,
                                    {
                                        export.launch(
                                            "TicketTracker-${java.time.LocalDate.now()}.zip"
                                        )
                                    },
                                ) { enabled ->
                                    if (
                                        enabled &&
                                            Build.VERSION.SDK_INT >= 33 &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS,
                                            ) != PackageManager.PERMISSION_GRANTED
                                    )
                                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else vm.reminders(enabled)
                                }
                            }
                            composable("detail/{id}") { backStack ->
                                val purchase =
                                    state.purchases.find {
                                        it.id == backStack.arguments?.getString("id")
                                    }
                                PurchaseDetailScreen(
                                    purchase,
                                    state.documents,
                                    { nav.popBackStack() },
                                    { purchase?.let { startEditor(it.id) } },
                                    { purchase?.let(vm::delete) },
                                    openDocument,
                                )
                            }
                            composable("edit/{id}") {
                                PurchaseEditorScreen(
                                    editor,
                                    state.documents,
                                    vm,
                                    { nav.popBackStack() },
                                    ::startImport,
                                    { nav.navigate("scanner") },
                                )
                            }
                            composable("scanner") {
                                ScannerScreen({ nav.popBackStack() }) { uri ->
                                    nav.popBackStack()
                                    vm.importDocument(
                                        uri,
                                        nav.currentBackStackEntry
                                            ?.destination
                                            ?.route
                                            ?.startsWith("edit/") == true,
                                    )
                                }
                            }
                        }
                }
            }
            if (processing)
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(label(R.string.processing)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(label(R.string.processing_body))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = vm::cancelImport) { Text(label(R.string.cancel)) }
                    },
                )
        }
    }
}
