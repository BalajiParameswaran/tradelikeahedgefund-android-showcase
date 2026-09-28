import SwiftUI

struct ContentView: View {
    var body: some View {
        TabView {
            TradeView()
                .tabItem { Label("Trade", systemImage: "chart.line.uptrend.xyaxis") }
            PortfolioView()
                .tabItem { Label("Portfolio", systemImage: "briefcase") }
            LearnView()
                .tabItem { Label("Learn", systemImage: "book") }
            AccountView()
                .tabItem { Label("Account", systemImage: "person.crop.circle") }
        }
        .tint(.bronzeGold)
        .background(Color.navyBg)
    }
}
