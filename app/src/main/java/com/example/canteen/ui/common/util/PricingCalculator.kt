package com.example.canteen.ui.common.util

import com.example.canteen.data.model.Voucher

data class PricingResult(
    val subtotal: Long,
    val voucherDiscount: Long,
    val pointsUsed: Int,
    val pointsDiscount: Long,
    val discountAmount: Long,
    val total: Long
)

object PricingCalculator {

    fun calculate(
        subtotal: Long,
        voucher: Voucher? = null,
        userPoints: Int = 0,
        usePoints: Boolean = false
    ): PricingResult {
        val safeSubtotal = subtotal.coerceAtLeast(0L)

        // 1. Voucher discount
        val voucherDiscount = if (voucher != null && voucher.active) {
            when (voucher.type) {
                "PERCENT" -> {
                    val raw = (safeSubtotal * voucher.value) / 100L
                    if (voucher.maxDiscount > 0L) minOf(raw, voucher.maxDiscount) else raw
                }
                "AMOUNT" -> minOf(voucher.value, safeSubtotal)
                else -> 0L
            }
        } else {
            0L
        }.coerceAtLeast(0L).coerceAtMost(safeSubtotal)

        // 2. Points discount
        val pointsUsed: Int
        val pointsDiscount: Long
        if (usePoints && userPoints > 0) {
            val remainingAfterVoucher = (safeSubtotal - voucherDiscount).coerceAtLeast(0L)
            val cap20Percent = (safeSubtotal * 20L) / 100L
            val capVnd = minOf(remainingAfterVoucher, cap20Percent)
            val maxPointsCanUse = (capVnd / 500L).toInt().coerceAtLeast(0)
            pointsUsed = minOf(userPoints, maxPointsCanUse)
            pointsDiscount = pointsUsed.toLong() * 500L
        } else {
            pointsUsed = 0
            pointsDiscount = 0L
        }

        val discountAmount = voucherDiscount + pointsDiscount
        val total = (safeSubtotal - discountAmount).coerceAtLeast(0L)

        return PricingResult(
            subtotal = safeSubtotal,
            voucherDiscount = voucherDiscount,
            pointsUsed = pointsUsed,
            pointsDiscount = pointsDiscount,
            discountAmount = discountAmount,
            total = total
        )
    }

    /**
     * Validate voucher rules.
     * Returns null if valid, or a Vietnamese error message if invalid.
     */
    fun validateVoucher(
        voucher: Voucher?,
        subtotal: Long,
        today: String,
        isAlreadyRedeemed: Boolean
    ): String? {
        if (voucher == null || !voucher.active) {
            return "Mã không tồn tại"
        }
        if (voucher.validUntil.isNotBlank() && voucher.validUntil < today) {
            return "Mã đã hết hạn"
        }
        if (voucher.minOrder > 0L && subtotal < voucher.minOrder) {
            return "Đơn tối thiểu ${voucher.minOrder.formatVnd()} để dùng mã này"
        }
        if (isAlreadyRedeemed) {
            return "Bạn đã dùng mã này rồi"
        }
        if (voucher.maxUses > 0 && voucher.usedCount >= voucher.maxUses) {
            return "Mã đã hết lượt sử dụng"
        }
        return null
    }
}

