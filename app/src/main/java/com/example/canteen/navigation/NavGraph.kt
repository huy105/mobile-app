package com.example.canteen.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.canteen.data.model.UserRole
import com.example.canteen.data.repository.AuthRepository
import com.example.canteen.ui.canteen.CanteenRootScreen
import com.example.canteen.ui.common.LoginScreen
import com.example.canteen.ui.common.LoginViewModel
import com.example.canteen.ui.student.OrderStatusScreen
import com.example.canteen.ui.student.OrderStatusViewModel
import com.example.canteen.ui.student.SlotPickerScreen
import com.example.canteen.ui.student.SlotPickerViewModel
import com.example.canteen.ui.student.StudentRootScreen

@Composable
fun AppNavGraph(
    authRepository: AuthRepository,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val currentUser by authRepository.currentUserFlow.collectAsState(initial = null)

    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user != null) {
            val destination = if (user.userRole == UserRole.CANTEEN) {
                Route.CanteenMain.route
            } else {
                Route.StudentMain.route
            }
            val currentRoute = navController.currentDestination?.route
            if (currentRoute == null || currentRoute == Route.Login.route) {
                navController.navigate(destination) {
                    popUpTo(Route.Login.route) { inclusive = true }
                }
            }
        } else {
            if (navController.currentDestination?.route != Route.Login.route) {
                navController.navigate(Route.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Login.route,
        modifier = modifier
    ) {
        composable(Route.Login.route) {
            val loginViewModel: LoginViewModel = hiltViewModel()
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = { user ->
                    val destination = if (user.userRole == UserRole.CANTEEN) {
                        Route.CanteenMain.route
                    } else {
                        Route.StudentMain.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.StudentMain.route) {
            currentUser?.let { user ->
                StudentRootScreen(
                    user = user,
                    onLogout = {
                        authRepository.logout()
                    },
                    onNavigateToSlotPicker = {
                        navController.navigate(Route.SlotPicker.route)
                    },
                    onNavigateToOrderStatus = { orderId ->
                        navController.navigate(Route.OrderStatus.createRoute(orderId))
                    }
                )
            }
        }

        composable(Route.SlotPicker.route) {
            val slotPickerViewModel: SlotPickerViewModel = hiltViewModel()
            SlotPickerScreen(
                viewModel = slotPickerViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOrderCreated = { orderId ->
                    navController.navigate(Route.OrderStatus.createRoute(orderId)) {
                        popUpTo(Route.StudentMain.route)
                    }
                }
            )
        }

        composable(
            route = Route.OrderStatus.route,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
            val orderStatusViewModel: OrderStatusViewModel = hiltViewModel()
            OrderStatusScreen(
                orderId = orderId,
                viewModel = orderStatusViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Route.CanteenMain.route) {
            currentUser?.let { user ->
                CanteenRootScreen(
                    user = user,
                    onLogout = {
                        authRepository.logout()
                    }
                )
            }
        }
    }
}
