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
            .sheet(isPresented: $showAddWatch) { addWatchSheet }
            .sheet(isPresented: $showAddPos) { positionSheet(existing: nil) }
            .sheet(item: $editPos) { p in positionSheet(existing: p) }
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
        return VStack(alignment: .leading, spacing: 4) {
            Text("Total value").font(.subheadline).foregroundColor(.muted)
            Text(money(value)).font(.largeTitle).bold().foregroundColor(.ink)
            Text("\(signedMoney(dayPnl)) today")
                .font(.title3)
                .foregroundColor(dayPnl >= 0 ? .bullGreen : .bearRed)
            Text(heroSubtitle(missing: missing))
                .font(.caption).foregroundColor(.muted)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.navySurface)
        .cornerRadius(10)
        .listRowBackground(Color.navyBg)
        .listRowSeparator(.hidden)
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
        .onTapGesture { onTradeSymbol(p.symbol) }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) { store.removePosition(p.symbol) } label: {
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
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) { store.removeWatch(w.symbol) } label: {
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
