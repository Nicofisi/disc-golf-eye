package si.nicofi.discgolfeye.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import si.nicofi.discgolfeye.ui.screens.ClientScreen
import si.nicofi.discgolfeye.ui.screens.RoleSelectionScreen
import si.nicofi.discgolfeye.ui.screens.ServerScreen

sealed class Screen(val route: String) {
    data object RoleSelection : Screen("role_selection")
    data object Server : Screen("server")
    data object Client : Screen("client")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.RoleSelection.route,
        modifier = modifier
    ) {
        composable(Screen.RoleSelection.route) {
            RoleSelectionScreen(
                onServerSelected = {
                    navController.navigate(Screen.Server.route) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                },
                onClientSelected = {
                    navController.navigate(Screen.Client.route) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Server.route) {
            ServerScreen()
        }

        composable(Screen.Client.route) {
            ClientScreen()
        }
    }
}
