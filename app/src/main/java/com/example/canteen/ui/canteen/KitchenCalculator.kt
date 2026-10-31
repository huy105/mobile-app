package com.example.canteen.ui.canteen

import com.example.canteen.data.model.MenuItem
import com.example.canteen.data.model.Order
import com.example.canteen.data.model.OrderStatus

data class KitchenDishItem(
    val name: String,
    val dishKey: String,
    val portions: Int,
    val costPrice: Long,
    val cost: Long,
    val revenue: Long,
    val hasCostPrice: Boolean
)

data class KitchenSummary(
    val items: List<KitchenDishItem> = emptyList(),
    val totalPortions: Int = 0,
    val totalRevenue: Long = 0L,
    val totalCost: Long = 0L,
    val totalDiscount: Long = 0L,
    val netRevenue: Long = 0L,
    val grossProfit: Long = 0L,
    val missingCostItemsCount: Int = 0
)

object KitchenCalculator {

    fun calculate(
        orders: List<Order>,
        menuItems: List<MenuItem>
    ): KitchenSummary {
        val nonCancelled = orders.filter { it.status != OrderStatus.CANCELLED.name }
        if (nonCancelled.isEmpty()) {
            return KitchenSummary()
        }

        // Map menuItems by id and by dishKey for fast costPrice lookup
        val costById = menuItems.associate { it.id to it.costPrice }
        val costByDishKey = menuItems.filter { it.dishKey.isNotBlank() }
            .associate { it.dishKey to it.costPrice }

        // Flatten all OrderItems and group by key (dishKey, or name if dishKey is blank)
        data class ItemAccumulator(
            val name: String,
            val dishKey: String,
            var portions: Int = 0,
            var revenue: Long = 0L,
            var costPrice: Long = 0L,
            var foundCost: Boolean = false
        )

        val groups = mutableMapOf<String, ItemAccumulator>()

        for (order in nonCancelled) {
            for (item in order.items) {
                val groupKey = if (item.dishKey.isNotBlank()) item.dishKey else item.name
                val acc = groups.getOrPut(groupKey) {
                    // Find costPrice
                    val cpFromId = if (item.menuItemId.isNotBlank()) costById[item.menuItemId] else null
                    val cpFromKey = if (item.dishKey.isNotBlank()) costByDishKey[item.dishKey] else null
                    val cp = (cpFromId ?: cpFromKey ?: 0L).coerceAtLeast(0L)
                    ItemAccumulator(
                        name = item.name,
                        dishKey = item.dishKey,
                        costPrice = cp,
                        foundCost = cp > 0L
                    )
                }
                acc.portions += item.quantity
                acc.revenue += (item.price * item.quantity)
            }
        }

        val dishItems = groups.values.map { acc ->
            val cost = acc.costPrice * acc.portions
            KitchenDishItem(
                name = acc.name,
                dishKey = acc.dishKey,
                portions = acc.portions,
                costPrice = acc.costPrice,
                cost = cost,
                revenue = acc.revenue,
                hasCostPrice = acc.foundCost
            )
        }.sortedByDescending { it.portions }

        val totalPortions = dishItems.sumOf { it.portions }
        val totalRevenue = dishItems.sumOf { it.revenue }
        val totalCost = dishItems.sumOf { it.cost }
        val totalDiscount = nonCancelled.sumOf { it.discountAmount }
        val netRevenue = totalRevenue - totalDiscount
        val grossProfit = netRevenue - totalCost
        val missingCount = dishItems.count { !it.hasCostPrice }

        return KitchenSummary(
            items = dishItems,
            totalPortions = totalPortions,
            totalRevenue = totalRevenue,
            totalCost = totalCost,
            totalDiscount = totalDiscount,
            netRevenue = netRevenue,
            grossProfit = grossProfit,
            missingCostItemsCount = missingCount
        )
    }
}

