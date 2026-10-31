package com.example.canteen

import com.example.canteen.data.model.MenuItem
import com.example.canteen.data.model.Order
import com.example.canteen.data.model.OrderItem
import com.example.canteen.data.model.OrderStatus
import com.example.canteen.ui.canteen.KitchenCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class KitchenCalculatorTest {

    @Test
    fun testEmptyOrdersReturnsZeroSummary() {
        val summary = KitchenCalculator.calculate(emptyList(), emptyList())
        assertEquals(0, summary.totalPortions)
        assertEquals(0L, summary.netRevenue)
        assertEquals(0L, summary.totalCost)
        assertEquals(0L, summary.grossProfit)
        assertEquals(0, summary.missingCostItemsCount)
    }

    @Test
    fun testCancelledOrdersExcluded() {
        val menu = listOf(
            MenuItem(id = "m1", name = "Cơm sườn", dishKey = "com-suon", price = 35000L, costPrice = 20000L)
        )
        val orders = listOf(
            Order(
                id = "o1",
                status = OrderStatus.CANCELLED.name,
                items = listOf(OrderItem(menuItemId = "m1", name = "Cơm sườn", price = 35000L, quantity = 2, dishKey = "com-suon")),
                total = 70000L
            )
        )
        val summary = KitchenCalculator.calculate(orders, menu)
        assertEquals(0, summary.totalPortions)
        assertEquals(0L, summary.netRevenue)
    }

    @Test
    fun testKitchenCalculationFormulas() {
        // Dish 1: 10 portions at 35k (cost 20k) -> Rev 350k, Cost 200k
        // Dish 2: 5 portions at 20k (cost 12k) -> Rev 100k, Cost 60k
        // Discount on order 1: 15k
        val menu = listOf(
            MenuItem(id = "m1", name = "Cơm sườn", dishKey = "com-suon", price = 35000L, costPrice = 20000L),
            MenuItem(id = "m2", name = "Trà tắc", dishKey = "tra-tac", price = 20000L, costPrice = 12000L)
        )
        val orders = listOf(
            Order(
                id = "o1",
                status = OrderStatus.RECEIVED.name,
                items = listOf(
                    OrderItem(menuItemId = "m1", name = "Cơm sườn", price = 35000L, quantity = 10, dishKey = "com-suon"),
                    OrderItem(menuItemId = "m2", name = "Trà tắc", price = 20000L, quantity = 5, dishKey = "tra-tac")
                ),
                subtotal = 450000L,
                discountAmount = 15000L,
                total = 435000L
            )
        )
        val summary = KitchenCalculator.calculate(orders, menu)
        assertEquals(15, summary.totalPortions)
        assertEquals(450000L, summary.totalRevenue)
        assertEquals(15000L, summary.totalDiscount)
        assertEquals(435000L, summary.netRevenue)
        assertEquals(260000L, summary.totalCost)
        assertEquals(175000L, summary.grossProfit)
        assertEquals(0, summary.missingCostItemsCount)
    }
}

