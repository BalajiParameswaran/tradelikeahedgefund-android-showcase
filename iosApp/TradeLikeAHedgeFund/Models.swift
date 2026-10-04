import Foundation

/// Swift mirrors of the shared KMP models (com.tlhf.shared.*).
/// Long-term these come from the KMP XCFramework; see SharedBridge.swift.

enum StrategyType: String, CaseIterable, Identifiable {
    case coveredCall = "Covered Call"
    case cashSecuredPut = "Cash-Secured Put"
    case bullCallSpread = "Bull Call Spread"
    case bearPutSpread = "Bear Put Spread"
    case ironCondor = "Iron Condor"
    case ironButterfly = "Iron Butterfly"
    case longStraddle = "Long Straddle"
    case longStrangle = "Long Strangle"
    case leapsCall = "LEAPS Call"
    var id: String { rawValue }
}

/// Plain-English market view, mirroring shared KMP `Outlook` (Outlook.kt).
enum Outlook: String, CaseIterable, Identifiable {
    case up = "Up"
    case down = "Down"
    case flat = "Flat"
    case swing = "Swing"
    var id: String { rawValue }
}

extension StrategyType {
    /// Strategies that fit [outlook], in `allCases` (declaration) order.
    /// Membership mirrors shared `strategiesForOutlook` exactly:
    /// up = {bullCallSpread, leapsCall, coveredCall, cashSecuredPut},
    /// down = {bearPutSpread},
    /// flat = {ironCondor, ironButterfly, coveredCall, cashSecuredPut},
    /// swing = {longStraddle, longStrangle}.
    static func strategies(for outlook: Outlook) -> [StrategyType] {
        let wanted: Set<StrategyType>
        switch outlook {
        case .up: wanted = [.bullCallSpread, .leapsCall, .coveredCall, .cashSecuredPut]
        case .down: wanted = [.bearPutSpread]
        case .flat: wanted = [.ironCondor, .ironButterfly, .coveredCall, .cashSecuredPut]
        case .swing: wanted = [.longStraddle, .longStrangle]
        }
        return StrategyType.allCases.filter { wanted.contains($0) }
    }

    /// Inverse of `strategies(for:)` — computed from it so the two never drift.
    var outlooks: Set<Outlook> {
        Set(Outlook.allCases.filter { StrategyType.strategies(for: $0).contains(self) })
    }

    /// Strategies that make sense for someone who already holds the stock
    /// (or cash to secure a put). Mirrors shared `holderStrategies()`.
    static var holderStrategies: Set<StrategyType> {
        [.coveredCall, .cashSecuredPut, .bullCallSpread, .bearPutSpread, .leapsCall]
    }

    /// The Trade deck for an outlook / holding state, in `allCases` order.
    /// When the holder intersection would empty the deck, the outlook-only
    /// (or full) list is used instead — the deck is never empty.
    /// Mirrors shared `filterDeck`.
    static func filterDeck(outlook: Outlook?, hasPosition: Bool) -> [StrategyType] {
        let byOutlook: [StrategyType]
        if let outlook {
            byOutlook = strategies(for: outlook)
        } else {
            byOutlook = StrategyType.allCases
        }
        if !hasPosition { return byOutlook }
        let intersected = byOutlook.filter { holderStrategies.contains($0) }
        return intersected.isEmpty ? byOutlook : intersected
    }
}

/// Lesson that teaches [type]. Related strategies share their closest
/// lesson. Mirrors shared `lessonIdForStrategy` (RiskQuiz.kt).
func lessonId(for type: StrategyType) -> String {
    switch type {
    case .coveredCall: return "coveredCall"
    case .cashSecuredPut: return "cashPut"
    case .bullCallSpread: return "bullCall"
    case .bearPutSpread: return "bearPut"
    case .ironCondor: return "ironCondor"
    case .ironButterfly: return "ironCondor"
    case .leapsCall: return "leapsCall"
    case .longStraddle: return "leapsCall"
    case .longStrangle: return "leapsCall"
    }
}

struct OptionLeg: Identifiable {
    let id = UUID()
    var strike: Double
    var premium: Double
    var isCall: Bool
    var isLong: Bool
}

struct StrategyAnalysis {
    /// nil = unlimited
    let maxProfit: Double?
    let maxLoss: Double
    let breakevens: [Double]
    let marginRequired: Double
    let plainEnglishRisks: [String]
    let payoffAt: (Double) -> Double
}

struct Lesson: Codable, Identifiable {
    let id: String
    let title: String
    let tagline: String
    let learn: [String]
    let tip: String
    let quiz: [QuizQuestion]
    let extraQuiz: [QuizQuestion]
}

struct QuizQuestion: Codable, Identifiable {
    var id: String { question }
    let question: String
    let choices: [String]
    let answerIndex: Int
    let explanation: String
}

func money(_ v: Double?) -> String {
    guard let v else { return "Unlimited" }
    if v.isInfinite { return v > 0 ? "Unlimited" : "-Unlimited" }
    let r = Int(v.rounded())
    let f = NumberFormatter()
    f.numberStyle = .decimal
    f.maximumFractionDigits = 0
    let s = f.string(from: NSNumber(value: abs(r))) ?? "\(abs(r))"
    return (r < 0 ? "-$" : "$") + s
}
