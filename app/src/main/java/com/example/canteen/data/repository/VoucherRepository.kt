package com.example.canteen.data.repository

import com.example.canteen.data.model.Voucher
import kotlinx.coroutines.flow.Flow

interface VoucherRepository {
    fun getActiveVouchers(): Flow<List<Voucher>>
    suspend fun getVoucher(code: String): Voucher?
    suspend fun isVoucherRedeemed(code: String, userId: String): Boolean
}

