package com.ganus.mobile

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.ganus.mobile.core.local.AlertaConAdjunto
import com.ganus.mobile.core.local.Adjunto
import com.ganus.mobile.core.local.CampoDinamico
import com.ganus.mobile.core.local.RepositorioLocalGanus
import com.ganus.mobile.core.network.AttachmentUploadUrlRequest
import com.ganus.mobile.core.network.AlertAttachmentUploadUrlRequest
import com.ganus.mobile.core.network.ConfirmAlertAttachmentRequest
import com.ganus.mobile.core.network.CreateAlertRequest
import com.ganus.mobile.core.network.CreateMilkingRequest
import com.ganus.mobile.core.network.mensajeParaUsuario
import com.ganus.mobile.core.network.ConfirmAttachmentRequest
import com.ganus.mobile.core.network.GanusApi
import com.ganus.mobile.core.network.PersistentAccessTokenProvider
import com.ganus.mobile.core.network.SaveTaskResponsesRequest
import com.ganus.mobile.core.network.uuidOrNull
import com.ganus.mobile.core.network.descargarTodasLasTareas
import com.ganus.mobile.core.network.descargarTodasLasVacas
import com.ganus.mobile.core.local.RepositorioLocalGanus.EvidenciaSeleccionada
import com.ganus.mobile.core.local.RegistroOrdenio
import com.ganus.mobile.core.local.RespuestaTarea
import com.ganus.mobile.core.local.Tarea
import com.ganus.mobile.core.local.Vaca
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class JourneyActivity : ComponentActivity() {
    private enum class EvidenceTarget {
        ALERT,
        TASK,
        MILKING,
    }

    private enum class Page {
        HOME,
        COW_SELECTION,
        VOICE_IDENTIFICATION,
        COW_IDENTIFIED,
        MILK_ENTRY,
        MILK_DETAILS,
        TASKS,
        TASK_DETAIL,
        TASK_COMPLETE,
        ALERTS,
        HISTORY,
        HISTORY_TASK_DETAIL,
        ACCOUNT,
    }

    private data class Cow(
        val number: String,
        val name: String,
        val emoji: String,
        val localId: String = "vaca-$number",
        val breed: String = "Holstein",
        val age: Int = 4,
    )

    private data class FarmTask(
        val title: String,
        val time: String,
        val description: String,
        val emoji: String,
        val dynamicExample: Boolean = false,
        val localTaskId: String? = null,
        val remoteTaskId: String? = null,
        val remoteStatus: String? = null,
        val type: String = "FORMULARIO",
        val cowRemoteIds: List<String> = emptyList(),
    )

    private data class HistoryItem(
        val timestamp: Long,
        val task: Tarea? = null,
        val milking: RegistroOrdenio? = null,
        val alert: AlertaConAdjunto? = null,
    )

    @Inject
    lateinit var repositorioLocal: RepositorioLocalGanus

    @Inject
    lateinit var ganusApi: GanusApi

    @Inject
    lateinit var accessTokenProvider: PersistentAccessTokenProvider

    @Inject
    @Named("storageUploadClient")
    lateinit var storageUploadClient: OkHttpClient

    private var cows = emptyList<Cow>()
    private val demoFarmTasks = listOf(
        FarmTask("Limpieza del área de ordeño", "07:00–07:30", "Limpiar el área de ordeño", "🧹"),
        FarmTask("Alimentación del lote de ordeño", "07:30–08:00", "Forraje y concentrado · 24 vacas", "🌿"),
        FarmTask("Revisión de terneras", "08:00–08:30", "Apetito, marcha y temperatura · 8 animales", "🐮"),
        FarmTask("Lavado de equipos de ordeño", "06:30–07:00", "María López · Guardada a las 07:00", "💧"),
        FarmTask("Revisión de bebederos", "06:15–06:30", "María López · Guardada a las 06:30", "〰️"),
        FarmTask("Inspección de vacas · lote norte", "08:30–09:00", "Ejemplo dinámico · 6 campos", "🐄", true),
        FarmTask("Registrar actividad de la finca", "09:00–09:30", "Ejemplo dinámico · 8 campos y adjuntos", "🧰", true),
        FarmTask("Revisar equipo de ordeño", "09:30–10:00", "Ejemplo dinámico · checklist", "⚙️", true),
    )
    private val farmTasks: List<FarmTask>
        get() {
            val userId = currentUserId
                ?: return demoFarmTasks.mapIndexed { index, task ->
                    task.copy(localTaskId = "tarea-demo-$index")
                }
            return allTasks.filter { it.identificadorAsignado == userId }.map { task ->
                FarmTask(
                    title = task.titulo ?: "Tarea asignada",
                    time = "Asignada",
                    description = "Tarea enviada desde la central de staging",
                    emoji = "☷",
                    localTaskId = task.identificadorLocal,
                    remoteTaskId = task.identificadorRemoto,
                    remoteStatus = task.estado,
                    type = task.tipo,
                    cowRemoteIds = Json.decodeFromString(task.vacasJson),
                )
            }
        }
    private val taskStatuses = mutableMapOf<Int, String>()
    private var farmAlerts = emptyList<AlertaConAdjunto>()
    private var savedAlertEvidence = emptyList<Adjunto>()
    private val taskResponses = mutableMapOf<Int, MutableMap<String, String>>()
    private var currentPage = Page.HOME
    private var selectedCow = Cow("", "Sin vaca seleccionada", "🐄", localId = "")
    private var milkAmount = "0"
    private var taskIndex = 0
    private var activeMilkingTaskIndex: Int? = null
    private var milkingTaskCowIds = emptySet<String>()
    private val milkingCompletedCowIds = mutableSetOf<String>()
    private var pendingDynamicFieldKey: String? = null
    private var taskStatus = "En curso"
    private lateinit var currentUser: String
    private var currentUserId: String? = null
    private var syncInProgress = false
    private var milkObservation = ""
    private var selectedFeeding = "Normal"
    private var selectedHealth = "Normal"
    private var selectedWeather = "Soleado"
    private var taskFilter = "Todas"
    private var alertTitle = ""
    private var alertDescription = ""
    private var alertPriority = "Media"
    private val alertEvidence = mutableListOf<EvidenciaSeleccionada>()
    private val milkEvidence = mutableListOf<EvidenciaSeleccionada>()
    private var taskEvidence = emptyList<Adjunto>()
    private var allTasks = emptyList<Tarea>()
    private var allTaskResponses = emptyList<RespuestaTarea>()
    private var allHistoryEvidence = emptyList<Adjunto>()
    private var allMilkingRecords = emptyList<RegistroOrdenio>()
    private var selectedHistoryTask: Tarea? = null
    private var currentTaskDynamicFields: List<CampoDinamico> = emptyList()
    private var currentContentScrollView: ScrollView? = null
    private var pendingCameraFile: File? = null
    private var pendingCameraUri: Uri? = null
    private var evidenceTarget = EvidenceTarget.ALERT
    private val evidencePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            guardarEvidenciasSeleccionadas(uris)
        } else {
            pendingDynamicFieldKey = null
        }
    }
    private val cameraCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val file = pendingCameraFile
        val uri = pendingCameraUri
        pendingCameraFile = null
        pendingCameraUri = null
        if (result.resultCode == RESULT_OK && file != null && uri != null && file.length() > 0L) {
            val evidencia = evidenciaDesdeUri(uri, conservarPermiso = false)
            guardarEvidenciasCapturadas(listOf(evidencia))
        } else {
            file?.delete()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentUser = intent.getStringExtra(EXTRA_CURRENT_USER)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "Usuario de demostración"
        currentUserId = intent.getStringExtra(EXTRA_CURRENT_USER_ID)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        GanusApplication.permitirCaptura(this)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.statusBarColor = getColor(R.color.primary_green_dark)
        window.navigationBarColor = getColor(R.color.surface)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars())
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        render()
        lifecycleScope.launch {
            try {
                if (currentUserId == null) repositorioLocal.inicializarDatosDemostracion()
                launch {
                    repositorioLocal.observarVacas().collect { vacas ->
                        cows = vacas
                            .filter { currentUserId == null || it.identificadorLocal.startsWith("remote-") }
                            .map { it.aModelo() }
                        selectedCow = cows.firstOrNull { it.localId == selectedCow.localId }
                            ?: cows.firstOrNull()
                            ?: Cow("", "Sin vaca seleccionada", "🐄", localId = "")
                        if (currentPage in setOf(Page.COW_SELECTION, Page.COW_IDENTIFIED)) render()
                    }
                }
                launch {
                    repositorioLocal.observarTareas().collect { tareas ->
                        allTasks = tareas
                        taskStatuses.clear()
                        farmTasks.forEachIndexed { index, tarea ->
                            val estado = tarea.remoteStatus ?: tarea.localTaskId
                                ?.removePrefix("tarea-demo-")
                                ?.toIntOrNull()
                                ?.let { demoIndex ->
                                    tareas.firstOrNull { it.identificadorLocal == "tarea-demo-$demoIndex" }?.estado
                                }
                                ?: "PENDIENTE"
                            taskStatuses[index] = when (estado) {
                                "COMPLETADA" -> "Completada"
                                "EN_CURSO" -> "En curso"
                                else -> "Pendiente"
                            }
                        }
                        if (currentPage == Page.TASKS || currentPage == Page.HOME) render()
                    }
                }
                launch {
                    repositorioLocal.observarRespuestasTarea().collect { respuestas ->
                        allTaskResponses = respuestas
                        if (currentPage == Page.HISTORY) render()
                    }
                }
                launch {
                    repositorioLocal.observarRegistrosOrdenio().collect { registros ->
                        allMilkingRecords = registros
                        if (currentPage == Page.HISTORY) render()
                    }
                }
                launch {
                    repositorioLocal.observarEvidenciasHistorial().collect { evidencias ->
                        allHistoryEvidence = evidencias
                        if (currentPage == Page.HISTORY) render()
                    }
                }
                launch {
                    repositorioLocal.observarAlertas().collect { alertas ->
                        farmAlerts = alertas.filter {
                            currentUserId == null || it.alerta.identificadorUsuario == currentUserId
                        }
                        if (currentPage == Page.ALERTS || currentPage == Page.HISTORY) render()
                    }
                }
                launch {
                    repositorioLocal.observarEvidenciasAlertas().collect { evidencias ->
                        savedAlertEvidence = evidencias
                        if (currentPage == Page.ALERTS || currentPage == Page.HISTORY) render()
                    }
                }
            } catch (exception: Exception) {
                Toast.makeText(
                    this@JourneyActivity,
                    "No se pudo inicializar el almacenamiento local: ${exception.localizedMessage ?: "error desconocido"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun Vaca.aModelo(): Cow = Cow(
        number = numero,
        name = nombre,
        emoji = if (numero.lastOrNull()?.digitToIntOrNull()?.rem(2) == 0) "🐮" else "🐄",
        localId = identificadorLocal,
        breed = raza,
        age = edadAnos ?: 0,
    )

    private fun cowsParaSeleccion(): List<Cow> {
        if (activeMilkingTaskIndex == null) return cows
        return cows.filter { cow ->
            cow.localId.removePrefix("remote-") in milkingTaskCowIds &&
                cow.localId.removePrefix("remote-") !in milkingCompletedCowIds
        }
    }

    override fun onBackPressed() {
        currentPage = when (currentPage) {
            Page.HOME -> {
                finish()
                return
            }
            Page.COW_SELECTION -> if (activeMilkingTaskIndex != null) {
                activeMilkingTaskIndex = null
                milkingTaskCowIds = emptySet()
                milkingCompletedCowIds.clear()
                Page.TASKS
            } else {
                Page.HOME
            }
            Page.VOICE_IDENTIFICATION -> Page.COW_SELECTION
            Page.COW_IDENTIFIED -> Page.COW_SELECTION
            Page.MILK_ENTRY -> Page.COW_IDENTIFIED
            Page.MILK_DETAILS -> Page.MILK_ENTRY
            Page.TASKS -> Page.HOME
            Page.TASK_DETAIL -> Page.TASKS
            Page.TASK_COMPLETE -> Page.TASKS
            Page.ALERTS -> Page.HOME
            Page.HISTORY -> Page.HOME
            Page.HISTORY_TASK_DETAIL -> Page.HISTORY
            Page.ACCOUNT -> Page.HOME
        }
        render()
    }

    @Deprecated("Deprecated in Android, retained for compatibility with the Activity-based app.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            REQUEST_SPEECH -> {
                val recognizedText = data
                    .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.trim()
                if (!recognizedText.isNullOrBlank()) {
                    alertDescription = listOf(alertDescription, recognizedText)
                        .filter(String::isNotBlank)
                        .joinToString(" ")
                    render()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        GanusApplication.permitirCaptura(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) GanusApplication.permitirCaptura(this)
    }

    private fun render() {
        val previousScrollPosition = currentContentScrollView?.scrollY ?: 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.screen_background))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        val isHome = currentPage == Page.HOME
        val title = when (currentPage) {
            Page.HOME -> "Ganus Mobile"
            Page.COW_SELECTION -> "Registro de ordeño"
            Page.VOICE_IDENTIFICATION -> "Identificar vaca"
            Page.COW_IDENTIFIED -> "Vaca identificada"
            Page.MILK_ENTRY -> "Registrar leche"
            Page.MILK_DETAILS -> "Información adicional"
            Page.TASKS -> "Tareas diarias"
            Page.TASK_DETAIL -> "Detalle de tarea"
            Page.TASK_COMPLETE -> "Resumen de tarea"
            Page.ALERTS -> "Alertas"
            Page.HISTORY -> "Historial"
            Page.HISTORY_TASK_DETAIL -> "Detalle de tarea"
            Page.ACCOUNT -> "Mi cuenta"
        }
        root.addView(toolbar(title, showBack = !isHome), LinearLayout.LayoutParams(-1, dp(56)))

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(18))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(22))
        }
        scrollView.addView(content)
        currentContentScrollView = scrollView
        root.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))

        when (currentPage) {
            Page.HOME -> showHome(content)
            Page.COW_SELECTION -> showCowSelection(content)
            Page.VOICE_IDENTIFICATION -> showVoiceIdentification(content)
            Page.COW_IDENTIFIED -> showCowIdentified(content)
            Page.MILK_ENTRY -> showMilkEntry(content)
            Page.MILK_DETAILS -> showMilkDetails(content)
            Page.TASKS -> showTasks(content)
            Page.TASK_DETAIL -> showTaskDetail(content)
            Page.TASK_COMPLETE -> showTaskComplete(content)
            Page.ALERTS -> showAlerts(content)
            Page.HISTORY -> showHistory(content)
            Page.HISTORY_TASK_DETAIL -> showHistoryTaskDetail(content)
            Page.ACCOUNT -> showAccount(content)
        }

        if (currentPage == Page.HOME ||
            currentPage == Page.COW_SELECTION ||
            currentPage == Page.TASKS ||
            currentPage == Page.HISTORY ||
            currentPage == Page.ACCOUNT
        ) {
            root.addView(bottomNavigation(), LinearLayout.LayoutParams(-1, dp(62)))
        }
        setContentView(root)
        scrollView.post {
            scrollView.scrollTo(0, previousScrollPosition.coerceAtMost(scrollView.getChildAt(0)?.height ?: 0))
        }
        GanusApplication.permitirCaptura(this)
        ViewCompat.requestApplyInsets(root)
    }

    private fun toolbar(title: String, showBack: Boolean): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(getColor(R.color.primary_green_dark))
        }
        if (showBack) {
            val back = ImageButton(this).apply {
                setImageResource(R.drawable.ic_task_back)
                contentDescription = "Volver"
                background = selectableBackground()
                setOnClickListener { onBackPressed() }
            }
            bar.addView(back, LinearLayout.LayoutParams(dp(40), dp(44)))
        } else if (currentPage != Page.HOME) {
            val brandIcon = text("♧", 24, Color.WHITE)
            bar.addView(brandIcon, LinearLayout.LayoutParams(dp(32), -2))
        }
        if (currentPage == Page.HOME) {
            bar.addView(
                ImageView(this).apply {
                    contentDescription = "GANUS"
                    setImageResource(R.drawable.brand_logo)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setColorFilter(Color.WHITE)
                },
                LinearLayout.LayoutParams(dp(76), dp(48)),
            )
        } else {
            bar.addView(
                text(title, 16, Color.WHITE, bold = true),
                LinearLayout.LayoutParams(0, -2, 1f),
            )
        }
        val help = ImageButton(this).apply {
            setImageResource(R.drawable.ic_task_help)
            contentDescription = "Ayuda"
            background = selectableBackground()
            setOnClickListener {
                Toast.makeText(this@JourneyActivity, "Consulta el detalle de cada tarea para ver sus instrucciones.", Toast.LENGTH_SHORT).show()
            }
        }
        bar.addView(help, LinearLayout.LayoutParams(dp(32), dp(44)))
        return bar
    }

    private fun showHome(content: LinearLayout) {
        content.addView(text("MARTES, 20 DE OCTUBRE DE 2026", 11, getColor(R.color.text_secondary)))
        content.addView(text("Buenos días, ${currentUser.substringBefore(' ')}", 25, getColor(R.color.text_primary), true), top(6))
        content.addView(
            text(
                if (currentUserId == null) "Vista de demostración · Jornada de la mañana"
                else "Sesión conectada · Jornada de la mañana",
                14,
                getColor(R.color.text_secondary),
            ),
            top(4),
        )
        content.addView(
            button("Alerta", red = true) {
                currentPage = Page.ALERTS
                render()
            },
            top(14),
        )

        val progress = card()
        val completedCount = taskStatuses.values.count { it == "Completada" }
        val totalTasks = farmTasks.size
        progress.addView(row(text("Tu jornada avanza", 14, getColor(R.color.text_primary), true), text("$completedCount de $totalTasks listas", 12, getColor(R.color.primary_green))))
        progress.addView(progressBar(completedCount.toFloat() / totalTasks.coerceAtLeast(1)), top(14))
        if (currentUserId == null) {
            progress.addView(text("◷  En curso: limpieza del área de ordeño", 12, getColor(R.color.text_secondary)), top(12))
        }
        content.addView(progress, top(14))

        content.addView(text("¿Qué vas a hacer?", 17, getColor(R.color.text_primary), true), top(20))
        content.addView(
            actionCard("▣", "Registrar ordeños", "Identifica la vaca y anota sus litros", true) {
                currentPage = Page.COW_SELECTION
                render()
            },
            top(10),
        )
        content.addView(
            actionCard("☷", "Tareas diarias", "Alimentación, revisión y limpieza", false) {
                currentPage = Page.TASKS
                render()
            },
            top(10),
        )
        if (currentUserId == null) {
            content.addView(
                text("Vista previa: datos de ejemplo, sin sincronización con una finca.", 12, getColor(R.color.text_secondary)),
                top(18),
            )
        }
    }

    private fun showCowSelection(content: LinearLayout) {
        val availableCows = cowsParaSeleccion()
        content.addView(stepIndicator(1))
        content.addView(
            text(
                if (activeMilkingTaskIndex == null) "¿Qué vaca vas a ordeñar?"
                else "Ordeño de tarea · ${availableCows.size} vacas pendientes",
                23,
                getColor(R.color.text_primary),
                true,
            ),
            top(16),
        )
        if (availableCows.isEmpty()) {
            content.addView(
                centered(
                    if (activeMilkingTaskIndex == null) {
                        "No hay vacas activas enviadas por una cuenta ADMIN. Sincroniza cuando se agreguen desde staging."
                    } else {
                        "No hay vacas pendientes asignadas a esta tarea. Sincroniza para actualizarla o vuelve a tus tareas."
                    },
                    14,
                    getColor(R.color.text_secondary),
                ),
                top(24),
            )
            if (activeMilkingTaskIndex != null) {
                content.addView(
                    button("Finalizar tarea de ordeño", filled = true) {
                        completarTareaOrdenio()
                    },
                    top(18),
                )
            }
            return
        }
        content.addView(text("Identifícala como te resulte más fácil.", 14, getColor(R.color.text_secondary)), top(4))
        val methods = row(
            button("🎙\nDecir número\n\nUsar mi voz", filled = true) {
                currentPage = Page.VOICE_IDENTIFICATION
                render()
            },
            button("▣\nEscanear tag\n\nUsar la cámara") {
                Toast.makeText(this, "El escaneo de tags requiere conectar la cámara.", Toast.LENGTH_SHORT).show()
            },
        )
        methods.getChildAt(0).layoutParams = LinearLayout.LayoutParams(0, dp(104), 1f).apply { marginEnd = dp(6) }
        methods.getChildAt(1).layoutParams = LinearLayout.LayoutParams(0, dp(104), 1f).apply { marginStart = dp(6) }
        content.addView(methods, top(16))
        content.addView(text("O selecciona en la lista", 13, getColor(R.color.text_primary)), top(18))
        content.addView(editField("Buscar por número o nombre"), top(8))
        content.addView(text("Vacas de la tarea · ${availableCows.size} animales", 15, getColor(R.color.text_primary), true), top(18))
        availableCows.chunked(2).forEach { pair ->
            val cowRow = row()
            pair.forEach { cow ->
                val item = cowCard(cow) {
                    seleccionarVaca(cow)
                    currentPage = Page.COW_IDENTIFIED
                    render()
                }
                cowRow.addView(item, LinearLayout.LayoutParams(0, dp(112), 1f).apply {
                    if (cowRow.childCount > 0) marginStart = dp(8)
                })
            }
            if (pair.size == 1) cowRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            content.addView(cowRow, top(10))
        }
        if (currentUserId == null) {
            content.addView(
                card().apply {
                    setBackgroundColor(getColor(R.color.soft_green))
                    addView(text("♧  Hoy: 186,5 L registrados · 18 de 24 vacas", 13, getColor(R.color.primary_green_dark)))
                },
                top(14),
            )
        }
    }

    private fun showVoiceIdentification(content: LinearLayout) {
        content.addView(stepIndicator(1))
        content.addView(text("Dime el número de la vaca", 22, getColor(R.color.text_primary), true), top(16))
        content.addView(text("Acerca el teléfono y habla con claridad.", 14, getColor(R.color.text_secondary)), top(4))
        val mic = text("🎙", 42, getColor(R.color.primary_green))
        mic.gravity = Gravity.CENTER
        mic.background = shape(getColor(R.color.soft_green), 200)
        content.addView(mic, LinearLayout.LayoutParams(dp(136), dp(136)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(34)
        })
        val listening = text("Escuchando…", 19, getColor(R.color.primary_green), true)
        listening.gravity = Gravity.CENTER
        content.addView(listening, top(18))
        content.addView(centered("La identificación por micrófono aún no está conectada.", 13, getColor(R.color.text_secondary)), top(6))
        content.addView(
            card().apply {
                gravity = Gravity.CENTER
                addView(centered("Vista de demostración", 13, getColor(R.color.text_secondary)))
                addView(centered("“Vaca 23”", 22, getColor(R.color.text_primary), true), top(6))
                addView(centered("Pulsa para continuar con la vaca de ejemplo.", 13, getColor(R.color.text_secondary)), top(6))
            },
            top(28),
        )
        content.addView(
            button("Simular identificación · Vaca 023", filled = true) {
                val availableCows = cowsParaSeleccion()
                val cow = availableCows.firstOrNull { it.number == "023" } ?: availableCows.firstOrNull()
                if (cow == null) {
                    Toast.makeText(this, "No hay vacas disponibles.", Toast.LENGTH_SHORT).show()
                    return@button
                }
                seleccionarVaca(cow)
                currentPage = Page.COW_IDENTIFIED
                render()
            },
            top(14),
        )
        content.addView(button("Cancelar") { currentPage = Page.COW_SELECTION; render() }, top(10))
    }

    private fun showCowIdentified(content: LinearLayout) {
        content.addView(stepIndicator(1))
        content.addView(text("Encontramos a ${selectedCow.name}", 22, getColor(R.color.text_primary), true), top(16))
        content.addView(
            text(
                if (activeMilkingTaskIndex == null) "Confirma que esta es la vaca correcta."
                else "Esta vaca está incluida en la tarea de ordeño asignada.",
                14,
                getColor(R.color.text_secondary),
            ),
            top(4),
        )
        val cowPhoto = centered(selectedCow.emoji, 68, getColor(R.color.primary_green))
        cowPhoto.gravity = Gravity.CENTER
        cowPhoto.background = shape(getColor(R.color.soft_green), 16)
        content.addView(cowPhoto, LinearLayout.LayoutParams(-1, dp(158)).apply { topMargin = dp(18) })
        val details = card()
        details.addView(text("Vaca ${selectedCow.number}", 21, getColor(R.color.text_primary), true))
        details.addView(text(selectedCow.name, 14, getColor(R.color.text_secondary)), top(4))
        details.addView(row(text("Raza", 13, getColor(R.color.text_secondary)), text(selectedCow.breed, 13, getColor(R.color.text_primary), true)), top(16))
        details.addView(row(text("Edad", 13, getColor(R.color.text_secondary)), text("${selectedCow.age} años", 13, getColor(R.color.text_primary), true)), top(10))
        details.addView(row(text("Último parto", 13, getColor(R.color.text_secondary)), text("12/03/2024", 13, getColor(R.color.text_primary), true)), top(10))
        details.addView(row(text("Último registro", 13, getColor(R.color.text_secondary)), text("9,0 L · ayer, mañana", 13, getColor(R.color.text_primary), true)), top(10))
        content.addView(details, top(14))
        content.addView(
            button("✓   ¿Es esta la vaca? Sí, continuar", filled = true) {
                currentPage = Page.MILK_ENTRY
                render()
            },
            top(16),
        )
        content.addView(button("No, buscar otra vaca") { currentPage = Page.COW_SELECTION; render() }, top(10))
    }

    private fun showMilkEntry(content: LinearLayout) {
        content.addView(stepIndicator(3))
        content.addView(cowSummary(), top(14))
        content.addView(text("¿Cuántos litros de leche dio?", 22, getColor(R.color.text_primary), true), top(22))
        val amount = TextView(this).apply {
            text = "$milkAmount L"
            textSize = 30f
            setTextColor(getColor(R.color.text_primary))
            gravity = Gravity.CENTER
            background = shape(Color.WHITE, 14, getColor(R.color.primary_green))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        content.addView(amount, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(12) })
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(",", "0", "⌫")).forEach { keys ->
            val keyRow = row()
            keys.forEach { key ->
                val keyButton = button(key) {
                    milkAmount = when (key) {
                        "⌫" -> milkAmount.dropLast(1).ifBlank { "0" }
                        "," -> if ('.' !in milkAmount) "$milkAmount." else milkAmount
                        else -> if (milkAmount == "0") key else milkAmount + key
                    }
                    render()
                }
                keyRow.addView(keyButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    if (keyRow.childCount > 0) marginStart = dp(6)
                })
            }
            content.addView(keyRow, top(6))
        }
        val increments = row(
            button("+ 0,5 L") { milkAmount = (milkAmount.toDoubleOrNull()?.plus(0.5) ?: 0.5).toString(); render() },
            button("+ 1 L") { milkAmount = (milkAmount.toDoubleOrNull()?.plus(1.0) ?: 1.0).toString(); render() },
        )
        increments.getChildAt(0).layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) }
        increments.getChildAt(1).layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(4) }
        content.addView(increments, top(8))
        content.addView(button("Siguiente  →", filled = true) {
            currentPage = Page.MILK_DETAILS
            render()
        }, top(16))
        content.addView(centered("Podrás revisar los datos antes de guardar.", 12, getColor(R.color.text_secondary)), top(8))
    }

    private fun showMilkDetails(content: LinearLayout) {
        content.addView(stepIndicator(4))
        content.addView(cowSummary(), top(14))
        content.addView(text("Un poco más sobre el ordeño", 21, getColor(R.color.text_primary), true), top(18))
        content.addView(text("Completa los datos de esta mañana.", 14, getColor(R.color.text_secondary)), top(4))
        content.addView(chipGroup("Alimentación", listOf("Mucho", "Normal", "Poco"), selectedFeeding) {
            selectedFeeding = it
            render()
        }, top(16))
        content.addView(chipGroup("Estado de salud", listOf("Normal", "Enferma"), selectedHealth) {
            selectedHealth = it
            render()
        }, top(14))
        content.addView(chipGroup("Clima", listOf("Soleado", "Nublado"), selectedWeather) {
            selectedWeather = it
            render()
        }, top(14))
        content.addView(text("Observaciones (opcional)", 13, getColor(R.color.text_primary), true), top(16))
        val notes = editField("Sin novedades en el ordeño").apply {
            minLines = 2
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(milkObservation)
            doAfterTextChanged { milkObservation = it?.toString().orEmpty() }
        }
        content.addView(notes, top(8))
        content.addView(iconButton("Adjuntar foto o video", R.drawable.ic_task_attachment) {
            abrirSelectorEvidencia(EvidenceTarget.MILKING)
        }, top(10))
        agregarEvidenciasSeleccionadas(content, milkEvidence)
        content.addView(iconButton("Decir por voz", R.drawable.ic_task_mic) {
            Toast.makeText(this, "El dictado por voz aún no está conectado.", Toast.LENGTH_SHORT).show()
        }, top(12))
        content.addView(text("◷  Hora del ordeño: 06:45 · Mañana", 12, getColor(R.color.text_secondary)), top(14))
        content.addView(button("Guardar registro · $milkAmount L", filled = true) {
            val litros = milkAmount.replace(',', '.').toDoubleOrNull()
            if (litros == null || litros < 0.0) {
                Toast.makeText(this, "Ingresa una cantidad válida de litros.", Toast.LENGTH_LONG).show()
                return@button
            }
            lifecycleScope.launch {
                try {
                    repositorioLocal.guardarRegistroOrdenio(
                        identificadorVaca = selectedCow.localId,
                        litros = litros,
                        alimentacion = selectedFeeding,
                        estadoSalud = selectedHealth,
                        clima = selectedWeather,
                        observaciones = milkObservation.trim().ifBlank { null },
                        evidencias = milkEvidence.toList(),
                        identificadorUsuario = currentUserId,
                        identificadorTarea = activeMilkingTaskIndex?.let { farmTasks[it].localTaskId },
                    )
                    milkAmount = "0"
                    milkObservation = ""
                    milkEvidence.clear()
                    selectedFeeding = "Normal"
                    selectedHealth = "Normal"
                    selectedWeather = "Soleado"
                    val activeTaskIndex = activeMilkingTaskIndex
                    if (activeTaskIndex == null) {
                        currentPage = Page.HOME
                        render()
                        Toast.makeText(this@JourneyActivity, "Registro guardado en el teléfono.", Toast.LENGTH_SHORT).show()
                    } else {
                        milkingCompletedCowIds += selectedCow.localId.removePrefix("remote-")
                        currentPage = Page.COW_SELECTION
                        render()
                        if (cowsParaSeleccion().isEmpty()) {
                            completarTareaOrdenio()
                        } else {
                            Toast.makeText(this@JourneyActivity, "Registro guardado. Continúa con la siguiente vaca asignada.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (exception: Exception) {
                    Toast.makeText(
                        this@JourneyActivity,
                        "No se pudo guardar el ordeño: ${exception.localizedMessage ?: "error desconocido"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }, top(14))
    }

    private fun showTasks(content: LinearLayout) {
        content.addView(text("Las labores de hoy", 21, getColor(R.color.text_primary), true))
        content.addView(
            text(
                if (currentUserId == null) "Martes 20 de octubre · Finca La Esperanza"
                else "Tareas asignadas desde staging",
                13,
                getColor(R.color.text_secondary),
            ),
            top(4),
        )
        content.addView(button("Alerta", red = true) {
            currentPage = Page.ALERTS
            render()
        }, top(12))
        val completed = taskStatuses.values.count { it == "Completada" }
        val progress = card().apply {
            setBackgroundColor(getColor(R.color.primary_green_dark))
            val totalTasks = farmTasks.size
            addView(row(
                text("$completed de $totalTasks completadas", 15, Color.WHITE, true),
                text("${completed * 100 / totalTasks.coerceAtLeast(1)}%", 15, Color.WHITE),
            ))
            addView(progressBar(completed.toFloat() / totalTasks.coerceAtLeast(1), light = true), top(10))
            val syncButton = button(
                if (syncInProgress) "Sincronizando..." else "Sincronizar",
                filled = true,
            ) {
                sincronizarDatos()
            }
            syncButton.isEnabled = !syncInProgress
            addView(syncButton, top(10))
        }
        content.addView(progress, top(12))
        val filters = row()
        listOf("Todas", "Pendientes", "En curso", "Completadas").forEach { filter ->
            val filterButton = button(filter, filled = taskFilter == filter) {
                taskFilter = filter
                render()
            }
            filters.addView(filterButton, LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                if (filters.childCount > 0) marginStart = dp(4)
            })
        }
        content.addView(filters, top(12))
        val visibleTasks = farmTasks.mapIndexedNotNull { index, task ->
            val status = taskStatuses[index] ?: "Pendiente"
            val matchesFilter = when (taskFilter) {
                "Pendientes" -> status == "Pendiente"
                "En curso" -> status == "En curso"
                "Completadas" -> status == "Completada"
                else -> true
            }
            if (matchesFilter) index to (task to status) else null
        }
        if (visibleTasks.isEmpty()) {
            content.addView(
                centered("No hay tareas en este estado.", 13, getColor(R.color.text_secondary)),
                top(20),
            )
        }
        visibleTasks.forEach { (index, taskAndStatus) ->
            val (task, status) = taskAndStatus
            content.addView(taskCard(task, status) {
                taskIndex = index
                taskStatus = status
                if (task.type == "ORDENIO") {
                    taskResponses[index] = mutableMapOf()
                    currentTaskDynamicFields = emptyList()
                    taskEvidence = emptyList()
                    if (activeMilkingTaskIndex != index) {
                        activeMilkingTaskIndex = index
                        milkingTaskCowIds = task.cowRemoteIds.toSet()
                        milkingCompletedCowIds.clear()
                        lifecycleScope.launch {
                            task.localTaskId?.let { localTaskId ->
                                repositorioLocal.obtenerVacasRegistradasParaTarea(localTaskId)
                                    .mapTo(milkingCompletedCowIds) { it.removePrefix("remote-") }
                            }
                            currentPage = Page.COW_SELECTION
                            render()
                        }
                    } else {
                        currentPage = Page.COW_SELECTION
                        render()
                    }
                    return@taskCard
                }
                currentPage = Page.TASK_DETAIL
                lifecycleScope.launch {
                    try {
                        val taskId = farmTasks[index].localTaskId ?: "tarea-demo-$index"
                        taskResponses[index] = repositorioLocal
                            .obtenerRespuestasTarea(taskId)
                            .toMutableMap()
                        currentTaskDynamicFields = repositorioLocal
                            .obtenerCamposDinamicos(taskId)
                        taskEvidence = repositorioLocal.obtenerEvidenciasTarea(taskId)
                        render()
                    } catch (exception: Exception) {
                        currentTaskDynamicFields = emptyList()
                        Toast.makeText(
                            this@JourneyActivity,
                            "No se pudieron cargar las respuestas locales: ${exception.localizedMessage ?: "error desconocido"}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }, top(8))
        }
    }

    private fun sincronizarDatos() {
        if (currentUserId == null) {
            Toast.makeText(this, "Inicia sesión para sincronizar con staging.", Toast.LENGTH_SHORT).show()
            return
        }
        if (syncInProgress) return
        syncInProgress = true
        render()
        lifecycleScope.launch {
            try {
                val tareas = ganusApi.descargarTodasLasTareas()
                val vacas = ganusApi.descargarTodasLasVacas()
                repositorioLocal.guardarTareasRemotas(tareas)
                repositorioLocal.guardarVacasRemotas(vacas)
                val alertasSincronizadas = sincronizarAlertasPendientes()
                val ordeniosSincronizados = sincronizarOrdeniosPendientes()
                Toast.makeText(
                    this@JourneyActivity,
                    "Datos sincronizados. Alertas: $alertasSincronizadas · Ordeños: $ordeniosSincronizados.",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Toast.makeText(
                    this@JourneyActivity,
                    "No se pudo sincronizar: ${exception.mensajeParaUsuario("verifica tu conexión")}",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                syncInProgress = false
                render()
            }
        }
    }

    private suspend fun sincronizarAlertasPendientes(): Int {
        val alertas = repositorioLocal.obtenerAlertasPendientesDeSincronizacion()
        alertas.forEach { alerta ->
            val alertId = alerta.identificadorRemoto ?: alerta.identificadorLocal
            if (alerta.identificadorRemoto == null) {
                ganusApi.createAlert(
                    CreateAlertRequest(
                        alertId = alertId,
                        titulo = alerta.titulo,
                        descripcion = alerta.descripcion,
                        prioridad = alerta.prioridad,
                        creadaEn = isoUtc(alerta.creadaEn),
                    ),
                )
                repositorioLocal.marcarAlertaSincronizada(
                    identificadorLocal = alerta.identificadorLocal,
                    identificadorRemoto = alertId,
                )
            }

            repositorioLocal.obtenerEvidenciasAlerta(alerta.identificadorLocal)
                .filter { it.estadoCarga != "SINCRONIZADO" }
                .forEach { evidencia ->
                    val bytes = leerBytesAdjunto(evidencia)
                    val mimeType = evidencia.tipoMime ?: "application/octet-stream"
                    val upload = ganusApi.createAlertAttachmentUploadUrl(
                        alertId = alertId,
                        request = AlertAttachmentUploadUrlRequest(
                            attachmentId = evidencia.identificadorLocal,
                            fileName = evidencia.nombreArchivo,
                            mimeType = mimeType,
                            sizeBytes = bytes.size.toLong(),
                        ),
                    ).data
                    subirArchivoFirmado(upload.uploadUrl, upload.headers, mimeType, bytes, evidencia.nombreArchivo)
                    ganusApi.confirmAlertAttachment(
                        alertId = alertId,
                        request = ConfirmAlertAttachmentRequest(
                            attachmentId = upload.attachmentId,
                            storagePath = upload.storagePath,
                            fileName = evidencia.nombreArchivo,
                            mimeType = mimeType,
                            sizeBytes = bytes.size.toLong(),
                        ),
                    )
                    repositorioLocal.marcarEvidenciaSincronizada(
                        identificadorLocal = evidencia.identificadorLocal,
                        identificadorRemoto = upload.attachmentId,
                    )
                }
        }
        return alertas.size
    }

    /** Hora en ISO 8601 UTC, como la pide la API (java.time exige Android 8; la app admite Android 7). */
    private fun isoUtc(milisegundos: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(milisegundos))

    /**
     * Envía los ordeños que el servidor no tiene y sus archivos. El id del registro es el UUID del teléfono, así
     * que un reintento no duplica. Se omiten los de la vista previa (vacas que no existen en el servidor).
     */
    private suspend fun sincronizarOrdeniosPendientes(): Int {
        var enviados = 0
        repositorioLocal.obtenerOrdeniosPendientesDeSincronizacion().forEach { registro ->
            val idVaca = registro.identificadorVaca.removePrefix("remote-").uuidOrNull() ?: return@forEach
            if (registro.identificadorRemoto == null) {
                val idTarea = registro.identificadorTareaOrdenio
                    ?.let { repositorioLocal.obtenerTareaLocal(it)?.identificadorRemoto }
                    .uuidOrNull()
                ganusApi.createMilking(
                    CreateMilkingRequest(
                        milkingId = registro.identificadorLocal,
                        cowId = idVaca,
                        taskId = idTarea,
                        litros = registro.litros,
                        alimentacion = registro.alimentacion,
                        estadoSalud = registro.estadoSalud,
                        clima = registro.clima,
                        turno = registro.turno,
                        observaciones = registro.observaciones,
                        registradoEn = isoUtc(registro.creadoEn),
                    ),
                )
                repositorioLocal.marcarOrdenioSincronizado(registro.identificadorLocal, registro.identificadorLocal)
                enviados++
            }
            repositorioLocal.obtenerEvidenciasOrdenio(registro.identificadorLocal)
                .filter { it.estadoCarga != "SINCRONIZADO" }
                .forEach { evidencia ->
                    val bytes = leerBytesAdjunto(evidencia)
                    val mimeType = evidencia.tipoMime ?: "application/octet-stream"
                    val upload = ganusApi.createMilkingAttachmentUploadUrl(
                        milkingId = registro.identificadorLocal,
                        request = AlertAttachmentUploadUrlRequest(
                            attachmentId = evidencia.identificadorLocal,
                            fileName = evidencia.nombreArchivo,
                            mimeType = mimeType,
                            sizeBytes = bytes.size.toLong(),
                        ),
                    ).data
                    subirArchivoFirmado(upload.uploadUrl, upload.headers, mimeType, bytes, evidencia.nombreArchivo)
                    ganusApi.confirmMilkingAttachment(
                        milkingId = registro.identificadorLocal,
                        request = ConfirmAlertAttachmentRequest(
                            attachmentId = upload.attachmentId,
                            storagePath = upload.storagePath,
                            fileName = evidencia.nombreArchivo,
                            mimeType = mimeType,
                            sizeBytes = bytes.size.toLong(),
                        ),
                    )
                    repositorioLocal.marcarEvidenciaSincronizada(
                        identificadorLocal = evidencia.identificadorLocal,
                        identificadorRemoto = upload.attachmentId,
                    )
                }
        }
        return enviados
    }

    private suspend fun leerBytesAdjunto(evidencia: Adjunto): ByteArray = withContext(Dispatchers.IO) {
        val input = contentResolver.openInputStream(Uri.parse(evidencia.uriLocal))
            ?: throw java.io.IOException("No se pudo leer ${evidencia.nombreArchivo}.")
        input.use { stream ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var totalBytes = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    if (totalBytes > MAX_ATTACHMENT_BYTES) {
                        throw IllegalArgumentException(
                            "La evidencia ${evidencia.nombreArchivo} supera el límite de 10 MiB.",
                        )
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    }

    private suspend fun subirArchivoFirmado(
        uploadUrl: String,
        headers: Map<String, String>,
        mimeType: String,
        bytes: ByteArray,
        fileName: String,
    ) {
        val requestBuilder = Request.Builder()
            .url(uploadUrl)
            .put(bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }
        withContext(Dispatchers.IO) {
            storageUploadClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw java.io.IOException("Storage rechazó $fileName (HTTP ${response.code}).")
                }
            }
        }
    }

    private fun showTaskDetail(content: LinearLayout) {
        val task = farmTasks[taskIndex]
        content.addView(text(task.title, 22, getColor(R.color.text_primary), true))
        content.addView(text("20 oct 2026 · ${task.time} · Mañana", 13, getColor(R.color.text_secondary)), top(5))
        if (taskStatus == "Completada") {
            showCompletedTaskChange(content)
            return
        }
        content.addView(text("Estado de la tarea", 13, getColor(R.color.text_primary), true), top(16))
        content.addView(chipGroup("Estado", listOf("Pendiente", "En curso", "Completada"), taskStatus) {
            taskStatus = it
            render()
        }, top(8))
        content.addView(text("Cambia el estado o guarda para continuar después.", 12, getColor(R.color.text_secondary)), top(8))
        if (currentTaskDynamicFields.isNotEmpty()) {
            renderDynamicTaskFields(content, currentTaskDynamicFields)
            return
        }

        val answers = taskResponses.getOrPut(taskIndex) { mutableMapOf() }
        content.addView(editField("Sala de ordeño").apply {
            setText(answers["area"].orEmpty())
            doAfterTextChanged { answers["area"] = it?.toString().orEmpty() }
        }, top(6))
        content.addView(text("Tarea a realizar", 13, getColor(R.color.text_primary), true), top(14))
        content.addView(editField(task.description).apply {
            setText(answers["accion"].orEmpty())
            doAfterTextChanged { answers["accion"] = it?.toString().orEmpty() }
        }, top(6))
        content.addView(text("Observaciones", 13, getColor(R.color.text_primary), true), top(14))
        val notes = editField("Se retiraron residuos y se dejó el piso sin charcos.").apply {
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(answers["observaciones"].orEmpty())
            doAfterTextChanged {
                answers["observaciones"] = it?.toString().orEmpty()
            }
        }
        content.addView(notes, top(6))
        content.addView(iconButton("Decir por voz", R.drawable.ic_task_mic) {
            Toast.makeText(this, "El dictado por voz aún no está conectado.", Toast.LENGTH_SHORT).show()
        }, top(10))
        content.addView(iconButton("Adjuntar evidencia", R.drawable.ic_task_attachment) {
            abrirSelectorEvidencia(EvidenceTarget.TASK)
        }, top(8))
        content.addView(text("Evidencias adjuntas", 13, getColor(R.color.text_primary), true), top(14))
        agregarEvidenciasAdjuntas(content, taskEvidence)
        content.addView(text("Responsable", 13, getColor(R.color.text_primary), true), top(14))
        content.addView(
            text(currentUser, 15, getColor(R.color.text_primary)).apply {
                background = shape(Color.WHITE, 10, getColor(R.color.border))
                setPadding(dp(12), dp(12), dp(12), dp(12))
            },
            top(6),
        )
        content.addView(text("◷  Avance guardado a las 07:24", 12, getColor(R.color.text_secondary)), top(12))
        content.addView(button("✓  Marcar como completada", filled = true) {
            taskStatus = "Completada"
            guardarTareaActual(estado = taskStatus, alGuardar = {
                currentPage = Page.TASK_COMPLETE
                render()
            })
        }, top(12))
        content.addView(button("▣  Guardar avance") {
            guardarTareaActual(estado = taskStatus, alGuardar = {
                currentPage = Page.TASKS
                render()
            })
        }, top(8))
    }

    private fun renderDynamicTaskFields(content: LinearLayout, fields: List<CampoDinamico>) {
        val answers = taskResponses.getOrPut(taskIndex) { mutableMapOf() }
        fields.filter { it.campo.visibilidad.uppercase() !in setOf("OCULTO", "HIDDEN", "FALSE") }
            .sortedBy { it.campo.orden }
            .forEach { field ->
            val key = field.campo.claveCampo
            val label = field.campo.etiqueta
            val required = field.campo.obligatorio
            val title = if (required) "$label *" else label
            answers.getOrPut(key) { field.campo.valorPredeterminado.orEmpty() }
            content.addView(text(title, 13, getColor(R.color.text_primary), true), top(14))
            field.campo.descripcion?.takeIf { it.isNotBlank() }?.let {
                content.addView(text(it, 12, getColor(R.color.text_secondary)), top(4))
            }
            val input = when (field.tipo.name) {
                "TEXTAREA" -> editField(field.campo.valorPredeterminado ?: "Escribe tu respuesta").apply {
                    minLines = 3
                    gravity = Gravity.TOP or Gravity.START
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    setText(answers[key].orEmpty())
                    doAfterTextChanged { answers[key] = it?.toString().orEmpty() }
                }
                "NUMBER" -> editField(field.campo.valorPredeterminado ?: "0").apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    setText(answers[key].orEmpty())
                    doAfterTextChanged { answers[key] = it?.toString().orEmpty() }
                }
                "DECIMAL" -> editField(field.campo.valorPredeterminado ?: "0.00").apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText(answers[key].orEmpty())
                    doAfterTextChanged { answers[key] = it?.toString().orEmpty() }
                }
                "DATE", "TIME", "DATETIME" -> button(
                    answers[key]?.takeIf(String::isNotBlank) ?: "Seleccionar ${field.tipo.name.lowercase()}",
                ) {
                    seleccionarFechaHora(field.tipo.name, answers[key].orEmpty()) { value ->
                        answers[key] = value
                        (content.findViewWithTag<Button>(key))?.text = value
                    }
                }.apply { tag = key }
                "BOOLEAN" -> {
                    val yesButton = button("Sí", filled = answers[key] == "true") { }
                    val noButton = button("No", filled = answers[key] == "false") { }
                    yesButton.setOnClickListener {
                        answers[key] = "true"
                        aplicarSeleccionBoton(yesButton, selected = true)
                        aplicarSeleccionBoton(noButton, selected = false)
                    }
                    noButton.setOnClickListener {
                        answers[key] = "false"
                        aplicarSeleccionBoton(yesButton, selected = false)
                        aplicarSeleccionBoton(noButton, selected = true)
                    }
                    row(yesButton, noButton)
                }
                "SELECT" -> {
                    val options = field.opciones
                    if (options.isEmpty()) {
                        editField(field.campo.valorPredeterminado ?: "Ingresar valor").apply {
                            setText(answers[key].orEmpty())
                            doAfterTextChanged { answers[key] = it?.toString().orEmpty() }
                        }
                    } else {
                        val selectedValue = answers[key] ?: field.campo.valorPredeterminado ?: options.firstOrNull().orEmpty()
                        val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                        val optionButtons = mutableListOf<Pair<String, Button>>()
                        options.forEach { option ->
                            val optionButton = button(option, filled = option == selectedValue) {
                                answers[key] = option
                                optionButtons.forEach { (value, item) ->
                                    aplicarSeleccionBoton(item, selected = value == option)
                                }
                            }
                            optionButtons += option to optionButton
                            group.addView(optionButton, top(6))
                        }
                        group
                    }
                }
                "MULTI_SELECT" -> {
                    val options = field.opciones
                    if (options.isEmpty()) {
                        editField(field.campo.valorPredeterminado ?: "Ingresar valores separados por coma").apply {
                            setText(answers[key].orEmpty())
                            doAfterTextChanged { answers[key] = it?.toString().orEmpty() }
                        }
                    } else {
                        val selectedValues = answers[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toMutableSet() ?: mutableSetOf()
                        val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                        val optionButtons = mutableListOf<Pair<String, Button>>()
                        options.forEach { option ->
                            val active = option in selectedValues
                            val optionButton = button(option, filled = active) {
                                if (option in selectedValues) selectedValues.remove(option) else selectedValues.add(option)
                                answers[key] = selectedValues.joinToString(", ")
                                optionButtons.forEach { (value, item) ->
                                    aplicarSeleccionBoton(item, selected = value in selectedValues)
                                }
                            }
                            optionButtons += option to optionButton
                            group.addView(optionButton, top(6))
                        }
                        group
                    }
                }
                "IMAGE", "FILE" -> button("Adjuntar ${field.tipo.name.lowercase()}", onClick = {
                    evidenceTarget = EvidenceTarget.TASK
                    pendingDynamicFieldKey = key
                    evidencePicker.launch(
                        if (field.tipo.name == "IMAGE") arrayOf("image/*") else arrayOf("*/*"),
                    )
                }).apply {
                    val attachedText = answers[key].orEmpty()
                    text = if (attachedText.isBlank()) "Adjuntar ${field.tipo.name.lowercase()}" else attachedText
                }
                else -> editField(field.campo.valorPredeterminado ?: "Ingresar valor").apply {
                    setText(answers[key].orEmpty())
                    doAfterTextChanged { answers[key] = it?.toString().orEmpty() }
                }
            }
            content.addView(input, top(6))
        }
        content.addView(iconButton("Decir por voz", R.drawable.ic_task_mic) {
            Toast.makeText(this, "El dictado por voz aún no está conectado.", Toast.LENGTH_SHORT).show()
        }, top(10))
        content.addView(iconButton("Adjuntar evidencia", R.drawable.ic_task_attachment) {
            abrirSelectorEvidencia(EvidenceTarget.TASK)
        }, top(8))
        content.addView(text("Evidencias adjuntas", 13, getColor(R.color.text_primary), true), top(14))
        agregarEvidenciasAdjuntas(content, taskEvidence)
        content.addView(text("Responsable", 13, getColor(R.color.text_primary), true), top(14))
        content.addView(
            text(currentUser, 15, getColor(R.color.text_primary)).apply {
                background = shape(Color.WHITE, 10, getColor(R.color.border))
                setPadding(dp(12), dp(12), dp(12), dp(12))
            },
            top(6),
        )
        content.addView(text("◷  Avance guardado a las 07:24", 12, getColor(R.color.text_secondary)), top(12))
        content.addView(button("✓  Marcar como completada", filled = true) {
            validarCamposDinamicos(fields, answers)?.let { validationError ->
                Toast.makeText(this, validationError, Toast.LENGTH_LONG).show()
                return@button
            }
            taskStatus = "Completada"
            guardarTareaActual(estado = taskStatus, alGuardar = {
                currentPage = Page.TASK_COMPLETE
                render()
            })
        }, top(12))
        content.addView(button("▣  Guardar avance") {
            guardarTareaActual(estado = taskStatus, alGuardar = {
                currentPage = Page.TASKS
                render()
            })
        }, top(8))
    }

    private fun seleccionarFechaHora(tipo: String, actual: String, onSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val parseFormat = when (tipo) {
            "DATE" -> SimpleDateFormat("yyyy-MM-dd", Locale.US)
            "TIME" -> SimpleDateFormat("HH:mm", Locale.US)
            else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        }.apply { isLenient = false }
        runCatching { parseFormat.parse(actual) }.getOrNull()?.let(calendar::setTime)

        fun mostrarHora() {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    val format = if (tipo == "DATETIME") "yyyy-MM-dd HH:mm" else "HH:mm"
                    onSelected(SimpleDateFormat(format, Locale.US).format(calendar.time))
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true,
            ).show()
        }

        if (tipo == "TIME") {
            mostrarHora()
            return
        }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                if (tipo == "DATETIME") {
                    mostrarHora()
                } else {
                    onSelected(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time))
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun validarCamposDinamicos(
        fields: List<CampoDinamico>,
        answers: Map<String, String>,
    ): String? {
        fields.filter { it.campo.visibilidad.uppercase() !in setOf("OCULTO", "HIDDEN", "FALSE") }
            .sortedBy { it.campo.orden }
            .forEach { field ->
                val value = answers[field.campo.claveCampo].orEmpty().trim()
                if (field.campo.obligatorio && value.isBlank()) {
                    return "Completa el campo obligatorio «${field.campo.etiqueta}»."
                }
                if (value.isBlank()) return@forEach
                val valid = when (field.tipo.name) {
                    "NUMBER" -> value.toLongOrNull()?.toDouble()?.let {
                        cumpleRango(it, field.campo.validacionesJson)
                    } == true
                    "DECIMAL" -> value.replace(',', '.').toDoubleOrNull()?.let {
                        cumpleRango(it, field.campo.validacionesJson)
                    } == true
                    "DATE" -> runCatching {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
                    }.getOrNull() != null
                    "TIME" -> runCatching {
                        SimpleDateFormat("HH:mm", Locale.US).apply { isLenient = false }.parse(value)
                    }.getOrNull() != null
                    "DATETIME" -> runCatching {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }.parse(value)
                    }.getOrNull() != null
                    "SELECT" -> field.opciones.isEmpty() || value in field.opciones
                    "MULTI_SELECT" -> field.opciones.isEmpty() || value.split(',').map(String::trim).all { it in field.opciones }
                    else -> true
                }
                if (!valid) return "Revisa el valor ingresado en «${field.campo.etiqueta}»."
            }
        return null
    }

    private fun cumpleRango(valor: Double, validacionesJson: String?): Boolean {
        if (validacionesJson.isNullOrBlank()) return true
        val validaciones = runCatching {
            Json.parseToJsonElement(validacionesJson).jsonObject
        }.getOrNull() ?: return true
        val minimo = (validaciones["min"] ?: validaciones["minimo"])
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toDoubleOrNull()
        val maximo = (validaciones["max"] ?: validaciones["maximo"])
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toDoubleOrNull()
        return (minimo == null || valor >= minimo) && (maximo == null || valor <= maximo)
    }

    private fun aplicarSeleccionBoton(boton: Button, selected: Boolean) {
        boton.background = shape(
            if (selected) getColor(R.color.primary_green) else Color.WHITE,
            12,
            if (selected) null else getColor(R.color.border),
        )
        boton.setTextColor(
            if (selected) Color.WHITE else getColor(R.color.primary_green),
        )
    }

    private fun showCompletedTaskChange(content: LinearLayout) {
        content.addView(button("Reintentar sincronización", filled = true) {
            guardarTareaActual(estado = "Completada", alGuardar = {
                currentPage = Page.TASKS
                render()
            })
        }, top(14))
        val answers = taskResponses.getOrPut(taskIndex) { mutableMapOf() }
        val dynamicObservationKey = currentTaskDynamicFields
            .firstOrNull { it.tipo.name == "TEXTAREA" }
            ?.campo
            ?.claveCampo
        if (currentTaskDynamicFields.isNotEmpty() && dynamicObservationKey == null) {
            content.addView(
                text("Esta tarea no tiene un campo de texto para registrar cambios adicionales.", 14, getColor(R.color.text_secondary)),
                top(16),
            )
            content.addView(button("Volver a tareas", filled = true) {
                currentPage = Page.TASKS
                render()
            }, top(18))
            return
        }
        val observationKey = dynamicObservationKey ?: "observaciones"
        content.addView(
            row(
                text("Estado", 13, getColor(R.color.text_secondary)),
                text("Completada", 14, getColor(R.color.primary_green), true),
            ),
            top(14),
        )
        content.addView(text("Observaciones anteriores", 13, getColor(R.color.text_primary), true), top(18))
        content.addView(
            text(answers[observationKey].orEmpty().ifBlank { "Sin observaciones anteriores." }, 14, getColor(R.color.text_secondary)),
            top(5),
        )
        content.addView(text("Añadir una observación", 13, getColor(R.color.text_primary), true), top(18))
        val changeInput = editField("Describe el cambio o seguimiento realizado.").apply {
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        content.addView(changeInput, top(6))
        content.addView(button("Añadir cambio", filled = true) {
            val change = changeInput.text.toString().trim()
            if (change.isBlank()) {
                changeInput.error = "Escribe la observación antes de guardarla."
                changeInput.requestFocus()
                return@button
            }
            val previousObservations = answers[observationKey].orEmpty().trim()
            val datedChange = "${formatoFechaHora(System.currentTimeMillis())}: $change"
            answers[observationKey] = listOf(previousObservations, datedChange)
                .filter(String::isNotBlank)
                .joinToString("\n")
            guardarTareaActual(estado = "Completada", alGuardar = {
                taskStatus = "Completada"
                render()
            })
        }, top(14))
    }

    private fun guardarTareaActual(estado: String, alGuardar: () -> Unit) {
        lifecycleScope.launch {
            try {
                val localTaskId = farmTasks[taskIndex].localTaskId ?: "tarea-demo-$taskIndex"
                val respuestas = taskResponses[taskIndex].orEmpty()
                repositorioLocal.guardarTarea(
                    identificadorTarea = localTaskId,
                    estado = estado,
                    respuestas = respuestas,
                )
                val tareaLocal = repositorioLocal.obtenerTareaLocal(localTaskId)
                val remoteTaskId = tareaLocal?.identificadorRemoto
                if (remoteTaskId != null) {
                    try {
                        // Una tarea de ordeño se termina cuando el servidor tiene el ordeño de todas sus vacas
                        if (farmTasks[taskIndex].type == "ORDENIO") sincronizarOrdeniosPendientes()
                        val uploadedAttachments = sincronizarEvidenciasTarea(
                            localTaskId = localTaskId,
                            remoteTaskId = remoteTaskId,
                        )
                        ganusApi.saveTaskResponses(
                            taskId = remoteTaskId,
                            request = SaveTaskResponsesRequest(
                                identificadorEvento = java.util.UUID.randomUUID().toString(),
                                capturadoEn = isoUtc(System.currentTimeMillis()),
                                estado = estado.uppercase().replace(' ', '_'),
                                respuestas = respuestas.mapNotNull { (key, value) ->
                                    value.takeIf(String::isNotBlank)?.let {
                                        key to respuestaJson(
                                            clave = key,
                                            valor = it,
                                            localTaskId = localTaskId,
                                            uploadedAttachments = uploadedAttachments,
                                        )
                                    }

                                }.toMap(),
                            ),
                        )
                        Toast.makeText(this@JourneyActivity, "Tarea guardada y sincronizada.", Toast.LENGTH_SHORT).show()
                    } catch (syncException: CancellationException) {
                        throw syncException
                    } catch (syncException: Exception) {
                        Toast.makeText(
                            this@JourneyActivity,
                            "Tarea guardada en el teléfono; no se pudo sincronizar: ${syncException.mensajeParaUsuario("sin conexión")}",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@launch
                    }
                } else {
                    Toast.makeText(this@JourneyActivity, "Tarea guardada en el teléfono.", Toast.LENGTH_SHORT).show()
                }
                alGuardar()
            } catch (exception: Exception) {
                Toast.makeText(
                    this@JourneyActivity,
                    "No se pudo guardar la tarea: ${exception.localizedMessage ?: "error desconocido"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    }

    private fun completarTareaOrdenio() {
        val index = activeMilkingTaskIndex ?: return
        if (milkingTaskCowIds.isEmpty() || milkingCompletedCowIds.intersect(milkingTaskCowIds).size != milkingTaskCowIds.size) {
            Toast.makeText(this, "Registra el ordeño de todas las vacas asignadas antes de finalizar.", Toast.LENGTH_LONG).show()
            return
        }
        taskIndex = index
        taskStatus = "Completada"
        guardarTareaActual(estado = "Completada") {
            activeMilkingTaskIndex = null
            milkingTaskCowIds = emptySet()
            milkingCompletedCowIds.clear()
            currentPage = Page.TASKS
            render()
        }
    }

    private suspend fun sincronizarEvidenciasTarea(
        localTaskId: String,
        remoteTaskId: String,
    ): Map<String, String> {
        val evidencias = repositorioLocal.obtenerEvidenciasTarea(localTaskId)
        val idsRemotos = mutableMapOf<String, String>()
        evidencias.forEach { evidencia ->
            val idRemotoExistente = evidencia.identificadorRemoto
                ?.takeIf { evidencia.estadoCarga == "SINCRONIZADO" }
            if (idRemotoExistente != null) {
                idsRemotos[evidencia.identificadorLocal] = idRemotoExistente
                return@forEach
            }

            val bytes = withContext(Dispatchers.IO) {
                val input = contentResolver.openInputStream(Uri.parse(evidencia.uriLocal))
                    ?: throw java.io.IOException("No se pudo leer ${evidencia.nombreArchivo}.")
                input.use { stream ->
                    ByteArrayOutputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var totalBytes = 0
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            if (totalBytes > MAX_ATTACHMENT_BYTES) {
                                throw IllegalArgumentException(
                                    "La evidencia ${evidencia.nombreArchivo} supera el límite de 10 MiB.",
                                )
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
            }
            require(bytes.isNotEmpty()) { "El archivo ${evidencia.nombreArchivo} está vacío." }
            val mimeType = evidencia.tipoMime ?: "application/octet-stream"
            val upload = ganusApi.createAttachmentUploadUrl(
                AttachmentUploadUrlRequest(
                    taskId = remoteTaskId,
                    attachmentId = evidencia.identificadorLocal,
                    fileName = evidencia.nombreArchivo,
                    mimeType = mimeType,
                    sizeBytes = bytes.size.toLong(),
                ),
            ).data
            require(upload.metodo.equals("PUT", ignoreCase = true)) {
                "El servicio devolvió un método de carga de archivo no compatible."
            }
            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val requestBuilder = Request.Builder()
                .url(upload.uploadUrl)
                .put(requestBody)
            upload.headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            withContext(Dispatchers.IO) {
                storageUploadClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw java.io.IOException("Storage rechazó ${evidencia.nombreArchivo} (HTTP ${response.code}).")
                    }
                }
            }
            ganusApi.confirmAttachment(
                ConfirmAttachmentRequest(
                    attachmentId = upload.attachmentId,
                    taskId = remoteTaskId,
                    storagePath = upload.storagePath,
                    fileName = evidencia.nombreArchivo,
                    mimeType = mimeType,
                    sizeBytes = bytes.size.toLong(),
                ),
            )
            repositorioLocal.marcarEvidenciaSincronizada(
                identificadorLocal = evidencia.identificadorLocal,
                identificadorRemoto = upload.attachmentId,
            )
            idsRemotos[evidencia.identificadorLocal] = upload.attachmentId
        }
        return idsRemotos
    }

    private suspend fun respuestaJson(
        clave: String,
        valor: String,
        localTaskId: String,
        uploadedAttachments: Map<String, String>,
    ): JsonElement {
        val tipo = currentTaskDynamicFields.firstOrNull { it.campo.claveCampo == clave }?.tipo?.name
        return when (tipo) {
            "NUMBER" -> valor.toLongOrNull()?.let(::JsonPrimitive)
                ?: valor.toDoubleOrNull()?.let(::JsonPrimitive)
                ?: JsonPrimitive(valor)
            "DECIMAL" -> valor.replace(',', '.').toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(valor)
            "BOOLEAN" -> JsonPrimitive(valor.equals("true", ignoreCase = true))
            "MULTI_SELECT" -> JsonArray(
                valor.split(',').map(String::trim).filter(String::isNotBlank).map(::JsonPrimitive),
            )
            "IMAGE", "FILE" -> {
                val adjuntos = repositorioLocal.obtenerEvidenciasTarea(localTaskId)
                val nombres = valor.split(',').map(String::trim).filter(String::isNotBlank)
                val identificadorAdjunto = nombres.firstNotNullOfOrNull { nombre ->
                    adjuntos.firstOrNull { it.nombreArchivo == nombre }
                        ?.let { uploadedAttachments[it.identificadorLocal] }
                } ?: throw IllegalStateException("Sincroniza primero el archivo del campo «$clave».")
                buildJsonObject { put("identificador_adjunto", identificadorAdjunto) }
            }
            else -> JsonPrimitive(valor)
        }
    }

    private fun abrirSelectorEvidencia(target: EvidenceTarget) {
        evidenceTarget = target
        AlertDialog.Builder(this)
            .setTitle("Agregar evidencia")
            .setItems(arrayOf("Elegir desde galería", "Tomar foto con cámara")) { _, choice ->
                when (choice) {
                    0 -> evidencePicker.launch(arrayOf("image/*", "video/*"))
                    1 -> abrirCamara()
                }
            }
            .show()
    }

    private fun abrirCamara() {
        val directory = File(filesDir, "evidencias")
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(this, "No se pudo preparar el almacenamiento para la foto.", Toast.LENGTH_LONG).show()
            return
        }
        val photo = File(
            directory,
            "foto_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg",
        )
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photo)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, "Foto de evidencia", uri)
        }
        pendingCameraFile = photo
        pendingCameraUri = uri
        try {
            cameraCapture.launch(cameraIntent)
        } catch (exception: ActivityNotFoundException) {
            pendingCameraFile = null
            pendingCameraUri = null
            photo.delete()
            Toast.makeText(this, "No se encontró una aplicación de cámara disponible.", Toast.LENGTH_LONG).show()
        }
    }

    private fun guardarEvidenciasSeleccionadas(uris: List<Uri>) {
        val selected = mutableListOf<EvidenciaSeleccionada>()
        try {
            uris.forEach { uri ->
                selected += evidenciaDesdeUri(uri, conservarPermiso = true)
            }
        } catch (exception: SecurityException) {
            Toast.makeText(
                this,
                "No se pudo conservar el acceso a una de las evidencias seleccionadas.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        guardarEvidenciasCapturadas(selected)
    }

    private fun evidenciaDesdeUri(uri: Uri, conservarPermiso: Boolean): EvidenciaSeleccionada {
        if (conservarPermiso) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        return EvidenciaSeleccionada(
            uri = uri.toString(),
            nombre = getDisplayName(uri),
            tipoMime = contentResolver.getType(uri) ?: if (uri.scheme == "content") "image/jpeg" else null,
            tamanoBytes = getFileSize(uri),
        )
    }

    private fun guardarEvidenciasCapturadas(selected: List<EvidenciaSeleccionada>) {
        when (evidenceTarget) {
            EvidenceTarget.ALERT -> alertEvidence.addAll(selected)
            EvidenceTarget.MILKING -> milkEvidence.addAll(selected)
            EvidenceTarget.TASK -> lifecycleScope.launch {
                try {
                    val taskId = farmTasks[taskIndex].localTaskId ?: "tarea-demo-$taskIndex"
                    repositorioLocal.guardarEvidenciasTarea(taskId, selected)
                    taskEvidence = repositorioLocal.obtenerEvidenciasTarea(taskId)
                    pendingDynamicFieldKey?.let { fieldKey ->
                        taskResponses.getOrPut(taskIndex) { mutableMapOf() }[fieldKey] =
                            selected.joinToString(", ") { it.nombre }
                    }
                    pendingDynamicFieldKey = null
                    render()
                    Toast.makeText(
                        this@JourneyActivity,
                        "Evidencia guardada en el detalle de la tarea.",
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (exception: Exception) {
                    pendingDynamicFieldKey = null
                    Toast.makeText(
                        this@JourneyActivity,
                        "No se pudo guardar la evidencia: ${exception.localizedMessage ?: "error desconocido"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
        if (evidenceTarget != EvidenceTarget.TASK) render()
    }

    private fun agregarEvidenciasSeleccionadas(
        content: LinearLayout,
        evidencias: List<EvidenciaSeleccionada>,
    ) {
        evidencias.forEach { evidencia ->
            content.addView(
                button("Ver ${evidencia.nombre}") {
                    abrirEvidencia(Uri.parse(evidencia.uri), evidencia.tipoMime)
                },
                top(6),
            )
        }
    }

    private fun agregarEvidenciasAdjuntas(content: LinearLayout, evidencias: List<Adjunto>) {
        if (evidencias.isEmpty()) {
            content.addView(text("Todavía no hay fotos o videos adjuntos.", 12, getColor(R.color.text_secondary)), top(4))
            return
        }
        evidencias.forEach { evidencia ->
            content.addView(
                button("Abrir ${evidencia.nombreArchivo}") {
                    abrirEvidencia(Uri.parse(evidencia.uriLocal), evidencia.tipoMime)
                },
                top(6),
            )
        }
    }

    private fun abrirEvidencia(uri: Uri, tipoMime: String?) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, tipoMime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(this, "No hay una aplicación para abrir esta foto o video.", Toast.LENGTH_LONG).show()
        } catch (exception: SecurityException) {
            Toast.makeText(this, "No se pudo acceder al archivo adjunto.", Toast.LENGTH_LONG).show()
        }
    }

    private fun seleccionarVaca(vaca: Cow) {
        selectedCow = vaca
        milkAmount = "0"
        milkObservation = ""
        milkEvidence.clear()
        selectedFeeding = "Normal"
        selectedHealth = "Normal"
        selectedWeather = "Soleado"
    }

    private fun showTaskComplete(content: LinearLayout) {
        val task = farmTasks[taskIndex]
        val completedBanner = card().apply {
            setBackgroundColor(getColor(R.color.soft_green))
            gravity = Gravity.CENTER
            addView(centered("✓", 30, Color.WHITE, true).apply {
                background = shape(getColor(R.color.primary_green), 60)
                setPadding(dp(10), dp(4), dp(10), dp(4))
            })
            addView(centered("¡Tarea completada!", 18, getColor(R.color.primary_green_dark), true), top(10))
            addView(centered("Registro guardado el 20 oct 2026 a las 07:30", 12, getColor(R.color.primary_green_dark)), top(4))
        }
        content.addView(completedBanner)
        content.addView(text(task.title, 21, getColor(R.color.text_primary), true), top(18))
        content.addView(text("20 oct 2026 · ${task.time} · Mañana", 13, getColor(R.color.text_secondary)), top(4))
        content.addView(row(text("Estado de la tarea", 13, getColor(R.color.text_secondary)), text("Completada", 13, getColor(R.color.primary_green), true)), top(14))
        val summary = card()
        summary.addView(text("Resumen de respuestas", 15, getColor(R.color.text_primary), true))
        if (currentTaskDynamicFields.isNotEmpty()) {
            val answers = taskResponses[taskIndex].orEmpty()
            currentTaskDynamicFields
                .filter { it.campo.visibilidad.uppercase() !in setOf("OCULTO", "HIDDEN", "FALSE") }
                .forEach { field ->
                    val value = answers[field.campo.claveCampo].orEmpty()
                    if (value.isNotBlank()) {
                        summary.addView(
                            row(
                                text(field.campo.etiqueta, 13, getColor(R.color.text_secondary)),
                                text(value, 13, getColor(R.color.text_primary), true),
                            ),
                            top(8),
                        )
                    }
                }
        } else {
            summary.addView(row(text("Área", 13, getColor(R.color.text_secondary)), text("Sala de ordeño", 13, getColor(R.color.text_primary), true)), top(12))
            summary.addView(row(text("Responsable", 13, getColor(R.color.text_secondary)), text(currentUser, 13, getColor(R.color.text_primary), true)), top(8))
            content.addView(text("Observaciones", 13, getColor(R.color.text_primary), true), top(14))
            content.addView(
                text(
                    taskResponses[taskIndex]?.get("observaciones").orEmpty().ifBlank { "Sin observaciones." },
                    14,
                    getColor(R.color.text_secondary),
                ),
                top(6),
            )
        }
        content.addView(summary, top(14))
        content.addView(button("Volver a tareas diarias", filled = true) {
            currentPage = Page.TASKS
            render()
        }, top(24))
        val completedCount = taskStatuses.values.count { it == "Completada" }
        content.addView(centered("Tu jornada: $completedCount de ${farmTasks.size} tareas completadas", 12, getColor(R.color.text_secondary)), top(8))
    }

    private fun showAlerts(content: LinearLayout) {
        content.addView(text("Crear una alerta", 22, getColor(R.color.text_primary), true))
        content.addView(
            text(
                "Registra una novedad o situación que necesite seguimiento en la finca.",
                14,
                getColor(R.color.text_secondary),
            ),
            top(6),
        )

        content.addView(text("Título de la alerta", 13, getColor(R.color.text_primary), true), top(20))
        val titleInput = editField("Ej. Vaca necesita revisión").apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(alertTitle)
            doAfterTextChanged { alertTitle = it?.toString().orEmpty() }
        }
        content.addView(titleInput, top(7))

        content.addView(
            chipGroup("Prioridad", listOf("Baja", "Media", "Alta"), alertPriority) {
                alertPriority = it
                render()
            },
            top(16),
        )

        content.addView(text("Descripción", 13, getColor(R.color.text_primary), true), top(16))
        val descriptionInput = editField("Describe qué ocurrió y qué seguimiento se necesita.").apply {
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(alertDescription)
            doAfterTextChanged { alertDescription = it?.toString().orEmpty() }
        }
        content.addView(descriptionInput, top(7))

        content.addView(iconButton("Decir observación", R.drawable.ic_task_mic) {
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Di la observación para la alerta")
            }
            try {
                startActivityForResult(speechIntent, REQUEST_SPEECH)
            } catch (exception: ActivityNotFoundException) {
                Toast.makeText(
                    this,
                    "No hay un servicio de reconocimiento de voz disponible en este dispositivo.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }, top(10))

        content.addView(iconButton("Adjuntar foto o video", R.drawable.ic_task_attachment) {
            abrirSelectorEvidencia(EvidenceTarget.ALERT)
        }, top(8))
        agregarEvidenciasSeleccionadas(content, alertEvidence)

        content.addView(
            button("Crear alerta", filled = true) {
                val title = alertTitle.trim()
                if (title.isBlank()) {
                    titleInput.error = "Escribe un título para la alerta"
                    titleInput.requestFocus()
                    return@button
                }

                lifecycleScope.launch {
                    try {
                        repositorioLocal.crearAlerta(
                            titulo = title,
                            descripcion = alertDescription.trim().ifBlank { "Sin descripción adicional." },
                            prioridad = alertPriority,
                            evidencias = alertEvidence.toList(),
                            identificadorUsuario = currentUserId,
                        )
                        alertTitle = ""
                        alertDescription = ""
                        alertPriority = "Media"
                        alertEvidence.clear()
                        render()
                        Toast.makeText(this@JourneyActivity, "Alerta guardada en el teléfono.", Toast.LENGTH_SHORT).show()
                    } catch (exception: Exception) {
                        Toast.makeText(
                            this@JourneyActivity,
                            "No se pudo guardar la alerta: ${exception.localizedMessage ?: "error desconocido"}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
            top(20),
        )
        content.addView(
            centered("Las alertas quedan guardadas en el teléfono y se enviarán a staging al sincronizar.", 12, getColor(R.color.text_secondary)),
            top(8),
        )

        if (farmAlerts.isNotEmpty()) {
            content.addView(text("Alertas creadas", 17, getColor(R.color.text_primary), true), top(26))
            farmAlerts.forEach { alert ->
                val alerta = alert.alerta
                val alertCard = card()
                val priorityColor = if (alerta.prioridad == "ALTA") {
                    Color.rgb(220, 48, 36)
                } else {
                    getColor(R.color.primary_green)
                }
                alertCard.addView(row(
                    text(alerta.titulo, 15, getColor(R.color.text_primary), true),
                    text(alerta.prioridad.lowercase().replaceFirstChar(Char::uppercase), 12, priorityColor, true),
                ))
                alertCard.addView(text(alerta.descripcion, 13, getColor(R.color.text_primary)), top(8))
                savedAlertEvidence
                    .filter { it.identificadorPropietario == alerta.identificadorLocal }
                    .forEach { evidencia ->
                        alertCard.addView(
                            button("Abrir ${evidencia.nombreArchivo}") {
                                abrirEvidencia(Uri.parse(evidencia.uriLocal), evidencia.tipoMime)
                            },
                            top(6),
                        )
                    }
                if (alert.nombreAdjunto != null && savedAlertEvidence.none {
                        it.identificadorPropietario == alerta.identificadorLocal
                    }
                ) {
                    alertCard.addView(
                        text("Adjunto: ${alert.nombreAdjunto}", 12, getColor(R.color.primary_green_dark)),
                        top(8),
                    )
                }
                content.addView(alertCard, top(10))
            }
        }
    }

    private fun showHistory(content: LinearLayout) {
        content.addView(
            text("Actividad finalizada", 22, getColor(R.color.text_primary), true),
        )
        content.addView(
            text("Tareas completadas y ordeños, ordenados por fecha y hora.", 14, getColor(R.color.text_secondary)),
            top(4),
        )

        val history = buildList {
            allTasks
                .filter {
                    it.estado == "COMPLETADA" &&
                        (currentUserId == null || it.identificadorAsignado == currentUserId)
                }
                .forEach { add(HistoryItem(timestamp = it.actualizadaEn, task = it)) }
            allMilkingRecords
                .filter { currentUserId == null || it.identificadorUsuario == currentUserId }
                .forEach {
                    add(HistoryItem(timestamp = it.creadoEn, milking = it))
                }
            farmAlerts
                .filter { currentUserId == null || it.alerta.identificadorUsuario == currentUserId }
                .forEach {
                    add(HistoryItem(timestamp = it.alerta.creadaEn, alert = it))
                }
        }.sortedByDescending(HistoryItem::timestamp)

        if (history.isEmpty()) {
            content.addView(
                centered("Aún no hay tareas completadas, ordeños ni alertas.", 14, getColor(R.color.text_secondary)),
                top(24),
            )
            return
        }

        history.forEach { item ->
            when {
                item.task != null -> agregarResumenTarea(content, item.task, item.timestamp)
                item.milking != null -> agregarResumenOrdenio(content, item.milking)
                item.alert != null -> agregarResumenAlerta(content, item.alert)
            }
        }
    }

    private fun agregarResumenAlerta(content: LinearLayout, item: AlertaConAdjunto) {
        val alerta = item.alerta
        val alertCard = card().apply {
            background = shape(Color.rgb(255, 239, 238), 14, Color.rgb(220, 48, 36))
        }
        alertCard.addView(row(
            text("⚠  Alerta · ${alerta.titulo}", 16, Color.rgb(170, 35, 29), true),
            text(formatoFechaHora(alerta.creadaEn), 12, Color.rgb(170, 35, 29), true),
        ))
        alertCard.addView(
            text("Prioridad ${alerta.prioridad.lowercase().replaceFirstChar(Char::uppercase)}", 13, Color.rgb(170, 35, 29), true),
            top(8),
        )
        alertCard.addView(text(alerta.descripcion, 14, getColor(R.color.text_primary)), top(6))
        savedAlertEvidence
            .filter { it.identificadorPropietario == alerta.identificadorLocal }
            .forEach { evidencia ->
                alertCard.addView(
                    button("Abrir ${evidencia.nombreArchivo}", red = true) {
                        abrirEvidencia(Uri.parse(evidencia.uriLocal), evidencia.tipoMime)
                    },
                    top(8),
                )
            }
        content.addView(alertCard, top(12))
    }

    private fun showAccount(content: LinearLayout) {
        val displayName = currentUser
            .substringBefore('@')
            .replace('.', ' ')
            .split(' ')
            .filter(String::isNotBlank)
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            .ifBlank { "Usuario de demostración" }

        content.addView(text("Datos de la cuenta", 22, getColor(R.color.text_primary), true))
        content.addView(
            card().apply {
                addView(text("Nombre", 12, getColor(R.color.text_secondary), true))
                addView(text(displayName, 17, getColor(R.color.text_primary), true), top(4))
                addView(text("Correo electrónico", 12, getColor(R.color.text_secondary), true), top(14))
                addView(text(currentUser, 15, getColor(R.color.text_primary)), top(4))
                addView(text("Rol", 12, getColor(R.color.text_secondary), true), top(14))
                addView(
                    text("Operador", 15, getColor(R.color.primary_green_dark), true).apply {
                        background = shape(getColor(R.color.soft_green), 12)
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                    },
                    top(4),
                )
            },
            top(16),
        )
        content.addView(
            button("Cerrar sesión", red = true) {
                AlertDialog.Builder(this)
                    .setTitle("Cerrar sesión")
                    .setMessage("¿Quieres salir de esta sesión?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Cerrar sesión") { _, _ ->
                        accessTokenProvider.clearSession()
                        finish()
                    }
                    .show()
            },
            top(22),
        )
        content.addView(
            centered(
                if (currentUserId == null) {
                    "Estos datos corresponden a la vista previa."
                } else {
                    "La sesión permanecerá activa en este dispositivo hasta que cierres sesión manualmente."
                },
                12,
                getColor(R.color.text_secondary),
            ),
            top(10),
        )
    }

    private fun agregarResumenTarea(content: LinearLayout, task: Tarea, timestamp: Long) {
        val answers = allTaskResponses
            .filter { it.identificadorTarea == task.identificadorLocal }
            .associate { it.claveCampo to it.valorTexto.orEmpty() }
        val taskIndex = task.identificadorLocal
            .removePrefix("tarea-demo-")
            .toIntOrNull()
        val title = taskIndex?.let { farmTasks.getOrNull(it)?.title }
            ?: answers["accion"]?.takeIf(String::isNotBlank)
            ?: "Tarea completada"
        val card = card().apply {
            isClickable = true
            setOnClickListener {
                selectedHistoryTask = task
                currentPage = Page.HISTORY_TASK_DETAIL
                render()
            }
        }
        card.addView(row(
            text(title, 16, getColor(R.color.text_primary), true),
            text(formatoFechaHora(timestamp), 12, getColor(R.color.primary_green_dark), true),
        ))
        card.addView(text("✓  Completada · ${task.identificadorLocal}", 12, getColor(R.color.primary_green), true), top(8))
        card.addView(text("Toca para ver el detalle completo", 12, getColor(R.color.primary_green_dark)), top(4))
        answers.forEach { (key, value) ->
            if (value.isNotBlank()) {
                card.addView(
                    text("${key.replace('_', ' ').replaceFirstChar(Char::uppercase)}: $value", 13, getColor(R.color.text_secondary)),
                    top(6),
                )
            }
        }
        val evidence = allHistoryEvidence.filter {
            it.tipoPropietario == "TAREA" && it.identificadorPropietario == task.identificadorLocal
        }
        agregarGaleriaEvidencias(card, evidence)
        content.addView(card, top(12))
    }

    private fun showHistoryTaskDetail(content: LinearLayout) {
        val task = selectedHistoryTask
        if (task == null) {
            content.addView(text("No se encontró la tarea seleccionada.", 14, getColor(R.color.text_secondary)))
            content.addView(button("Volver al historial", filled = true) {
                currentPage = Page.HISTORY
                render()
            }, top(16))
            return
        }

        val answers = allTaskResponses
            .filter { it.identificadorTarea == task.identificadorLocal }
            .associate { it.claveCampo to it.valorTexto.orEmpty() }
        val taskIndex = task.identificadorLocal
            .removePrefix("tarea-demo-")
            .toIntOrNull()
        val title = taskIndex?.let { farmTasks.getOrNull(it)?.title }
            ?: answers["accion"]?.takeIf(String::isNotBlank)
            ?: "Tarea completada"

        content.addView(text(title, 22, getColor(R.color.text_primary), true))
        content.addView(text("Resumen de tarea completada", 14, getColor(R.color.primary_green)), top(4))
        content.addView(row(
            text("Estado", 13, getColor(R.color.text_secondary)),
            text("Completada", 14, getColor(R.color.primary_green), true),
        ), top(14))
        content.addView(row(
            text("Fecha y hora", 13, getColor(R.color.text_secondary)),
            text(formatoFechaHora(task.actualizadaEn), 13, getColor(R.color.text_primary), true),
        ), top(8))

        answers.forEach { (key, value) ->
            if (value.isNotBlank()) {
                val label = when (key) {
                    "area" -> "Área de trabajo"
                    "accion" -> "Tarea realizada"
                    "observaciones" -> "Observaciones"
                    "responsable" -> "Responsable"
                    else -> key.replace('_', ' ').replaceFirstChar(Char::uppercase)
                }
                content.addView(text(label, 13, getColor(R.color.text_primary), true), top(14))
                content.addView(text(value, 14, getColor(R.color.text_secondary)), top(4))
            }
        }

        val evidence = allHistoryEvidence.filter {
            it.tipoPropietario == "TAREA" && it.identificadorPropietario == task.identificadorLocal
        }
        content.addView(text("Evidencias adjuntas", 16, getColor(R.color.text_primary), true), top(20))
        if (evidence.isEmpty()) {
            content.addView(text("Esta tarea no tiene evidencias adjuntas.", 13, getColor(R.color.text_secondary)), top(6))
        } else {
            agregarGaleriaEvidencias(content, evidence)
        }
        content.addView(button("Volver al historial", filled = true) {
            currentPage = Page.HISTORY
            render()
        }, top(24))
    }

    private fun agregarResumenOrdenio(content: LinearLayout, milking: RegistroOrdenio) {
        val cow = cows.firstOrNull { it.localId == milking.identificadorVaca }
        val card = card()
        card.addView(row(
            text("Ordeño · ${cow?.let { "Vaca ${it.number} · ${it.name}" } ?: milking.identificadorVaca}", 16, getColor(R.color.text_primary), true),
            text(formatoFechaHora(milking.creadoEn), 12, getColor(R.color.primary_green_dark), true),
        ))
        card.addView(text("${milking.litros} L · Turno ${milking.turno.lowercase().replaceFirstChar(Char::uppercase)}", 14, getColor(R.color.primary_green), true), top(8))
        card.addView(text("Alimentación: ${milking.alimentacion} · Salud: ${milking.estadoSalud} · Clima: ${milking.clima}", 13, getColor(R.color.text_secondary)), top(6))
        milking.observaciones?.takeIf(String::isNotBlank)?.let {
            card.addView(text("Observaciones: $it", 13, getColor(R.color.text_secondary)), top(6))
        }
        agregarGaleriaEvidencias(
            card,
            allHistoryEvidence.filter {
                it.tipoPropietario == "ORDENIO" && it.identificadorPropietario == milking.identificadorLocal
            },
        )
        content.addView(card, top(12))
    }

    private fun agregarGaleriaEvidencias(card: LinearLayout, evidencias: List<Adjunto>) {
        if (evidencias.isEmpty()) return
        card.addView(text("Evidencias", 13, getColor(R.color.text_primary), true), top(10))
        evidencias.forEach { evidencia ->
            if (evidencia.tipoMime?.startsWith("image/") == true) {
                val image = ImageView(this).apply {
                    contentDescription = evidencia.nombreArchivo
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = shape(getColor(R.color.soft_green), 10)
                    setOnClickListener {
                        abrirEvidencia(Uri.parse(evidencia.uriLocal), evidencia.tipoMime)
                    }
                }
                try {
                    contentResolver.openInputStream(Uri.parse(evidencia.uriLocal))?.use { input ->
                        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                        image.setImageBitmap(BitmapFactory.decodeStream(input, null, options))
                    }
                } catch (exception: Exception) {
                    Toast.makeText(
                        this,
                        "No se pudo cargar la vista previa de ${evidencia.nombreArchivo}.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                card.addView(image, LinearLayout.LayoutParams(-1, dp(180)).apply {
                    topMargin = dp(8)
                })
            } else {
                card.addView(
                    button("▶  Abrir video: ${evidencia.nombreArchivo}") {
                        abrirEvidencia(Uri.parse(evidencia.uriLocal), evidencia.tipoMime)
                    },
                    top(8),
                )
            }
        }
    }

    private fun formatoFechaHora(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "CO")).format(Date(timestamp))

    private fun stepIndicator(step: Int): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(row(text("REGISTRO DE LECHE", 10, getColor(R.color.text_secondary)), text("$step de 4", 11, getColor(R.color.primary_green), true)))
        val bars = row()
        repeat(4) { index ->
            bars.addView(View(this).apply {
                background = shape(
                    if (index < step) getColor(R.color.primary_green) else getColor(R.color.border),
                    6,
                )
            }, LinearLayout.LayoutParams(0, dp(4), 1f).apply {
                if (index > 0) marginStart = dp(4)
            })
        }
        box.addView(bars, top(8))
        return box
    }

    private fun cowSummary(): View = card().apply {
        setBackgroundColor(getColor(R.color.soft_green))
        addView(row(text("◇  Vaca ${selectedCow.number} · ${selectedCow.name}", 14, getColor(R.color.text_primary), true), text("✓", 16, getColor(R.color.primary_green))))
        addView(text("Tag 023 · Holstein", 12, getColor(R.color.text_secondary)), top(4))
    }

    private fun cowCard(cow: Cow, onClick: () -> Unit): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.WHITE, 12, getColor(R.color.border))
            clipToOutline = true
            setOnClickListener { onClick() }
        }
        val animal = centered(cow.emoji, 28, getColor(R.color.primary_green))
        animal.gravity = Gravity.CENTER
        animal.setBackgroundColor(getColor(R.color.soft_green))
        item.addView(animal, LinearLayout.LayoutParams(-1, dp(58)))
        item.addView(text("Vaca ${cow.number}", 12, getColor(R.color.text_primary), true).apply {
            setPadding(dp(7), dp(4), dp(7), 0)
        })
        item.addView(text(cow.name, 11, getColor(R.color.text_secondary)).apply {
            setPadding(dp(7), 0, dp(7), dp(5))
        })
        return item
    }

    private fun taskCard(task: FarmTask, status: String, onClick: () -> Unit): View {
        val item = card().apply { setOnClickListener { onClick() } }
        val badgeColor = when (status) {
            "Completada" -> getColor(R.color.soft_green)
            "En curso" -> Color.rgb(224, 239, 248)
            else -> Color.rgb(255, 242, 220)
        }
        val badge = text(status, 11, getColor(R.color.text_primary), true).apply {
            background = shape(badgeColor, 14)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        item.addView(row(text(task.time, 11, getColor(R.color.text_secondary)), badge))
        item.addView(text("${task.emoji}  ${task.title}", 15, getColor(R.color.text_primary), true), top(8))
        if (task.type == "ORDENIO") {
            item.addView(text("Ordeño · ${task.cowRemoteIds.size} vacas asignadas", 12, getColor(R.color.primary_green_dark), true), top(5))
        } else if (task.cowRemoteIds.isNotEmpty()) {
            item.addView(text("Vacas relacionadas · ${task.cowRemoteIds.size}", 12, getColor(R.color.primary_green_dark)), top(5))
        }
        item.addView(text(task.description, 12, getColor(R.color.text_secondary)), top(4))
        if (status == "En curso") item.addView(text("Continuar formulario   →", 12, getColor(R.color.primary_green), true), top(8))
        return item
    }

    private fun bottomNavigation(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }
        val items = listOf("⌂\nInicio", "♧\nOrdeño", "☷\nTareas", "◷\nHistorial", "♙\nCuenta")
        items.forEachIndexed { index, label ->
            val selected = (index == 0 && currentPage == Page.HOME) ||
                (index == 1 && currentPage == Page.COW_SELECTION) ||
                (index == 2 && currentPage == Page.TASKS) ||
                (index == 3 && (currentPage == Page.HISTORY || currentPage == Page.HISTORY_TASK_DETAIL)) ||
                (index == 4 && currentPage == Page.ACCOUNT)
            val item = centered(label, 10, getColor(if (selected) R.color.primary_green else R.color.text_secondary)).apply {
                if (selected) background = shape(getColor(R.color.soft_green), 12)
                setOnClickListener {
                    when (index) {
                        0 -> currentPage = Page.HOME
                        1 -> currentPage = Page.COW_SELECTION
                        2 -> currentPage = Page.TASKS
                        3 -> currentPage = Page.HISTORY
                        4 -> currentPage = Page.ACCOUNT
                    }
                    render()
                }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, -1, 1f).apply {
                if (index > 0) marginStart = dp(3)
            })
        }
        return nav
    }

    private fun actionCard(icon: String, title: String, subtitle: String, primary: Boolean, onClick: () -> Unit): View {
        val item = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = shape(
                getColor(if (primary) R.color.primary_green else R.color.surface),
                16,
                if (primary) getColor(R.color.primary_green) else getColor(R.color.border),
            )
            setPadding(dp(12), dp(14), dp(14), dp(14))
            setOnClickListener { onClick() }
        }
        val iconView = centered(icon, 22, getColor(if (primary) R.color.surface else R.color.primary_green))
        iconView.background = shape(getColor(if (primary) R.color.primary_green_dark else R.color.soft_green), 12)
        item.addView(iconView, LinearLayout.LayoutParams(dp(42), dp(42)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
            addView(text(title, 15, getColor(if (primary) R.color.surface else R.color.text_primary), true))
            addView(text(subtitle, 12, getColor(if (primary) R.color.surface else R.color.text_secondary)), top(4))
        }
        item.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        item.addView(text("›", 24, getColor(if (primary) R.color.surface else R.color.primary_green)))
        return item
    }

    private fun chipGroup(
        label: String,
        choices: List<String>,
        selected: String,
        onSelected: (String) -> Unit,
    ): View {
        val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (label != "Estado") group.addView(text(label, 13, getColor(R.color.text_primary), true))
        val choicesRow = row()
        choices.forEach { choice ->
            choicesRow.addView(button(choice, filled = choice == selected) {
                onSelected(choice)
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                if (choicesRow.childCount > 0) marginStart = dp(6)
                if (label != "Estado") topMargin = dp(8)
            })
        }
        group.addView(choicesRow)
        return group
    }

    private fun progressBar(progress: Float, light: Boolean = false): View {
        val track = LinearLayout(this).apply {
            background = shape(if (light) 0x55FFFFFF else getColor(R.color.border), 8)
            clipToOutline = true
        }
        track.addView(View(this).apply {
            background = shape(if (light) Color.WHITE else getColor(R.color.primary_green), 8)
        }, LinearLayout.LayoutParams(0, dp(6), progress.coerceIn(0.05f, 1f)))
        track.addView(View(this), LinearLayout.LayoutParams(0, dp(6), 1f - progress.coerceIn(0.05f, 1f)))
        return track
    }

    private fun editField(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        textSize = 14f
        setTextColor(getColor(R.color.text_primary))
        setHintTextColor(getColor(R.color.text_secondary))
        background = shape(Color.WHITE, 12, getColor(R.color.border))
        setPadding(dp(14), dp(10), dp(14), dp(10))
        minHeight = dp(48)
    }

    private fun button(
        label: String,
        filled: Boolean = false,
        red: Boolean = false,
        dark: Boolean = false,
        onClick: () -> Unit,
    ): Button = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(
            when {
                red || filled || dark -> Color.WHITE
                else -> getColor(R.color.primary_green)
            },
        )
        background = shape(
            when {
                red -> Color.rgb(220, 48, 36)
                filled -> getColor(R.color.primary_green)
                dark -> getColor(R.color.primary_green_dark)
                else -> Color.WHITE
            },
            12,
            if (filled || red || dark) null else getColor(R.color.border),
        )
        minHeight = dp(44)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setOnClickListener { onClick() }
    }

    private fun iconButton(label: String, icon: Int, onClick: () -> Unit): Button =
        button(label, onClick = onClick).apply {
            setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
            compoundDrawablePadding = dp(8)
        }

    private fun selectableBackground() =
        android.util.TypedValue().let { value ->
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
            getDrawable(value.resourceId)
        }

    private fun getDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameColumn)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "Archivo adjunto"
    }

    private fun getFileSize(uri: Uri): Long? =
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeColumn >= 0 && cursor.moveToFirst()) cursor.getLong(sizeColumn) else null
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(Color.WHITE, 16, getColor(R.color.border))
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { index, view ->
            addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun centered(value: String, size: Int, color: Int, bold: Boolean = false): TextView =
        text(value, size, color, bold).apply { gravity = Gravity.CENTER }

    private fun shape(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            stroke?.let { setStroke(dp(1), it) }
        }

    private fun top(value: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(value) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_CURRENT_USER = "com.ganus.mobile.CURRENT_USER"
        const val EXTRA_CURRENT_USER_ID = "com.ganus.mobile.CURRENT_USER_ID"
        const val EXTRA_AUTHENTICATED = "com.ganus.mobile.AUTHENTICATED"
        private const val REQUEST_SPEECH = 1001
        private const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024
    }
}
