import SwiftUI

/// Portfolio home: hero value/P&L, positions, watchlist.
/// Quotes come from Yahoo via PortfolioEngine — never invented. Symbols with
/// no quote are excluded from totals and the UI says so explicitly.
struct PortfolioView: View {
    @StateObject private var store = PortfolioStore()
    var onTradeSymbol: (String) -> Void = { _ in }

    @State private var showAddWatch = false
    @State private var showAddPos = false
    @State private var editPos: Position?
    @State private var confirmRemove: String?  // symbol awaiting delete confirmation
    @State private var sheetPos: Position?
    @State private var range: HistoryRange = .oneDay
    @State private var histories: [String: [PricePoint]] = [:]
    @State private var historyLoading = false

    var body: some View {
        NavigationStack {
            List {
                headerSection
                heroSection
                if let err = store.quoteError { errorBanner(err) }
                positionsSection
                watchlistSection
                footerNote
            }
            .listStyle(.plain)
            .background(Color.navyBg)
            .navigationTitle("Portfolio")
            .task { await store.refreshQuotes() }
            .task(id: "\(range)_\(store.positions.map(\.symbol).joined(separator: ","))") {
                await loadHistories()
            }
            .sheet(isPresented: $showAddWatch) { addWatchSheet }
            .sheet(isPresented: $showAddPos) { positionSheet(existing: nil) }
            .sheet(item: $editPos) { p in positionSheet(existing: p) }
            .sheet(item: $sheetPos) { p in
                PositionStrategiesSheet(position: p, quote: store.quotes[p.symbol]) {
                    onTradeSymbol(p.symbol)
                }
            }
            .confirmationDialog("Remove \(confirmRemove ?? "")?",
                                isPresented: .init(
                                    get: { confirmRemove != nil },
                                    set: { if !$0 { confirmRemove = nil } }),
                                titleVisibility: .visible) {
                Button("Remove", role: .destructive) {
                    if let s = confirmRemove {
                        if store.positions.contains(where: { $0.symbol == s }) {
                            store.removePosition(s)
                        } else {
                            store.removeWatch(s)
                        }
                    }
                    confirmRemove = nil
                }
                Button("Cancel", role: .cancel) { confirmRemove = nil }
            }
        }
    }

    // MARK: - Sections

    private var headerSection: some View {
        HStack {
            if store.loading { ProgressView().tint(.bronzeGold) }
            Spacer()
            Button { Task { await store.refreshQuotes() } } label: {
                Label("Refresh", systemImage: "arrow.clockwise")
            }
            .tint(.bronzeGold)
        }
        .listRowBackground(Color.navyBg)
        .listRowSeparator(.hidden)
    }

    private var heroSection: some View {
        let value = PortfolioEngine.positionsValue(store.positions, quotes: store.quotes)
        let dayPnl = PortfolioEngine.positionsDayPnl(store.positions, quotes: store.quotes)
        let missing = PortfolioEngine.missingQuotes(store.positions, quotes: store.quotes)
        let dayBase = value - dayPnl
        let dayPct = dayBase > 0 ? dayPnl / dayBase * 100 : 0
        let series = PortfolioEngine.portfolioValueSeries(store.positions, histories: histories)
        let missingHist = PortfolioEngine.missingHistory(store.positions, histories: histories)
        let accent: Color = dayPnl >= 0 ? .bullGreen : .bearRed
        return VStack(alignment: .leading, spacing: 4) {
            Text("Total value").font(.subheadline).foregroundColor(.muted)
            Text(money(value)).font(.largeTitle).bold().foregroundColor(.ink)
            Text("\(signedMoney(dayPnl)) (\(signedPct(dayPct))) today")
                .font(.title3)
                .foregroundColor(accent)
            Text(heroSubtitle(missing: missing))
                .font(.caption).foregroundColor(.muted)
            if series.count >= 2 {
                Canvas { ctx, size in
                    let closes = series.map(\.close)
                    var minV = closes.min() ?? 0
                    var maxV = closes.max() ?? 0
                    if maxV - minV < 0.000001 {
                        let pad = max(abs(minV) * 0.01, 1)
                        minV -= pad
                        maxV += pad
                    } else {
                        let pad = (maxV - minV) * 0.05
                        minV -= pad
                        maxV += pad
                    }
                    let w = size.width
                    let h = size.height
                    func xAt(_ i: Int) -> Double { w * Double(i) / Double(max(series.count - 1, 1)) }
                    func yAt(_ v: Double) -> Double { h * (1 - (v - minV) / (maxV - minV)) }
                    var line = Path()
                    var fill = Path()
                    for (i, pt) in series.enumerated() {
                        let c = CGPoint(x: xAt(i), y: yAt(pt.close))
                        if i == 0 {
                            line.move(to: c)
                            fill.move(to: CGPoint(x: c.x, y: h))
                            fill.addLine(to: c)
                        } else {
                            line.addLine(to: c)
                            fill.addLine(to: c)
                        }
                    }
                    if series.count >= 2 {
                        fill.addLine(to: CGPoint(x: xAt(series.count - 1), y: h))
                        fill.closeSubpath()
                    }
                    ctx.fill(fill, with: .color(accent.opacity(0.15)))
                    ctx.stroke(line, with: .color(accent), lineWidth: 2.5)
                }
                .frame(height: 110)
                .padding(.top, 8)
            }
            if !store.positions.isEmpty && series.count < 2 {
                Text("Price history isn't available for this range right now.")
                    .font(.caption).foregroundColor(.muted)
                    .padding(.top, 8)
            }
            if !missingHist.isEmpty && series.count >= 2 {
                Text("Chart excludes \(missingHist.joined(separator: ", ")) — no price history.")
                    .font(.caption).foregroundColor(.muted)
            }
            HStack(spacing: 8) {
                ForEach(HistoryRange.allCases, id: \.self) { r in
                    Button { range = r } label: {
                        Text(r.label)
                            .font(.caption)
                            .foregroundColor(.ink)
                            .padding(.vertical, 6).padding(.horizontal, 10)
                            .background(range == r ? Color.bronzeGold.opacity(0.25) : Color.navyBg)
                            .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                }
                if historyLoading {
                    ProgressView().tint(.bronzeGold)
                }
                Spacer()
            }
            .padding(.top, 8)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.navySurface)
        .cornerRadius(10)
        .listRowBackground(Color.navyBg)
        .listRowSeparator(.hidden)
    }

    /// Fetch per-symbol history for the selected range, keeping successes.
    /// Runs via `.task(id:)` keyed on range + position symbols, so a range
    /// or holdings change refetches and a cancelled task just drops out.
    private func loadHistories() async {
        if store.positions.isEmpty {
            histories = [:]
            historyLoading = false
            return
        }
        historyLoading = true
        let symbols = store.positions.map(\.symbol)
        let currentRange = range
        var fetched: [String: [PricePoint]] = [:]
        await withTaskGroup(of: (String, [PricePoint]?).self) { group in
            for s in symbols {
                group.addTask {
                    (s, try? await PortfolioEngine.fetchHistory(s, range: currentRange))
                }
            }
            for await (s, pts) in group {
                if let pts { fetched[s] = pts }
            }
        }
        // A superseded task (range/symbols changed mid-flight) is cancelled;
        // don't overwrite the newer task's state with stale results.
        if Task.isCancelled { return }
        histories = fetched
        historyLoading = false
    }

    private func heroSubtitle(missing: [String]) -> String {
        if store.positions.isEmpty { return "Add positions below to track value here." }
        if !missing.isEmpty { return "Excludes \(missing.joined(separator: ", ")) — no quote yet." }
        if let at = store.quotesAt {
            return "Quotes as of \(at.formatted(date: .omitted, time: .shortened)) · Yahoo Finance"
        }
        return "Waiting for quotes…"
    }

    private func errorBanner(_ err: String) -> some View {
        Text(err).font(.body).foregroundColor(.bearRed)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.navySurface)
            .cornerRadius(10)
            .listRowBackground(Color.navyBg)
            .listRowSeparator(.hidden)
    }

    private var positionsSection: some View {
        Section {
            if store.positions.isEmpty {
                Text("No positions yet. Add the stock positions behind your options trades to track value and P&L.")
                    .font(.body).foregroundColor(.muted)
                    .listRowBackground(Color.navySurface)
            } else {
                ForEach(store.positions) { p in positionRow(p) }
            }
        } header: {
            sectionHeader("Positions") { showAddPos = true }
        }
    }

    private var watchlistSection: some View {
        Section {
            if store.watchlist.isEmpty {
                Text("Nothing watched yet. Add tickers you want to keep an eye on.")
                    .font(.body).foregroundColor(.muted)
                    .listRowBackground(Color.navySurface)
            } else {
                ForEach(store.watchlist) { w in watchRow(w) }
            }
        } header: {
            sectionHeader("Watchlist") { showAddWatch = true }
        }
    }

    private var footerNote: some View {
        Text("Quotes by Yahoo Finance, for education only — not investment advice. Your list is stored encrypted on this device only.")
            .font(.caption).foregroundColor(.muted)
            .listRowBackground(Color.navyBg)
            .listRowSeparator(.hidden)
    }

    private func sectionHeader(_ title: String, add: @escaping () -> Void) -> some View {
        HStack {
            Text(title).font(.headline).foregroundColor(.bronzeGold)
            Spacer()
            Button(action: add) { Image(systemName: "plus") }.tint(.bronzeGold)
        }
    }

    // MARK: - Rows

    private func positionRow(_ p: Position) -> some View {
        let q = store.quotes[p.symbol]
        return HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(p.symbol).font(.headline).foregroundColor(.ink)
                Text("\(trimNum(p.shares)) sh @ \(money(p.avgCost))")
                    .font(.caption).foregroundColor(.muted)
                if let q {
                    Text("\(money(q.price)) (\(signedPct(q.changePct)))")
                        .font(.caption).foregroundColor(.muted)
                } else {
                    Text("Quote unavailable").font(.caption).foregroundColor(.bearRed)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(q != nil ? money(q!.price * p.shares) : "—")
                    .font(.headline).foregroundColor(.ink)
                if let q {
                    let u = PortfolioEngine.unrealized(p, price: q.price)
                    Text("\(signedMoney(u.pnl)) (\(signedPct(u.pct)))")
                        .font(.caption)
                        .foregroundColor(u.pnl >= 0 ? .bullGreen : .bearRed)
                } else {
                    Text("—").font(.caption).foregroundColor(.muted)
                }
            }
        }
        .padding(.vertical, 4)
        .listRowBackground(Color.navySurface)
        .contentShape(Rectangle())
        .onTapGesture { sheetPos = p }
        .swipeActions(edge: .leading) {
            Button { editPos = p } label: {
                Label("Edit", systemImage: "pencil")
            }
            .tint(.bronzeGold)
        }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) { confirmRemove = p.symbol } label: {
                Label("Delete", systemImage: "trash")
            }
        }
        .contextMenu {
            Button { onTradeSymbol(p.symbol) } label: { Label("Analyze in Trade", systemImage: "chart.line.uptrend.xyaxis") }
            Button { editPos = p } label: { Label("Edit", systemImage: "pencil") }
            Button(role: .destructive) { confirmRemove = p.symbol } label: { Label("Remove", systemImage: "trash") }
        }
    }

    private func watchRow(_ w: WatchEntry) -> some View {
        let q = store.quotes[w.symbol]
        return HStack {
            Text(w.symbol).font(.headline).foregroundColor(.ink)
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(q != nil ? money(q!.price) : "—")
                    .font(.headline).foregroundColor(.ink)
                if let q {
                    Text("\(signedMoney(q.change)) (\(signedPct(q.changePct)))")
                        .font(.caption)
                        .foregroundColor(q.change >= 0 ? .bullGreen : .bearRed)
                } else {
                    Text("Quote unavailable").font(.caption).foregroundColor(.bearRed)
                }
            }
        }
        .padding(.vertical, 4)
        .listRowBackground(Color.navySurface)
        .contentShape(Rectangle())
        .onTapGesture { onTradeSymbol(w.symbol) }
        .swipeActions(edge: .leading) {
            Button { onTradeSymbol(w.symbol) } label: {
                Label("Analyze", systemImage: "chart.line.uptrend.xyaxis")
            }
            .tint(.bronzeGold)
        }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) { confirmRemove = w.symbol } label: {
                Label("Delete", systemImage: "trash")
            }
        }
        .contextMenu {
            Button { onTradeSymbol(w.symbol) } label: { Label("Analyze in Trade", systemImage: "chart.line.uptrend.xyaxis") }
            Button(role: .destructive) { confirmRemove = w.symbol } label: { Label("Remove", systemImage: "trash") }
        }
    }

    // MARK: - Sheets

    private var addWatchSheet: some View {
        AddWatchSheet { raw in store.addWatch(raw) }
    }

    private func positionSheet(existing: Position?) -> some View {
        PositionSheet(existing: existing) { sym, shares, avg in
            store.upsertPosition(symbol: sym, shares: shares, avgCost: avg)
        }
    }

    // MARK: - Formatting

    private func money(_ v: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.currencyCode = "USD"
        f.maximumFractionDigits = 2
        return f.string(from: NSNumber(value: v)) ?? "$\(v)"
    }

    private func signedMoney(_ v: Double) -> String { (v >= 0 ? "+" : "") + money(v) }

    private func signedPct(_ v: Double) -> String {
        (v >= 0 ? "+" : "") + String(format: "%.2f%%", v)
    }

    private func trimNum(_ v: Double) -> String {
        v == v.rounded() ? String(Int(v)) : String(v)
    }
}

// MARK: - Position strategies sheet

/// Holder strategies for one position, in plain English with no invented
/// numbers: what you could do with shares you already own, then a jump
/// into Trade to analyze the symbol.
private struct PositionStrategiesSheet: View {
    @Environment(\.dismiss) private var dismiss
    let position: Position
    let quote: StockQuote?
    var onAnalyze: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    holdingSummary
                    strategySection(
                        title: "Covered Call — Own the stock, rent out the upside",
                        paragraphs: [
                            "You own the shares and sell someone the right to buy them at a higher strike. The premium is yours to keep.",
                            "If the stock stays under the strike, the call expires and you can sell another. Above the strike, your shares are sold at the strike — profit is capped.",
                            capacityLine
                        ])
                    strategySection(
                        title: "Cash-Secured Put — Get paid to name your buy price",
                        paragraphs: [
                            "Sell a put at a price you'd happily pay, with the cash set aside to buy. If the stock stays above the strike you keep the premium; below it, you buy the shares at the strike."
                        ])
                    strategySection(
                        title: "LEAPS Call — A year of upside, defined risk",
                        paragraphs: [
                            "A long-dated call controls 100 shares of upside for just the premium. Max loss is the premium — but time decay eats it if the stock goes nowhere."
                        ])
                    strategySection(
                        title: "Save from earnings",
                        paragraphs: [
                            "Before an earnings report, option premiums usually swell because a big move is expected. Some holders sell a covered call into that — collecting a richer premium in exchange for capping gains if the stock pops. The catch: a big beat can run far past your strike, and your shares get called away."
                        ])
                    Button("Analyze \(position.symbol) in Trade") {
                        dismiss()
                        onAnalyze()
                    }
                    .buttonStyle(.bordered)
                    .tint(.bronzeGold)
                    .frame(maxWidth: .infinity)
                }
                .padding()
            }
            .background(Color.navyBg)
            .navigationTitle("\(position.symbol) strategies")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }

    private var capacityLine: String {
        let contracts = Int(position.shares / 100)
        if contracts >= 1 {
            return "Your \(trimNum(position.shares)) shares can support \(contracts) covered-call contract(s)."
        }
        return "Covered calls need 100 shares per contract — this position isn't there yet."
    }

    private var holdingSummary: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(trimNum(position.shares)) shares @ \(fmtMoney(position.avgCost))")
                .font(.headline).foregroundColor(.ink)
            if let quote {
                let u = PortfolioEngine.unrealized(position, price: quote.price)
                Text("Now \(fmtMoney(quote.price))")
                    .font(.body).foregroundColor(.muted)
                Text("\(signedMoney(u.pnl)) (\(signedPct(u.pct)))")
                    .font(.body)
                    .foregroundColor(u.pnl >= 0 ? .bullGreen : .bearRed)
            } else {
                Text("Quote unavailable right now")
                    .font(.body).foregroundColor(.bearRed)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.navySurface)
        .cornerRadius(10)
    }

    private func strategySection(title: String, paragraphs: [String]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.headline).foregroundColor(.bronzeGold)
            ForEach(paragraphs, id: \.self) { p in
                Text(p).font(.body).foregroundColor(.ink)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.navySurface)
        .cornerRadius(10)
    }

    // Formatting helpers mirror PortfolioView's, kept local to this sheet
    // (and named distinctly so they never collide with the global
    // money(Double?) from Models.swift).
    private func fmtMoney(_ v: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.currencyCode = "USD"
        f.maximumFractionDigits = 2
        return f.string(from: NSNumber(value: v)) ?? "$\(v)"
    }

    private func signedMoney(_ v: Double) -> String {
        (v >= 0 ? "+" : "") + fmtMoney(v)
    }

    private func signedPct(_ v: Double) -> String {
        (v >= 0 ? "+" : "") + String(format: "%.2f%%", v)
    }

    private func trimNum(_ v: Double) -> String {
        v == v.rounded() ? String(Int(v)) : String(v)
    }
}

// MARK: - Add sheets

private struct AddWatchSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var symbol = ""
    @State private var error: String?
    var onAdd: (String) -> String?

    var body: some View {
        NavigationStack {
            Form {
                TextField("Ticker", text: $symbol)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                if let error {
                    Text(error).foregroundColor(.bearRed).font(.caption)
                }
            }
            .navigationTitle("Add to watchlist")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        if let e = onAdd(symbol) { error = e } else { dismiss() }
                    }
                }
            }
        }
    }
}

private struct PositionSheet: View {
    @Environment(\.dismiss) private var dismiss
    let existing: Position?
    @State private var symbol: String
    @State private var shares: String
    @State private var avg: String
    @State private var error: String?
    var onSave: (String, Double, Double) -> String?

    init(existing: Position?, onSave: @escaping (String, Double, Double) -> String?) {
        self.existing = existing
        self.onSave = onSave
        _symbol = State(initialValue: existing?.symbol ?? "")
        _shares = State(initialValue: existing.map { $0.shares == $0.shares.rounded() ? String(Int($0.shares)) : String($0.shares) } ?? "")
        _avg = State(initialValue: existing.map { String($0.avgCost) } ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Ticker", text: $symbol)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .disabled(existing != nil)
                TextField("Shares", text: $shares)
                    .keyboardType(.decimalPad)
                TextField("Average cost per share", text: $avg)
                    .keyboardType(.decimalPad)
                if let error {
                    Text(error).foregroundColor(.bearRed).font(.caption)
                }
            }
            .navigationTitle(existing != nil ? "Edit position" : "Add position")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        guard let sh = Double(shares), let ac = Double(avg) else {
                            error = "Enter numbers for shares and average cost"
                            return
                        }
                        if let e = onSave(symbol, sh, ac) { error = e } else { dismiss() }
                    }
                }
            }
        }
    }
}
