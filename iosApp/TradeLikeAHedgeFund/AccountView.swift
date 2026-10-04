import SwiftUI

/// Account tab: auth + data-source connections.
///
/// Firebase is NOT bundled — no GoogleService-Info.plist, no secrets in the
/// repo. To enable sign-in: add the plist to the Xcode target and wire
/// FirebaseAuth where marked TODO below. Until then the sign-in card says so
/// in plain English instead of crashing or showing developer jargon.
struct AccountView: View {
    @State private var email = ""
    @State private var password = ""
    @State private var status: String?
    @State private var tradierToken = ""
    @State private var tradierStatus: String?
    @State private var useSandbox = true

    // TODO(firebase): set true after adding GoogleService-Info.plist +
    // FirebaseAuth SDK, then implement sign-in with the Firebase APIs.
    private let firebaseReady = false

    private let tradierTokenKey = "tradier_token"
    private let tradierSandboxKey = "tradier_sandbox"

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Account").font(.title2).bold().foregroundColor(.ink)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Sign in").font(.headline).foregroundColor(.bronzeGold)
                        if !firebaseReady {
                            Text("Sign-in isn't available in this build yet. This copy of the app was built without its private Firebase configuration file (GoogleService-Info.plist). That file is added by the developer and is never part of the public source code. You don't need an account to use the app — your portfolio, watchlist and AI chats live only on this phone.")
                                .font(.body).foregroundColor(.muted)
                        }
                        if let status {
                            Text(status).font(.body).foregroundColor(.muted)
                        }
                    }
                    .padding(16).background(Color.navySurface).cornerRadius(10)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Market data").font(.headline).foregroundColor(.bronzeGold)
                        Text("Included free with the app: stock quotes and a delayed option chain (data by Yahoo Finance). Quotes can be delayed — always check your broker before trading.")
                            .font(.body).foregroundColor(.muted)
                        Button("Live data — coming soon") {}
                            .buttonStyle(.bordered)
                            .disabled(true)
                        Text("Real-time streaming quotes will be offered as a paid add-on. It's not available yet, so there's nothing to buy or enable here.")
                            .font(.caption).foregroundColor(.muted)
                    }
                    .padding(16).background(Color.navySurface).cornerRadius(10)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Tradier").font(.headline).foregroundColor(.bronzeGold)
                        SecureField("API token", text: $tradierToken)
                            .textFieldStyle(.roundedBorder)
                        Text("Host: \(useSandbox ? "sandbox.tradier.com" : "api.tradier.com") — " +
                             "a production token against the sandbox host will fail; keep them matched. " +
                             "Token stays on this device; it is never logged.")
                            .font(.caption).foregroundColor(.muted)
                        HStack {
                            Button("Save token") { saveTradierToken() }
                                .buttonStyle(.bordered).tint(.bronzeGold)
                            Button("Clear") { clearTradierToken() }
                                .buttonStyle(.bordered)
                        }
                        if let tradierStatus {
                            Text(tradierStatus).font(.caption).foregroundColor(.muted)
                        }
                        Toggle("Sandbox", isOn: $useSandbox)
                            .tint(.bronzeGold)
                            .onChange(of: useSandbox) { newValue in
                                UserDefaults.standard.set(newValue, forKey: tradierSandboxKey)
                            }
                    }
                    .padding(16).background(Color.navySurface).cornerRadius(10)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("E*TRADE").font(.headline).foregroundColor(.bronzeGold)
                        Text("E*TRADE connections use OAuth 1.0a sign-in with your own consumer key and secret.")
                            .font(.caption).foregroundColor(.muted)
                        Button("Connect E*TRADE — not available in this build") {}
                            .buttonStyle(.bordered)
                            .disabled(true)
                        Text("The sign-in flow isn't wired into this screen yet. Your E*TRADE account is not connected.")
                            .font(.caption).foregroundColor(.muted)
                    }
                    .padding(16).background(Color.navySurface).cornerRadius(10)

                    Text("Everything on this page is stored only on this phone.")
                        .font(.caption).foregroundColor(.muted)
                }
                .padding()
            }
            .background(Color.navyBg)
            .navigationTitle("Account")
            .onAppear { loadTradierSettings() }
        }
    }

    private func loadTradierSettings() {
        tradierToken = UserDefaults.standard.string(forKey: tradierTokenKey) ?? ""
        if let saved = UserDefaults.standard.object(forKey: tradierSandboxKey) as? Bool {
            useSandbox = saved
        } else {
            useSandbox = true
        }
    }

    private func saveTradierToken() {
        let token = tradierToken.trimmingCharacters(in: .whitespacesAndNewlines)
        if token.isEmpty {
            UserDefaults.standard.removeObject(forKey: tradierTokenKey)
            tradierStatus = "Token cleared."
        } else {
            UserDefaults.standard.set(token, forKey: tradierTokenKey)
            tradierStatus = "Token saved on this device."
        }
    }

    private func clearTradierToken() {
        tradierToken = ""
        UserDefaults.standard.removeObject(forKey: tradierTokenKey)
        tradierStatus = "Token cleared."
    }
}
