package cc.nanio.pass.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.addedit.AddPasswordScreen
import cc.nanio.pass.ui.deleted.RecentlyDeletedScreen
import cc.nanio.pass.ui.detail.PasswordDetailScreen
import cc.nanio.pass.ui.home.HomeScreen
import cc.nanio.pass.ui.passwordlist.PasswordListScreen
import cc.nanio.pass.ui.security.SecurityScreen
import cc.nanio.pass.ui.settings.SecuritySettingsScreen
import cc.nanio.pass.ui.settings.SettingsScreen
import cc.nanio.pass.ui.totp.TotpListScreen

object Routes {
    const val HOME = "home"
    const val PASSWORDS = "passwords"
    const val DETAIL = "detail/{id}"
    const val ADD = "add"
    const val TOTP = "totp"
    const val SECURITY = "security"
    const val DELETED = "deleted"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val SECURITY_SETTINGS = "security_settings"

    fun detail(id: Long) = "detail/$id"
}

@Composable
fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(200))
        },
        exitTransition = {
            fadeOut(tween(160))
        },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = {
            slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(180))
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onOpenPasswords = { nav.navigate(Routes.PASSWORDS) },
                onOpenTotp = { nav.navigate(Routes.TOTP) },
                onOpenSecurity = { nav.navigate(Routes.SECURITY) },
                onOpenDeleted = { nav.navigate(Routes.DELETED) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onAdd = { nav.navigate(Routes.ADD) },
                onOpenSearch = { nav.navigate(Routes.SEARCH) },
            )
        }
        composable(Routes.PASSWORDS) {
            PasswordListScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.detail(id)) },
                onAdd = { nav.navigate(Routes.ADD) },
                onOpenSearch = { nav.navigate(Routes.SEARCH) },
            )
        }
        composable(Routes.SEARCH) {
            cc.nanio.pass.ui.search.SearchScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.detail(id)) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            PasswordDetailScreen(
                vm = vm,
                id = id,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = Routes.ADD,
            enterTransition = { slideInVertically(tween(280)) { it } + fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(160)) },
            popExitTransition = { slideOutVertically(tween(240)) { it } + fadeOut(tween(180)) },
        ) {
            cc.nanio.pass.ui.components.DrawerDialog(onDismissRequest = { nav.popBackStack() }) {
                AddPasswordScreen(
                    vm = vm,
                    editId = null,
                    onClose = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                    inDrawer = true,
                )
            }
        }
        composable(Routes.TOTP) {
            TotpListScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenPassword = { id -> nav.navigate(Routes.detail(id)) },
                onAddPassword = { nav.navigate(Routes.ADD) },
            )
        }
        composable(Routes.SECURITY) {
            SecurityScreen(vm = vm, onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate(Routes.detail(id)) })
        }
        composable(Routes.DELETED) {
            RecentlyDeletedScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onRestore = { id -> vm.restore(id) },
                onOpen = { id -> nav.navigate(Routes.detail(id)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenSecurity = { nav.navigate(Routes.SECURITY_SETTINGS) },
            )
        }
        composable(Routes.SECURITY_SETTINGS) {
            SecuritySettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
