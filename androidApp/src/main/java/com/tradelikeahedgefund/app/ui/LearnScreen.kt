package com.tradelikeahedgefund.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlhf.shared.learn.LESSONS
import com.tlhf.shared.learn.Lesson
import com.tlhf.shared.learn.QuizQuestion
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.BullGreen
import com.tradelikeahedgefund.app.ui.theme.BearRed
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface

/** Learn tab: lesson list -> detail (bullets + tip + quiz). Content from :shared. */
@Composable
fun LearnScreen() {
    var selected by remember { mutableStateOf<Lesson?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selected == null) {
            Text("Learn", style = MaterialTheme.typography.headlineSmall, color = Ink)
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
