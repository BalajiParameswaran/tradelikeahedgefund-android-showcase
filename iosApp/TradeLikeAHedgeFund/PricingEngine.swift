import Foundation

/// Pure-Swift pricing + strategy math, mirroring the shared KMP backend
/// (shared/src/commonMain/.../pricing + strategies) so the iOS app compiles
/// and runs standalone TODAY. When the KMP XCFramework is plugged in
/// (see SharedBridge.swift), this file becomes the fallback / test oracle.
///
/// MONEY CONVENTION: per-share P&L first, x100 exactly once at the total.

enum PricingEngine {
    static let contractMultiplier = 100.0

    // MARK: - Normal distribution

    static func normCdf(_ x: Double) -> Double {
        let t = 1.0 / (1.0 + 0.2316419 * abs(x))
        let d = 0.3989422804014327 * exp(-x * x / 2.0)
        let p = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        return x > 0 ? 1.0 - p : p
    }

    static func normPdf(_ x: Double) -> Double {
        exp(-0.5 * x * x) / sqrt(2.0 * Double.pi)
    }

    // MARK: - Black-Scholes

    struct Greeks {
        let price, delta, gamma, theta, vega, rho: Double // theta annualized
    }

    static func greeks(s: Double, k: Double, t: Double, r: Double, sig: Double, isCall: Bool) -> Greeks {
        guard s > 0, k > 0, t > 0, sig > 0 else {
            return Greeks(price: 0, delta: 0, gamma: 0, theta: 0, vega: 0, rho: 0)
        }
        let d1 = (log(s / k) + (r + sig * sig / 2.0) * t) / (sig * sqrt(t))
        let d2 = d1 - sig * sqrt(t)
        let disc = exp(-r * t)
        let nd1 = normPdf(d1)
        let price = isCall ? s * normCdf(d1) - k * disc * normCdf(d2)
                           : k * disc * normCdf(-d2) - s * normCdf(-d1)
        let delta = isCall ? normCdf(d1) : normCdf(d1) - 1.0
        let gamma = nd1 / (s * sig * sqrt(t))
        let theta = -(s * nd1 * sig) / (2.0 * sqrt(t))
            - (isCall ? r * k * disc * normCdf(d2) : -r * k * disc * normCdf(-d2))
        let vega = s * nd1 * sqrt(t)
        let rho = isCall ? k * t * disc * normCdf(d2) : -k * t * disc * normCdf(-d2)
        return Greeks(price: price, delta: delta, gamma: gamma, theta: theta, vega: vega, rho: rho)
    }

    // MARK: - Strategy payoffs

    static func legPayoffPerShare(_ leg: OptionLeg, price: Double) -> Double {
        let intrinsic = leg.isCall ? max(0, price - leg.strike) : max(0, leg.strike - price)
        return leg.isLong ? intrinsic - leg.premium : leg.premium - intrinsic
    }

    static func payoff(legs: [OptionLeg], price: Double, stockQty: Int = 0, stockCost: Double = 0) -> Double {
        legs.map { legPayoffPerShare($0, price: price) }.reduce(0, +) * contractMultiplier
            + Double(stockQty) * (price - stockCost)
    }

    static func legRisk(_ leg: OptionLeg) -> String {
        let kind = leg.isCall ? "call" : "put"
        let dir = leg.isLong ? "bought" : "sold"
        let desc = "$\(Int(leg.strike)) \(kind) (\(dir), $\(leg.premium)/share)"
        switch (leg.isLong, leg.isCall) {
        case (true, true):
            return "Long \(desc): the most you can lose is the premium. You need the stock above $\(Int(leg.strike)) plus what you paid to profit."
        case (true, false):
            return "Long \(desc): the most you can lose is the premium. You need the stock below $\(Int(leg.strike)) minus what you paid to profit."
        case (false, true):
            return "Short \(desc): you keep the premium if the stock stays below $\(Int(leg.strike)). Above it you owe the difference — losses can be large unless this call is covered by stock or a long call."
        case (false, false):
            return "Short \(desc): you keep the premium if the stock stays above $\(Int(leg.strike)). Below it you may be forced to buy the stock at $\(Int(leg.strike)) — keep cash ready."
        }
    }

    static func strategyNotes(_ type: StrategyType) -> [String] {
        switch type {
        case .coveredCall:
            return ["Profit is capped at the strike you sold — a moonshot leaves money on the table.",
                    "The premium only softens a crash; you still own the downside on the shares."]
        case .cashSecuredPut:
            return ["Only sell puts on stocks you would happily own at the strike.",
                    "Best outcome: the put expires worthless and you keep the full premium."]
        case .bullCallSpread:
            return ["Cheaper than buying the call outright, but profit is capped at the short strike.",
                    "Max loss is the debit you paid — defined up front."]
        case .bearPutSpread:
            return ["Cheaper than buying the put outright, but profit is capped at the short strike.",
                    "Max loss is the debit you paid — defined up front."]
        case .ironCondor:
            return ["You are short premium on both sides: you want the stock to sit still.",
                    "A strong move either way hits max loss — defined, but real.",
                    "Falling implied volatility helps you; rising volatility hurts."]
        case .ironButterfly:
            return ["Like an iron condor with the short strikes pinned together: bigger credit, narrower sweet spot.",
                    "Max profit needs the stock pinned exactly at the middle strike at expiry."]
        case .longStraddle:
            return ["You need a big move — up or down. Small moves lose to time decay.",
                    "Implied volatility crush after events (earnings) can sink this even if you called the direction."]
        case .longStrangle:
            return ["Cheaper than a straddle but needs an even bigger move to profit.",
                    "Time decay works against you every single day."]
        case .leapsCall:
            return ["Controls 100 shares of upside for a fraction of the stock's cost; max loss is capped at the premium.",
                    "If the stock goes nowhere, time decay quietly eats the premium — at expiry an at-the-money call is worth $0."]
        }
    }

    static func margin(type: StrategyType, legs: [OptionLeg]) -> Double {
        switch type {
        case .cashSecuredPut:
            guard let sp = legs.first(where: { !$0.isCall && !$0.isLong }) else { return 0 }
            return sp.strike * contractMultiplier - sp.premium * contractMultiplier
        case .ironCondor, .ironButterfly:
            let calls = legs.filter { $0.isCall }.map(\.strike)
            let puts = legs.filter { !$0.isCall }.map(\.strike)
            let wCall = calls.count >= 2 ? (calls.max()! - calls.min()!) : 0
            let wPut = puts.count >= 2 ? (puts.max()! - puts.min()!) : 0
            let credit = legs.map { $0.isLong ? -$0.premium : $0.premium }.reduce(0, +)
            return max(0, max(wCall, wPut) * contractMultiplier - credit * contractMultiplier)
        default:
            return 0
        }
    }

    static func breakevens(payoffAt: @escaping (Double) -> Double, lo: Double, hi: Double) -> [Double] {
        var found: [Double] = []
        let n = 400
        var prevX = lo, prevY = payoffAt(lo)
        for i in 1...n {
            let x = lo + (hi - lo) * Double(i) / Double(n)
            let y = payoffAt(x)
            if prevY == 0 { found.append(prevX) }
            else if y == 0 { found.append(x) }
            else if (prevY < 0) != (y < 0) {
                var a = prevX, b = x, fa = prevY
                for _ in 0..<40 {
                    let m = (a + b) / 2, fm = payoffAt(m)
                    if (fa < 0) != (fm < 0) { b = m } else { a = m; fa = fm }
                }
                found.append((a + b) / 2)
            }
            prevX = x; prevY = y
        }
        var dedup: [Double] = []
        for v in found.sorted() where v > 0.01 {
            if dedup.isEmpty || abs(dedup.last - v) > 0.05 { dedup.append(v) }
        }
        return dedup
    }

    static func analyze(type: StrategyType, legs: [OptionLeg], stockPrice: Double,
                        stockQty: Int = 0, stockCost: Double = 0) -> StrategyAnalysis {
        let payoffAt: (Double) -> Double = { payoff(legs: legs, price: $0, stockQty: stockQty, stockCost: stockCost) }
        let strikes = legs.map(\.strike)
        let lo = 0.01
        let hi = max((strikes.max() ?? stockPrice) * 2.5, stockPrice * 2.5) + 50

        var points: Set<Double> = [lo, hi]
        for s in strikes {
            points.insert(s)
            if s > 1 { points.insert(s - 0.01); points.insert(s + 0.01) }
        }
        var x = lo
        while x < hi { points.insert(x); x += hi / 200 }

        var minP = Double.infinity, maxP = -Double.infinity
        for p in points {
            let v = payoffAt(p)
            minP = min(minP, v); maxP = max(maxP, v)
        }
        if minP > 0 { minP = 0 }

        let unbounded: Set<StrategyType> = [.longStraddle, .longStrangle, .leapsCall]
        return StrategyAnalysis(
            maxProfit: unbounded.contains(type) ? nil : maxP,
            maxLoss: minP,
            breakevens: breakevens(payoffAt: payoffAt, lo: lo, hi: hi),
            marginRequired: margin(type: type, legs: legs),
            plainEnglishRisks: legs.map(legRisk) + strategyNotes(type),
            payoffAt: payoffAt
        )
    }
}
