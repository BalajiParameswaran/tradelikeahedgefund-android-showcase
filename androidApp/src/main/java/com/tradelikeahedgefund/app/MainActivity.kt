package com.tradelikeahedgefund.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.tradelikeahedgefund.app.ui.AccountScreen
import com.tradelikeahedgefund.app.ui.LearnScreen
import com.tradelikeahedgefund.app.ui.PortfolioScreen
import com.tradelikeahedgefund.app.ui.TradeScreen
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.NavySurface
import com.tradelikeahedgefund.app.ui.theme.TabActive
import com.tradelikeahedgefund.app.ui.theme.TabIdle
import com.tradelikeahedgefund.app.ui.theme.TlhfTheme

private data class Tab(val title: String, val icon: ImageVector, val screen: @Composable () -> Unit)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TlhfTheme {
                var tradeQuestion by remember { mutableStateOf<String?>(null) }
                var tradeSymbol by remember { mutableStateOf<String?>(null) }
                var selected by remember { mutableIntStateOf(0) }
                val tabs = listOf(
                    Tab("Trade", Icons.Filled.ShowChart, {
                        TradeScreen(
                            onAskTutor = { q -> tradeQuestion = q; selected = 2 },
                            externalSymbol = tradeSymbol,
                            onExternalConsumed = { tradeSymbol = null }
                        )
                    }),
                    Tab("Portfolio", Icons.Filled.Home, {
                        PortfolioScreen(onTradeSymbol = { s -> tradeSymbol = s; selected = 0 })
                    }),
                    Tab("Learn", Icons.Filled.Info, {
                        LearnScreen(
                            externalQuestion = tradeQuestion,
                            onExternalConsumed = { tradeQuestion = null }
                        )
                    }),
                    Tab("Account", Icons.Filled.AccountCircle, { AccountScreen() })
                )
                Scaffold(
                    containerColor = com.tradelikeahedgefund.app.ui.theme.NavyBg,
                    bottomBar = {
                        NavigationBar(containerColor = NavySurface) {
                            tabs.forEachIndexed { i, tab ->
                                NavigationBarItem(
                                    selected = selected == i,
                                    onClick = { selected = i },
                                    icon = { Icon(tab.icon, contentDescription = tab.title) },
                                    label = { Text(tab.title) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BronzeGold,
                                        selectedTextColor = TabActive,
                                        unselectedIconColor = TabIdle,
                                        unselectedTextColor = TabIdle,
                                        indicatorColor = NavySurface
                                    )
                                )
                            }
                        }
                    }
                ) { inner ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(inner)) {
                        tabs[selected].screen()
                    }
                }
            }
        }
    }
}
