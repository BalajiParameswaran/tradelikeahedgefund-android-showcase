import SwiftUI

struct ContentView: View {
    @State private var selection = 0
    @State private var tradeQuestion: String?
    @State private var tradeSymbol: String?

    var body: some View {
        TabView(selection: $selection) {
            TradeView(onAskTutor: { q in
                tradeQuestion = q
                selection = 2
            },
            externalSymbol: tradeSymbol,
            onExternalConsumed: { tradeSymbol = nil })
            .tabItem { Label("Trade", systemImage: "chart.line.uptrend.xyaxis") }
            .tag(0)
            PortfolioView(onTradeSymbol: { s in
                tradeSymbol = s
                selection = 0
            })
                .tabItem { Label("Portfolio", systemImage: "briefcase") }
                .tag(1)
            LearnView(
                externalQuestion: tradeQuestion,
                onExternalConsumed: { tradeQuestion = nil }
            )
            .tabItem { Label("Learn", systemImage: "book") }
            .tag(2)
            AccountView()
                .tabItem { Label("Account", systemImage: "person.crop.circle") }
                .tag(3)
        }
        .tint(.bronzeGold)
        .background(Color.navyBg)
    }
}
