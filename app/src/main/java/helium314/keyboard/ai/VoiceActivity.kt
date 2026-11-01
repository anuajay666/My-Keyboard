// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Voice typing for the keyboard's microphone key. Keeps listening until you tap Done, so pauses do not cut you off. */
class VoiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AiLog.installCrashHandler(this)
        AiModelManager.refresh(this)
        val lang = intent.getStringExtra("lang") ?: "en-IN"
        setContent { InklyTheme { VoiceScreen(lang, onClose = { finish() }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceScreen(initialLang: String, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val modelState by AiModelManager.state.collectAsState()
    var lang by remember { mutableStateOf(initialLang) }
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("Starting…") }
    var working by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var wantListening by remember { mutableStateOf(true) }
    var emptyRounds by remember { mutableStateOf(0) }
    var committed by remember { mutableStateOf("") } // finished sentences

    fun join(a: String, b: String) = if (a.isBlank()) b else if (b.isBlank()) a else a.trimEnd() + " " + b

    fun stop() {
        wantListening = false; listening = false
        try { recognizer?.stopListening() } catch (_: Throwable) {}
        try { recognizer?.cancel() } catch (_: Throwable) {}
    }

    lateinit var begin: () -> Unit
    begin = {
        try {
            recognizer?.destroy()
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                note = "This phone has no speech recognition service. Install or enable Google's speech services."
                wantListening = false
            } else {
                val r = SpeechRecognizer.createSpeechRecognizer(ctx)
                recognizer = r
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { listening = true; note = "Listening… speak now" }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        if (quiet && wantListening && emptyRounds < 4) { emptyRounds++; begin(); return }
                        listening = false; wantListening = false
                        note = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Did not hear anything. Tap Speak to try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off for Inkly."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "The speech service needs internet for this language."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Try again."
                            else -> "Voice error ($error)."
                        }
                        AiLog.add(ctx, "voice error $error")
                    }
                    override fun onResults(results: Bundle?) {
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (heard.isNotBlank()) { committed = join(committed, heard); text = committed; emptyRounds = 0 }
                        if (wantListening) begin() else listening = false
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (heard.isNotBlank()) text = join(committed, heard)
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                r.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
                )
            }
        } catch (e: Throwable) {
            listening = false; wantListening = false; note = "Voice typing could not start: ${e.message}"
            AiLog.add(ctx, "voice start failed: $e")
        }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { wantListening = true; begin() }
        else { note = "Allow the microphone for Inkly (phone Settings > Apps > Inkly > Permissions) to use voice typing."; wantListening = false }
    }
    fun speak() {
        committed = text
        emptyRounds = 0; wantListening = true
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) begin()
        else perm.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { speak() }
    DisposableEffect(Unit) { onDispose { wantListening = false; try { recognizer?.destroy() } catch (_: Throwable) {} } }

    fun insert(result: String) {
        AiBridge.setPending(ctx, AiPending(AiSession("", Scope.CURSOR), result, true))
        onClose()
    }

    fun cleanAndInsert() {
        stop(); working = true; note = "Cleaning up with AI…"
        val input = text
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val r = AiRunner.run(ctx, AiCatalog.cleanup, input) { p, total, _ -> note = if (total > 1) "Cleaning up… part $p of $total" else "Cleaning up…" }
                withContext(Dispatchers.Main) { insert(r.options.first()) }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { working = false; note = "AI cleanup failed (${e.message}). You can still insert the text as it is." }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { stop(); onClose() }) {
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Voice typing", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ml-IN" to "മലയാളം", "en-IN" to "English (India)", "en-US" to "English (US)", "ta-IN" to "தமிழ்", "hi-IN" to "हिन्दी").forEach { (tag, label) ->
                        FilterChip(selected = lang == tag, enabled = !working, onClick = {
                            if (lang != tag) { lang = tag; if (wantListening) { try { recognizer?.cancel() } catch (_: Throwable) {}; committed = text; begin() } }
                        }, label = { Text(label) })
                    }
                }
                OutlinedTextField(text, { text = it; committed = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
                    label = { Text("What I heard (you can edit it)") })
                Text(note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (listening || wantListening) Button(enabled = !working, onClick = { stop(); note = "Stopped" }) { Text("Stop") }
                    else OutlinedButton(enabled = !working, onClick = { speak() }) { Text("Speak") }
                    Button(enabled = text.isNotBlank() && !working, onClick = { stop(); insert(text) }) { Text("Insert") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = text.isNotBlank() && !working && modelState == ModelState.Ready, onClick = { cleanAndInsert() }) { Text("Clean up with AI + insert") }
                    TextButton(onClick = { stop(); onClose() }) { Text("Cancel") }
                }
                if (modelState != ModelState.Ready) Text("AI cleanup needs the AI model (set it up from any AI key).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
