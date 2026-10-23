// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AiLog.installCrashHandler(this)
        AiLog.add(this, "panel opened")
        AiModelManager.refresh(this)
        val session = AiBridge.readSession(this)
        val tab = intent.getIntExtra("tab", 0)
        val autorun = intent.getBooleanExtra("autorun", false)
        setContent { InklyTheme { AiScreen(session, tab, autorun, onClose = { finish() }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate() // fresh text and tab from the keyboard
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) AiRunner.cancelled = true
    }
}

@Composable
fun InklyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme: ColorScheme = if (dark) darkColorScheme(primary = Color(0xFF9DB4FF), onPrimary = Color(0xFF0B1B52), surface = Color(0xFF14161C), onSurface = Color(0xFFE6E8EF), surfaceVariant = Color(0xFF232733), onSurfaceVariant = Color(0xFFC3C7D4), secondaryContainer = Color(0xFF2B3A6B), onSecondaryContainer = Color(0xFFDCE3FF))
    else lightColorScheme(primary = Color(0xFF1E3A8A), onPrimary = Color.White, surface = Color(0xFFFAFAFD), onSurface = Color(0xFF14161C), surfaceVariant = Color(0xFFEBEEF7), onSurfaceVariant = Color(0xFF454A5C), secondaryContainer = Color(0xFFDCE3FF), onSecondaryContainer = Color(0xFF0B1B52))
    MaterialTheme(colorScheme = scheme, content = content)
}

private val tabs = listOf("Fix & polish", "Tone", "Assistants", "Translate", "Reply")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiScreen(session: AiSession, initialTab: Int, autorun: Boolean, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val modelState by AiModelManager.state.collectAsState()
    var text by remember { mutableStateOf(session.original) }
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, tabs.lastIndex)) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var options by remember { mutableStateOf<List<String>>(emptyList()) }
    var chosen by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastTasks by remember { mutableStateOf<List<AiTask>>(emptyList()) }
    var lastInput by remember { mutableStateOf("") }
    var customTones by remember { mutableStateOf(AiCustom.load(ctx, AiCustom.TONES)) }
    var customAssistants by remember { mutableStateOf(AiCustom.load(ctx, AiCustom.ASSISTANTS)) }
    var dialogFor by remember { mutableStateOf<String?>(null) }
    var voiceLang by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var listening by remember { mutableStateOf(false) }
    var voiceNote by remember { mutableStateOf<String?>(null) }
    var showLog by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    LaunchedEffect(Unit) {
        // empty field: offer the copied text (e.g. a message to reply to)
        if (session.original.isEmpty()) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = try { cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty() } catch (_: Throwable) { "" }
            if (clip.isNotBlank()) text = clip
        }
    }

    fun start(tasks: List<AiTask>, input: String) {
        AiLog.add(ctx, "start ${tasks.firstOrNull()?.id} on ${input.length} chars")
        if (input.isBlank()) { Toast.makeText(ctx, "Type or select some text first", Toast.LENGTH_SHORT).show(); return }
        lastTasks = tasks; lastInput = input
        running = true; error = null; options = emptyList(); notes = emptyList(); chosen = 0
        job = CoroutineScope(Dispatchers.Default).launch {
            try {
                val outs = mutableListOf<String>(); val ns = mutableListOf<String>()
                tasks.forEachIndexed { idx, t ->
                    val r = AiRunner.run(ctx, t, input) { p, total, _ ->
                        progress = if (total > 1) "Working… part $p of $total" else if (tasks.size > 1) "Writing option ${idx + 1} of ${tasks.size}…" else "Working…"
                    }
                    outs += r.options; ns += r.notes
                }
                withContext(Dispatchers.Main) { options = outs; notes = ns.distinct(); running = false }
            } catch (_: InterruptedException) {
                withContext(Dispatchers.Main) { running = false }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { error = e.message ?: e.toString(); running = false }
            }
        }
    }

    fun stopListening() { try { recognizer?.stopListening() } catch (_: Throwable) {}; listening = false }

    fun beginListening() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { voiceNote = "This phone has no speech recognition service available."; return }
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            recognizer = r
            val base = text
            r.setRecognitionListener(object : RecognitionListener {
                private fun join(spoken: String) = if (base.isBlank()) spoken else base.trimEnd() + " " + spoken
                override fun onReadyForSpeech(params: Bundle?) { voiceNote = "Listening… speak now" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { voiceNote = "Processing…" }
                override fun onError(error: Int) {
                    listening = false
                    voiceNote = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Did not hear anything. Tap Speak and try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off for Inkly."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service needs internet for this language."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy, try again."
                        else -> "Voice typing error ($error)."
                    }
                    AiLog.add(ctx, "voice error $error")
                }
                override fun onResults(results: Bundle?) {
                    listening = false; voiceNote = null
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) text = join(it) }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) text = join(it) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (voiceLang.isNotEmpty()) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLang)
            listening = true; voiceNote = "Starting…"
            r.startListening(i)
        } catch (e: Throwable) {
            listening = false; voiceNote = "Voice typing could not start: ${e.message}"
            AiLog.add(ctx, "voice start failed: $e")
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginListening() else voiceNote = "Allow the microphone for Inkly (phone Settings > Apps > Inkly > Permissions) to use voice typing."
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { try { recognizer?.destroy() } catch (_: Throwable) {} } }

    LaunchedEffect(modelState) {
        if (autorun && modelState == ModelState.Ready && text.isNotBlank() && lastTasks.isEmpty()) start(listOf(AiCatalog.fix), text)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) AiModelManager.importFrom(ctx, uri) }

    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }) {
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.92f).statusBarsPadding()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Inkly AI", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Close") }
                }

                if (modelState != ModelState.Ready) {
                    ModelSetup(modelState, onPick = { pick.launch(arrayOf("*/*")) })
                } else {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
                        label = { Text(when (session.scope) { Scope.SELECTION -> "Selected text"; Scope.ALL -> "Your text"; Scope.CURSOR -> "Type, paste or dictate here" }) },
                        supportingText = { Text("${text.length} characters") },
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.primaryClip?.getItemAt(0)?.text?.toString()?.let { text = it }
                        }) { Text("Paste copied") }
                        OutlinedButton(onClick = {
                            if (listening) stopListening()
                            else if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) beginListening()
                            else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                        }) { Text(if (listening) "Stop" else "Speak") }
                        FilterChip(selected = voiceLang == "", onClick = { voiceLang = "" }, label = { Text("Auto") })
                        FilterChip(selected = voiceLang == "ml-IN", onClick = { voiceLang = "ml-IN" }, label = { Text("മലയാളം") })
                        FilterChip(selected = voiceLang == "en-IN", onClick = { voiceLang = "en-IN" }, label = { Text("English") })
                    }

                    voiceNote?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                        tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                    }

                    val enabled = !running
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (tab) {
                            0 -> AiCatalog.polish.forEach { t -> FilterChip(selected = false, enabled = enabled, onClick = { start(listOf(t), text) }, label = { Text(t.label) }) }
                            1 -> {
                                AiCatalog.tones.forEach { t -> FilterChip(selected = false, enabled = enabled, onClick = { start(listOf(t), text) }, label = { Text(t.label) }) }
                                customTones.forEach { c -> FilterChip(selected = false, enabled = enabled, onClick = { start(listOf(AiCatalog.tone(c.name, c.instruction)), text) }, label = { Text("★ " + c.name) }) }
                                TextButton(onClick = { dialogFor = "tone" }) { Text("+ Custom tone") }
                            }
                            2 -> {
                                AiCatalog.assistants.forEach { t -> FilterChip(selected = false, enabled = enabled, onClick = { start(listOf(t), text) }, label = { Text(t.label) }) }
                                customAssistants.forEach { c -> FilterChip(selected = false, enabled = enabled, onClick = { start(listOf(AiTask("c_" + c.name, c.name, c.instruction, Kind.GENERATE, temperature = 0.6)), text) }, label = { Text("★ " + c.name) }) }
                                TextButton(onClick = { dialogFor = "assistant" }) { Text("+ My assistant") }
                            }
                            3 -> AiCatalog.languages.forEach { l -> FilterChip(selected = false, enabled = enabled, onClick = { start(listOf(AiCatalog.translate(l)), text) }, label = { Text(l) }) }
                            4 -> {
                                Text("Paste or type the message you received, then get three reply options.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                                Button(enabled = enabled, onClick = { start(AiCatalog.replyStyles, text) }) { Text("Suggest replies") }
                            }
                        }
                    }

                    if (running) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(progress, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = { AiRunner.cancelled = true; AiEngine.cancel() }) { Text("Stop") }
                        }
                    }
                    error?.let { Text("Something went wrong: $it", color = Color(0xFFD32F2F)) }

                    if (options.isNotEmpty()) {
                        val task = lastTasks.firstOrNull()
                        val rewrite = task != null && task.kind != Kind.GENERATE
                        if (rewrite) {
                            Text("Before", fontWeight = FontWeight.SemiBold)
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Text(lastInput, Modifier.padding(12.dp))
                            }
                        }
                        options.forEachIndexed { i, o ->
                            if (options.size > 1) Text(lastTasks.getOrNull(i)?.label ?: "Option ${i + 1}", fontWeight = FontWeight.SemiBold)
                            else Text("After", fontWeight = FontWeight.SemiBold)
                            val shown: AnnotatedString = if (rewrite && task?.kind == Kind.REWRITE) highlight(lastInput, o) else AnnotatedString(o)
                            Card(
                                Modifier.fillMaxWidth().clickable { chosen = i },
                                colors = CardDefaults.cardColors(containerColor = if (i == chosen && options.size > 1) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                            ) { Text(shown, Modifier.padding(12.dp)) }
                        }
                        if (rewrite) Text("Length: ${lastInput.length} → ${options[chosen].length} characters", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        notes.forEach { Text("• $it", fontSize = 12.sp, color = Color(0xFFB26A00)) }
                        val insertOnly = task?.insertOnly == true || session.scope == Scope.CURSOR
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                AiBridge.setPending(ctx, AiPending(session, options[chosen], insertOnly))
                                onClose()
                            }) { Text(if (insertOnly) "Insert" else "Replace") }
                            OutlinedButton(onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Inkly AI", options[chosen]))
                                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                            }) { Text("Copy") }
                            OutlinedButton(enabled = !running, onClick = { start(lastTasks, lastInput) }) { Text("Try again") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = { AiModelManager.delete(ctx) }) { Text("Remove AI model", fontSize = 12.sp) }
                        TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Hide diagnostics" else "Diagnostics", fontSize = 12.sp) }
                    }
                    if (showLog) {
                        Text(AiLog.read(ctx).ifBlank { "(empty)" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Inkly AI log", AiLog.read(ctx)))
                            Toast.makeText(ctx, "Log copied", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy log") }
                    }
                }
            }
        }
    }

    dialogFor?.let { kind ->
        var name by remember { mutableStateOf("") }
        var instr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogFor = null },
            title = { Text(if (kind == "tone") "New custom tone" else "New assistant") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(instr, { instr = it }, label = { Text(if (kind == "tone") "Describe the tone, e.g. 'warm but brief, like a close friend'" else "What should it do with your text?") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && instr.isNotBlank(), onClick = {
                    if (kind == "tone") { customTones = customTones + AiCustom.Item(name.trim(), "a tone that is: ${instr.trim()}"); AiCustom.save(ctx, AiCustom.TONES, customTones) }
                    else { customAssistants = customAssistants + AiCustom.Item(name.trim(), instr.trim()); AiCustom.save(ctx, AiCustom.ASSISTANTS, customAssistants) }
                    dialogFor = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { dialogFor = null }) { Text("Cancel") } },
        )
    }
}

private fun highlight(old: String, new: String): AnnotatedString {
    val ranges = TextDiff.changedRanges(old, new)
    if (ranges.isEmpty()) return AnnotatedString(new)
    return buildAnnotatedString {
        var pos = 0
        for (r in ranges) {
            if (r.first > pos) append(new.substring(pos, r.first))
            withStyle(SpanStyle(background = Color(0x5543A047))) { append(new.substring(r.first, r.last + 1)) }
            pos = r.last + 1
        }
        if (pos < new.length) append(new.substring(pos))
    }
}

@Composable
private fun ModelSetup(state: ModelState, onPick: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var url by remember { mutableStateOf(AiModelManager.url(ctx)) }
    var token by remember { mutableStateOf(AiModelManager.token(ctx)) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Set up the on-device AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Inkly runs Google's Gemma model on this phone, so your text never leaves it. The model is a one-time download of about 2.6 GB. Use Wi-Fi, keep the screen on, and keep this window open until it finishes.", fontSize = 14.sp)
            when (state) {
                is ModelState.Downloading -> {
                    val frac = if (state.total > 0) state.done.toFloat() / state.total else 0f
                    Text("%.2f GB of %.2f GB".format(state.done / 1e9, state.total.coerceAtLeast(0) / 1e9))
                    if (state.total > 0) LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = { AiModelManager.cancel() }) { Text("Pause") }
                }
                else -> {
                    if (state is ModelState.Failed) Text(state.message, color = Color(0xFFD32F2F), fontSize = 13.sp)
                    OutlinedTextField(url, { url = it }, label = { Text("Model download link") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    OutlinedTextField(token, { token = it }, label = { Text("Hugging Face token (only if the page asks for one)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { AiModelManager.save(ctx, url, token); AiModelManager.download(ctx) }) { Text("Download model") }
                    OutlinedButton(onClick = onPick) { Text("I already have the file – choose it") }
                }
            }
        }
    }
}
