package com.example.canteen

import com.example.canteen.data.model.Voucher
import com.example.canteen.ui.common.util.PricingCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PricingCalculatorTest {

    private val welcome10 = Voucher(
        code = "WELCOME10",
        title = "Giảm 10% tối đa 15.000đ cho đơn từ 30.000đ",
        type = "PERCENT",
        value = 10L,
        minOrder = 30000L,
        maxDiscount = 15000L,
        validUntil = "2099-12-31",
        maxUses = 0,
        usedCount = 0,
        active = true
    )

    private val giam5k = Voucher(
        code = "GIAM5K",
        title = "Giảm 5.000đ cho đơn từ 20.000đ",
        type = "AMOUNT",
        value = 5000L,
        minOrder = 20000L,
        maxDiscount = 0L,
        validUntil = "2099-12-31",
        maxUses = 0,
        usedCount = 0,
        active = true
    )

    private val hethan = Voucher(
        code = "HETHAN",
        title = "Mã đã hết hạn",
        type = "AMOUNT",
        value = 10000L,
        minOrder = 0L,
        maxDiscount = 0L,
        validUntil = "2020-01-01",
        maxUses = 0,
        usedCount = 0,
        active = true
    )

    @Test
    fun testWelcome10_60k() {
        val result = PricingCalculator.calculate(
            subtotal = 60000L,
            voucher = welcome10,
            userPoints = 0,
            usePoints = false
        )
        assertEquals(60000L, result.subtotal)
        assertEquals(6000L, result.voucherDiscount)
        assertEquals(54000L, result.total)
    }

    @Test
    fun testWelcome10_200k_cappedAt15k() {
        val result = PricingCalculator.calculate(
            subtotal = 200000L,
            voucher = welcome10,
            userPoints = 0,
            usePoints = false
        )
        assertEquals(15000L, result.voucherDiscount)
        assertEquals(185000L, result.total)
    }

    @Test
    fun testWelcome10_belowMinOrder() {
        val error = PricingCalculator.validateVoucher(
            voucher = welcome10,
            subtotal = 25000L,
            today = "2026-10-31",
            isAlreadyRedeemed = false
        )
        assertEquals("Đơn tối thiểu 30.000đ để dùng mã này", error)
    }

    @Test
    fun testHethan_voucher() {
        val error = PricingCalculator.validateVoucher(
            voucher = hethan,
            subtotal = 50000L,
            today = "2026-10-31",
            isAlreadyRedeemed = false
        )
        assertEquals("Mã đã hết hạn", error)
    }

    @Test
    fun testUnknown_voucher() {
        val error = PricingCalculator.validateVoucher(
            voucher = null,
            subtotal = 50000L,
            today = "2026-10-31",
            isAlreadyRedeemed = false
        )
        assertEquals("Mã không tồn tại", error)
    }

    @Test
    fun testAlreadyRedeemed_voucher() {
        val error = PricingCalculator.validateVoucher(
            voucher = giam5k,
            subtotal = 50000L,
            today = "2026-10-31",
            isAlreadyRedeemed = true
        )
        assertEquals("Bạn đã dùng mã này rồi", error)
    }

    @Test
    fun testPoints_100k_100points() {
        // Subtotal 100k, 100 points
        // cap = 20% of 100k = 20k -> 40 points = 20k
        val result = PricingCalculator.calculate(
            subtotal = 100000L,
            voucher = null,
            userPoints = 100,
            usePoints = true
        )
        assertEquals(40, result.pointsUsed)
        assertEquals(20000L, result.pointsDiscount)
        assertEquals(80000L, result.total)
    }

    @Test
    fun testVoucherAndPointsCombined() {
        // Subtotal 79.000đ, WELCOME10 (10% = 7.900đ)
        // remaining = 71.100đ
        // cap20% = floor(79.000 * 0.20) = 15.800đ
        // capVnd = min(71.100, 15.800) = 15.800đ
        // pointsUsed = min(userPoints, 15800 / 500 = 31) -> 31 points = 15.500đ
        // total = 79.000 - 7.900 - 15.500 = 55.600đ
        val result = PricingCalculator.calculate(
            subtotal = 79000L,
            voucher = welcome10,
            userPoints = 50,
            usePoints = true
        )
        assertEquals(7900L, result.voucherDiscount)
        assertEquals(31, result.pointsUsed)
        assertEquals(15500L, result.pointsDiscount)
        assertEquals(23400L, result.discountAmount)
        assertEquals(55600L, result.total)
    }
}

