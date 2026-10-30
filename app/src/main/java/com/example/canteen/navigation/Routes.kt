package com.example.canteen.navigation

sealed class Route(val route: String) {
    // Auth
    data object Login : Route("login")

    // Student Flow
    data object StudentMain : Route("student_main") {
        data object Menu : Route("student_menu")
        data object Cart : Route("student_cart")
        data object History : Route("student_history")
    }
    data object SlotPicker : Route("slot_picker")
    data object OrderStatus : Route("order_status/{orderId}") {
        fun createRoute(orderId: String) = "order_status/$orderId"
    }

    // Canteen Flow
    data object CanteenMain : Route("canteen_main") {
        data object Orders : Route("canteen_orders")
        data object MenuManage : Route("canteen_menu_manage")
        data object VerifyCode : Route("canteen_verify_code")
    }
}

