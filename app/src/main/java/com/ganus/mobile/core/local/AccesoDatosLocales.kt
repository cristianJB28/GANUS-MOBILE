package com.ganus.mobile.core.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EstadoSincronizacionDao {
    @Query("SELECT * FROM estado_sincronizacion WHERE flujo = :flujo")
    suspend fun obtener(flujo: String): EstadoSincronizacion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(estado: EstadoSincronizacion)
}

@Dao
interface EventosRecibidosDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarSiEsNuevo(evento: EventoRecibido): Long

    @Query(
        """
        SELECT * FROM eventos_recibidos
        WHERE estado_aplicacion = 'PENDIENTE'
        ORDER BY recibido_en
        """,
    )
    suspend fun obtenerPendientes(): List<EventoRecibido>

    @Query(
        """
        UPDATE eventos_recibidos
        SET estado_aplicacion = :estado, ultimo_error = :error
        WHERE identificador_evento = :identificador
        """,
    )
    suspend fun actualizarEstado(identificador: String, estado: String, error: String?)
}

@Dao
interface PlantillasTareaDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(plantilla: PlantillaTarea)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarSiNoExiste(plantilla: PlantillaTarea)

    @Query(
        """
        SELECT * FROM plantillas_tarea
        WHERE identificador_plantilla = :identificador
        ORDER BY version DESC
        """,
    )
    fun observarVersiones(identificador: String): Flow<List<PlantillaTarea>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarCampos(campos: List<CampoTarea>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarCamposSiNoExisten(campos: List<CampoTarea>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarCampo(campo: CampoTarea)

    @Query(
        """
        SELECT * FROM campos_tarea
        WHERE identificador_plantilla = :identificador AND version = :version
        ORDER BY orden
        """,
    )
    suspend fun obtenerCampos(identificador: String, version: Int): List<CampoTarea>
}

@Dao
interface TareasDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(tarea: Tarea)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarSiNoExiste(tarea: Tarea)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodas(tareas: List<Tarea>)

    @Query("SELECT * FROM tareas ORDER BY fecha_programada")
    fun observarTodas(): Flow<List<Tarea>>

    @Query("SELECT * FROM tareas WHERE estado = :estado ORDER BY fecha_programada")
    fun observarPorEstado(estado: String): Flow<List<Tarea>>

    @Query("SELECT * FROM tareas WHERE identificador_local = :identificador")
    suspend fun obtener(identificador: String): Tarea?

    @Query("SELECT COUNT(*) FROM tareas")
    suspend fun contar(): Int

    @Query(
        """
        UPDATE tareas
        SET estado = :estado, actualizada_en = :actualizadaEn
        WHERE identificador_local = :identificador
        """,
    )
    suspend fun actualizarEstado(identificador: String, estado: String, actualizadaEn: Long)

    @Query(
        """
        UPDATE tareas
        SET estado = :estado, actualizada_en = :actualizadaEn
        WHERE identificador_remoto = :identificadorRemoto
        """,
    )
    suspend fun actualizarEstadoRemoto(identificadorRemoto: String, estado: String, actualizadaEn: Long)

    @Query(
        """
        UPDATE tareas
        SET tipo = :tipo, vacas_json = :vacasJson
        WHERE identificador_remoto = :identificadorRemoto
        """,
    )
    suspend fun actualizarDetallesRemotos(
        identificadorRemoto: String,
        tipo: String,
        vacasJson: String,
    )
}

@Dao
interface RespuestasTareaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(respuesta: RespuestaTarea)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarTodas(respuestas: List<RespuestaTarea>)

    @Query("SELECT * FROM respuestas_tarea WHERE identificador_tarea = :identificador")
    fun observarDeTarea(identificador: String): Flow<List<RespuestaTarea>>

    @Query("SELECT * FROM respuestas_tarea WHERE identificador_tarea = :identificador")
    suspend fun obtenerDeTarea(identificador: String): List<RespuestaTarea>

    @Query("SELECT * FROM respuestas_tarea ORDER BY identificador_tarea")
    fun observarTodas(): Flow<List<RespuestaTarea>>
}

@Dao
interface AlertasDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(alerta: Alerta)

    @Query(
        """
        SELECT * FROM alertas
        WHERE identificador_remoto IS NULL
           OR EXISTS (
               SELECT 1 FROM adjuntos
               WHERE tipo_propietario = 'ALERTA'
                 AND identificador_propietario = alertas.identificador_local
                 AND estado_carga != 'SINCRONIZADO'
           )
        ORDER BY creada_en
        """,
    )
    suspend fun obtenerPendientesDeSincronizacion(): List<Alerta>

    @Query(
        """
        SELECT alertas.*,
            (SELECT nombre_archivo FROM adjuntos
             WHERE tipo_propietario = 'ALERTA'
               AND identificador_propietario = alertas.identificador_local
             ORDER BY creado_en LIMIT 1) AS nombre_adjunto
        FROM alertas
        ORDER BY creada_en DESC
        """,
    )
    fun observarTodas(): Flow<List<AlertaConAdjunto>>

    @Query("SELECT * FROM alertas WHERE estado = :estado ORDER BY creada_en DESC")
    fun observarPorEstado(estado: String): Flow<List<Alerta>>

    @Query(
        """
        UPDATE alertas
        SET identificador_remoto = :identificadorRemoto, actualizada_en = :actualizadaEn
        WHERE identificador_local = :identificadorLocal
        """,
    )
    suspend fun marcarSincronizada(
        identificadorLocal: String,
        identificadorRemoto: String,
        actualizadaEn: Long,
    )
}

data class AlertaConAdjunto(
    @androidx.room.Embedded
    val alerta: Alerta,
    @androidx.room.ColumnInfo(name = "nombre_adjunto")
    val nombreAdjunto: String?,
)

@Dao
interface AdjuntosDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(adjunto: Adjunto)

    @Query(
        """
        SELECT * FROM adjuntos
        WHERE tipo_propietario = :tipo AND identificador_propietario = :identificador
        ORDER BY creado_en
        """,
    )
    fun observarDelPropietario(tipo: String, identificador: String): Flow<List<Adjunto>>

    @Query(
        """
        SELECT * FROM adjuntos
        WHERE tipo_propietario = :tipo AND identificador_propietario = :identificador
        ORDER BY creado_en
        """,
    )
    suspend fun obtenerDelPropietario(tipo: String, identificador: String): List<Adjunto>

    @Query(
        """
        UPDATE adjuntos
        SET estado_carga = 'SINCRONIZADO', identificador_remoto = :identificadorRemoto
        WHERE identificador_local = :identificadorLocal
        """,
    )
    suspend fun marcarSincronizado(identificadorLocal: String, identificadorRemoto: String)

    @Query("SELECT * FROM adjuntos WHERE tipo_propietario = :tipo ORDER BY creado_en")
    fun observarPorTipo(tipo: String): Flow<List<Adjunto>>

    @Query("SELECT * FROM adjuntos WHERE tipo_propietario IN (:tipos) ORDER BY creado_en")
    fun observarPorTipos(tipos: List<String>): Flow<List<Adjunto>>
}

@Dao
interface VacasDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarTodas(vacas: List<Vaca>)

    @Query("UPDATE vacas SET activa = 0 WHERE identificador_local LIKE 'remote-%'")
    suspend fun desactivarRemotas()

    @Query(
        """
        UPDATE vacas
        SET numero = :numero, nombre = :nombre, raza = :raza,
            edad_anos = :edadAnos, numero_tag = :numeroTag, activa = 1
        WHERE identificador_local = :identificador
        """,
    )
    suspend fun actualizarRemota(
        identificador: String,
        numero: String,
        nombre: String,
        raza: String,
        edadAnos: Int?,
        numeroTag: String?,
    )

    @Query("SELECT * FROM vacas WHERE activa = 1 ORDER BY numero")
    fun observarTodas(): Flow<List<Vaca>>
}

@Dao
interface RegistrosOrdenioDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(registro: RegistroOrdenio)

    @Query(
        """
        SELECT * FROM registros_ordenio
        WHERE identificador_vaca = :identificadorVaca
        ORDER BY creado_en DESC
        """,
    )
    fun observarDeVaca(identificadorVaca: String): Flow<List<RegistroOrdenio>>

    @Query("SELECT * FROM registros_ordenio ORDER BY creado_en DESC")
    fun observarTodos(): Flow<List<RegistroOrdenio>>

    @Query(
        """
        SELECT identificador_vaca FROM registros_ordenio
        WHERE identificador_tarea_ordenio = :identificadorTarea
        """,
    )
    suspend fun obtenerVacasRegistradasParaTarea(identificadorTarea: String): List<String>
}

@Dao
interface EventosPendientesDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(evento: EventoPendiente)

    @Query(
        """
        SELECT * FROM eventos_pendientes
        WHERE estado IN ('PENDIENTE', 'FALLIDO')
          AND proximo_reintento <= :ahora
        ORDER BY creado_en
        LIMIT :limite
        """,
    )
    suspend fun obtenerParaEnviar(ahora: Long, limite: Int): List<EventoPendiente>

    @Query(
        """
        UPDATE eventos_pendientes
        SET estado = :estado, intentos = :intentos,
            proximo_reintento = :proximoReintento, ultimo_error = :error
        WHERE identificador_evento = :identificador
        """,
    )
    suspend fun actualizarEstado(
        identificador: String,
        estado: String,
        intentos: Int,
        proximoReintento: Long,
        error: String?,
    )

    @Query("DELETE FROM eventos_pendientes WHERE identificador_evento = :identificador")
    suspend fun eliminarConfirmado(identificador: String)
}
