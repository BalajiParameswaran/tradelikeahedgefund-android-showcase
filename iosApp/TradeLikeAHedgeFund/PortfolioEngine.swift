import Foundation

/// Swift mirror of `com.tlhf.shared.portfolio` — same validation rules, same
/// math. The KMP module stays the cross-platform source of truth; this mirror
/// keeps the iOS app compiling without the XCFramework (see SharedBridge.swift).
/// Quotes are NEVER invented: value/P&L skip symbols with no known quote.

struct WatchEntry: Codable, Identifiable, Hashable {
    var id: String { symbol }
    let symbol: String
    let addedAt: Int64
}

struct Position: Codable, Identifiable, Hashable {
    var id: String { symbol }
    let symbol: String
    let shares: Double
    let avgCost: Double
}

struct StockQuote: Hashable {
    let symbol: String
    let price: Double
    let change: Double
    let changePct: Double
}

enum PortfolioEngine {
    private static let symbolRe = try! NSRegularExpression(pattern: "^[A-Z]{1,5}(\\.[A-Z]{1,2})?$")

    static func normalizeSymbol(_ raw: String) -> String? {
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let r = NSRange(s.startIndex..., in: s)
        return symbolRe.firstMatch(in: s, range: r) != nil ? s : nil
    }

    static func positionsValue(_ positions: [Position], quotes: [String: StockQuote]) -> Double {
        positions.reduce(0) { $0 + (quotes[$1.symbol]?.price ?? 0) * $1.shares }
    }

    static func positionsDayPnl(_ positions: [Position], quotes: [String: StockQuote]) -> Double {
        positions.reduce(0) { $0 + (quotes[$1.symbol]?.change ?? 0) * $1.shares }
    }

    static func missingQuotes(_ positions: [Position], quotes: [String: StockQuote]) -> [String] {
        positions.map(\.symbol).filter { quotes[$0] == nil }
    }

    static func unrealized(_ p: Position, price: Double) -> (pnl: Double, pct: Double) {
        let pnl = (price - p.avgCost) * p.shares
        let pct = p.avgCost > 0 ? (price / p.avgCost - 1) * 100 : 0
        return (pnl, pct)
    }

    /// Yahoo Finance chart endpoint (meta only). Throws on any failure —
    /// the UI shows "quote unavailable" instead of a fabricated number.
    static func fetchQuote(_ symbol: String) async throws -> StockQuote {
        let url = URL(string: "https://query1.finance.yahoo.com/v8/finance/chart/\(symbol)")!
        var req = URLRequest(url: url, timeoutInterval: 15)
        req.setValue("Mozilla/5.0 (compatible; TLHF/5.0)", forHTTPHeaderField: "User-Agent")
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw QuoteError.http }
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let result = (root["chart"] as? [String: Any])?["result"] as? [[String: Any]],
              let meta = result.first?["meta"] as? [String: Any],
              let price = meta["regularMarketPrice"] as? Double, price > 0
        else { throw QuoteError.badPayload }
        func num(_ k: String) -> Double { meta[k] as? Double ?? 0 }
        return StockQuote(symbol: meta["symbol"] as? String ?? symbol,
                          price: price,
                          change: num("regularMarketChange"),
                          changePct: num("regularMarketChangePercent"))
    }

    enum QuoteError: Error { case http, badPayload }
}
