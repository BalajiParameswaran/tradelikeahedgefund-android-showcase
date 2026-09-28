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
