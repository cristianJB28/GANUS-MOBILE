package com.ganus.mobile.core.local

import androidx.room.withTransaction
import com.ganus.mobile.core.network.RemoteTask
import com.ganus.mobile.core.network.RemoteCow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepositorioLocalGanus @Inject constructor(
    private val baseDatos: BaseDatosGanus,
    private val plantillasDao: PlantillasTareaDao,
    private val tareasDao: TareasDao,
    private val respuestasDao: RespuestasTareaDao,
    private val alertasDao: AlertasDao,
    private val adjuntosDao: AdjuntosDao,
    private val vacasDao: VacasDao,
    private val registrosOrdenioDao: RegistrosOrdenioDao,
    private val eventosPendientesDao: EventosPendientesDao,
) {
    data class EvidenciaSeleccionada(
        val uri: String,
        val nombre: String,
        val tipoMime: String?,
        val tamanoBytes: Long?,
    )

    fun observarVacas(): Flow<List<Vaca>> = vacasDao.observarTodas()

    fun observarTareas(): Flow<List<Tarea>> = tareasDao.observarTodas()

    fun observarRespuestasTarea(): Flow<List<RespuestaTarea>> = respuestasDao.observarTodas()

    fun observarRegistrosOrdenio(): Flow<List<RegistroOrdenio>> = registrosOrdenioDao.observarTodos()

    fun observarEvidenciasHistorial(): Flow<List<Adjunto>> =
        adjuntosDao.observarPorTipos(listOf("TAREA", "ORDENIO"))

    fun observarAlertas(): Flow<List<AlertaConAdjunto>> = alertasDao.observarTodas()

    fun observarEvidenciasAlertas(): Flow<List<Adjunto>> = adjuntosDao.observarPorTipo("ALERTA")

    suspend fun obtenerAlertasPendientesDeSincronizacion(): List<Alerta> =
        alertasDao.obtenerPendientesDeSincronizacion()

    suspend fun obtenerEvidenciasAlerta(identificadorAlerta: String): List<Adjunto> =
        adjuntosDao.obtenerDelPropietario("ALERTA", identificadorAlerta)

    suspend fun marcarAlertaSincronizada(identificadorLocal: String, identificadorRemoto: String) {
        alertasDao.marcarSincronizada(
            identificadorLocal = identificadorLocal,
            identificadorRemoto = identificadorRemoto,
            actualizadaEn = System.currentTimeMillis(),
        )
    }

    suspend fun obtenerOrdeniosPendientesDeSincronizacion(): List<RegistroOrdenio> =
        registrosOrdenioDao.obtenerPendientesDeSincronizacion()

    suspend fun obtenerEvidenciasOrdenio(identificadorOrdenio: String): List<Adjunto> =
        adjuntosDao.obtenerDelPropietario("ORDENIO", identificadorOrdenio)

    suspend fun marcarOrdenioSincronizado(identificadorLocal: String, identificadorRemoto: String) {
        registrosOrdenioDao.marcarSincronizado(identificadorLocal, identificadorRemoto)
    }

    suspend fun inicializarDatosDemostracion() {
        baseDatos.withTransaction {
            val ahora = System.currentTimeMillis()
            vacasDao.insertarTodas(
                listOf(
                    Vaca("vaca-023", "023", "Lucera", "Holstein", 4, "023"),
                    Vaca("vaca-006", "006", "Estrella", "Holstein", 5, "006"),
                    Vaca("vaca-010", "010", "Luna", "Holstein", 3, "010"),
                    Vaca("vaca-018", "018", "Canela", "Holstein", 4, "018"),
                    Vaca("vaca-026", "026", "Mora", "Holstein", 6, "026"),
                    Vaca("vaca-031", "031", "Paloma", "Holstein", 3, "031"),
                ),
            )
            if (tareasDao.contar() == 0) {
                plantillasDao.insertarSiNoExiste(
                    PlantillaTarea(
                        identificadorPlantilla = "plantilla-demostracion",
                        version = 1,
                        identificadorRemoto = "demo-template",
                        titulo = "Tarea diaria",
                        activa = true,
                        recibidaEn = ahora,
                    ),
                )
                plantillasDao.insertarCamposSiNoExisten(
                    listOf(
                        CampoTarea("plantilla-demostracion", 1, "area", "Área de trabajo", "TEXTO", true, 1),
                        CampoTarea("plantilla-demostracion", 1, "accion", "Tarea a realizar", "TEXTO", true, 2),
                        CampoTarea("plantilla-demostracion", 1, "observaciones", "Observaciones", "TEXTO", false, 3),
                        CampoTarea("plantilla-demostracion", 1, "responsable", "Responsable", "TEXTO", true, 4),
                    ),
                )
                val tareas = listOf(
                    Triple("Limpieza del área de ordeño", "Limpiar el área de ordeño", "EN_CURSO"),
                    Triple("Alimentación del lote de ordeño", "Forraje y concentrado · 24 vacas", "PENDIENTE"),
                    Triple("Revisión de terneras", "Apetito, marcha y temperatura · 8 animales", "PENDIENTE"),
                    Triple("Lavado de equipos de ordeño", "María López · Guardada a las 07:00", "COMPLETADA"),
                    Triple("Revisión de bebederos", "María López · Guardada a las 06:30", "COMPLETADA"),
                )
                tareasDao.insertarTodas(
                    tareas.mapIndexed { index, tarea ->
                        Tarea(
                            identificadorLocal = "tarea-demo-$index",
                            identificadorRemoto = null,
                            identificadorPlantilla = "plantilla-demostracion",
                            versionPlantilla = 1,
                            identificadorFinca = "finca-demostracion",
                            identificadorAsignado = "usuario-demostracion",
                            estado = tarea.third,
                            fechaProgramada = ahora + index * 60_000L,
                            actualizadaEn = ahora,
                        )
                    },
                )
                tareas.forEachIndexed { index, tarea ->
                    val idTarea = "tarea-demo-$index"
                    respuestasDao.guardarTodas(
                        listOf(
                            RespuestaTarea(idTarea, "plantilla-demostracion", 1, "area", "Sala de ordeño", actualizadaEn = ahora),
                            RespuestaTarea(idTarea, "plantilla-demostracion", 1, "accion", tarea.second, actualizadaEn = ahora),
                            RespuestaTarea(
                                idTarea,
                                "plantilla-demostracion",
                                1,
                                "observaciones",
                                if (index >= 3) "Actividad registrada previamente." else "",
                                actualizadaEn = ahora,
                            ),
                            RespuestaTarea(
                                idTarea,
                                "plantilla-demostracion",
                                1,
                                "responsable",
                                "María López",
                                actualizadaEn = ahora,
                            ),
                        ),
                    )
                }
            }

            val plantillasEjemplo = EjemplosPlantillas.todas.map { jsonPlantilla ->
                val dto = DynamicTaskMapper.parsePlantilla(jsonPlantilla)
                val plantilla = DynamicTaskMapper.aPlantillaLocal(dto)
                plantillasDao.insertarSiNoExiste(plantilla)
                val campos = DynamicTaskMapper.aCamposLocales(
                    identificadorPlantilla = plantilla.identificadorPlantilla,
                    version = plantilla.version,
                    dto = dto,
                )
                if (campos.isNotEmpty()) plantillasDao.insertarCamposSiNoExisten(campos)
                plantilla
            }
            val tareasEjemplo = listOf(
                Triple("tarea-demo-5", 0, "Inspección de vacas · lote norte"),
                Triple("tarea-demo-6", 1, "Registrar actividad de la finca"),
                Triple("tarea-demo-7", 2, "Revisar equipo de ordeño"),
            )
            tareasEjemplo.forEachIndexed { index, (idTarea, indicePlantilla, titulo) ->
                if (tareasDao.obtener(idTarea) == null) {
                    val plantilla = plantillasEjemplo[indicePlantilla]
                    tareasDao.insertarSiNoExiste(
                        Tarea(
                            identificadorLocal = idTarea,
                            identificadorRemoto = null,
                            identificadorPlantilla = plantilla.identificadorPlantilla,
                            versionPlantilla = plantilla.version,
                            identificadorFinca = "finca-demostracion",
                            identificadorAsignado = "usuario-demostracion",
                            estado = if (index == 0) "EN_CURSO" else "PENDIENTE",
                            fechaProgramada = ahora + (index + 5) * 60_000L,
                            actualizadaEn = ahora,
                            titulo = titulo,
                        ),
                    )
                }
            }
        }
    }

    suspend fun obtenerRespuestasTarea(identificadorTarea: String): Map<String, String> =
        respuestasDao.obtenerDeTarea(identificadorTarea).associate { it.claveCampo to it.valorTexto.orEmpty() }

    suspend fun guardarTareasRemotas(tareas: List<RemoteTask>) {
        baseDatos.withTransaction {
            val ahora = System.currentTimeMillis()
            tareas.forEach { tareaRemota ->
                val plantillaDto = PlantillaTareaDto(
                    identificadorPlantilla = tareaRemota.identificadorPlantilla,
                    nombre = tareaRemota.titulo,
                    version = tareaRemota.versionPlantilla,
                    campos = tareaRemota.campos,
                )
                val plantilla = DynamicTaskMapper.aPlantillaLocal(plantillaDto)
                plantillasDao.insertarSiNoExiste(plantilla)
                val campos = DynamicTaskMapper.aCamposLocales(
                    identificadorPlantilla = tareaRemota.identificadorPlantilla,
                    version = tareaRemota.versionPlantilla,
                    dto = plantillaDto,
                )
                if (campos.isNotEmpty()) plantillasDao.insertarCamposSiNoExisten(campos)

                val identificadorLocal = "remote-${tareaRemota.identificadorRemoto}"
                tareasDao.insertarSiNoExiste(
                    Tarea(
                        identificadorLocal = identificadorLocal,
                        identificadorRemoto = tareaRemota.identificadorRemoto,
                        identificadorPlantilla = tareaRemota.identificadorPlantilla,
                        versionPlantilla = tareaRemota.versionPlantilla,
                        identificadorFinca = null,
                        identificadorAsignado = tareaRemota.identificadorAsignado,
                        estado = tareaRemota.estado,
                        fechaProgramada = null,
                        actualizadaEn = ahora,
                        titulo = tareaRemota.titulo,
                        estadoSincronizacion = "SINCRONIZADA",
                    ),
                )
                tareasDao.actualizarEstadoRemoto(
                    tareaRemota.identificadorRemoto,
                    tareaRemota.estado,
                    ahora,
                )
                tareasDao.actualizarDetallesRemotos(
                    identificadorRemoto = tareaRemota.identificadorRemoto,
                    tipo = tareaRemota.tipo,
                    vacasJson = Json.encodeToString(tareaRemota.vacas.map { it.identificadorRemoto }),
                )
                tareaRemota.respuestas.forEach { (clave, valor) ->
                    val texto = when (valor) {
                        is kotlinx.serialization.json.JsonPrimitive ->
                            valor.contentOrNull ?: valor.toString()
                        is kotlinx.serialization.json.JsonObject ->
                            valor["identificador_adjunto"]?.jsonPrimitive?.contentOrNull ?: valor.toString()
                        else -> valor.toString()
                    }
                    respuestasDao.guardar(
                        DynamicTaskMapper.respuestaDesdeValor(
                            identificadorTarea = identificadorLocal,
                            identificadorPlantilla = tareaRemota.identificadorPlantilla,
                            versionPlantilla = tareaRemota.versionPlantilla,
                            claveCampo = clave,
                            valor = texto,
                            actualizadoEn = ahora,
                        ),
                    )
                }
            }
        }
    }

    suspend fun guardarVacasRemotas(vacas: List<RemoteCow>) {
        baseDatos.withTransaction {
            vacasDao.desactivarRemotas()
            val vacasLocales = vacas.map { vaca ->
                Vaca(
                    identificadorLocal = "remote-${vaca.identificadorRemoto}",
                    numero = vaca.numero,
                    nombre = vaca.nombre,
                    raza = vaca.raza,
                    edadAnos = vaca.edadAnos,
                    numeroTag = vaca.numeroTag,
                )
            }
            if (vacasLocales.isNotEmpty()) {
                vacasDao.insertarTodas(vacasLocales)
                vacasLocales.forEach { vaca ->
                    vacasDao.actualizarRemota(
                        identificador = vaca.identificadorLocal,
                        numero = vaca.numero,
                        nombre = vaca.nombre,
                        raza = vaca.raza,
                        edadAnos = vaca.edadAnos,
                        numeroTag = vaca.numeroTag,
                    )
                }
            }
        }
    }

    suspend fun obtenerTareaLocal(identificador: String): Tarea? =
        tareasDao.obtener(identificador)

    suspend fun guardarPlantillaDinamica(
        jsonPlantilla: String,
        identificadorPlantilla: String? = null,
    ): PlantillaTarea {
        val plantillaDto = DynamicTaskMapper.parsePlantilla(jsonPlantilla, identificadorPlantilla ?: "plantilla-dinamica")
        val plantillaLocal = DynamicTaskMapper.aPlantillaLocal(plantillaDto)
        val campos = DynamicTaskMapper.aCamposLocales(
            identificadorPlantilla = plantillaLocal.identificadorPlantilla,
            version = plantillaLocal.version,
            dto = plantillaDto,
        )
        baseDatos.withTransaction {
            plantillasDao.insertarSiNoExiste(plantillaLocal)
            if (campos.isNotEmpty()) {
                plantillasDao.insertarCamposSiNoExisten(campos)
            }
        }
        return plantillaLocal
    }

    suspend fun obtenerCamposDinamicos(identificadorTarea: String): List<CampoDinamico> {
        val tarea = requireNotNull(tareasDao.obtener(identificadorTarea)) { "No se encontró la tarea local $identificadorTarea." }
        val campos = plantillasDao.obtenerCampos(tarea.identificadorPlantilla, tarea.versionPlantilla)
        return campos.map { CampoDinamico(it) }
    }

    suspend fun guardarRespuestaDinamica(
        identificadorTarea: String,
        claveCampo: String,
        valor: String?,
    ) {
        val tarea = requireNotNull(tareasDao.obtener(identificadorTarea)) { "No se encontró la tarea local $identificadorTarea." }
        val respuesta = DynamicTaskMapper.respuestaDesdeValor(
            identificadorTarea = identificadorTarea,
            identificadorPlantilla = tarea.identificadorPlantilla,
            versionPlantilla = tarea.versionPlantilla,
            claveCampo = claveCampo,
            valor = valor,
        )
        respuestasDao.guardar(respuesta)
    }

    suspend fun obtenerEvidenciasTarea(identificadorTarea: String): List<Adjunto> =
        adjuntosDao.obtenerDelPropietario("TAREA", identificadorTarea)

    suspend fun marcarEvidenciaSincronizada(identificadorLocal: String, identificadorRemoto: String) {
        adjuntosDao.marcarSincronizado(identificadorLocal, identificadorRemoto)
    }

    suspend fun guardarEvidenciasTarea(
        identificadorTarea: String,
        evidencias: List<EvidenciaSeleccionada>,
    ) {
        baseDatos.withTransaction {
            val ahora = System.currentTimeMillis()
            val adjuntos = evidencias.map { evidencia ->
                Adjunto(
                    identificadorLocal = UUID.randomUUID().toString(),
                    tipoPropietario = "TAREA",
                    identificadorPropietario = identificadorTarea,
                    uriLocal = evidencia.uri,
                    nombreArchivo = evidencia.nombre,
                    tipoMime = evidencia.tipoMime,
                    tamanoBytes = evidencia.tamanoBytes,
                    creadoEn = ahora,
                )
            }
            adjuntos.forEach { adjuntosDao.insertar(it) }
            agregarEvento(
                entidad = "TAREA",
                idEntidad = identificadorTarea,
                tipo = "EVIDENCIA_TAREA_AGREGADA",
                contenido = Json.encodeToString(adjuntos.map {
                    EventoAdjunto(it.identificadorLocal, it.uriLocal, it.nombreArchivo, it.tipoMime)
                }),
                ahora = ahora,
            )
        }
    }

    suspend fun guardarTarea(
        identificadorTarea: String,
        estado: String,
        respuestas: Map<String, String>,
    ) {
        baseDatos.withTransaction {
            val tarea = requireNotNull(tareasDao.obtener(identificadorTarea)) {
                "No se encontró la tarea local $identificadorTarea."
            }
            val clavesValidas = plantillasDao
                .obtenerCampos(tarea.identificadorPlantilla, tarea.versionPlantilla)
                .mapTo(mutableSetOf()) { it.claveCampo }
            require(respuestas.keys.all { it in clavesValidas }) {
                "La respuesta incluye campos que no pertenecen a la versión de plantilla asignada a la tarea."
            }
            val ahora = System.currentTimeMillis()
            val valores = respuestas.map { (clave, valor) ->
                RespuestaTarea(
                    identificadorTarea = identificadorTarea,
                    identificadorPlantilla = tarea.identificadorPlantilla,
                    versionPlantilla = tarea.versionPlantilla,
                    claveCampo = clave,
                    valorTexto = valor,
                    actualizadaEn = ahora,
                )
            }
            respuestasDao.guardarTodas(valores)
            tareasDao.actualizarEstado(identificadorTarea, estadoAAlmacenamiento(estado), ahora)
            agregarEvento(
                entidad = "TAREA",
                idEntidad = identificadorTarea,
                tipo = "TAREA_ACTUALIZADA",
                contenido = Json.encodeToString(EventoTarea(estado, respuestas)),
                ahora = ahora,
            )
        }
    }

    suspend fun crearAlerta(
        titulo: String,
        descripcion: String,
        prioridad: String,
        evidencias: List<EvidenciaSeleccionada>,
        identificadorUsuario: String? = null,
    ) {
        baseDatos.withTransaction {
            val ahora = System.currentTimeMillis()
            val idAlerta = UUID.randomUUID().toString()
            alertasDao.insertar(
                Alerta(
                    identificadorLocal = idAlerta,
                    titulo = titulo,
                    descripcion = descripcion,
                    prioridad = prioridad.uppercase(),
                    identificadorUsuario = identificadorUsuario,
                    creadaEn = ahora,
                    actualizadaEn = ahora,
                ),
            )
            evidencias.forEach { evidencia ->
                adjuntosDao.insertar(
                    Adjunto(
                        identificadorLocal = UUID.randomUUID().toString(),
                        tipoPropietario = "ALERTA",
                        identificadorPropietario = idAlerta,
                        uriLocal = evidencia.uri,
                        nombreArchivo = evidencia.nombre,
                        tipoMime = evidencia.tipoMime,
                        tamanoBytes = evidencia.tamanoBytes,
                        creadoEn = ahora,
                    ),
                )
            }
            agregarEvento(
                entidad = "ALERTA",
                idEntidad = idAlerta,
                tipo = "ALERTA_CREADA",
                contenido = Json.encodeToString(EventoAlerta(titulo, descripcion, prioridad)),
                ahora = ahora,
            )
        }
    }

    suspend fun guardarRegistroOrdenio(
        identificadorVaca: String,
        litros: Double,
        alimentacion: String,
        estadoSalud: String,
        clima: String,
        observaciones: String?,
        evidencias: List<EvidenciaSeleccionada>,
        identificadorUsuario: String? = null,
        identificadorTarea: String? = null,
    ) {
        baseDatos.withTransaction {
            val ahora = System.currentTimeMillis()
            val idRegistro = UUID.randomUUID().toString()
            registrosOrdenioDao.insertar(
                RegistroOrdenio(
                    identificadorLocal = idRegistro,
                    identificadorVaca = identificadorVaca,
                    litros = litros,
                    alimentacion = alimentacion,
                    estadoSalud = estadoSalud,
                    clima = clima,
                    observaciones = observaciones,
                    turno = "MANANA",
                    creadoEn = ahora,
                    identificadorUsuario = identificadorUsuario,
                    identificadorTareaOrdenio = identificadorTarea,
                ),
            )
            val adjuntos = evidencias.map { evidencia ->
                Adjunto(
                    identificadorLocal = UUID.randomUUID().toString(),
                    tipoPropietario = "ORDENIO",
                    identificadorPropietario = idRegistro,
                    uriLocal = evidencia.uri,
                    nombreArchivo = evidencia.nombre,
                    tipoMime = evidencia.tipoMime,
                    tamanoBytes = evidencia.tamanoBytes,
                    creadoEn = ahora,
                )
            }
            adjuntos.forEach { adjuntosDao.insertar(it) }
            agregarEvento(
                entidad = "REGISTRO_ORDENIO",
                idEntidad = idRegistro,
                tipo = "REGISTRO_ORDENIO_CREADO",
                contenido = Json.encodeToString(
                    EventoOrdenio(
                        identificadorVaca,
                        litros,
                        alimentacion,
                        estadoSalud,
                        clima,
                        observaciones,
                        adjuntos.map { it.identificadorLocal },
                    ),
                ),
                ahora = ahora,
            )
        }
    }

    suspend fun obtenerVacasRegistradasParaTarea(identificadorTarea: String): List<String> =
        registrosOrdenioDao.obtenerVacasRegistradasParaTarea(identificadorTarea)

    private suspend fun agregarEvento(
        entidad: String,
        idEntidad: String,
        tipo: String,
        contenido: String,
        ahora: Long,
    ) {
        eventosPendientesDao.insertar(
            EventoPendiente(
                identificadorEvento = UUID.randomUUID().toString(),
                entidad = entidad,
                identificadorEntidad = idEntidad,
                tipoEvento = tipo,
                contenidoJson = contenido,
                creadoEn = ahora,
            ),
        )
    }

    private fun estadoAAlmacenamiento(estado: String): String = when (estado) {
        "En curso" -> "EN_CURSO"
        "Completada" -> "COMPLETADA"
        else -> "PENDIENTE"
    }

    @Serializable
    private data class EventoTarea(val estado: String, val respuestas: Map<String, String>)

    @Serializable
    private data class EventoAlerta(val titulo: String, val descripcion: String, val prioridad: String)

    @Serializable
    private data class EventoOrdenio(
        val identificadorVaca: String,
        val litros: Double,
        val alimentacion: String,
        val estadoSalud: String,
        val clima: String,
        val observaciones: String?,
        val identificadoresAdjuntos: List<String>,
    )

    @Serializable
    private data class EventoAdjunto(
        val identificadorLocal: String,
        val uri: String,
        val nombre: String,
        val tipoMime: String?,
    )
}
