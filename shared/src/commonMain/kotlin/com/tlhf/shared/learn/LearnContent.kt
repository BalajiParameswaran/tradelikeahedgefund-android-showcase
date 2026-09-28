package com.tlhf.shared.learn

/**
 * Learn content ported from the web app's LESSONS + FLASH_EXTRA data
 * (web/app.html). HTML markup stripped to plain text; quiz answer indices
 * preserved exactly as in the source.
 */
data class QuizQuestion(
    val question: String,
    val choices: List<String>,
    val answerIndex: Int,
    val explanation: String
)

data class Lesson(
    val id: String,
    val title: String,
    val tagline: String,
    val learn: List<String>,
    val tip: String,
    val quiz: List<QuizQuestion>,
    val extraQuiz: List<QuizQuestion>
)

val LESSONS: List<Lesson> = listOf(
    Lesson(
        id = "coveredCall",
        title = "Covered Call",
        tagline = "Own the stock, rent out the upside",
        learn = listOf("You own 100 shares and sell someone the right to buy them from you at ${"$"}105 — here, the stock starts at ${"$"}100.", "The premium is yours immediately. If the stock drifts sideways or climbs gently, you keep it. That's the income.", "The catch: above ${"$"}105 your shares get called away, so profit is capped — and if the stock crashes, the premium only softens the fall."),
        tip = "Drag the stock slider past ${"$"}105 — watch profit flatline. That ceiling is the strike you sold.",
        quiz = listOf(
        QuizQuestion(
            question = "You own 100 sh @ ${"$"}100 and sell the ${"$"}105 call for ${"$"}2. Max profit?",
            choices = listOf("${"$"}200 — just the premium", "${"$"}700", "Unlimited", "${"$"}500"),
            answerIndex = 1,
            explanation = "You keep the ${"$"}5/share climb to the strike plus the ${"$"}2 premium: (${"$"}105−${"$"}100+${"$"}2)×100 = ${"$"}700."
        ),
        QuizQuestion(
            question = "The stock crashes to ${"$"}0. Your loss?",
            choices = listOf("Unlimited", "${"$"}9,800", "${"$"}200", "Nothing — the premium covers it"),
            answerIndex = 1,
            explanation = "100 shares at ${"$"}0 = −${"$"}10,000, softened only by the ${"$"}200 premium = −${"$"}9,800."
        ),
        QuizQuestion(
            question = "When does a covered call earn its maximum?",
            choices = listOf("If the stock doubles", "Stock at/above ${"$"}105 at expiration", "If the stock crashes", "Anytime before expiration"),
            answerIndex = 1,
            explanation = "At or above the strike your shares are called away — profit locks in at the cap."
        ),
        ),
        extraQuiz = listOf(
        QuizQuestion(
            question = "You own 100 sh @ ${"$"}100 and sell the ${"$"}105 call for ${"$"}2. Stock closes at ${"$"}103 on expiry. Total P&L?",
            choices = listOf("+${"$"}500", "+${"$"}200", "+${"$"}300", "−${"$"}200"),
            answerIndex = 0,
            explanation = "Stock rose ${"$"}3 → +${"$"}300 on the shares, and the ${"$"}105 call expires worthless so you keep the full ${"$"}2 premium (+${"$"}200). Total +${"$"}500. You only leave money on the table above ${"$"}105."
        ),
        QuizQuestion(
            question = "The ${"$"}105 call you sold gets exercised early. What happens?",
            choices = listOf("Your shares are called away at ${"$"}105", "You pay a penalty fee", "The call just disappears", "Nothing — early exercise can't happen"),
            answerIndex = 0,
            explanation = "Assignment means you sell your 100 shares at the ${"$"}105 strike — and you still keep the ${"$"}2 premium you collected."
        ),
        )
    ),
    Lesson(
        id = "cashPut",
        title = "Cash-Secured Put",
        tagline = "Get paid to name your buy price",
        learn = listOf("You sell a ${"$"}95 put with the stock at ${"$"}100 — and keep enough cash ready to buy 100 shares at ${"$"}95.", "If the stock stays above ${"$"}95, the put expires worthless and you keep the full premium.", "If it drops below ${"$"}95, you're buying the stock at ${"$"}95 (minus your premium cushion). Only do this on stocks you'd happily own."),
        tip = "Drag the stock slider down through ${"$"}95 — the put goes in-the-money and losses begin.",
        quiz = listOf(
        QuizQuestion(
            question = "You sell the ${"$"}95 put for ${"$"}2 (cash-secured). Max profit?",
            choices = listOf("${"$"}200", "${"$"}9,500", "Unlimited", "${"$"}95"),
            answerIndex = 0,
            explanation = "A seller's best day: the put expires worthless and you keep the whole ${"$"}2×100 = ${"$"}200."
        ),
        QuizQuestion(
            question = "The stock goes to ${"$"}0. Your loss?",
            choices = listOf("${"$"}200", "Unlimited", "${"$"}9,300", "${"$"}0 — cash-secured means safe"),
            answerIndex = 2,
            explanation = "You're forced to buy at ${"$"}95: −(${"$"}95−${"$"}2)×100 = −${"$"}9,300. The premium is a cushion, not armor."
        ),
        QuizQuestion(
            question = "When should you sell a cash-secured put?",
            choices = listOf("On any hot stock", "Only on a stock you'd buy at the strike", "When you expect a crash", "Never — too risky"),
            answerIndex = 1,
            explanation = "Assignment means owning 100 shares at ${"$"}95 — pick a strike price you'd pay anyway."
        ),
        ),
        extraQuiz = listOf(
        QuizQuestion(
            question = "You sell the ${"$"}95 put for ${"$"}2. Stock closes at ${"$"}97 on expiry. Result?",
            choices = listOf("Put expires worthless — you keep ${"$"}200", "You're assigned at ${"$"}95", "You lose ${"$"}200", "You must buy shares at ${"$"}97"),
            answerIndex = 0,
            explanation = "Above the ${"$"}95 strike the put is worthless. You keep the full ${"$"}2×100 = ${"$"}200 credit."
        ),
        QuizQuestion(
            question = "Stock closes at ${"$"}90 — you're assigned the ${"$"}95 put you sold for ${"$"}2. Effective buy price per share?",
            choices = listOf("${"$"}95.00", "${"$"}90.00", "${"$"}93.00", "${"$"}97.00"),
            answerIndex = 2,
            explanation = "Assigned at ${"$"}95, but you collected ${"$"}2 premium: ${"$"}95 − ${"$"}2 = ${"$"}93.00 effective cost per share."
        ),
        )
    ),
    Lesson(
        id = "bullCall",
        title = "Bull Call Spread",
        tagline = "Bullish on a budget",
        learn = listOf("Buy the ${"$"}100 call, sell the ${"$"}110 call. The short call funds part of the long call, so you pay less up front.", "You profit as the stock rises toward ${"$"}110 — but gains stop there. The short call caps your upside, just like a covered call.", "Max loss is the net debit you paid. Defined risk is the whole point of a spread."),
        tip = "Move the stock slider between ${"$"}100 and ${"$"}110 — that's the spread's sweet spot.",
        quiz = listOf(
        QuizQuestion(
            question = "Buy the ${"$"}100 call for ${"$"}4, sell the ${"$"}110 call for ${"$"}1.50. Max profit?",
            choices = listOf("${"$"}1,000", "${"$"}750", "${"$"}250", "Unlimited"),
            answerIndex = 1,
            explanation = "Spread width minus debit: (${"$"}110−${"$"}100−${"$"}2.50)×100 = ${"$"}750."
        ),
        QuizQuestion(
            question = "Max loss?",
            choices = listOf("${"$"}1,000", "Unlimited", "${"$"}250", "${"$"}400"),
            answerIndex = 2,
            explanation = "Both expire worthless — you lose the ${"$"}2.50 net debit, nothing more."
        ),
        QuizQuestion(
            question = "Breakeven at expiration?",
            choices = listOf("${"$"}100", "${"$"}110", "${"$"}102.50", "${"$"}104"),
            answerIndex = 2,
            explanation = "Long strike plus what you paid: ${"$"}100 + ${"$"}2.50 = ${"$"}102.50."
        ),
        ),
        extraQuiz = listOf(
        QuizQuestion(
            question = "Buy the ${"$"}100 call for ${"$"}4, sell the ${"$"}110 call for ${"$"}1.50. Stock closes at ${"$"}112. Your profit?",
            choices = listOf("${"$"}750", "${"$"}1,000", "${"$"}250", "${"$"}500"),
            answerIndex = 0,
            explanation = "The spread is worth its ${"$"}10 max = ${"$"}1,000. Minus the ${"$"}2.50 debit (${"$"}250) = ${"$"}750 profit."
        ),
        QuizQuestion(
            question = "Same spread — stock closes at ${"$"}99. What do you lose?",
            choices = listOf("The full ${"$"}250 debit", "${"$"}1,000", "Nothing", "${"$"}150"),
            answerIndex = 0,
            explanation = "Both calls expire worthless below ${"$"}100. Max loss is the ${"$"}2.50 debit = ${"$"}250 — that's the defined-risk part."
        ),
        )
    ),
    Lesson(
        id = "bearPut",
        title = "Bear Put Spread",
        tagline = "Bearish on a budget",
        learn = listOf("Buy the ${"$"}100 put, sell the ${"$"}90 put. The mirror image of the bull call spread — cheaper than a naked long put.", "Profit grows as the stock falls toward ${"$"}90, then caps out. Below ${"$"}90 both puts are fully in-the-money.", "Risk is the net debit, known before you enter. No surprises."),
        tip = "Drag the stock slider down through ${"$"}100 — the long put wakes up and profit climbs.",
        quiz = listOf(
        QuizQuestion(
            question = "Buy the ${"$"}100 put for ${"$"}4, sell the ${"$"}90 put for ${"$"}1.50. Max profit?",
            choices = listOf("${"$"}750", "${"$"}250", "${"$"}1,000", "Unlimited"),
            answerIndex = 0,
            explanation = "(${"$"}100−${"$"}90−${"$"}2.50)×100 = ${"$"}750 when the stock is at or below ${"$"}90."
        ),
        QuizQuestion(
            question = "Max loss?",
            choices = listOf("Unlimited", "${"$"}750", "${"$"}1,000", "${"$"}250"),
            answerIndex = 3,
            explanation = "The net debit — both puts expire worthless above ${"$"}100, so you lose ${"$"}250."
        ),
        QuizQuestion(
            question = "Where is profit highest?",
            choices = listOf("Stock above ${"$"}100", "Stock at/below ${"$"}90", "Stock exactly ${"$"}95", "Anywhere — it's fixed"),
            answerIndex = 1,
            explanation = "Below ${"$"}90 both puts are fully in-the-money — the spread pays its full width."
        ),
        ),
        extraQuiz = listOf(
        QuizQuestion(
            question = "Buy the ${"$"}100 put for ${"$"}4, sell the ${"$"}90 put for ${"$"}1.50. Breakeven at expiry?",
            choices = listOf("${"$"}97.50", "${"$"}95.00", "${"$"}92.50", "${"$"}100.00"),
            answerIndex = 0,
            explanation = "Long-put strike minus the debit: ${"$"}100 − ${"$"}2.50 = ${"$"}97.50."
        ),
        QuizQuestion(
            question = "Same spread — stock closes at ${"$"}85. Your profit?",
            choices = listOf("${"$"}750", "${"$"}1,000", "${"$"}250", "${"$"}500"),
            answerIndex = 0,
            explanation = "The spread hits its ${"$"}10 max value = ${"$"}1,000. Minus the ${"$"}2.50 debit (${"$"}250) = ${"$"}750."
        ),
        )
    ),
    Lesson(
        id = "ironCondor",
        title = "Iron Condor",
        tagline = "Profit from calm",
        learn = listOf("Sell a ${"$"}90/${"$"}85 put spread AND a ${"$"}110/${"$"}115 call spread. You collect two credits; the long wings define your risk.", "Best case: the stock sits between ${"$"}90 and ${"$"}110 through expiration — both spreads expire worthless and you keep everything.", "A big move either way hurts. This is a bet on boredom, paid for by time decay."),
        tip = "Move the stock slider between ${"$"}90 and ${"$"}110 — both spreads expire worthless and you keep the full credit.",
        quiz = listOf(
        QuizQuestion(
            question = "Put spread ${"$"}90/${"$"}85 + call spread ${"$"}110/${"$"}115, ${"$"}2 total credit. Max profit?",
            choices = listOf("${"$"}200", "${"$"}500", "${"$"}300", "Unlimited"),
            answerIndex = 0,
            explanation = "Stock between ${"$"}90 and ${"$"}110: everything expires worthless, you keep ${"$"}2×100 = ${"$"}200."
        ),
        QuizQuestion(
            question = "Max loss?",
            choices = listOf("${"$"}200", "${"$"}300", "${"$"}500", "Unlimited"),
            answerIndex = 1,
            explanation = "One side goes fully against you: ${"$"}5 width − ${"$"}2 credit = ${"$"}3×100 = ${"$"}300."
        ),
        QuizQuestion(
            question = "What market view fits an iron condor?",
            choices = listOf("Expecting a huge rally", "Expecting a calm, range-bound price", "Expecting a crash", "No view needed"),
            answerIndex = 1,
            explanation = "It harvests time decay — big moves in either direction are what hurt."
        ),
        ),
        extraQuiz = listOf(
        QuizQuestion(
            question = "Stock gaps to ${"$"}120 at expiry (your short call is ${"$"}110, long call ${"$"}115). Your loss?",
            choices = listOf("${"$"}300", "${"$"}200", "${"$"}500", "Unlimited"),
            answerIndex = 0,
            explanation = "The call spread goes fully against you: ${"$"}5 width − ${"$"}2 credit = ${"$"}3×100 = ${"$"}300. Defined risk — that's the whole point of the wings."
        ),
        QuizQuestion(
            question = "IV collapses the day after you open an iron condor. Good or bad for you?",
            choices = listOf("Good — you sold premium", "Bad — your credits shrink", "No effect at all", "Bad — the spreads widen"),
            answerIndex = 0,
            explanation = "You're short premium on both sides; falling IV deflates the options you sold. Time decay + IV crush both work for you."
        ),
        )
    ),
    Lesson(
        id = "leapsCall",
        title = "LEAPS Call",
        tagline = "A year of upside, defined risk",
        learn = listOf("Buy a call expiring a year or more out — here, the ${"$"}100 strike. You control 100 shares' upside without buying the stock.", "The most you can lose is the premium, even if the stock goes to zero. But that premium can melt to nothing.", "Time is the enemy: every quiet day shaves extrinsic value. You need the move, and you need it big enough."),
        tip = "Drag days-to-expiration down toward 180 — watch the premium shrink as time value drains.",
        quiz = listOf(
        QuizQuestion(
            question = "You buy a 1-year ${"$"}100 call for ${"$"}8. Max loss?",
            choices = listOf("Unlimited", "${"$"}800", "${"$"}10,000", "${"$"}8"),
            answerIndex = 1,
            explanation = "A long option can only lose its premium: ${"$"}8×100 = ${"$"}800."
        ),
        QuizQuestion(
            question = "Max profit?",
            choices = listOf("${"$"}800", "${"$"}1,600", "Unlimited", "${"$"}10,000"),
            answerIndex = 2,
            explanation = "No cap on a long call — the higher the stock climbs, the more it's worth."
        ),
        QuizQuestion(
            question = "A LEAPS call's biggest enemy?",
            choices = listOf("High volatility", "Time decay", "Dividends", "Low volume"),
            answerIndex = 1,
            explanation = "Theta melts extrinsic value every day — you need a big enough move to outrun it."
        ),
        ),
        extraQuiz = listOf(
        QuizQuestion(
            question = "Why buy a LEAPS call instead of 100 shares at ${"$"}100?",
            choices = listOf("Less capital at risk, same upside exposure", "Guaranteed profit", "No time decay", "You collect dividends"),
            answerIndex = 0,
            explanation = "${"$"}800 controls 100 shares' upside vs ${"$"}10,000 for the stock — and max loss is capped at the ${"$"}800 premium."
        ),
        QuizQuestion(
            question = "The stock sits exactly at ${"$"}100 for the full year. Your ${"$"}8 LEAPS call?",
            choices = listOf("Worth far less — time decay ate it", "Still worth ${"$"}8", "Worth more than ${"$"}8", "Converts into shares"),
            answerIndex = 0,
            explanation = "With no move, extrinsic value melts away. At expiry an at-the-money call is worth ${"$"}0 — you lose the full ${"$"}800."
        ),
        )
    ),
)

/** All flashcards: one info card per learn bullet + tip, one guess card per quiz question. */
data class Flashcard(val lessonId: String, val kind: FlashcardKind, val front: String, val choices: List<String> = emptyList(), val answerIndex: Int = -1, val back: String)

enum class FlashcardKind { INFO, GUESS }

fun buildFlashcards(): List<Flashcard> {
    val cards = mutableListOf<Flashcard>()
    for (lesson in LESSONS) {
        for (bullet in lesson.learn) cards += Flashcard(lesson.id, FlashcardKind.INFO, bullet, back = bullet)
        cards += Flashcard(lesson.id, FlashcardKind.INFO, "Try it: " + lesson.tip, back = lesson.tip)
        for (q in lesson.quiz + lesson.extraQuiz) {
            cards += Flashcard(lesson.id, FlashcardKind.GUESS, q.question, q.choices, q.answerIndex, q.explanation)
        }
    }
    return cards
}
