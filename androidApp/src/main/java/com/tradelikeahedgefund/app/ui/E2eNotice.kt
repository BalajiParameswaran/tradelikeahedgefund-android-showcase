package com.tradelikeahedgefund.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tradelikeahedgefund.app.ui.theme.Muted

/**
 * Privacy notice shown at the top of every main page (whiteboard item #3):
 * the user's chats, portfolio, and progress never leave this phone and are
 * stored encrypted. One shared component so every page says it the same way —
 * when Branch 2 redesigns a page, it just drops this in at the top.
 */
@Composable
fun E2eNotice(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("🔒", style = MaterialTheme.typography.bodySmall)
        Text(
            "End-to-end encrypted · runs 100% on this device",
            color = Muted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
