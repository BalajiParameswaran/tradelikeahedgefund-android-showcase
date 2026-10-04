import SwiftUI

/// 🔒 End-to-end encrypted · runs 100% on this device — mirrors Android's
/// E2eNotice. Wraps every tab's root in ContentView, above the page content.
struct E2ENoticeView: View {
    var body: some View {
        HStack(spacing: 6) {
            Text("🔒").font(.caption)
            Text("End-to-end encrypted · runs 100% on this device")
                .font(.caption).foregroundColor(.muted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
        .background(Color.navyBg)
    }
}

struct ContentView: View {
    @State private var selection = 0
    @State private var tradeQuestion: String?
    @State private var tradeSymbol: String?

    var body: some View {
        TabView(selection: $selection) {
            VStack(spacing: 0) {
                E2ENoticeView()
                TradeView(onAskTutor: { q in
                    tradeQuestion = q
                    selection = 2
                },
                externalSymbol: tradeSymbol,
                onExternalConsumed: { tradeSymbol = nil })
            }
            .tabItem { Label("Trade", systemImage: "chart.line.uptrend.xyaxis") }
            .tag(0)
            VStack(spacing: 0) {
                E2ENoticeView()
                PortfolioView(onTradeSymbol: { s in
                    tradeSymbol = s
                    selection = 0
                })
            }
                .tabItem { Label("Portfolio", systemImage: "briefcase") }
                .tag(1)
            VStack(spacing: 0) {
                E2ENoticeView()
                LearnView(
                    externalQuestion: tradeQuestion,
                    onExternalConsumed: { tradeQuestion = nil }
                )
            }
            .tabItem { Label("Learn", systemImage: "book") }
            .tag(2)
            VStack(spacing: 0) {
                E2ENoticeView()
                AccountView()
            }
                .tabItem { Label("Account", systemImage: "person.crop.circle") }
                .tag(3)
        }
        .tint(.bronzeGold)
        .background(Color.navyBg)
    }
}
