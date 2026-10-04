package com.tlhf.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YahooClientTest {

    @Test
    fun historyRangeParams() {
        assertEquals("1d" to "5m", HistoryRange.ONE_DAY.rangeParam to HistoryRange.ONE_DAY.intervalParam)
        assertEquals("5d" to "30m", HistoryRange.ONE_WEEK.rangeParam to HistoryRange.ONE_WEEK.intervalParam)
        assertEquals("1mo" to "1d", HistoryRange.ONE_MONTH.rangeParam to HistoryRange.ONE_MONTH.intervalParam)
        assertEquals("3mo" to "1d", HistoryRange.THREE_MONTHS.rangeParam to HistoryRange.THREE_MONTHS.intervalParam)
        assertEquals("ytd" to "1d", HistoryRange.YEAR_TO_DATE.rangeParam to HistoryRange.YEAR_TO_DATE.intervalParam)
    }

    @Test
    fun parseSkipsNullAndNonPositiveClosesSortsAndConvertsSecondsToMs() {
        // Timestamps deliberately out of order; closes include a null and a 0.
        val payload = """
            {
              "chart": {
                "result": [
                  {
                    "timestamp": [1700000200, 1700000000, 1700000100, 1700000300],
                    "indicators": {
                      "quote": [
                        { "close": [null, 150.25, 151.0, 0] }
                      ]
                    }
                  }
                ],
                "error": null
              }
            }
        """.trimIndent()
        val points = parseChartHistory(payload)
        assertEquals(
            listOf(
                PricePoint(epochMs = 1_700_000_000_000L, close = 150.25),
                PricePoint(epochMs = 1_700_000_100_000L, close = 151.0)
            ),
            points
        )
    }

    @Test
    fun parseHandlesMissingAndMalformedPayloadsAsEmpty() {
        assertTrue(parseChartHistory("not json {{{").isEmpty())
        assertTrue(parseChartHistory("""{"chart":{"result":[],"error":null}}""").isEmpty())
        assertTrue(parseChartHistory("""{"chart":{"result":[{"timestamp":[1],"indicators":{"quote":[{"close":[null]}]}}],"error":null}}""").isEmpty())
        assertTrue(parseChartHistory("""{}""").isEmpty())
    }
}
