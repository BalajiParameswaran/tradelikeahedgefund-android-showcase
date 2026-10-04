package com.tradelikeahedgefund.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.tradelikeahedgefund.app.ai.AiPlatform
import com.tradelikeahedgefund.app.ui.theme.BearRed
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.BullGreen
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface

/**
 * Account tab: sign-in, market data, and broker data-source settings.
 *
 * Every control on this screen either does something or is visibly
 * disabled with a plain-English reason:
 * - Sign in: Firebase email/password when configured; otherwise a plain
 *   explanation with no dead fields or buttons. Failures are shown in
 *   plain English (the raw error is kept only as small technical detail).
 * - Market data: free Yahoo data is included; live data is a clearly
 *   disabled "coming soon" button — there is nothing to buy or enable.
 * - Tradier: the token field is real — Save/Clear persist to encrypted
 *   on-device storage, and the sandbox switch saves immediately.
 * - E*TRADE: the OAuth flow exists in shared code but is not wired into
 *   this screen yet, so its connect button is disabled and says so.
 *
 * FIREBASE SETUP (manual, keeps secrets out of the repo):
 * 1. Firebase console -> Project settings -> add Android app
 *    (package com.tradelikeahedgefund.app), download google-services.json
 *    into androidApp/ (next to this file's module root).
 * 2. Apply the google-services Gradle plugin in androidApp/build.gradle.kts.
 * 3. Add your SHA-1 under Project settings -> SHA certificate fingerprints.
 * Until then this screen shows an honest "not configured" state.
 */
private fun friendlySignInError(e: Exception): String {
    val code = (e as? FirebaseAuthException)?.errorCode.orEmpty()
    val msg = e.message?.lowercase().orEmpty()
    return when {
        code == "ERROR_INVALID_EMAIL" ||
            msg.contains("email address is badly formatted") ||
            msg.contains("invalid email") ->
            "That doesn't look like a valid email address."

        code == "ERROR_TOO_MANY_REQUESTS" ||
            msg.contains("too many") ||
            msg.contains("blocked all requests") ->
            "Too many attempts. Wait a bit and try again."

        code == "ERROR_NETWORK_REQUEST_FAILED" ||
            e is FirebaseNetworkException ||
            msg.contains("network") ->
            "No connection — check your internet and try again."

        code == "ERROR_WRONG_PASSWORD" ||
            code == "ERROR_INVALID_CREDENTIAL" ||
            code == "ERROR_INVALID_LOGIN_CREDENTIALS" ||
            code == "ERROR_USER_NOT_FOUND" ||
            code == "ERROR_USER_DISABLED" ||
            e is FirebaseAuthInvalidCredentialsException ||
            e is FirebaseAuthInvalidUserException ||
            msg.contains("password") ||
            msg.contains("credential") ||
            msg.contains("user record") ||
            msg.contains("no user") ->
            "That email and password didn't match. Check them and try again."

        else -> "Sign-in failed. Try again."
    }
}

@Composable
fun AccountScreen() {
    val context = LocalContext.current
    val storage = remember { AiPlatform.storage(context) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authStatus by remember { mutableStateOf<String?>(null) }
    var authDetail by remember { mutableStateOf<String?>(null) }
    var authIsError by remember { mutableStateOf(false) }

    var tradierToken by remember { mutableStateOf(storage.get("tradier_token") ?: "") }
    var useSandbox by remember { mutableStateOf(storage.get("tradier_sandbox") != "0") }
    var tradierStatus by remember { mutableStateOf<String?>(null) }

    val firebaseReady = remember {
        try {
            FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            false
        }
    }

    var signedInEmail by remember {
        mutableStateOf(
            if (firebaseReady) {
                try {
                    FirebaseAuth.getInstance().currentUser?.let { it.email ?: it.uid }
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        )
    }

    val scroll = rememberScrollState()

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        E2eNotice()
        Text("Account", style = MaterialTheme.typography.headlineSmall, color = Ink)

        Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sign in", color = BronzeGold, style = MaterialTheme.typography.titleMedium)
                if (!firebaseReady) {
                    Text(
                        "Sign-in isn't available in this build yet. This copy of the app was built " +
                            "without its private Firebase configuration file (google-services.json). " +
                            "That file is added by the developer and is never part of the public " +
                            "source code. You don't need an account to use the app — your portfolio, " +
                            "watchlist and AI chats live only on this phone.",
                        color = Ink
                    )
                } else if (signedInEmail != null) {
                    Text("Signed in as $signedInEmail", color = Ink)
                    Button(
                        onClick = {
                            FirebaseAuth.getInstance().signOut()
                            signedInEmail = null
                            password = ""
                            authStatus = "Signed out."
                            authDetail = null
                            authIsError = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign out") }
                } else {
                    OutlinedTextField(
                        email, { email = it }, label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        password, { password = it }, label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (email.trim().isEmpty() || password.isEmpty()) {
                                authStatus = "Enter your email and password."
                                authDetail = null
                                authIsError = true
                            } else {
                                authStatus = null
                                authDetail = null
                                authIsError = false
                                FirebaseAuth.getInstance()
                                    .signInWithEmailAndPassword(email.trim(), password)
                                    .addOnSuccessListener { result ->
                                        signedInEmail = result.user?.email ?: result.user?.uid ?: email.trim()
                                        authStatus = "Signed in."
                                        authDetail = null
                                        authIsError = false
                                    }
                                    .addOnFailureListener { e ->
                                        authStatus = friendlySignInError(e)
                                        authDetail = e.message
                                        authIsError = true
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign in with email") }
                }
                if (firebaseReady) {
                    authStatus?.let {
                        Text(
                            it,
                            color = if (authIsError) BearRed else BullGreen,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    authDetail?.let {
                        Text(it, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Market data", color = BronzeGold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Included free with the app: stock quotes and a delayed option chain " +
                        "(data by Yahoo Finance). Quotes can be delayed — always check your " +
                        "broker before trading.",
                    color = Ink
                )
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Live data — coming soon") }
                Text(
                    "Real-time streaming quotes will be offered as a paid add-on. It's not " +
                        "available yet, so there's nothing to buy or enable here.",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (useSandbox) "Sandbox" else "Production", color = Ink)
                        Text(
                            if (useSandbox) "sandbox.tradier.com" else "api.tradier.com",
                            color = Muted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = useSandbox,
                        onCheckedChange = { checked ->
                            useSandbox = checked
                            storage.put("tradier_sandbox", if (checked) "1" else "0")
                        }
                    )
                }
                Text(
                    "Host: ${if (useSandbox) "sandbox.tradier.com" else "api.tradier.com"} — " +
                        "a production token against the sandbox host will fail; keep them matched. " +
                        "Token stays on this device; it is never logged.",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val trimmed = tradierToken.trim()
                            if (trimmed.isEmpty()) {
                                storage.remove("tradier_token")
                                tradierToken = ""
                                tradierStatus = "Token cleared."
                            } else {
                                storage.put("tradier_token", trimmed)
                                tradierToken = trimmed
                                tradierStatus = "Token saved on this device."
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save token") }
                    TextButton(
                        onClick = {
                            storage.remove("tradier_token")
                            tradierToken = ""
                            tradierStatus = "Token cleared."
                        }
                    ) { Text("Clear") }
                }
                tradierStatus?.let {
                    Text(
                        it,
                        color = if (it == "Token saved on this device.") BullGreen else Muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Connect E*TRADE — not available in this build") }
                Text(
                    "The sign-in flow isn't wired into this screen yet. Your E*TRADE account is not connected.",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text(
            "Everything on this page is stored only on this phone.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }
}
