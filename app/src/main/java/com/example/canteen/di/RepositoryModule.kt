package com.example.canteen.di

import com.example.canteen.data.repository.AuthRepository
import com.example.canteen.data.repository.AuthRepositoryImpl
import com.example.canteen.data.repository.MenuRepository
import com.example.canteen.data.repository.MenuRepositoryImpl
import com.example.canteen.data.repository.OrderRepository
import com.example.canteen.data.repository.OrderRepositoryImpl
import com.example.canteen.data.repository.SeedRepository
import com.example.canteen.data.repository.SeedRepositoryImpl
import com.example.canteen.data.repository.SlotRepository
import com.example.canteen.data.repository.SlotRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMenuRepository(impl: MenuRepositoryImpl): MenuRepository

    @Binds
    @Singleton
    abstract fun bindSlotRepository(impl: SlotRepositoryImpl): SlotRepository

    @Binds
    @Singleton
    abstract fun bindOrderRepository(impl: OrderRepositoryImpl): OrderRepository

    @Binds
    @Singleton
    abstract fun bindSeedRepository(impl: SeedRepositoryImpl): SeedRepository

    @Binds
    @Singleton
    abstract fun bindCartRepository(impl: com.example.canteen.data.repository.CartRepositoryImpl): com.example.canteen.data.repository.CartRepository

    @Binds
    @Singleton
    abstract fun bindVoucherRepository(impl: com.example.canteen.data.repository.VoucherRepositoryImpl): com.example.canteen.data.repository.VoucherRepository
}

