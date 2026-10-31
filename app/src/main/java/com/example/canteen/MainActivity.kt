package com.example.canteen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.canteen.data.repository.AuthRepository
import com.example.canteen.data.repository.SeedRepository
import com.example.canteen.navigation.AppNavGraph
import com.example.canteen.ui.common.theme.CanteenTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var seedRepository: SeedRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Seed sample data for today if menu/slots are empty, and ensure 20 items / vouchers
        lifecycleScope.launch {
            seedRepository.seedIfEmpty()
            seedRepository.seedMissingMenuItems()
        }

        setContent {
            CanteenTheme {
                AppNavGraph(authRepository = authRepository)
            }
        }
    }
}
