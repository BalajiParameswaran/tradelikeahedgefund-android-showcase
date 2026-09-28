import SwiftUI

struct TradeView: View {
    let pricing: PricingService = LocalPricingService()

    @State private var type: StrategyType = .coveredCall
    @State private var symbol = "AAPL"
    @State private var stockPrice = "100"
    @State private var dte = "45"
    @State private var iv = "30"
    @State private var rate = "5"
    @State private var legs: [OptionLeg] = TradeView.defaultLegs(for: .coveredCall)
    @State private var analysis: StrategyAnalysis?
    @State private var riskLeg: UUID?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Picker("Strategy", selection: $type) {
                        ForEach(StrategyType.allCases) { t in Text(t.rawValue).tag(t) }
                    }
                    .pickerStyle(.menu)
                    .tint(.bronzeGold)
                    .onChange(of: type) {
                        legs = Self.defaultLegs(for: type)
                        analysis = nil
                    }

                    HStack(spacing: 10) {
                        labeled("Symbol") { TextField("AAPL", text: $symbol).textFieldStyle(.roundedBorder) }
                        labeled("Stock price") {
                            TextField("100", text: $stockPrice)
                                .keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                        }
                    }
                    HStack(spacing: 10) {
                        labeled("DTE (days)") {
                            TextField("45", text: $dte).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                        }
                        labeled("IV %") {
                            TextField("30", text: $iv).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                        }
                        labeled("Rate %") {
                            TextField("5", text: $rate).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                        }
                    }

                    Text("Legs — tap a leg for its plain-English risk")
                        .font(.caption).foregroundColor(.muted)

                    ForEach($legs) { $leg in
                        legCard($leg)
                    }

                    Button("Analyze") { runAnalysis() }
                        .buttonStyle(.borderedProminent)
                        .tint(.bronzeGold)
                        .foregroundColor(.navyBg)
                        .frame(maxWidth: .infinity)

                    if let a = analysis {
                        resultCard(a)
                        greeksCard()
                    }
                }
                .padding()
            }
            .background(Color.navyBg)
            .navigationTitle("Trade")
        }
    }

    // MARK: - pieces

    private func labeled<Content: View>(_ title: String, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption).foregroundColor(.muted)
            content()
        }
    }

    private func legCard(_ leg: Binding<OptionLeg>) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("\(leg.wrappedValue.isLong ? "Long" : "Short") \(leg.wrappedValue.isCall ? "Call" : "Put")")
                    .foregroundColor(.bronzeGold).font(.headline)
                Spacer()
                Button(riskLeg == leg.wrappedValue.id ? "Hide risk" : "Risk") {
                    riskLeg = riskLeg == leg.wrappedValue.id ? nil : leg.wrappedValue.id
                }
                .font(.caption)
            }
            HStack(spacing: 10) {
                labeled("Strike") {
                    TextField("Strike", value: leg.strike, format: .number)
                        .keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                }
                labeled("Premium") {
                    TextField("Premium", value: leg.premium, format: .number)
                        .keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                }
            }
            if riskLeg == leg.wrappedValue.id {
                Text(pricing.legRisk(leg.wrappedValue))
                    .font(.body).foregroundColor(.ink)
            }
        }
        .padding(12)
        .background(Color.navySurface)
        .cornerRadius(10)
    }

    private func resultCard(_ a: StrategyAnalysis) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            statRow("Max profit", money(a.maxProfit), .bullGreen)
            statRow("Max loss", money(a.maxLoss), .bearRed)
            statRow("Breakevens", a.breakevens.map { String(format: "$%.2f", $0) }.joined(separator: ", "), .ink)
            if a.marginRequired > 0 {
                statRow("Margin required", money(a.marginRequired), .ink)
            }
            PayoffChartView(payoffAt: a.payoffAt, center: Double(stockPrice) ?? 100)
                .frame(height: 180)
            Text("Plain-English risks").font(.headline).foregroundColor(.bronzeGold)
            ForEach(a.plainEnglishRisks, id: \.self) { r in
                Text("• \(r)").foregroundColor(.ink).font(.body)
            }
        }
        .padding(12)
        .background(Color.navySurface)
        .cornerRadius(10)
    }

    private func statRow(_ label: String, _ value: String, _ color: Color) -> some View {
        HStack {
            Text(label).foregroundColor(.muted)
            Spacer()
            Text(value).foregroundColor(color)
        }
    }

    private func greeksCard() -> some View {
        guard let leg = legs.first,
              let s = Double(stockPrice),
              let t = Double(dte), let ivv = Double(iv), let rr = Double(rate) else {
            return AnyView(EmptyView())
        }
        let g = pricing.greeks(s: s, k: leg.strike, t: t / 365, r: rr / 100, sig: ivv / 100, isCall: leg.isCall)
        return AnyView(
            VStack(alignment: .leading, spacing: 6) {
                Text("Greeks — first leg").font(.headline).foregroundColor(.bronzeGold)
                Text(String(format: "Δ %.3f   Γ %.4f", g.delta, g.gamma)).foregroundColor(.ink)
                Text(String(format: "θ %.2f/day   Vega %.2f/pt", g.theta / 365, g.vega / 100)).foregroundColor(.ink)
            }
            .padding(12)
            .background(Color.navySurface)
            .cornerRadius(10)
        )
    }

    private func runAnalysis() {
        guard let s = Double(stockPrice), !legs.isEmpty else { return }
        let qty = type == .coveredCall ? 100 : 0
        analysis = pricing.analyze(type: type, legs: legs, stockPrice: s, stockQty: qty, stockCost: s)
    }

    static func defaultLegs(for type: StrategyType) -> [OptionLeg] {
        switch type {
        case .coveredCall: return [OptionLeg(strike: 105, premium: 2.0, isCall: true, isLong: false)]
        case .cashSecuredPut: return [OptionLeg(strike: 95, premium: 2.0, isCall: false, isLong: false)]
        case .bullCallSpread: return [OptionLeg(strike: 100, premium: 5, isCall: true, isLong: true),
                                      OptionLeg(strike: 110, premium: 2, isCall: true, isLong: false)]
        case .bearPutSpread: return [OptionLeg(strike: 100, premium: 5, isCall: false, isLong: true),
                                      OptionLeg(strike: 90, premium: 2, isCall: false, isLong: false)]
        case .ironCondor: return [OptionLeg(strike: 105, premium: 1.0, isCall: true, isLong: false),
                                   OptionLeg(strike: 110, premium: 0.5, isCall: true, isLong: true),
                                   OptionLeg(strike: 95, premium: 1.0, isCall: false, isLong: false),
                                   OptionLeg(strike: 90, premium: 0.5, isCall: false, isLong: true)]
        case .ironButterfly: return [OptionLeg(strike: 100, premium: 3, isCall: true, isLong: false),
                                      OptionLeg(strike: 100, premium: 3, isCall: false, isLong: false),
                                      OptionLeg(strike: 110, premium: 1, isCall: true, isLong: true),
                                      OptionLeg(strike: 90, premium: 1, isCall: false, isLong: true)]
        case .longStraddle: return [OptionLeg(strike: 100, premium: 4, isCall: true, isLong: true),
                                     OptionLeg(strike: 100, premium: 4, isCall: false, isLong: true)]
        case .longStrangle: return [OptionLeg(strike: 105, premium: 2.5, isCall: true, isLong: true),
                                      OptionLeg(strike: 95, premium: 2.5, isCall: false, isLong: true)]
        case .leapsCall: return [OptionLeg(strike: 100, premium: 8, isCall: true, isLong: true)]
        }
    }
}

/// Expiry payoff chart drawn with SwiftUI Canvas.
struct PayoffChartView: View {
    let payoffAt: (Double) -> Double
    let center: Double

    var body: some View {
        Canvas { ctx, size in
            let lo = center * 0.6, hi = center * 1.4
            let n = 120
            let vals = (0...n).map { i -> lo + (hi - lo) * Double(i) / Double(n) }.map { ($0, payoffAt($0)) }
            let finite = vals.map { $0.1 }.filter { $0.isFinite }
            let maxA = max(finite.max() ?? 1, 1), minA = min(finite.min() ?? -1, -1)
            func X(_ p: Double) -> Double { size.width * (p - lo) / (hi - lo) }
            func Y(_ v: Double) -> Double { size.height * (1 - (v - minA) / (maxA - minA)) }
            // zero line
            ctx.stroke(Path { p in p.move(to: CGPoint(x: 0, y: Y(0))); p.addLine(to: CGPoint(x: size.width, y: Y(0))) },
                       with: .color(.bronzeGold.opacity(0.5)), lineWidth: 2)
            // center line
            ctx.stroke(Path { p in p.move(to: CGPoint(x: X(center), y: 0)); p.addLine(to: CGPoint(x: X(center), y: size.height)) },
                       with: .color(.muted.opacity(0.4)), lineWidth: 1)
            var path = Path()
            for (i, (p, v)) in vals.enumerated() {
                let pt = CGPoint(x: X(p), y: Y(v.isFinite ? v : (v > 0 ? maxA : minA)))
                if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            ctx.stroke(path, with: .color(.bullGreen), lineWidth: 3)
        }
    }
}
