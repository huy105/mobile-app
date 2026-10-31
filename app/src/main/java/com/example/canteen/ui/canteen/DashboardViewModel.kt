package com.example.canteen.ui.canteen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.canteen.data.model.MenuItem
import com.example.canteen.data.model.Order
import com.example.canteen.data.model.OrderStatus
import com.example.canteen.data.model.TimeSlot
import com.example.canteen.data.repository.MenuRepository
import com.example.canteen.data.repository.OrderRepository
import com.example.canteen.data.repository.SlotRepository
import com.example.canteen.ui.common.util.formatVnd
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class SlotCapacityStatus {
    FULL,         // Đỏ (Đầy, count >= maxOrders)
    ALMOST_FULL,  // Cam (Gần đầy, ratio >= 0.8)
    AVAILABLE     // Xanh (Còn chỗ, ratio < 0.8)
}

data class DashboardSlotItem(
    val slotId: String,
    val label: String,
    val count: Int,
    val maxOrders: Int,
    val progress: Float,
    val status: SlotCapacityStatus
)

sealed interface LowStockTagType {
    data class SoldOutQuota(val sold: Int, val total: Int) : LowStockTagType
    data class RemainingQuota(val remaining: Int) : LowStockTagType
    object Disabled : LowStockTagType
}

data class DashboardLowStockItem(
    val id: String,
    val name: String,
    val remainingQty: Int,
    val tagType: LowStockTagType,
    val isDimmed: Boolean
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val revenue: Long = 0L,
    val completedOrdersCount: Int = 0,
    val totalOrdersCount: Int = 0,
    val inProgressOrdersCount: Int = 0,
    val cancelledOrdersCount: Int = 0,
    val itemsSoldCount: Int = 0,
    val avgPrepTimeText: String = "—",
    val slots: List<DashboardSlotItem> = emptyList(),
    val lowStockItems: List<DashboardLowStockItem> = emptyList(),
    val formattedDate: String = "",
    val errorMessage: String? = null
) {
    val revenueText: String
        get() = revenue.formatVnd()

    val revenueSubtitle: String
        get() = "từ $completedOrdersCount đơn đã hoàn thành"

    val ordersSubtitle: String
        get() = "$inProgressOrdersCount đang xử lý · $cancelledOrdersCount đã hủy"

    val itemsSoldSubtitle: String
        get() = "không tính đơn hủy"

    val avgPrepSubtitle: String
        get() = "từ \"Bắt đầu làm\" đến \"Sẵn sàng\""
}

object DashboardCalculator {

    fun calculateRevenue(orders: List<Order>): Long {
        return orders.filter { it.status == OrderStatus.COMPLETED.name }
            .sumOf { it.total }
    }

    fun calculateCompletedOrdersCount(orders: List<Order>): Int {
        return orders.count { it.status == OrderStatus.COMPLETED.name }
    }

    fun calculateTotalOrders(orders: List<Order>): Triple<Int, Int, Int> {
        val total = orders.size
        val inProgress = orders.count {
            it.status == OrderStatus.RECEIVED.name ||
                it.status == OrderStatus.PREPARING.name ||
                it.status == OrderStatus.READY.name
        }
        val cancelled = orders.count { it.status == OrderStatus.CANCELLED.name }
        return Triple(total, inProgress, cancelled)
    }

    fun calculateItemsSold(orders: List<Order>): Int {
        return orders.filter { it.status != OrderStatus.CANCELLED.name }
            .sumOf { order -> order.items.sumOf { it.quantity } }
    }

    fun calculateAvgPrepTime(orders: List<Order>): String {
        val durations = orders.mapNotNull { order ->
            val prep = order.preparingAt
            val ready = order.readyAt
            if (prep != null && ready != null) {
                val diffSeconds = ready.seconds - prep.seconds
                if (diffSeconds >= 0) diffSeconds / 60.0 else null
            } else {
                null
            }
        }
        return if (durations.isEmpty()) {
            "—"
        } else {
            val avg = durations.average()
            String.format(Locale.US, "%.1f", avg).replace('.', ',') + " phút"
        }
    }

    fun computeSlotStatus(slots: List<TimeSlot>): List<DashboardSlotItem> {
        return slots.map { slot ->
            val count = maxOf(0, slot.currentOrders)
            val max = maxOf(1, slot.maxOrders)
            val progress = (count.toFloat() / max.toFloat()).coerceIn(0f, 1f)
            val status = when {
                count >= max -> SlotCapacityStatus.FULL
                count.toFloat() / max >= 0.8f -> SlotCapacityStatus.ALMOST_FULL
                else -> SlotCapacityStatus.AVAILABLE
            }
            DashboardSlotItem(
                slotId = slot.id,
                label = slot.label,
                count = count,
                maxOrders = max,
                progress = progress,
                status = status
            )
        }
    }

    fun computeLowStockItems(menuItems: List<MenuItem>): List<DashboardLowStockItem> {
        val candidates = mutableListOf<DashboardLowStockItem>()
        for (item in menuItems) {
            val hasQuota = item.dailyQuota > 0
            val rem = if (hasQuota) (item.remainingQty ?: 0) else Int.MAX_VALUE

            if (hasQuota && rem <= 0) {
                candidates.add(
                    DashboardLowStockItem(
                        id = item.id,
                        name = item.name,
                        remainingQty = 0,
                        tagType = LowStockTagType.SoldOutQuota(
                            sold = maxOf(item.soldQty, item.dailyQuota),
                            total = item.dailyQuota
                        ),
                        isDimmed = false
                    )
                )
            } else if (hasQuota && rem in 1..3) {
                candidates.add(
                    DashboardLowStockItem(
                        id = item.id,
                        name = item.name,
                        remainingQty = rem,
                        tagType = LowStockTagType.RemainingQuota(remaining = rem),
                        isDimmed = false
                    )
                )
            } else if (!item.inStock) {
                candidates.add(
                    DashboardLowStockItem(
                        id = item.id,
                        name = item.name,
                        remainingQty = 9999,
                        tagType = LowStockTagType.Disabled,
                        isDimmed = true
                    )
                )
            }
        }
        return candidates.sortedBy { it.remainingQty }.take(5)
    }

    fun formatDateHeader(date: Date = Date()): String {
        val cal = Calendar.getInstance().apply { time = date }
        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "Chủ Nhật"
            Calendar.MONDAY -> "Thứ Hai"
            Calendar.TUESDAY -> "Thứ Ba"
            Calendar.WEDNESDAY -> "Thứ Tư"
            Calendar.THURSDAY -> "Thứ Năm"
            Calendar.FRIDAY -> "Thứ Sáu"
            Calendar.SATURDAY -> "Thứ Bảy"
            else -> ""
        }
        val dayMonth = SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
        return "Hôm nay · $dayOfWeek, $dayMonth"
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val menuRepository: MenuRepository,
    private val slotRepository: SlotRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val todayDate: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        val today = todayDate
        val formattedDate = DashboardCalculator.formatDateHeader()

        viewModelScope.launch {
            combine(
                orderRepository.getTodayOrders(today),
                menuRepository.getTodayMenuItems(today),
                slotRepository.getTimeSlots()
            ) { orders, menuItems, slots ->
                val revenue = DashboardCalculator.calculateRevenue(orders)
                val completedCount = DashboardCalculator.calculateCompletedOrdersCount(orders)
                val (total, inProgress, cancelled) = DashboardCalculator.calculateTotalOrders(orders)
                val itemsSold = DashboardCalculator.calculateItemsSold(orders)
                val avgPrepTime = DashboardCalculator.calculateAvgPrepTime(orders)
                val slotItems = DashboardCalculator.computeSlotStatus(slots)
                val lowStockList = DashboardCalculator.computeLowStockItems(menuItems)

                DashboardUiState(
                    isLoading = false,
                    revenue = revenue,
                    completedOrdersCount = completedCount,
                    totalOrdersCount = total,
                    inProgressOrdersCount = inProgress,
                    cancelledOrdersCount = cancelled,
                    itemsSoldCount = itemsSold,
                    avgPrepTimeText = avgPrepTime,
                    slots = slotItems,
                    lowStockItems = lowStockList,
                    formattedDate = formattedDate,
                    errorMessage = null
                )
            }.catch { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Đã xảy ra lỗi khi tải dữ liệu",
                        formattedDate = formattedDate
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}

