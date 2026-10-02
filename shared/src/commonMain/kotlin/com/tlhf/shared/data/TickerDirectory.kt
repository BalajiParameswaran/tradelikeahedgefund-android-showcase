package com.tlhf.shared.data

data class TickerInfo(
    val symbol: String,
    val name: String
)

object TickerDirectory {
    val POPULAR_TICKERS = listOf(
        TickerInfo("AAPL", "Apple Inc."),
        TickerInfo("MSFT", "Microsoft Corporation"),
        TickerInfo("NVDA", "NVIDIA Corporation"),
        TickerInfo("GOOGL", "Alphabet Inc."),
        TickerInfo("AMZN", "Amazon.com Inc."),
        TickerInfo("META", "Meta Platforms Inc."),
        TickerInfo("TSLA", "Tesla Inc."),
        TickerInfo("SPY", "SPDR S&P 500 ETF Trust"),
        TickerInfo("QQQ", "Invesco QQQ Trust"),
        TickerInfo("AMD", "Advanced Micro Devices Inc."),
        TickerInfo("NFLX", "Netflix Inc."),
        TickerInfo("PLTR", "Palantir Technologies Inc."),
        TickerInfo("BAC", "Bank of America Corp."),
        TickerInfo("JPM", "JPMorgan Chase & Co."),
        TickerInfo("DIS", "The Walt Disney Company"),
        TickerInfo("COIN", "Coinbase Global Inc."),
        TickerInfo("COST", "Costco Wholesale Corp."),
        TickerInfo("WMT", "Walmart Inc."),
        TickerInfo("LLY", "Eli Lilly and Company"),
        TickerInfo("V", "Visa Inc."),
        TickerInfo("MA", "Mastercard Inc."),
        TickerInfo("XOM", "Exxon Mobil Corp."),
        TickerInfo("UNH", "UnitedHealth Group Inc."),
        TickerInfo("INTC", "Intel Corporation"),
        TickerInfo("PYPL", "PayPal Holdings Inc."),
        TickerInfo("UBER", "Uber Technologies Inc."),
        TickerInfo("ABNB", "Airbnb Inc."),
        TickerInfo("HOOD", "Robinhood Markets Inc."),
        TickerInfo("SOFI", "SoFi Technologies Inc."),
        TickerInfo("SMCI", "Super Micro Computer Inc."),
        TickerInfo("IWM", "iShares Russell 2000 ETF"),
        TickerInfo("DIA", "SPDR Dow Jones Industrial Average ETF"),
        TickerInfo("TLT", "iShares 20+ Year Treasury Bond ETF"),
        TickerInfo("GLD", "SPDR Gold Shares"),
        TickerInfo("SLV", "iShares Silver Trust"),
        TickerInfo("SQ", "Block Inc."),
        TickerInfo("SHOP", "Shopify Inc."),
        TickerInfo("CRM", "Salesforce Inc."),
        TickerInfo("ORCL", "Oracle Corporation"),
        TickerInfo("CSCO", "Cisco Systems Inc."),
        TickerInfo("PFE", "Pfizer Inc."),
        TickerInfo("MRK", "Merck & Co. Inc."),
        TickerInfo("JNJ", "Johnson & Johnson"),
        TickerInfo("KO", "The Coca-Cola Company"),
        TickerInfo("PEP", "PepsiCo Inc."),
        TickerInfo("NKE", "NIKE Inc."),
        TickerInfo("SBUX", "Starbucks Corp."),
        TickerInfo("MSTR", "MicroStrategy Inc.")
    )

    fun search(query: String, limit: Int = 5): List<TickerInfo> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val startsWithSym = POPULAR_TICKERS.filter { it.symbol.lowercase().startsWith(q) }
        val containsName = POPULAR_TICKERS.filter { !it.symbol.lowercase().startsWith(q) && it.name.lowercase().contains(q) }
        return (startsWithSym + containsName).take(limit)
    }
}
