import SwiftUI

struct TradeView: View {
    let pricing: PricingService = LocalPricingService()

    /// Called with a pre-built prompt when the user taps "Ask tutor about this trade".
    var onAskTutor: (String) -> Void = { _ in }
    /// Symbol handed off from the Portfolio tab ("Analyze in Trade").
    var externalSymbol: String?
    var onExternalConsumed: () -> Void = {}

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
            .onChange(of: externalSymbol) { s in
                if let s { symbol = s; onExternalConsumed() }
            }
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
            PayoffChartView(
                payoffAt: a.payoffAt,
                center: Double(stockPrice) ?? 100,
                breakevens: a.breakevens,
                analysisKey: legs.map { "\($0.strike)-\($0.premium)-\($0.isCall)-\($0.isLong)" }.joined(separator: "|") + "@" + symbol
            )
            Text("Plain-English risks").font(.headline).foregroundColor(.bronzeGold)
            ForEach(a.plainEnglishRisks, id: \.self) { r in
                Text("• \(r)").foregroundColor(.ink).font(.body)
            }
            Button("Ask tutor about this trade") {
                let spot = Double(stockPrice) ?? 0
                let be = a.breakevens.map { String(format: "$%.2f", $0) }.joined(separator: ", ")
                onAskTutor(
                    "I'm looking at a \(type.rawValue) on \(symbol) with the stock at \(String(format: "$%.2f", spot)). " +
                    "Max profit \(String(format: "$%.0f", a.maxProfit)), max loss \(String(format: "$%.0f", a.maxLoss)), " +
                    "breakeven(s) at \(be). Explain the key risks of this trade in plain English."
                )
            }
            .buttonStyle(.bordered)
            .tint(.bronzeGold)
            .frame(maxWidth: .infinity)
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

/// Interactive expiry-payoff chart: drag to scrub, slider, live P&L readout,
/// tappable breakeven markers. Math comes from the shared pricing engine via `payoffAt`.
struct PayoffChartView: View {
    let payoffAt: (Double) -> Double
    let center: Double
    let breakevens: [Double]
    let analysisKey: String

    @State private var scrub: Double = 0
    @State private var beInfo: Double? = nil

    private var lo: Double { center * 0.6 }
    private var hi: Double { center * 1.4 }

    var body: some View {
        VStack(spacing: 4) {
            let pl = payoffAt(scrub)
            HStack {
                Text("Drag the chart or the slider")
                    .font(.caption).foregroundColor(.muted)
                Spacer()
                Text("\(money(scrub)) → \(money(pl))")
                    .font(.subheadline).bold()
                    .foregroundColor(pl >= 0 ? .bullGreen : .bearRed)
            }
            GeometryReader { geo in
                Canvas { ctx, size in
                    let w = size.width
                    func X(_ p: Double) -> Double { w * (p - lo) / (hi - lo) }
                    let n = 120
                    let vals = (0...n).map { i -> lo + (hi - lo) * Double(i) / Double(n) }
                        .map { ($0, payoffAt($0)) }
                    let finite = vals.map { $0.1 }.filter { $0.isFinite }
                    let maxA = max(finite.max() ?? 1, 1), minA = min(finite.min() ?? -1, -1)
                    func Y(_ v: Double) -> Double { size.height * (1 - (v - minA) / (maxA - minA)) }
                    // zero line
                    ctx.stroke(Path { p in
                        p.move(to: CGPoint(x: 0, y: Y(0)))
                        p.addLine(to: CGPoint(x: w, y: Y(0)))
                    }, with: .color(.bronzeGold.opacity(0.5)), lineWidth: 2)
                    // spot line
                    ctx.stroke(Path { p in
                        p.move(to: CGPoint(x: X(center), y: 0))
                        p.addLine(to: CGPoint(x: X(center), y: size.height))
                    }, with: .color(.muted.opacity(0.4)), lineWidth: 1)
                    // payoff curve
                    var path = Path()
                    for (i, (p, v)) in vals.enumerated() {
                        let pt = CGPoint(x: X(p), y: Y(min(max(v, minA), maxA)))
                        if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
                    }
                    ctx.stroke(path, with: .color(.bullGreen), lineWidth: 3)
                    // breakeven markers — tap one for an explanation
                    for be in breakevens {
                        let c = CGPoint(x: X(be), y: Y(0))
                        ctx.fill(Path(ellipseIn: CGRect(x: c.x - 7, y: c.y - 7, width: 14, height: 14)),
                                 with: .color(.bronzeGold))
                        ctx.fill(Path(ellipseIn: CGRect(x: c.x - 3, y: c.y - 3, width: 6, height: 6)),
                                 with: .color(.white))
                    }
                    // scrubber crosshair + dot
                    let sx = X(scrub)
                    ctx.stroke(Path { p in
                        p.move(to: CGPoint(x: sx, y: 0))
                        p.addLine(to: CGPoint(x: sx, y: size.height))
                    }, with: .color(.white.opacity(0.7)), lineWidth: 1)
                    let dot = CGPoint(x: sx, y: Y(pl))
                    ctx.fill(Path(ellipseIn: CGRect(x: dot.x - 6, y: dot.y - 6, width: 12, height: 12)),
                             with: .color(.white))
                    ctx.stroke(Path(ellipseIn: CGRect(x: dot.x - 6, y: dot.y - 6, width: 12, height: 12)),
                               with: .color(.bronzeGold), lineWidth: 2)
                    // tooltip label
                    let label = Text("\(money(scrub)) · P&L \(money(pl))")
                        .font(.caption).foregroundColor(.white)
                    let resolved = ctx.resolve(label)
                    let boxW = resolved.measure(in: CGSize(width: w, height: 30)).width + 16
                    let boxH: Double = 28
                    let boxX = min(max(sx + 10, 0), max(w - boxW, 0))
                    let boxRect = CGRect(x: boxX, y: 6, width: boxW, height: boxH)
                    ctx.fill(Path(roundedRect: boxRect, cornerRadius: 6), with: .color(Color(red: 0.06, green: 0.09, blue: 0.15)))
                    ctx.stroke(Path(roundedRect: boxRect, cornerRadius: 6),
                               with: .color(.bronzeGold.opacity(0.6)), lineWidth: 1)
                    ctx.draw(resolved, at: CGPoint(x: boxX + 8, y: 6 + (boxH - 16) / 2), anchor: .topLeading)
                }
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            scrub = xToPrice(v.location.x, width: geo.size.width)
                        }
                        .onEnded { v in
                            // A near-motionless press is a tap: check breakeven markers first.
                            if abs(v.translation.width) < 8, abs(v.translation.height) < 8 {
                                let w = geo.size.width
                                if let be = breakevens.min(by: {
                                    abs(X($0, w: w) - v.location.x) < abs(X($1, w: w) - v.location.x)
                                }), abs(X(be, w: w) - v.location.x) < 28 {
                                    beInfo = be
                                }
                            }
                        }
                )
            }
            .frame(height: 200)
            Slider(value: $scrub, in: lo...hi)
                .tint(.bronzeGold)
        }
        .onAppear { scrub = center }
        .onChange(of: analysisKey) { scrub = center }
        .alert("Breakeven \(money(beInfo))", isPresented: Binding(
            get: { beInfo != nil },
            set: { if !$0 { beInfo = nil } }
        )) {
            Button("Got it", role: .cancel) { beInfo = nil }
        } message: {
            Text("The stock price at expiry where this trade breaks even — profit is exactly $0. " +
                 "Drag the scrubber across this point on the chart and watch the P&L flip sign.")
        }
    }

    private func xToPrice(_ x: Double, width: Double) -> Double {
        min(max(x / width * (hi - lo) + lo, lo), hi)
    }

    private func X(_ p: Double, w: Double) -> Double { w * (p - lo) / (hi - lo) }
}
