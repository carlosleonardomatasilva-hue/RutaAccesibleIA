package com.example.acesssense

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.acesssense.ui.theme.AcessSenseTheme
import com.example.acesssense.ui.theme.Amber400
import com.example.acesssense.ui.theme.Amber50
import com.example.acesssense.ui.theme.Amber700
import com.example.acesssense.ui.theme.Blue600
import com.example.acesssense.ui.theme.Green400
import com.example.acesssense.ui.theme.Green50
import com.example.acesssense.ui.theme.Green600
import com.example.acesssense.ui.theme.Green700
import com.example.acesssense.ui.theme.Red400
import com.example.acesssense.ui.theme.Red50
import com.example.acesssense.ui.theme.Red700
import com.example.acesssense.ui.theme.Slate100
import com.example.acesssense.ui.theme.Slate300
import com.example.acesssense.ui.theme.Slate50
import com.example.acesssense.ui.theme.Slate700
import com.example.acesssense.ui.theme.Slate800
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantener la pantalla encendida durante los recorridos urbanos
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()

        setContent {
            AcessSenseTheme {
                RutaAccesibleScreen()
            }
        }
    }
}

enum class RiskLevel {
    NONE, LOW, MEDIUM, HIGH
}

data class AccessibilityEvaluation(
    val fullReport: String,
    val audioAlert: String,
    val riskLevel: RiskLevel
)

object UserPreferences {
    private const val PREFS_NAME = "ruta_accesible_prefs"
    private const val KEY_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_USER_NAME = "user_name"

    fun isFirstLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    fun getUserName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }

    fun saveUserName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }
}

fun isTalkBackEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    if (accessibilityManager != null && accessibilityManager.isEnabled) {
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
        return enabledServices.any { service ->
            service.id.contains("talkback", ignoreCase = true) ||
                service.id.contains("marvin", ignoreCase = true) ||
                service.id.contains("screenreader", ignoreCase = true)
        }
    }
    return false
}

fun isExitCommand(userSpeech: String): Boolean {
    val speech = userSpeech.lowercase(Locale.getDefault()).trim()
    val exitKeywords = listOf(
        "salir", "cerrar", "adiós", "adios", "hasta luego", "chao", "chau", "apagar",
        "cerrar aplicación", "cerrar la aplicación", "salir de la aplicación",
        "cerrar app", "salir de la app", "nos vemos"
    )
    return exitKeywords.any { speech.contains(it) }
}

class TtsHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    var isReady = mutableStateOf(false)
        private set

    private val readyDeferred = CompletableDeferred<Boolean>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.forLanguageTag("es-MX"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.forLanguageTag("es-ES")
            }

            tts?.setPitch(0.95f)
            tts?.setSpeechRate(1.15f)

            isReady.value = true
            readyDeferred.complete(true)
        } else {
            Log.e("TTS", "Inicialización de TextToSpeech fallida")
            readyDeferred.complete(false)
        }
    }

    suspend fun awaitReady(): Boolean {
        if (isReady.value) return true
        return try {
            withTimeout(3000L) {
                readyDeferred.await()
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun speakAndWait(text: String) {
        if (!awaitReady() || text.isBlank()) return
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val utteranceId = "Utterance_${System.currentTimeMillis()}"
                val listener = object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
                tts?.setOnUtteranceProgressListener(listener)
                val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (speakResult != TextToSpeech.SUCCESS) {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

class SttHelper(context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    suspend fun listenForSpeech(): String? = withContext(Dispatchers.Main) {
        val recognizer = speechRecognizer ?: return@withContext null

        suspendCancellableCoroutine { continuation ->
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
            }

            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.w("STT", "SpeechRecognizer error code: $error")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (continuation.isActive) {
                        continuation.resume(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

            recognizer.setRecognitionListener(listener)
            recognizer.startListening(intent)

            continuation.invokeOnCancellation {
                try {
                    recognizer.stopListening()
                } catch (e: Exception) {
                    Log.w("STT", "Error al detener SpeechRecognizer", e)
                }
            }
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w("STT", "Error destruyendo SpeechRecognizer", e)
        }
    }
}

fun Bitmap.scaleToMaxDimension(maxDimension: Int = 480): Bitmap {
    val width = this.width
    val height = this.height
    if (width <= maxDimension && height <= maxDimension) return this
    val ratio = width.toFloat() / height.toFloat()
    val targetWidth: Int
    val targetHeight: Int
    if (width >= height) {
        targetWidth = maxDimension
        targetHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
    } else {
        targetHeight = maxDimension
        targetWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
    }
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

fun extractCleanName(rawSpeech: String): String {
    var text = rawSpeech.trim()
    val lower = text.lowercase(Locale.getDefault())
    val prefixes = listOf("me llamo", "mi nombre es", "soy", "me dicen", "llámame")
    for (prefix in prefixes) {
        if (lower.startsWith(prefix)) {
            text = text.substring(prefix.length).trim()
            break
        }
    }
    return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        .ifBlank { "Viajero" }
}

private fun parseAccessibilityResponse(rawText: String): AccessibilityEvaluation {
    // La respuesta puede incluir encabezados Markdown como **3. Nivel de riesgo:**.
    val normalizedText = rawText.replace("**", "").trim()

    // Capturamos el valor que aparece DESPUÉS de la etiqueta de riesgo, no los
    // valores de ejemplo de "(Bajo/Medio/Alto)" que acompañan al encabezado.
    val riskPatterns = listOf(
        Regex(
            """(?im)^\s*3[.)]\s*nivel de riesgo(?:\s*\(\s*bajo\s*/\s*medio\s*/\s*alto\s*\))?\s*[:\-]?\s*(bajo|medio|alto)\b"""
        ),
        Regex(
            """(?im)^\s*(?:nivel de\s+)?riesgo(?:\s*\(\s*bajo\s*/\s*medio\s*/\s*alto\s*\))?\s*[:\-]\s*(bajo|medio|alto)\b"""
        )
    )
    val detectedRisk = riskPatterns.firstNotNullOfOrNull { pattern ->
        pattern.find(normalizedText)?.groupValues?.getOrNull(1)
    }
    val risk = when (detectedRisk?.lowercase(Locale.ROOT)) {
        "alto" -> RiskLevel.HIGH
        "medio" -> RiskLevel.MEDIUM
        "bajo" -> RiskLevel.LOW
        else -> RiskLevel.MEDIUM // Conserva el comportamiento predeterminado anterior.
    }

    // DOT_MATCHES_ALL permite capturar recomendaciones que ocupan varias líneas.
    val audioMatch = Regex(
        """(?ims)^\s*4[.)]\s*mensaje corto de advertencia auditiva\s*[:.\-]?\s*(.*?)(?=^\s*\d+[.)]\s|\z)"""
    ).find(normalizedText)

    val cleanAudio = audioMatch?.groupValues?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: normalizedText

    val riskPrefix = when (risk) {
        RiskLevel.HIGH -> "Precaución, riesgo alto. "
        RiskLevel.MEDIUM -> "Riesgo medio. "
        RiskLevel.LOW -> "Ruta segura, riesgo bajo. "
        RiskLevel.NONE -> ""
    }

    return AccessibilityEvaluation(
        fullReport = rawText,
        audioAlert = riskPrefix + cleanAudio,
        riskLevel = risk
    )
}

/**
 * Evalúa la accesibilidad urbana de una imagen capturada por CameraX utilizando
 * Google Gemini API (gemini-3.5-flash-lite) con el System Prompt y estructura oficial
 * del Caso de Estudio 2 (Hackatón UVM 2026).
 */
private suspend fun evaluarAccesibilidadUrbana(
    bitmap: Bitmap?,
    userVoiceContext: String,
    userName: String
): AccessibilityEvaluation {
    return withContext(Dispatchers.IO) {
        if (bitmap == null) {
            return@withContext AccessibilityEvaluation(
                fullReport = "No se pudo capturar la imagen de la cámara. Intenta de nuevo.",
                audioAlert = "No pude capturar la imagen. Por favor apunta la cámara al frente e intenta de nuevo.",
                riskLevel = RiskLevel.NONE
            )
        }

        try {
            val config = generationConfig {
                temperature = 0.2f
                maxOutputTokens = 260
            }

            // Paso 1 del Caso de Estudio: System Instructions de accesibilidad urbana y diseño universal
            val generativeModel = GenerativeModel(
                modelName = "gemini-3.5-flash-lite",
                apiKey = BuildConfig.GEMINI_API_KEY,
                generationConfig = config,
                systemInstruction = content {
                    text(
                        "Eres un experto en accesibilidad urbana y diseño universal. Analiza la " +
                            "imagen del entorno peatonal recibida e identifica: 1) Si hay accesibilidad " +
                            "para sillas de ruedas, 2) Obstáculos presentes (baches, autos mal estacionados, " +
                            "falta de rampa), 3) Un nivel de riesgo (Bajo, Medio, Alto) y 4) Una " +
                            "recomendación breve de navegación por audio."
                    )
                }
            )

            // Paso 2 del Caso de Estudio: Prompt estructurado
            val userContextNote = if (userVoiceContext.isNotBlank()) {
                "\nContexto dictado por el usuario ($userName): \"$userVoiceContext\"."
            } else {
                ""
            }

            val prompt = """
                Analiza esta infraestructura peatonal para una persona en silla de ruedas.$userContextNote
                Responde en formato estructurado:
                1. ¿Cuenta con rampa? (Sí/No)
                2. Obstáculos detectados.
                3. Nivel de riesgo (Bajo/Medio/Alto).
                4. Mensaje corto de advertencia auditiva.
            """.trimIndent()

            val inputContent = content {
                image(bitmap)
                text(prompt)
            }

            val response = generativeModel.generateContent(inputContent)
            val resultText = response.text?.trim()
                ?: "No se obtuvo diagnóstico del entorno."

            parseAccessibilityResponse(resultText)
        } catch (e: Exception) {
            Log.e("RutaAccesibleIA", "Error al evaluar accesibilidad con Gemini", e)
            AccessibilityEvaluation(
                fullReport = "Error en la evaluación de infraestructura: ${e.localizedMessage ?: "Error de red"}",
                audioAlert = "Disculpa $userName, ocurrió un problema de conexión al analizar la ruta.",
                riskLevel = RiskLevel.NONE
            )
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onPreviewViewCreated: (PreviewView) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                onPreviewViewCreated(this)
            }
        },
        modifier = modifier,
        update = { previewView ->
            onPreviewViewCreated(previewView)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (exc: Exception) {
                    Log.e("CameraPreview", "Error al vincular la cámara", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )

    DisposableEffect(lifecycleOwner) {
        onDispose {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            }, ContextCompat.getMainExecutor(context))
        }
    }
}

@Composable
fun RutaAccesibleScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var dynamicContent by remember { mutableStateOf("Iniciando RutaAccesible IA...") }
    var currentRisk by remember { mutableStateOf(RiskLevel.NONE) }
    var isAnalyzing by remember { mutableStateOf(false) }

    var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }

    val ttsHelper = remember(context) { TtsHelper(context) }
    val sttHelper = remember(context) { SttHelper(context) }

    DisposableEffect(Unit) {
        onDispose {
            ttsHelper.shutdown()
            sttHelper.destroy()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    // 1. Bienvenida accesible y solicitud de permisos
    LaunchedEffect(Unit) {
        ttsHelper.awaitReady()
        val isFirstLaunch = UserPreferences.isFirstLaunch(context)

        if (isFirstLaunch) {
            val talkBackNotice = if (isTalkBackEnabled(context)) {
                " Lector de pantalla TalkBack detectado."
            } else ""

            val welcomeSpeech = "Bienvenido a RutaAccesible IA, tu asistente urbano de movilidad segura.$talkBackNotice Necesito permisos de cámara y micrófono para detectar rampas y obstáculos en tu trayecto."
            dynamicContent = welcomeSpeech
            ttsHelper.speakAndWait(welcomeSpeech)

            if (!hasCameraPermission || !hasAudioPermission) {
                permissionsLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }
        } else if (!hasCameraPermission || !hasAudioPermission) {
            permissionsLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    val hasAllPermissions = hasCameraPermission && hasAudioPermission

    // Color dinámico del borde y panel según el nivel de riesgo detectado
    val borderColor by animateColorAsState(
        targetValue = when (currentRisk) {
            RiskLevel.HIGH -> Red400
            RiskLevel.MEDIUM -> Amber400
            RiskLevel.LOW -> Green400
            RiskLevel.NONE -> if (hasAllPermissions) Green400 else Slate300
        },
        animationSpec = tween(durationMillis = 300),
        label = "borderColor"
    )

    val panelBackgroundColor by animateColorAsState(
        targetValue = when (currentRisk) {
            RiskLevel.HIGH -> Red50
            RiskLevel.MEDIUM -> Amber50
            RiskLevel.LOW -> Green50
            RiskLevel.NONE -> Slate50
        },
        animationSpec = tween(durationMillis = 300),
        label = "panelBackgroundColor"
    )

    // Función reutilizable para evaluar el entorno (por voz o botón táctil)
    suspend fun runEvaluation(voiceContext: String, userName: String) {
        if (isAnalyzing) return
        isAnalyzing = true
        try {
            dynamicContent = "Analizando accesibilidad peatonal con Gemini 3.5 Flash Lite..."
            val frameBitmap = previewViewInstance?.bitmap?.scaleToMaxDimension(480)
            val evaluation = evaluarAccesibilidadUrbana(frameBitmap, voiceContext, userName)
            currentRisk = evaluation.riskLevel
            dynamicContent = evaluation.fullReport
            ttsHelper.speakAndWait(evaluation.audioAlert)
        } finally {
            isAnalyzing = false
        }
    }

    // 2. Flujo continuo manos libres una vez concedidos los permisos
    LaunchedEffect(hasAllPermissions) {
        if (!hasAllPermissions) return@LaunchedEffect

        ttsHelper.awaitReady()
        val isFirstLaunch = UserPreferences.isFirstLaunch(context)

        if (isFirstLaunch) {
            val namePrompt = "Por favor, dime tu nombre para personalizar tus alertas de navegación."
            dynamicContent = namePrompt
            ttsHelper.speakAndWait(namePrompt)

            var capturedName = ""
            while (capturedName.isBlank() && isActive) {
                dynamicContent = "Escuchando tu nombre..."
                val spokenText = sttHelper.listenForSpeech()
                if (!spokenText.isNullOrBlank()) {
                    capturedName = extractCleanName(spokenText)
                } else {
                    val retryPrompt = "No logré escucharte. Dime tu nombre para continuar."
                    dynamicContent = retryPrompt
                    ttsHelper.speakAndWait(retryPrompt)
                }
            }

            if (capturedName.isNotBlank()) {
                UserPreferences.saveUserName(context, capturedName)
                UserPreferences.setFirstLaunchCompleted(context)

                val readySpeech = "Listo $capturedName. Apunta la cámara al frente y di: evalúa la ruta, o presiona el botón inferior."
                dynamicContent = readySpeech
                ttsHelper.speakAndWait(readySpeech)
            }
        } else {
            val savedName = UserPreferences.getUserName(context).ifBlank { "Viajero" }
            val greeting = "Hola $savedName, RutaAccesible IA está lista. Di evalúa el camino o toca el botón para analizar el entorno."
            dynamicContent = greeting
            ttsHelper.speakAndWait(greeting)
        }

        val currentUserName = UserPreferences.getUserName(context).ifBlank { "Viajero" }

        while (isActive) {
            if (!isAnalyzing) {
                val userSpeech = sttHelper.listenForSpeech()
                if (!userSpeech.isNullOrBlank() && isActive) {
                    if (isExitCommand(userSpeech)) {
                        val exitSpeech = "Hasta luego $currentUserName, que tengas un trayecto seguro."
                        dynamicContent = exitSpeech
                        ttsHelper.speakAndWait(exitSpeech)
                        (context as? ComponentActivity)?.finishAndRemoveTask()
                        return@LaunchedEffect
                    }

                    // Cualquier consulta o reporte dictado por voz evalúa la imagen actual + el contexto hablado
                    runEvaluation(voiceContext = userSpeech, userName = currentUserName)
                } else {
                    delay(250L)
                }
            } else {
                delay(250L)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = Slate100
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Encabezado Institucional / Proyecto
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate800)
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "♿ RutaAccesible IA",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Asistente Urbano Multimodal • ODS 10 y 11",
                            color = Slate300,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Vista de Cámara en Tiempo Real (CameraX)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .border(
                                border = BorderStroke(3.dp, borderColor),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(Slate50, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPermission) {
                            CameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                onPreviewViewCreated = { view ->
                                    previewViewInstance = view
                                }
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Se requieren permisos de cámara y micrófono para evaluar la accesibilidad urbana",
                                    color = Slate700,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Button(
                                    onClick = {
                                        permissionsLauncher.launch(
                                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Green600)
                                ) {
                                    Text("Conceder Permisos", color = Color.White)
                                }
                            }
                        }
                    }

                    // Semáforo Visual de Nivel de Riesgo
                    if (currentRisk != RiskLevel.NONE) {
                        val (badgeText, badgeBg, badgeFg) = when (currentRisk) {
                            RiskLevel.HIGH -> Triple("⚠️ NIVEL DE RIESGO: ALTO", Red50, Red700)
                            RiskLevel.MEDIUM -> Triple("⚡ NIVEL DE RIESGO: MEDIO", Amber50, Amber700)
                            RiskLevel.LOW -> Triple("✅ NIVEL DE RIESGO: BAJO", Green50, Green700)
                            RiskLevel.NONE -> Triple("", Slate50, Slate700)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .background(badgeBg, RoundedCornerShape(8.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .padding(vertical = 6.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeFg,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Panel Inferior con Diagnóstico Estructurado + Botón Accesible
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(panelBackgroundColor)
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(135.dp)
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dynamicContent,
                                color = Slate800,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (hasAllPermissions) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val savedName = UserPreferences.getUserName(context).ifBlank { "Viajero" }
                                    coroutineScope.launch {
                                        runEvaluation(
                                            voiceContext = "Evaluación manual solicitada por botón",
                                            userName = savedName
                                        )
                                    }
                                },
                                enabled = !isAnalyzing,
                                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = if (isAnalyzing) "Analizando..." else "📷 Evaluar Entorno Peatonal",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
