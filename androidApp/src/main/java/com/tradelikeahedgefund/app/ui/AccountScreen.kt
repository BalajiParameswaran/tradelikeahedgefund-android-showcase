package com.tradelikeahedgefund.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface

/**
 * Account tab: Firebase Auth + data-source connections.
 *
 * FIREBASE SETUP (manual, keeps secrets out of the repo):
 * 1. Firebase console -> Project settings -> add Android app
 *    (package com.tradelikeahedgefund.app), download google-services.json
 *    into androidApp/ (next to this file's module root).
 * 2. Apply the google-services Gradle plugin in androidApp/build.gradle.kts.
 * 3. Add your SHA-1 under Project settings -> SHA certificate fingerprints.
 * Until then this screen shows an honest "not configured" state.
 */
@Composable
fun AccountScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var tradierToken by remember { mutableStateOf("") }
    var useSandbox by remember { mutableStateOf(true) }

    val firebaseReady = remember {
        try {
            FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account", style = MaterialTheme.typography.headlineSmall, color = Ink)

        Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sign in", color = BronzeGold, style = MaterialTheme.typography.titleMedium)
                if (!firebaseReady) {
                    Text(
                        "Firebase is not configured in this build — add your google-services.json " +
                            "and apply the google-services plugin (see the KDoc above) to enable sign-in.",
                        color = Muted
                    )
                } else {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        Text("Signed in as ${user.email ?: user.uid}", color = Ink)
                        Button(onClick = {
                            FirebaseAuth.getInstance().signOut()
                            status = "Signed out."
                        }) { Text("Sign out") }
                    } else {
                        OutlinedTextField(email, { email = it }, label = { Text("Email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(password, { password = it }, label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            FirebaseAuth.getInstance()
                                .signInWithEmailAndPassword(email.trim(), password)
                                .addOnSuccessListener { status = "Signed in." }
                                .addOnFailureListener { status = "Sign-in failed: ${it.message}" }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Sign in with email") }
                        // TODO: Google sign-in via GoogleSignInClient + firebase-auth
                        // GoogleAuthProvider (needs play-services-auth + your web client ID).
                    }
                }
                status?.let { Text(it, color = Muted) }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tradier", color = BronzeGold, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    tradierToken, { tradierToken = it }, label = { Text("API token") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Host: ${if (useSandbox) "sandbox.tradier.com" else "api.tradier.com"} — " +
                        "a production token against the sandbox host will fail; keep them matched. " +
                        "Token stays on this device; it is never logged.",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { useSandbox = !useSandbox }) {
                    Text(if (useSandbox) "Using sandbox — switch to production" else "Using production — switch to sandbox")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("E*TRADE", color = BronzeGold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "OAuth 1.0a connect flow uses the shared EtradeAuth signers in :shared " +
                        "(pure Kotlin HMAC-SHA1, unit-tested). Enter your consumer key/secret on " +
                        "the connect screen to begin the request-token -> authorize -> access-token flow. " +
                        "Tokens are stored encrypted on-device.",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
