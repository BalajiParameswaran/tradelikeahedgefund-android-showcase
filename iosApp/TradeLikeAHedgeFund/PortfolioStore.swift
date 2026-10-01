import Foundation
import Security
import Combine

/// Portfolio state: watchlist + positions, persisted encrypted in the Keychain
/// (share counts and cost basis are sensitive), quotes held in memory only.
@MainActor
final class PortfolioStore: ObservableObject {
    @Published private(set) var watchlist: [WatchEntry] = []
    @Published private(set) var positions: [Position] = []
    @Published private(set) var quotes: [String: StockQuote] = [:]
    @Published private(set) var quotesAt: Date?
    @Published private(set) var loading = false
    @Published private(set) var quoteError: String?

    private struct State: Codable {
        var watchlist: [WatchEntry] = []
        var positions: [Position] = []
    }

    private enum KC {
        static let service = "com.tradelikeahedgefund.portfolio"
        static let account = "tlhf_portfolio_v1"

        static func load() -> Data? {
            let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account,
                                    kSecReturnData as String: true,
                                    kSecMatchLimit as String: kSecMatchLimitOne]
            var out: AnyObject?
            guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess else { return nil }
            return out as? Data
        }

        static func save(_ data: Data) {
            let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account]
            let attrs: [String: Any] = [kSecValueData as String: data,
                                        kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
            if SecItemCopyMatching(q as CFDictionary, nil) == errSecSuccess {
                SecItemUpdate(q as CFDictionary, attrs as CFDictionary)
            } else {
                var add = q; add[kSecValueData as String] = data
                add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
                SecItemAdd(add as CFDictionary, nil)
            }
        }
    }

    init() {
        if let data = KC.load(),
           let decoded = try? JSONDecoder().decode(State.self, from: data) {
            watchlist = decoded.watchlist
            positions = decoded.positions
        }
    }

    private func save() {
        if let data = try? JSONEncoder().encode(State(watchlist: watchlist, positions: positions)) {
            KC.save(data)
        }
    }

    /// Returns an error message, or nil on success.
    func addWatch(_ raw: String) -> String? {
        guard let sym = PortfolioEngine.normalizeSymbol(raw) else {
            return "Enter a valid ticker, e.g. AAPL"
        }
        guard !watchlist.contains(where: { $0.symbol == sym }) else {
            return "\(sym) is already on your watchlist"
        }
        watchlist = (watchlist + [WatchEntry(symbol: sym,
                                             addedAt: Int64(Date().timeIntervalSince1970 * 1000))])
            .sorted { $0.symbol < $1.symbol }
        save()
        return nil
    }

    func removeWatch(_ symbol: String) {
        watchlist.removeAll { $0.symbol == symbol }
        save()
    }

    /// Returns an error message, or nil on success.
    func upsertPosition(symbol raw: String, shares: Double, avgCost: Double) -> String? {
        guard let sym = PortfolioEngine.normalizeSymbol(raw) else {
            return "Enter a valid ticker, e.g. AAPL"
        }
        guard shares > 0, shares.isFinite else { return "Shares must be greater than zero" }
        guard avgCost >= 0, avgCost.isFinite else { return "Average cost can't be negative" }
        positions = (positions.filter { $0.symbol != sym }
                     + [Position(symbol: sym, shares: shares, avgCost: avgCost)])
            .sorted { $0.symbol < $1.symbol }
        save()
        return nil
    }

    func removePosition(_ symbol: String) {
        positions.removeAll { $0.symbol == symbol }
        save()
    }

    func refreshQuotes() async {
        let symbols = Array(Set(positions.map(\.symbol) + watchlist.map(\.symbol)))
        guard !symbols.isEmpty else { return }
        loading = true
        quoteError = nil
        var ok: [String: StockQuote] = [:]
        var failed: [String] = []
        await withTaskGroup(of: (String, StockQuote?).self) { group in
            for s in symbols {
                group.addTask { (s, try? await PortfolioEngine.fetchQuote(s)) }
            }
            for await (s, q) in group {
                if let q { ok[s] = q } else { failed.append(s) }
            }
        }
        quotes.merge(ok) { _, new in new }
        if ok.isEmpty {
            quoteError = "Couldn't reach Yahoo Finance — check your connection and retry."
        } else if !failed.isEmpty {
            quoteError = "No quote for \(failed.sorted().joined(separator: ", ")) — not counted in totals."
        }
        if !ok.isEmpty { quotesAt = Date() }
        loading = false
    }
}
