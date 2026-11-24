package site.brutt.betterred.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import site.brutt.betterred.Words
import site.brutt.betterred.data.Article
import site.brutt.betterred.ui.calendar.CalendarScreen
import site.brutt.betterred.widget.Widgets
import site.brutt.betterred.ui.news.ArticleScreen
import site.brutt.betterred.ui.news.NewsScreen
import site.brutt.betterred.ui.onboarding.OnboardingScreen
import site.brutt.betterred.ui.partner.PartnerScreen
import site.brutt.betterred.ui.partner.ScanScreen
import site.brutt.betterred.ui.profile.ProfileScreen
import site.brutt.betterred.ui.theme.BetterRedTheme
import site.brutt.betterred.ui.theme.isDark
import java.time.LocalDate

private enum class Tab(val label: String, val on: ImageVector, val off: ImageVector) {
    CALENDAR(Words.Tabs.CALENDAR, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    NEWS(Words.Tabs.NEWS, Icons.Rounded.Newspaper, Icons.Outlined.Newspaper),
    PARTNER(Words.Tabs.PARTNER, Icons.Rounded.Favorite, Icons.Outlined.FavoriteBorder),
    PROFILE(Words.Tabs.PROFILE, Icons.Rounded.Person, Icons.Outlined.Person), // shown as her own profile icon
}

/** Root of the UI. [onDarkTheme] lets the activity match the status-bar icons to the theme. */
@Composable
fun BetterRedApp(vm: AppViewModel, onDarkTheme: (Boolean) -> Unit) {
    val state by vm.state.collectAsState()
    val prefs = state.prefs
    val dark = prefs.theme.isDark()
    LaunchedEffect(dark) { onDarkTheme(dark) }

    BetterRedTheme(themeMode = prefs.theme, accent = prefs.accent) {
        when {
            !state.loaded -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            !prefs.onboarded -> {
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(vm::importFrom)
                }
                var scanning by rememberSaveable { mutableStateOf(false) }
                if (scanning) ScanScreen(onCode = vm::connectPartner, onBack = { scanning = false })
                else OnboardingScreen(
                    onFinish = vm::finishOnboarding, onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onPartner = { scanning = true },
                )
            }
            else -> MainScaffold(state, vm)
        }
    }
}

@Composable
private fun MainScaffold(state: AppState, vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.CALENDAR) }
    var reading by remember { mutableStateOf<Article?>(null) }
    var scanning by remember { mutableStateOf(false) } // partner: scanning a new code
    val snackbar = remember { SnackbarHostState() }
    val today = LocalDate.now()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // A widget was tapped: jump to the tab, or open the article in the reader.
    val open by vm.open.collectAsState()
    LaunchedEffect(open) {
        when (val r = open) {
            is OpenRequest.Tab -> { reading = null; tab = if (r.name == Widgets.TAB_NEWS) Tab.NEWS else Tab.CALENDAR }
            is OpenRequest.Read -> { tab = Tab.NEWS; reading = r.article; vm.markRead(r.article.link) }
            null -> return@LaunchedEffect
        }
        vm.openHandled()
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab, onClick = { tab = t },
                        icon = {
                            if (t == Tab.PROFILE) Avatar(state.prefs.avatar, state.prefs.name, 26.dp)
                            else Icon(if (t == tab) t.on else t.off, contentDescription = null)
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.CALENDAR -> CalendarScreen(
                state = state, today = today,
                onTogglePeriod = vm::togglePeriod, onMood = vm::setMood, onSaveLog = vm::saveLog,
                onOpenProfile = { tab = Tab.PROFILE },
                modifier = modifier,
            )
            Tab.NEWS -> NewsScreen(state = state, vm = vm, onOpen = { reading = it }, modifier = modifier)
            Tab.PARTNER -> PartnerScreen(state = state, vm = vm, onScanNew = { scanning = true }, modifier = modifier)
            Tab.PROFILE -> ProfileScreen(state = state, vm = vm, today = today, modifier = modifier)
        }
    }
    // The article reader sits on top, so the list underneath isn't reloaded when she comes back.
    reading?.let { ArticleScreen(it, onClose = { reading = null }) }
    if (scanning) ScanScreen(
        onCode = { text -> vm.connectPartner(text).also { if (it == null) scanning = false } },
        onBack = { scanning = false },
    )
    }
}
