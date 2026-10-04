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

/// One historical close. Mirrors shared `com.tlhf.shared.data.PricePoint`
/// (epoch milliseconds + close).
struct PricePoint: Hashable {
    let epochMs: Int64
    let close: Double
}

/// History window for `fetchHistory`: Yahoo `range` + `interval` params.
/// Mirrors shared `HistoryRange` (YahooClient.kt), plus a chip label.
enum HistoryRange: CaseIterable {
    case oneDay, oneWeek, oneMonth, threeMonths, yearToDate

    var rangeParam: String {
        switch self {
        case .oneDay: return "1d"
        case .oneWeek: return "5d"
        case .oneMonth: return "1mo"
        case .threeMonths: return "3mo"
        case .yearToDate: return "ytd"
        }
    }

    var intervalParam: String {
        switch self {
        case .oneDay: return "5m"
        case .oneWeek: return "30m"
        case .oneMonth: return "1d"
        case .threeMonths: return "1d"
        case .yearToDate: return "1d"
        }
    }

    var label: String {
        switch self {
        case .oneDay: return "1D"
        case .oneWeek: return "1W"
        case .oneMonth: return "1M"
        case .threeMonths: return "3M"
        case .yearToDate: return "YTD"
        }
    }
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

    /// Historical closes for [symbol] over [range] from the Yahoo chart
    /// endpoint. Never invents points: only closes actually present in the
    /// payload are returned, and a payload with no usable closes throws
    /// `QuoteError.badPayload`. Mirrors shared `YahooClient.history` /
    /// `parseChartHistory`.
    static func fetchHistory(_ symbol: String, range: HistoryRange) async throws -> [PricePoint] {
        let url = URL(string: "https://query1.finance.yahoo.com/v8/finance/chart/\(symbol)?range=\(range.rangeParam)&interval=\(range.intervalParam)&includePrePost=false")!
        var req = URLRequest(url: url, timeoutInterval: 15)
        req.setValue("Mozilla/5.0 (compatible; TLHF/5.0)", forHTTPHeaderField: "User-Agent")
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw QuoteError.http }
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let result = (root["chart"] as? [String: Any])?["result"] as? [[String: Any]],
              let first = result.first,
              let timestamps = first["timestamp"] as? [Any],
              let indicators = first["indicators"] as? [String: Any],
              let quotes = indicators["quote"] as? [[String: Any]],
              let closes = quotes.first?["close"] as? [Any]
        else { throw QuoteError.badPayload }
        var points: [PricePoint] = []
        let n = min(timestamps.count, closes.count)
        for i in 0..<n {
            guard let ts = timestamps[i] as? NSNumber else { continue }
            // Close elements may be NSNull (no trade in that interval).
            guard let closeNum = closes[i] as? NSNumber else { continue }
            let close = closeNum.doubleValue
            guard close > 0 else { continue }
            points.append(PricePoint(epochMs: ts.int64Value * 1000, close: close))
        }
        points.sort { $0.epochMs < $1.epochMs }
        guard !points.isEmpty else { throw QuoteError.badPayload }
        return points
    }

    /// Total portfolio value over time, forward-filled: at each timestamp in
    /// the union of all position symbols' histories, every position
    /// contributes shares × its latest close at or before that timestamp.
    /// Positions with no history points at all contribute nothing.
    /// Mirror of shared `portfolioValueSeries` (Portfolio.kt).
    static func portfolioValueSeries(_ positions: [Position], histories: [String: [PricePoint]]) -> [PricePoint] {
        if positions.isEmpty { return [] }
        let sortedHistories: [(Position, [PricePoint])] = positions.map { p in
            (p, (histories[p.symbol] ?? []).sorted { $0.epochMs < $1.epochMs })
        }
        if sortedHistories.allSatisfy({ $0.1.isEmpty }) { return [] }
        let timestamps = Set(sortedHistories.flatMap { $0.1.map(\.epochMs) }).sorted()
        var series: [PricePoint] = []
        for t in timestamps {
            var total = 0.0
            var any = false
            for (pos, hist) in sortedHistories {
                if hist.isEmpty { continue }
                guard let latest = hist.last(where: { $0.epochMs <= t }) else { continue }
                total += pos.shares * latest.close
                any = true
            }
            if any { series.append(PricePoint(epochMs: t, close: total)) }
        }
        return series
    }

    /// Symbols of [positions] whose history is missing or empty, in
    /// position order, distinct. Mirror of shared `missingHistory`.
    static func missingHistory(_ positions: [Position], histories: [String: [PricePoint]]) -> [String] {
        var seen = Set<String>()
        var out: [String] = []
        for p in positions {
            if seen.contains(p.symbol) { continue }
            seen.insert(p.symbol)
            if (histories[p.symbol] ?? []).isEmpty { out.append(p.symbol) }
        }
        return out
    }

    enum QuoteError: Error { case http, badPayload }
}
