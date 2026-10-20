package es.sergiotech.tickettracker.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import es.sergiotech.tickettracker.domain.ContractKind
import es.sergiotech.tickettracker.domain.ReceiptDocument
import es.sergiotech.tickettracker.notifications.DeadlineReminderWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketTrackerApp(
    vm: TrackerViewModel,
    accountVm: AccountViewModel,
    store: PrivateDocumentStore,
    openDocument: (ReceiptDocument) -> Unit,
    gmailVm: GmailViewModel? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val accountState by accountVm.state.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()
    val processing by vm.processing.collectAsStateWithLifecycle()
    val sharedAttachments by vm.sharedAttachments.collectAsStateWithLifecycle()
    val sharedText by vm.sharedText.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    var addMenu by remember { mutableStateOf(false) }
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
                            true && event.review
                    )
                        startEditor()
                    snackbar.showSnackbar(
                        resources.getString(
                            if (event.duplicate) R.string.duplicate_notice
                            else if (event.optimizationFailed) R.string.image_optimization_failed
                            else if (event.ocrFailed) R.string.ocr_failed
                            else if (event.partialRead) R.string.ocr_partial
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
                        Box {
                            NavigationBar(
                                modifier =
                                    Modifier.background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                    ),
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                            ) {
                                val icons =
                                    listOf(
                                        Icons.Outlined.Home,
                                        Icons.Outlined.Inventory2,
                                        Icons.Outlined.Description,
                                        Icons.Outlined.MoreHoriz,
                                    )
                                val titles =
                                    listOf(
                                        R.string.home,
                                        R.string.nav_purchases,
                                        R.string.nav_documents,
                                        R.string.more,
                                    )
                                topRoutes.forEachIndexed { i, destination ->
                                    if (i == 2) Spacer(Modifier.width(68.dp))
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
                                                selectedIconColor =
                                                    MaterialTheme.colorScheme.primary,
                                                selectedTextColor =
                                                    MaterialTheme.colorScheme.primary,
                                                indicatorColor = Color.Transparent,
                                            ),
                                        modifier = Modifier.testTag("nav-$destination"),
                                    )
                                }
                            }
                            if (!state.loading && !state.loadError)
                                Surface(
                                    onClick = { addMenu = true },
                                    modifier =
                                        Modifier.align(Alignment.TopCenter)
                                            .offset(y = (-12).dp)
                                            .size(64.dp)
                                            .testTag("add-menu"),
                                    shape = CircleShape,
                                    color = Color(0xFF007A81),
                                    contentColor = Color.White,
                                    shadowElevation = 7.dp,
                                    border = BorderStroke(2.dp, Color(0xFFA4F2EA)),
                                ) {
                                    Box(
                                        Modifier.background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF14BFC0), Color(0xFF006771))
                                            )
                                        ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Add,
                                            label(R.string.add_menu),
                                            Modifier.size(35.dp),
                                        )
                                    }
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
                                    { top("purchases") },
                                    { nav.navigate("contracts/${it.name}") },
                                    { nav.navigate("contract/${it.kind.name}/${it.id}") },
                                    { nav.navigate("detail/$it") },
                                    accountState,
                                    { nav.navigate("account") },
                                )
                            }
                            composable("account") {
                                AccountScreen(state, accountState, accountVm) { nav.popBackStack() }
                            }
                            composable("mail") {
                                gmailVm?.let { mail ->
                                    GmailScreen(mail, { nav.popBackStack() }) { uri, owner ->
                                        vm.importDocument(uri, expectedOwner = owner)
                                    }
                                }
                            }
                            composable("contracts/{kind}") { backStack ->
                                val kind =
                                    ContractKind.valueOf(backStack.arguments!!.getString("kind")!!)
                                ContractsScreen(
                                    kind,
                                    state.contracts.filter { it.kind == kind },
                                    { nav.popBackStack() },
                                    { nav.navigate("contract/${kind.name}/new") },
                                    { nav.navigate("contract/${kind.name}/${it.id}") },
                                )
                            }
                            composable("contract/{kind}/{id}") { backStack ->
                                val kind =
                                    ContractKind.valueOf(backStack.arguments!!.getString("kind")!!)
                                val id = backStack.arguments!!.getString("id")!!
                                ContractEditorScreen(
                                    kind,
                                    state.contracts.find { it.id == id },
                                    id == "new",
                                    { nav.popBackStack() },
                                    vm::saveContract,
                                    vm::archiveContract,
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
                                DocumentsScreen(
                                    state.documents,
                                    store,
                                    ::startImport,
                                    openDocument,
                                    if (gmailVm != null) ({ nav.navigate("mail") }) else null,
                                )
                            }
                            composable("profile") {
                                ProfileScreen(
                                    state,
                                    vm,
                                    accountState,
                                    { nav.navigate("account") },
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
            if (addMenu)
                ModalBottomSheet(onDismissRequest = { addMenu = false }) {
                    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                        Text(
                            label(R.string.add_menu),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                        ListItem(
                            headlineContent = { Text(label(R.string.manual_entry)) },
                            leadingContent = { Icon(Icons.Outlined.Inventory2, null) },
                            modifier =
                                Modifier.testTag("add-purchase")
                                    .then(Modifier.fillMaxWidth())
                                    .clickable {
                                        addMenu = false
                                        startEditor()
                                    },
                        )
                        ListItem(
                            headlineContent = { Text(label(R.string.scan)) },
                            leadingContent = { Icon(Icons.Outlined.DocumentScanner, null) },
                            modifier =
                                Modifier.clickable {
                                    addMenu = false
                                    nav.navigate("scanner")
                                },
                        )
                        ListItem(
                            headlineContent = { Text(label(R.string.import_document)) },
                            leadingContent = { Icon(Icons.Outlined.FileUpload, null) },
                            modifier =
                                Modifier.clickable {
                                    addMenu = false
                                    startImport()
                                },
                        )
                        if (gmailVm != null)
                            ListItem(
                                headlineContent = { Text(label(R.string.mail_title)) },
                                leadingContent = { Icon(Icons.Outlined.Email, null) },
                                modifier =
                                    Modifier.testTag("add-mail").clickable {
                                        addMenu = false
                                        nav.navigate("mail")
                                    },
                            )
                        ContractKind.entries.forEach { kind ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        label(
                                            if (kind == ContractKind.SUBSCRIPTION)
                                                R.string.subscription_record
                                            else R.string.insurance_record
                                        )
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        if (kind == ContractKind.SUBSCRIPTION)
                                            Icons.Outlined.EventRepeat
                                        else Icons.Outlined.Shield,
                                        null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        addMenu = false
                                        nav.navigate("contract/${kind.name}/new")
                                    },
                            )
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
            if (!processing && (sharedAttachments.isNotEmpty() || sharedText != null))
                AlertDialog(
                    onDismissRequest = vm::dismissShared,
                    title = { Text(label(R.string.share_title)) },
                    text = {
                        Column(
                            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                label(
                                    if (sharedText != null) R.string.share_text_help
                                    else R.string.share_attachment_help
                                )
                            )
                            sharedAttachments.forEachIndexed { index, attachment ->
                                OutlinedButton(
                                    onClick = {
                                        vm.dismissShared()
                                        vm.importDocument(
                                            attachment.uri,
                                            nav.currentBackStackEntry
                                                ?.destination
                                                ?.route
                                                ?.startsWith("edit/") == true,
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        attachment.name
                                            ?: resources.getString(
                                                R.string.share_attachment,
                                                index + 1,
                                            )
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        if (sharedText != null)
                            TextButton(
                                onClick = {
                                    val intoEditor =
                                        nav.currentBackStackEntry
                                            ?.destination
                                            ?.route
                                            ?.startsWith("edit/") == true
                                    vm.useSharedText(intoEditor)
                                    if (!intoEditor) startEditor()
                                }
                            ) {
                                Text(label(R.string.share_text_import))
                            }
                    },
                    dismissButton = {
                        TextButton(onClick = vm::dismissShared) { Text(label(R.string.cancel)) }
                    },
                )
        }
    }
}
