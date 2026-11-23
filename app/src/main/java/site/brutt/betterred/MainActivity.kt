package site.brutt.betterred

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import site.brutt.betterred.ui.AppViewModel
import site.brutt.betterred.data.Article
import site.brutt.betterred.ui.BetterRedApp
import site.brutt.betterred.ui.OpenRequest
import site.brutt.betterred.widget.Widgets

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Shows the animated logo on launch, and keeps it up until her data has loaded
        // (so there's never a blank screen in between).
        installSplashScreen().setKeepOnScreenCondition { !vm.state.value.loaded }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleWidgetTap(intent)
        enableEdgeToEdge()
        setContent {
            BetterRedApp(vm, onDarkTheme = { dark ->
                // Keep status/navigation bar icons readable when the app theme differs from the system.
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetTap(intent)
    }

    override fun onStart() {
        super.onStart()
        Widgets.updateAll(this) // e.g. the phone switched to dark mode while the app was closed
    }

    /** A widget was tapped: open the right tab, or the article. */
    private fun handleWidgetTap(intent: Intent?) {
        intent ?: return
        val link = intent.getStringExtra(Widgets.EXTRA_ARTICLE_LINK)
        when {
            link != null -> vm.requestOpen(
                OpenRequest.Read(
                    Article(
                        link = link,
                        title = intent.getStringExtra(Widgets.EXTRA_ARTICLE_TITLE).orEmpty(),
                        summary = "",
                        source = intent.getStringExtra(Widgets.EXTRA_ARTICLE_SOURCE).orEmpty(),
                        published = null,
                    )
                )
            )
            intent.hasExtra(Widgets.EXTRA_TAB) -> vm.requestOpen(OpenRequest.Tab(intent.getStringExtra(Widgets.EXTRA_TAB)!!))
        }
    }
}
