import SwiftUI

/// Portfolio tab. Honest stub: no positions are invented; encrypted
/// on-device storage + broker sync wire into the shared data clients later.
struct PortfolioView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Portfolio").font(.title2).bold().foregroundColor(.ink)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("No positions yet").font(.headline).foregroundColor(.ink)
                        Text("Positions you save will be stored encrypted on this device only. " +
                             "Broker sync (E*TRADE / Tradier) plugs into the shared data clients — " +
                             "wire your tokens on the Account tab first.")
                            .font(.body).foregroundColor(.muted)
                    }
                    .padding(16)
                    .background(Color.navySurface)
                    .cornerRadius(10)
                }
                .padding()
            }
            .background(Color.navyBg)
            .navigationTitle("Portfolio")
        }
    }
}
