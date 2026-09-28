import Foundation

/// Bridge between the SwiftUI views and the pricing backend.
///
/// TODAY the views talk to `PricingService` through `LocalPricingService`,
/// which is the pure-Swift PricingEngine — so the app compiles and runs on
/// any Mac with Xcode right now, with zero extra setup.
///
/// TOMORROW (one shared backend for both apps):
///  1. On your Mac:  `./gradlew :shared:assembleXCFramework`
///     (from native/ — needs Xcode command-line tools).
///  2. In Xcode: drag the built `shared.xcframework` into
///     TradeLikeAHedgeFund.xcodeproj → Frameworks, and add
///     `import shared` where noted below.
///  3. Replace `LocalPricingService` with `KmpPricingService` (sketch below),
///     which delegates every call to the Kotlin implementations in
///     com.tlhf.shared.pricing / com.tlhf.shared.strategies. The views do NOT
///     change — they only know the `PricingService` protocol.
///
/// The Swift mirror (PricingEngine) then remains as the unit-test oracle:
/// KMP and Swift must agree to the cent.

protocol PricingService {
    func greeks(s: Double, k: Double, t: Double, r: Double, sig: Double, isCall: Bool)
        -> PricingEngine.Greeks
    func analyze(type: StrategyType, legs: [OptionLeg], stockPrice: Double,
                 stockQty: Int, stockCost: Double) -> StrategyAnalysis
    func legRisk(_ leg: OptionLeg) -> String
}

struct LocalPricingService: PricingService {
    func greeks(s: Double, k: Double, t: Double, r: Double, sig: Double, isCall: Bool)
        -> PricingEngine.Greeks {
        PricingEngine.greeks(s: s, k: k, t: t, r: r, sig: sig, isCall: isCall)
    }
    func analyze(type: StrategyType, legs: [OptionLeg], stockPrice: Double,
                 stockQty: Int, stockCost: Double) -> StrategyAnalysis {
        PricingEngine.analyze(type: type, legs: legs, stockPrice: stockPrice,
                              stockQty: stockQty, stockCost: stockCost)
    }
    func legRisk(_ leg: OptionLeg) -> String { PricingEngine.legRisk(leg) }
}

// TODO(kmp): after adding shared.xcframework to the target, replace the
// service above with this one. Kotlin types come from the framework:
//
//   import shared
//
//   struct KmpPricingService: PricingService {
//       func greeks(s: Double, k: Double, t: Double, r: Double, sig: Double, isCall: Bool)
//           -> PricingEngine.Greeks {
//           let g = PricingKt.bsGreeks(s: s, k: k, t: t, r: r, sig: sig, isCall: isCall)
//           return PricingEngine.Greeks(price: g.price, delta: g.delta, gamma: g.gamma,
//                                       theta: g.theta, vega: g.vega, rho: g.rho)
//       }
//       func analyze(type: StrategyType, legs: [OptionLeg], stockPrice: Double,
//                    stockQty: Int, stockCost: Double) -> StrategyAnalysis {
//           let kLegs = legs.map { SharedLeg(strike: $0.strike, isCall: $0.isCall,
//                                            isLong: $0.isLong, premium: $0.premium) }
//           let r = StrategiesKt.analyze(type: ..., legs: kLegs, stockPrice: stockPrice,
//                                        stockLegQty: Int32(stockQty), stockCostBasis: stockCost)
//           ... map back to StrategyAnalysis ...
//       }
//       func legRisk(_ leg: OptionLeg) -> String {
//           StrategiesKt.legRiskPlainEnglish(leg: SharedLeg(...))
//       }
//   }
//
// Data clients (Tradier/Yahoo/E*TRADE signers) ship in the same framework:
// `TradierClient(token:sandbox:)`, `YahooClient()`, `EtradeAuthKt.*`.
