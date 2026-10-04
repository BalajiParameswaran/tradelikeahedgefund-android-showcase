package com.tradelikeahedgefund.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tlhf.shared.ai.ModelState
import com.tlhf.shared.learn.LESSONS
import com.tlhf.shared.learn.LearnProgress
import com.tlhf.shared.learn.Lesson
import com.tlhf.shared.learn.QuizQuestion
import com.tradelikeahedgefund.app.ai.AiPlatform
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.BullGreen
import com.tradelikeahedgefund.app.ui.theme.BearRed
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface

/**
 * Learn tab: Lessons | Flashcards | AI Tutor.
 * One shared MediaPipe engine lives in the tutor controller.
 *
 * [externalQuestion] carries a question handed off from the Trade tab
 * ("Ask tutor about this trade"); it is pushed into the tutor and consumed once.
 */
@Composable
fun LearnScreen(
    externalQuestion: String? = null,
    onExternalConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val tutorCtl = remember { AiTutorController(context).also { it.refresh() } }
    val progress = remember { LearnProgress(AiPlatform.storage(context)) }
    var tab by remember { mutableIntStateOf(0) }
    var tutorPending by remember { mutableStateOf<String?>(null) }
    val tabs = listOf("Lessons", "Flashcards", "AI Tutor")

    LaunchedEffect(externalQuestion) {
        if (externalQuestion != null) {
            tutorPending = externalQuestion
            tab = 2
            onExternalConsumed()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TutorStatusPill(controller = tutorCtl, onTap = { tab = 2 })
        E2eNotice()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { i, title ->
                val selected = i == tab
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) BronzeGold.copy(alpha = 0.25f) else NavySurface
                    ),
                    modifier = Modifier.weight(1f).clickable { tab = i }
                ) {
                    Text(
                        title, color = Ink, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
        when (tab) {
            0 -> LessonsTab(progress = progress)
            1 -> FlashcardsScreen(
                context = context,
                progress = progress,
                onReviewMistakes = { prompt -> tutorPending = prompt; tab = 2 }
            )
            2 -> AiTutorScreen(
                controller = tutorCtl,
                pendingQuestion = tutorPending,
                onPendingConsumed = { tutorPending = null }
            )
        }
    }
}

@Composable
private fun LessonsTab(progress: LearnProgress) {
    var selected by remember { mutableStateOf<Lesson?>(null) }
    var progressTick by remember { mutableIntStateOf(0) }
    // Read progress values keyed on progressTick so marking a lesson complete
    // (which bumps the tick) refreshes the banner and the list.
    val completed = remember(progressTick) { progress.completedLessons() }
    val currentLevel = remember(progressTick) { progress.currentLevel() }
    val currentInfo = remember(progressTick) { progress.levelInfo(progress.currentLevel()) }
    val nextInfo = remember(progressTick) { progress.nextLevelInfo() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selected == null) {
            Text("Lessons", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Level $currentLevel — ${currentInfo?.title ?: ""}",
                        color = BronzeGold, style = MaterialTheme.typography.titleMedium
                    )
                    if (currentInfo != null) {
                        Text(currentInfo.canTradeMessage, color = Ink, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (nextInfo != null) {
                        Text(
                            "Next: finish '${nextInfo.title}' to reach Level ${nextInfo.level} and unlock ${nextInfo.title}.",
                            color = Muted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            LESSONS.forEach { lesson ->
                val unlocked = progress.isUnlocked(lesson.id)
                val isCompleted = lesson.id in completed
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    modifier = if (unlocked) {
                        Modifier.fillMaxWidth().clickable { selected = lesson }
                    } else {
                        Modifier.fillMaxWidth()
                    }
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(lesson.title, color = BronzeGold, style = MaterialTheme.typography.titleMedium)
                            if (isCompleted) {
                                Text("✓ Completed", color = BullGreen, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(lesson.tagline, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        val lp = progress.lessonProgress(lesson.id)
                        if (!isCompleted && lp != null && lp.answers.isNotEmpty()) {
                            Text(
                                "In progress — ${lp.answers.size} answered",
                                color = BronzeGold, style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!unlocked) {
                            Text(
                                "Locked — finish the previous lesson to unlock.",
                                color = Muted, style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        } else {
            val lesson = selected!!
            LessonDetail(
                lesson = lesson,
                progress = progress,
                isCompleted = lesson.id in completed,
                onLessonComplete = { lessonId ->
                    progress.markLessonComplete(lessonId)
                    progressTick++
                },
                onBack = { selected = null }
            )
        }
    }
}

@Composable
private fun LessonDetail(
    lesson: Lesson,
    progress: LearnProgress,
    isCompleted: Boolean,
    onLessonComplete: (String) -> Unit,
    onBack: () -> Unit
) {
    val scroll = rememberScrollState()
    val questions = lesson.quiz + lesson.extraQuiz
    // Saved quiz answers restore exactly where the user left off; picks are
    // recorded on every answer so leaving mid-lesson loses nothing.
    var answers by remember(lesson.id) {
        mutableStateOf(progress.lessonProgress(lesson.id)?.answers ?: emptyMap())
    }
    var correctLatch by remember(lesson.id) {
        mutableStateOf(
            answers.entries
                .filter { (qi, pick) -> questions.getOrNull(qi)?.answerIndex == pick }
                .map { it.key }
                .toSet()
        )
    }
    val allCorrect = questions.isNotEmpty() && correctLatch.size >= questions.size
    LaunchedEffect(allCorrect) {
        if (allCorrect && !isCompleted) onLessonComplete(lesson.id)
    }
    Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onBack) { Text("← All lessons") }
        Text(lesson.title, style = MaterialTheme.typography.headlineSmall, color = Ink)
        Text(lesson.tagline, color = BronzeGold, style = MaterialTheme.typography.titleSmall)
        lesson.learn.forEach { bullet ->
            Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
                Text("• $bullet", color = Ink, modifier = Modifier.padding(12.dp))
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
            Text("💡 Try it: ${lesson.tip}", color = Ink, modifier = Modifier.padding(12.dp))
        }
        Text("Quiz", color = BronzeGold, style = MaterialTheme.typography.titleMedium)
        if (allCorrect) {
            Text(
                "Lesson complete — check the Lessons list for your new level.",
                color = BullGreen, style = MaterialTheme.typography.bodyMedium
            )
        }
        questions.forEachIndexed { qi, q ->
            QuizCard(
                q, index = qi,
                initialPicked = answers[qi],
                onPick = { ci ->
                    answers = answers + (qi to ci)
                    progress.recordLessonAnswer(lesson.id, qi, ci, questions.size)
                },
                onAnswered = { correct -> if (correct) correctLatch = correctLatch + qi }
            )
        }
    }
}

@Composable
private fun QuizCard(
    q: QuizQuestion,
    index: Int,
    initialPicked: Int? = null,
    onPick: (Int) -> Unit = {},
    onAnswered: (Boolean) -> Unit = {}
) {
    var picked by remember { mutableStateOf(initialPicked) }
    Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Q${index + 1}. ${q.question}", color = Ink, style = MaterialTheme.typography.bodyLarge)
            q.choices.forEachIndexed { ci, choice ->
                val bg = when {
                    picked == null -> NavySurface
                    ci == q.answerIndex -> BullGreen.copy(alpha = 0.25f)
                    ci == picked -> BearRed.copy(alpha = 0.25f)
                    else -> NavySurface
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = bg),
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (picked == null) {
                            picked = ci
                            onPick(ci)
                            onAnswered(ci == q.answerIndex)
                        }
                    }
                ) {
                    Text(choice, color = Ink, modifier = Modifier.padding(10.dp))
                }
            }
            if (picked != null) {
                Text(
                    if (picked == q.answerIndex) "Correct. " else "Not quite. ",
                    color = if (picked == q.answerIndex) BullGreen else BearRed
                )
                Text(q.explanation, color = Muted, style = MaterialTheme.typography.bodyMedium)
                Row { Button(onClick = { picked = null }) { Text("Retry") } }
            }
        }
    }
}

/**
 * Always-visible on-device tutor status: download / loading / ready / error states.
 * Tapping jumps to the AI Tutor sub-tab.
 */
@Composable
private fun TutorStatusPill(controller: AiTutorController, onTap: () -> Unit) {
    val (label, color) = when {
        controller.deviceNote != null -> "AI unavailable on this device" to BearRed
        controller.error != null -> "Tutor error — tap to view" to BearRed
        controller.modelState == ModelState.DOWNLOADING ->
            "Downloading tutor model ${controller.progress?.percent ?: 0}%…" to BronzeGold
        controller.modelState == ModelState.LOADING -> "Loading tutor model…" to BronzeGold
        controller.engineLoaded || controller.modelState == ModelState.READY ->
            "Tutor ready — on-device" to BullGreen
        controller.modelState == ModelState.DOWNLOADED ->
            "Model downloaded — tap to load tutor" to BronzeGold
        controller.modelState == ModelState.PAUSED ->
            "Model download paused — tap to resume" to BronzeGold
        else -> "Tutor model not downloaded — tap to set up" to Muted
    }
    Surface(
        color = NavySurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onTap)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(10.dp)) {
                drawCircle(color)
            }
            Text(label, color = Ink, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("AI Tutor ›", color = BronzeGold, style = MaterialTheme.typography.labelMedium)
        }
    }
}
