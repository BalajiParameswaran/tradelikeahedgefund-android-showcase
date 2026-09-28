import SwiftUI

/// Account tab: auth + data-source connections.
///
/// Firebase is NOT bundled — no GoogleService-Info.plist, no secrets in the
/// repo. To enable: add the plist to the Xcode target and wire FirebaseAuth
/// where marked TODO below. Until then this shows an honest not-configured
/// state instead of crashing.
struct AccountView: View {
    @State private var email = ""
    @State private var password = ""
    @State private var status: String?
    @State private var tradierToken = ""
    @State private var useSandbox = true

    // TODO(firebase): set true after adding GoogleService-Info.plist +
    // FirebaseAuth SDK, then implement sign-in with the Firebase APIs.
    private let firebaseReady = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Account").font(.title2).bold().foregroundColor(.ink)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Sign in").font(.headline).foregroundColor(.bronzeGold)
                        if !firebaseReady {
                            Text("Firebase is not configured in this build — add your " +
                                 "GoogleService-Info.plist to the Xcode target (see the " +
                                 "comment at the top of this file) to enable sign-in.")
                                .font(.body).foregroundColor(.muted)
                        }
                        if let status {
                            Text(status).font(.body).foregroundColor(.muted)
                        }
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
                        Button(useSandbox ? "Using sandbox — switch to production"
                                          : "Using production — switch to sandbox") {
                            useSandbox.toggle()
                        }.tint(.bronzeGold)
                    }
                    .padding(16).background(Color.navySurface).cornerRadius(10)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("E*TRADE").font(.headline).foregroundColor(.bronzeGold)
                        Text("OAuth 1.0a connect flow uses the shared EtradeAuth signers " +
                             "(pure-Kotlin HMAC-SHA1, unit-tested, shipped in the KMP framework). " +
                             "Enter your consumer key/secret on the connect screen to begin the " +
                             "request-token → authorize → access-token flow. Tokens are stored " +
                             "encrypted on-device.")
                            .font(.caption).foregroundColor(.muted)
                    }
                    .padding(16).background(Color.navySurface).cornerRadius(10)
                }
                .padding()
            }
            .background(Color.navyBg)
            .navigationTitle("Account")
        }
    }
}
