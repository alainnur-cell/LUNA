package com.gomobile.luna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gomobile.luna.data.UserProfile
import com.gomobile.luna.ui.screens.BroadcastsScreen
import com.gomobile.luna.ui.screens.DashboardScreen
import com.gomobile.luna.ui.screens.LoadingScreen
import com.gomobile.luna.ui.screens.LoginScreen
import com.gomobile.luna.ui.screens.NoProfileScreen
import com.gomobile.luna.ui.screens.NotConfiguredScreen
import com.gomobile.luna.ui.screens.ShopDetailScreen
import com.gomobile.luna.ui.screens.ShopFilter
import com.gomobile.luna.ui.screens.ShopsScreen
import com.gomobile.luna.ui.theme.LunaTheme
import android.net.Uri

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LunaTheme {
                Surface(color = MaterialTheme.colorScheme.background) { LunaApp(vm) }
            }
        }
    }
}

@Composable
private fun LunaApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    when (val s = state.session) {
        Session.Loading -> LoadingScreen()
        Session.NotConfigured -> NotConfiguredScreen()
        Session.SignedOut -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            Box(Modifier.padding(pad)) { LoginScreen(state.busy, vm::signIn, vm::resetPassword) }
        }
        is Session.NoProfile -> NoProfileScreen(s.uid, vm::retry, vm::signOut)
        is Session.SignedIn -> SignedInApp(s.profile, state, vm, snackbar)
    }
}

private object Routes {
    const val DASHBOARD = "dashboard"
    const val SHOPS = "shops?manager={manager}&filter={filter}"
    const val SHOP = "shop/{code}"
    const val BROADCASTS = "broadcasts"

    fun shops(manager: String? = null, filter: ShopFilter = ShopFilter.ALL) =
        "shops?manager=${Uri.encode(manager.orEmpty())}&filter=${filter.name}"
    fun shop(code: String) = "shop/${Uri.encode(code)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInApp(profile: UserProfile, state: UiState, vm: AppViewModel, snackbar: SnackbarHostState) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    fun goTab(target: String) = nav.navigate(target) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (route) {
                            Routes.SHOPS -> "Shops"
                            Routes.SHOP -> "Shop"
                            Routes.BROADCASTS -> "Broadcasts"
                            else -> "GoMobile Sales"
                        }
                    )
                },
                navigationIcon = {
                    if (route == Routes.SHOP) {
                        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                actions = {
                    IconButton(onClick = vm::signOut) { Icon(Icons.AutoMirrored.Filled.Logout, "Sign out") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = route == Routes.DASHBOARD,
                    onClick = { goTab(Routes.DASHBOARD) },
                    icon = { Icon(Icons.Filled.Dashboard, null) },
                    label = { Text("Dashboard") },
                )
                NavigationBarItem(
                    selected = route == Routes.SHOPS || route == Routes.SHOP,
                    onClick = { goTab(Routes.shops()) },
                    icon = { Icon(Icons.Filled.Store, null) },
                    label = { Text("Shops") },
                )
                NavigationBarItem(
                    selected = route == Routes.BROADCASTS,
                    onClick = { goTab(Routes.BROADCASTS) },
                    icon = {
                        BadgedBox(badge = { if (state.unreadBroadcasts > 0) Badge { Text("${state.unreadBroadcasts}") } }) {
                            Icon(Icons.Filled.Campaign, null)
                        }
                    },
                    label = { Text("Broadcasts") },
                )
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Routes.DASHBOARD, modifier = Modifier.padding(pad)) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    profile = profile,
                    shops = state.shops,
                    loaded = state.shopsLoaded,
                    meta = state.meta,
                    onOpenManager = { nav.navigate(Routes.shops(manager = it)) },
                    onOpenAging = { nav.navigate(Routes.shops(filter = ShopFilter.NO_SALE)) },
                )
            }
            composable(
                Routes.SHOPS,
                arguments = listOf(
                    navArgument("manager") { type = NavType.StringType; defaultValue = "" },
                    navArgument("filter") { type = NavType.StringType; defaultValue = ShopFilter.ALL.name },
                ),
            ) { back ->
                val manager = back.arguments?.getString("manager").orEmpty().ifBlank { null }
                val filter = runCatching { ShopFilter.valueOf(back.arguments?.getString("filter").orEmpty()) }
                    .getOrDefault(ShopFilter.ALL)
                ShopsScreen(
                    shops = state.shops,
                    managerFilter = manager,
                    initialFilter = filter,
                    onClearManager = { nav.navigate(Routes.shops()) { popUpTo(Routes.SHOPS) { inclusive = true } } },
                    onOpenShop = { nav.navigate(Routes.shop(it)) },
                )
            }
            composable(Routes.SHOP, arguments = listOf(navArgument("code") { type = NavType.StringType })) { back ->
                val code = back.arguments?.getString("code")
                ShopDetailScreen(state.shops.firstOrNull { it.code == code })
            }
            composable(Routes.BROADCASTS) {
                BroadcastsScreen(
                    broadcasts = state.broadcasts,
                    lastSeen = state.lastSeenBroadcast,
                    isAdmin = profile.isAdmin,
                    busy = state.busy,
                    onSend = vm::sendBroadcast,
                    onDelete = vm::deleteBroadcast,
                    onSeen = vm::markBroadcastsSeen,
                )
            }
        }
    }
}
