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
import com.tlhf.shared.learn.Lesson
import com.tlhf.shared.learn.QuizQuestion
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.BullGreen
import com.tradelikeahedgefund.app.ui.theme.BearRed
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface

/**
 * Learn tab: Lessons | Flashcards | AI Topics | AI Tutor.
 * One shared MediaPipe engine lives in the tutor controller and is borrowed
 * by AI Topics (two loaded models would blow the RAM budget).
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
    var tab by remember { mutableIntStateOf(0) }
    var tutorPending by remember { mutableStateOf<String?>(null) }
    val tabs = listOf("Lessons", "Flashcards", "AI Topics", "AI Tutor")

    LaunchedEffect(externalQuestion) {
        if (externalQuestion != null) {
            tutorPending = externalQuestion
            tab = 3
            onExternalConsumed()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TutorStatusPill(controller = tutorCtl, onTap = { tab = 3 })
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
            0 -> LessonsTab()
            1 -> FlashcardsScreen(
                context = context,
                onReviewMistakes = { prompt -> tutorPending = prompt; tab = 3 }
            )
            2 -> AiTopicsScreen(
                engine = tutorCtl.engine,
                engineLoaded = tutorCtl.engineLoaded,
                onGoToTutor = { tab = 3 }
            )
            3 -> AiTutorScreen(
                controller = tutorCtl,
                pendingQuestion = tutorPending,
                onPendingConsumed = { tutorPending = null }
            )
        }
    }
}

@Composable
private fun LessonsTab() {
    var selected by remember { mutableStateOf<Lesson?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selected == null) {
            Text("Lessons", style = MaterialTheme.typography.headlineSmall, color = Ink)
            LESSONS.forEach { lesson ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    modifier = Modifier.fillMaxWidth().clickable { selected = lesson }
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(lesson.title, color = BronzeGold, style = MaterialTheme.typography.titleMedium)
                        Text(lesson.tagline, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            LessonDetail(selected!!, onBack = { selected = null })
        }
    }
}

@Composable
private fun LessonDetail(lesson: Lesson, onBack: () -> Unit) {
    val scroll = rememberScrollState()
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
        (lesson.quiz + lesson.extraQuiz).forEachIndexed { qi, q ->
            QuizCard(q, index = qi)
        }
    }
}

@Composable
private fun QuizCard(q: QuizQuestion, index: Int) {
    var picked by remember { mutableStateOf<Int?>(null) }
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
                    modifier = Modifier.fillMaxWidth().clickable { if (picked == null) picked = ci }
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
