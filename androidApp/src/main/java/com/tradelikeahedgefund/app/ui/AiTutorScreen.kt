package com.tradelikeahedgefund.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlhf.shared.ai.AI_MODELS
import com.tlhf.shared.ai.AiChatStore
import com.tlhf.shared.ai.AiModel
import com.tlhf.shared.ai.ChatMessage
import com.tlhf.shared.ai.DownloadProgress
import com.tlhf.shared.ai.LlmEngine
import com.tlhf.shared.ai.ModelState
import com.tlhf.shared.ai.aiModelById
import com.tlhf.shared.ai.aiStudyPrompt
import com.tlhf.shared.ai.buildTutorPrompt
import com.tlhf.shared.ai.formatBytes
import com.tlhf.shared.ai.marketContext
import com.tlhf.shared.ai.matchLessonTitle
import com.tlhf.shared.ai.parseAiCards
import com.tlhf.shared.ai.DynamicCard
import com.tlhf.shared.ai.shouldAutoUnload
import com.tradelikeahedgefund.app.ai.AiPlatform
import com.tradelikeahedgefund.app.ai.MediaPipeEngine
import com.tradelikeahedgefund.app.ai.ModelDownloader
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.BullGreen
import com.tradelikeahedgefund.app.ui.theme.BearRed
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface

/**
 * AI Tutor tab: on-device chat (Qwen2.5 via MediaPipe), 100% on-device.
 * Sessions persist encrypted (max 10 sessions × 40 messages).
 */
class AiTutorController(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private fun ui(fn: () -> Unit) = main.post(fn)

    val engine: LlmEngine = MediaPipeEngine(context)
    private val downloader = ModelDownloader()
    private val chatStore = AiChatStore(AiPlatform.storage(context))
    private val prefs = AiPlatform.prefs(context)

    var model by mutableStateOf(aiModelById(prefs.getString("ai_model_id", null) ?: "balanced"))
    var modelState by mutableStateOf(ModelState.NOT_DOWNLOADED)
    var progress by mutableStateOf<DownloadProgress?>(null)
    var wifiOnly by mutableStateOf(prefs.getBoolean("ai_wifi_only", true))
    var deviceNote by mutableStateOf<String?>(null)
    var engineLoaded by mutableStateOf(false)
    var sessions by mutableStateOf(chatStore.load())
    var activeId by mutableStateOf<String?>(null)
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var streaming by mutableStateOf("")
    var input by mutableStateOf("")
    var ticker by mutableStateOf("AAPL")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var lastQuestion by mutableStateOf("")
    var studyCards by mutableStateOf<List<DynamicCard>>(emptyList())
    var studyBusy by mutableStateOf(false)

    fun refresh() {
        val check = AiPlatform.deviceCheck(context)
        deviceNote = if (check.supported) null else "On-device AI unavailable: ${check.reason}"
        val file = AiPlatform.modelFile(context, model)
        modelState = when {
            downloader.isRunning -> ModelState.DOWNLOADING
            engineLoaded -> ModelState.READY
            file.exists() -> ModelState.DOWNLOADED
            else -> ModelState.NOT_DOWNLOADED
        }
        sessions = chatStore.load()
        if (activeId == null) activeId = sessions.firstOrNull()?.id
        messages = activeId?.let { chatStore.get(it)?.messages } ?: emptyList()
    }

    fun selectModel(m: AiModel) {
        if (m.id == model.id) return
        engine.unload()
        engineLoaded = false
        model = m
        prefs.edit().putString("ai_model_id", m.id).apply()
        progress = null
        studyCards = emptyList()
        refresh()
    }

    fun toggleWifiOnly(v: Boolean) {
        wifiOnly = v
        prefs.edit().putBoolean("ai_wifi_only", v).apply()
    }

    fun startDownload() {
        error = null
        val dest = AiPlatform.modelFile(context, model)
        val reason = downloader.start(
            model.androidUrl, dest, wifiOnly,
            isWifiNow = { AiPlatform.isWifi(context) },
            listener = object : ModelDownloader.Listener {
                override fun onProgress(d: Long, t: Long) { ui {
                    progress = DownloadProgress(d, t)
                    modelState = ModelState.DOWNLOADING
                } }
                override fun onDone(err: String?) { ui {
                    if (err == null || err == "cancelled" && AiPlatform.modelFile(context, model).exists()) {
                        progress = null
                        refresh()
                    } else if (err != "cancelled") {
                        modelState = ModelState.ERROR
                        error = "Download failed: $err"
                    } else refresh()
                } }
            }
        )
        if (reason != null) error = if (reason == "wifi_required") "Connect to WiFi to download the model (or allow cellular below)." else reason
        else modelState = ModelState.DOWNLOADING
    }

    fun pauseDownload() { downloader.pause(); modelState = ModelState.PAUSED }
    fun resumeDownload() {
        val dest = AiPlatform.modelFile(context, model)
        downloader.resume(model.androidUrl, dest, wifiOnly, { AiPlatform.isWifi(context) },
            listener = downloadListener())
        modelState = ModelState.DOWNLOADING
    }
    private fun downloadListener() = object : ModelDownloader.Listener {
        override fun onProgress(d: Long, t: Long) { ui {
            progress = DownloadProgress(d, t); modelState = ModelState.DOWNLOADING
        } }
        override fun onDone(err: String?) { ui {
            if (err == null) { progress = null; refresh() }
            else if (err != "cancelled") { modelState = ModelState.ERROR; error = "Download failed: $err" }
            else refresh()
        } }
    }
    fun cancelDownload() { downloader.cancel() }

    fun deleteModel() {
        downloader.delete(AiPlatform.modelFile(context, model))
        engine.unload()
        engineLoaded = false
        progress = null
        refresh()
    }

    fun loadModel() {
        val file = AiPlatform.modelFile(context, model)
        if (!file.exists()) { error = "Download the model first."; return }
        idleUnloadIfNeeded(force = false)
        error = null
        modelState = ModelState.LOADING
        engine.loadModel(file.absolutePath) { err -> ui {
            if (err == null) {
                engineLoaded = true
                modelState = ModelState.READY
                touchUsed()
            } else {
                engineLoaded = false
                modelState = ModelState.ERROR
                error = err
            }
        } }
    }

    fun unloadModel() {
        engine.unload()
        engineLoaded = false
        refresh()
    }

    private fun touchUsed() {
        prefs.edit().putLong("ai_last_used", System.currentTimeMillis()).apply()
    }

    /** 30-minute idle auto-unload, like the web app. */
    private fun idleUnloadIfNeeded(force: Boolean) {
        val last = prefs.getLong("ai_last_used", 0L)
        if (engineLoaded && (force || shouldAutoUnload(last, System.currentTimeMillis()))) {
            engine.unload()
            engineLoaded = false
        }
    }

    fun newChat() {
        val s = chatStore.newSession()
        sessions = chatStore.load()
        activeId = s.id
        messages = emptyList()
        streaming = ""
        studyCards = emptyList()
        lastQuestion = ""
    }

    fun openSession(id: String) {
        activeId = id
        messages = chatStore.get(id)?.messages ?: emptyList()
        streaming = ""
        studyCards = emptyList()
    }

    fun deleteSession(id: String) {
        chatStore.delete(id)
        if (activeId == id) { activeId = null; messages = emptyList() }
        sessions = chatStore.load()
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        if (deviceNote != null) { error = deviceNote; return }
        if (!engineLoaded) { error = "Load the model first."; return }
        idleUnloadIfNeeded(force = false)
        if (!engineLoaded) { error = "Model was unloaded (idle 30 min) — tap Load."; refresh(); return }

        var sid = activeId
        if (sid == null) {
            val s = chatStore.newSession()
            sid = s.id
            sessions = chatStore.load()
            activeId = sid
        }
        val sessionId = sid
        input = ""
        error = null
        busy = true
        streaming = ""
        studyCards = emptyList()
        lastQuestion = text
        chatStore.addMessage(sessionId, "user", text)
        messages = chatStore.get(sessionId)?.messages ?: emptyList()
        touchUsed()

        val prompt = buildTutorPrompt(
            market = marketContext(ticker, null, null, null),
            portfolioJson = "{}",
            history = messages,
            userText = text
        )
        engine.generate(
            prompt,
            onToken = { partial -> ui { streaming = partial } },
            onDone = { full, err -> ui {
                busy = false
                if (err != null) {
                    error = "Couldn't generate a reply: $err"
                    streaming = ""
                } else {
                    val reply = full.orEmpty()
                    chatStore.addMessage(sessionId, "bot", reply)
                    messages = chatStore.get(sessionId)?.messages ?: emptyList()
                    streaming = ""
                    touchUsed()
                }
            } }
        )
    }

    fun makeStudyCards() {
        if (studyBusy || busy || !engineLoaded || lastQuestion.isEmpty()) return
        studyBusy = true
        val label = matchLessonTitle(lastQuestion) ?: "Options basics"
        val prompt = aiStudyPrompt(label, ticker.uppercase(), null, lastQuestion)
        engine.generate(
            prompt,
            onToken = {},
            onDone = { full, err -> ui {
                studyBusy = false
                if (err == null && full != null) {
                    studyCards = parseAiCards(full, 6)
                    touchUsed()
                }
            } }
        )
    }
}
@Composable
fun AiTutorScreen(
    controller: AiTutorController,
    pendingQuestion: String?,
    onPendingConsumed: () -> Unit
) {
    val ctl = controller
    val listState = rememberLazyListState()
    var showSessions by remember { mutableStateOf(false) }
    var showStudyCards by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ctl.refresh() }
    LaunchedEffect(pendingQuestion) {
        if (!pendingQuestion.isNullOrBlank()) {
            ctl.input = pendingQuestion
            ctl.send()
            onPendingConsumed()
        }
    }
    LaunchedEffect(ctl.messages.size, ctl.streaming) {
        val n = ctl.messages.size + if (ctl.streaming.isNotEmpty()) 1 else 0
        if (n > 0) listState.animateScrollToItem(maxOf(0, n - 1))
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI Tutor", style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { showSessions = true }) { Text("Chats (${ctl.sessions.size})") }
        }
        Text("Runs 100% on this phone. No account, no cloud.", color = Muted, style = MaterialTheme.typography.bodySmall)

        if (ctl.deviceNote != null) {
            Card(colors = CardDefaults.cardColors(containerColor = BearRed.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
                Text(ctl.deviceNote!!, color = BearRed, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        ModelCard(ctl)

        if (ctl.error != null) {
            Text(ctl.error!!, color = BearRed, style = MaterialTheme.typography.bodySmall)
        }

        // Messages
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ctl.messages) { msg ->
                val isUser = msg.role == "user"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) BronzeGold.copy(alpha = 0.25f) else NavySurface
                        ),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Text(msg.text, color = Ink, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (ctl.streaming.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = NavySurface), modifier = Modifier.fillMaxWidth(0.85f)) {
                        Text(ctl.streaming, color = Ink, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Study cards toggle
        if (ctl.studyCards.isNotEmpty()) {
            OutlinedButton(onClick = { showStudyCards = !showStudyCards }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showStudyCards) "Hide study cards (${ctl.studyCards.size})" else "Show study cards (${ctl.studyCards.size})")
            }
            if (showStudyCards) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ctl.studyCards.forEach { card ->
                        var flipped by remember(card) { mutableStateOf(false) }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NavySurface),
                            modifier = Modifier.fillMaxWidth().clickable { flipped = !flipped }
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(if (flipped) "Back — tap to flip" else "Front — tap to flip", color = Muted, style = MaterialTheme.typography.labelSmall)
                                Text(if (flipped) card.back else card.front, color = Ink, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        // Input
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = ctl.ticker,
                onValueChange = { ctl.ticker = it.uppercase().filter { ch -> ch.isLetter() }.take(6) },
                label = { Text("Ticker") },
                singleLine = true,
                modifier = Modifier.width(110.dp)
            )
            TextField(
                value = ctl.input,
                onValueChange = { ctl.input = it },
                placeholder = { Text("Ask about options…") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { ctl.send() })
            )
            Button(onClick = { ctl.send() }, enabled = !ctl.busy && ctl.engineLoaded) { Text("Send") }
        }
        OutlinedButton(
            onClick = { ctl.makeStudyCards() },
            enabled = ctl.studyBusy.not() && ctl.engineLoaded && !ctl.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (ctl.studyBusy) "Making cards…" else "Make study cards from this answer")
        }
    }

    if (showSessions) {
        AlertDialog(
            onDismissRequest = { showSessions = false },
            title = { Text("Chats", color = Ink) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item {
                        Button(onClick = { ctl.newChat(); showSessions = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("+ New chat")
                        }
                    }
                    items(ctl.sessions) { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.title ?: "New chat", color = Ink, modifier = Modifier.weight(1f).clickable {
                                    ctl.openSession(s.id); showSessions = false
                                }
                            )
                            TextButton(onClick = { ctl.deleteSession(s.id) }) { Text("Delete", color = BearRed) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSessions = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun ModelCard(ctl: AiTutorController) {
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = NavySurface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${ctl.model.name} (${ctl.model.model} ${ctl.model.quant})", color = Ink, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (ctl.modelState) {
                            ModelState.NOT_DOWNLOADED -> "Not downloaded"
                            ModelState.DOWNLOADING -> "Downloading…"
                            ModelState.PAUSED -> "Paused"
                            ModelState.DOWNLOADED -> "Downloaded — tap Load"
                            ModelState.LOADING -> "Loading…"
                            ModelState.READY -> "Ready"
                            ModelState.ERROR -> "Error"
                        },
                        color = if (ctl.modelState == ModelState.READY) BullGreen else Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Manage") }
            }

            if (ctl.progress != null && (ctl.modelState == ModelState.DOWNLOADING || ctl.modelState == ModelState.PAUSED)) {
                LinearProgressIndicator(progress = { ctl.progress!!.fraction }, modifier = Modifier.fillMaxWidth())
                Text(ctl.progress!!.label, color = Muted, style = MaterialTheme.typography.bodySmall)
            }

            if (expanded) {
                Text("Model", color = BronzeGold, style = MaterialTheme.typography.labelMedium)
                AI_MODELS.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = m.id == ctl.model.id, onClick = { ctl.selectModel(m) })
                        Column(Modifier.clickable { ctl.selectModel(m) }) {
                            Text("${m.name} (${m.model} ${m.quant})", color = Ink, style = MaterialTheme.typography.bodyMedium)
                            Text("${m.desc} · ${m.sizeLabel}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi only downloads", color = Ink, modifier = Modifier.weight(1f))
                    Switch(checked = ctl.wifiOnly, onCheckedChange = { ctl.toggleWifiOnly(it) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (ctl.modelState) {
                        ModelState.NOT_DOWNLOADED, ModelState.ERROR -> {
                            Button(onClick = { ctl.startDownload() }, enabled = ctl.deviceNote == null) { Text("Download") }
                        }
                        ModelState.DOWNLOADING -> {
                            OutlinedButton(onClick = { ctl.pauseDownload() }) { Text("Pause") }
                            TextButton(onClick = { ctl.cancelDownload() }) { Text("Cancel", color = BearRed) }
                        }
                        ModelState.PAUSED -> {
                            Button(onClick = { ctl.resumeDownload() }) { Text("Resume") }
                            TextButton(onClick = { ctl.cancelDownload() }) { Text("Cancel", color = BearRed) }
                        }
                        ModelState.DOWNLOADED -> {
                            Button(onClick = { ctl.loadModel() }, enabled = ctl.deviceNote == null) { Text("Load") }
                            TextButton(onClick = { ctl.deleteModel() }) { Text("Delete", color = BearRed) }
                        }
                        ModelState.LOADING -> {
                            Text("Loading model…", color = Muted)
                        }
                        ModelState.READY -> {
                            OutlinedButton(onClick = { ctl.unloadModel() }) { Text("Unload") }
                            TextButton(onClick = { ctl.deleteModel() }) { Text("Delete", color = BearRed) }
                        }
                    }
                }
                Text(
                    "Model file is stored privately on this phone and runs fully offline after download.",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
