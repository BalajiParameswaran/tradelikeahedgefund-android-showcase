package com.tradelikeahedgefund.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface

/**
 * Portfolio tab. Honest stub: holdings storage (encrypted DataStore) and
 * broker position sync are not wired yet — this screen renders an empty
 * state instead of inventing positions.
 */
@Composable
fun PortfolioScreen() {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Portfolio", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No positions yet", color = Ink, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Positions you save will be stored encrypted on this device only. " +
                        "Broker sync (E*TRADE / Tradier) plugs into the shared data clients " +
                        "in :shared — wire your tokens on the Account tab first.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
