package com.ganus.mobile.core.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "estado_sincronizacion")
data class EstadoSincronizacion(
    @PrimaryKey
    @ColumnInfo(name = "flujo")
    val flujo: String,
    @ColumnInfo(name = "cursor_remoto")
    val cursorRemoto: String? = null,
    @ColumnInfo(name = "ultima_sincronizacion_correcta")
    val ultimaSincronizacionCorrecta: Long? = null,
)

@Entity(
    tableName = "eventos_recibidos",
    indices = [Index(value = ["estado_aplicacion", "cursor_remoto"])],
)
data class EventoRecibido(
    @PrimaryKey
    @ColumnInfo(name = "identificador_evento")
    val identificadorEvento: String,
    @ColumnInfo(name = "entidad")
    val entidad: String,
    @ColumnInfo(name = "cursor_remoto")
    val cursorRemoto: String?,
    @ColumnInfo(name = "version_esquema")
    val versionEsquema: Int,
    @ColumnInfo(name = "contenido_json")
    val contenidoJson: String,
    @ColumnInfo(name = "estado_aplicacion")
    val estadoAplicacion: String = "PENDIENTE",
    @ColumnInfo(name = "ultimo_error")
    val ultimoError: String? = null,
    @ColumnInfo(name = "recibido_en")
    val recibidoEn: Long,
)

@Entity(
    tableName = "plantillas_tarea",
    primaryKeys = ["identificador_plantilla", "version"],
)
data class PlantillaTarea(
    @ColumnInfo(name = "identificador_plantilla")
    val identificadorPlantilla: String,
    @ColumnInfo(name = "version")
    val version: Int,
    @ColumnInfo(name = "identificador_remoto")
    val identificadorRemoto: String,
    @ColumnInfo(name = "titulo")
    val titulo: String,
    @ColumnInfo(name = "activa")
    val activa: Boolean,
    @ColumnInfo(name = "recibida_en")
    val recibidaEn: Long,
    @ColumnInfo(name = "descripcion")
    val descripcion: String? = null,
    @ColumnInfo(name = "estado_plantilla", defaultValue = "'ACTIVA'")
    val estadoPlantilla: String = "ACTIVA",
    @ColumnInfo(name = "fecha_creacion")
    val fechaCreacion: Long? = null,
    @ColumnInfo(name = "ultima_sincronizacion")
    val ultimaSincronizacion: Long? = null,
)

@Entity(
    tableName = "campos_tarea",
    primaryKeys = ["identificador_plantilla", "version", "clave_campo"],
    foreignKeys = [
        ForeignKey(
            entity = PlantillaTarea::class,
            parentColumns = ["identificador_plantilla", "version"],
            childColumns = ["identificador_plantilla", "version"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["identificador_plantilla", "version"])],
)
data class CampoTarea(
    @ColumnInfo(name = "identificador_plantilla")
    val identificadorPlantilla: String,
    @ColumnInfo(name = "version")
    val version: Int,
    @ColumnInfo(name = "clave_campo")
    val claveCampo: String,
    @ColumnInfo(name = "etiqueta")
    val etiqueta: String,
    @ColumnInfo(name = "tipo_campo")
    val tipoCampo: String,
    @ColumnInfo(name = "obligatorio")
    val obligatorio: Boolean,
    @ColumnInfo(name = "orden")
    val orden: Int,
    @ColumnInfo(name = "opciones_json")
    val opcionesJson: String? = null,
    @ColumnInfo(name = "validaciones_json")
    val validacionesJson: String? = null,
    @ColumnInfo(name = "descripcion")
    val descripcion: String? = null,
    @ColumnInfo(name = "valor_predeterminado")
    val valorPredeterminado: String? = null,
    @ColumnInfo(name = "visibilidad", defaultValue = "'VISIBLE'")
    val visibilidad: String = "VISIBLE",
)

@Entity(
    tableName = "tareas",
    foreignKeys = [
        ForeignKey(
            entity = PlantillaTarea::class,
            parentColumns = ["identificador_plantilla", "version"],
            childColumns = ["identificador_plantilla", "version_plantilla"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["identificador_remoto"], unique = true),
        Index(value = ["identificador_plantilla", "version_plantilla"]),
        Index(value = ["estado", "fecha_programada"]),
    ],
)
data class Tarea(
    @PrimaryKey
    @ColumnInfo(name = "identificador_local")
    val identificadorLocal: String,
    @ColumnInfo(name = "identificador_remoto")
    val identificadorRemoto: String?,
    @ColumnInfo(name = "identificador_plantilla")
    val identificadorPlantilla: String,
    @ColumnInfo(name = "version_plantilla")
    val versionPlantilla: Int,
    @ColumnInfo(name = "identificador_finca")
    val identificadorFinca: String?,
    @ColumnInfo(name = "identificador_asignado")
    val identificadorAsignado: String?,
    @ColumnInfo(name = "estado")
    val estado: String,
    @ColumnInfo(name = "fecha_programada")
    val fechaProgramada: Long?,
    @ColumnInfo(name = "actualizada_en")
    val actualizadaEn: Long,
    @ColumnInfo(name = "titulo")
    val titulo: String? = null,
    @ColumnInfo(name = "estado_sincronizacion", defaultValue = "'PENDIENTE'")
    val estadoSincronizacion: String = "PENDIENTE",
    @ColumnInfo(name = "tipo", defaultValue = "'FORMULARIO'")
    val tipo: String = "FORMULARIO",
    @ColumnInfo(name = "vacas_json", defaultValue = "'[]'")
    val vacasJson: String = "[]",
)

@Entity(
    tableName = "respuestas_tarea",
    primaryKeys = ["identificador_tarea", "clave_campo"],
    foreignKeys = [
        ForeignKey(
            entity = Tarea::class,
            parentColumns = ["identificador_local"],
            childColumns = ["identificador_tarea"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CampoTarea::class,
            parentColumns = ["identificador_plantilla", "version", "clave_campo"],
            childColumns = ["identificador_plantilla", "version_plantilla", "clave_campo"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["identificador_plantilla", "version_plantilla", "clave_campo"]),
    ],
)
data class RespuestaTarea(
    @ColumnInfo(name = "identificador_tarea")
    val identificadorTarea: String,
    @ColumnInfo(name = "identificador_plantilla")
    val identificadorPlantilla: String,
    @ColumnInfo(name = "version_plantilla")
    val versionPlantilla: Int,
    @ColumnInfo(name = "clave_campo")
    val claveCampo: String,
    @ColumnInfo(name = "valor_texto")
    val valorTexto: String? = null,
    @ColumnInfo(name = "valor_numero")
    val valorNumero: Double? = null,
    @ColumnInfo(name = "valor_booleano")
    val valorBooleano: Boolean? = null,
    @ColumnInfo(name = "valor_fecha")
    val valorFecha: Long? = null,
    @ColumnInfo(name = "valor_json")
    val valorJson: String? = null,
    @ColumnInfo(name = "actualizada_en")
    val actualizadaEn: Long,
    @ColumnInfo(name = "estado_sincronizacion", defaultValue = "'PENDIENTE'")
    val estadoSincronizacion: String = "PENDIENTE",
)

@Entity(
    tableName = "alertas",
    indices = [Index(value = ["identificador_remoto"], unique = true)],
)
data class Alerta(
    @PrimaryKey
    @ColumnInfo(name = "identificador_local")
    val identificadorLocal: String,
    @ColumnInfo(name = "identificador_remoto")
    val identificadorRemoto: String? = null,
    @ColumnInfo(name = "titulo")
    val titulo: String,
    @ColumnInfo(name = "descripcion")
    val descripcion: String,
    @ColumnInfo(name = "prioridad")
    val prioridad: String,
    @ColumnInfo(name = "estado")
    val estado: String = "ABIERTA",
    @ColumnInfo(name = "identificador_usuario")
    val identificadorUsuario: String?,
    @ColumnInfo(name = "creada_en")
    val creadaEn: Long,
    @ColumnInfo(name = "actualizada_en")
    val actualizadaEn: Long,
)

@Entity(
    tableName = "adjuntos",
    indices = [Index(value = ["tipo_propietario", "identificador_propietario"])],
)
data class Adjunto(
    @PrimaryKey
    @ColumnInfo(name = "identificador_local")
    val identificadorLocal: String,
    @ColumnInfo(name = "tipo_propietario")
    val tipoPropietario: String,
    @ColumnInfo(name = "identificador_propietario")
    val identificadorPropietario: String,
    @ColumnInfo(name = "uri_local")
    val uriLocal: String,
    @ColumnInfo(name = "nombre_archivo")
    val nombreArchivo: String,
    @ColumnInfo(name = "tipo_mime")
    val tipoMime: String?,
    @ColumnInfo(name = "tamano_bytes")
    val tamanoBytes: Long?,
    @ColumnInfo(name = "estado_carga")
    val estadoCarga: String = "PENDIENTE",
    @ColumnInfo(name = "identificador_remoto")
    val identificadorRemoto: String? = null,
    @ColumnInfo(name = "creado_en")
    val creadoEn: Long,
)

@Entity(
    tableName = "eventos_pendientes",
    indices = [
        Index(value = ["estado", "proximo_reintento"]),
        Index(value = ["entidad", "identificador_entidad"]),
    ],
)
data class EventoPendiente(
    @PrimaryKey
    @ColumnInfo(name = "identificador_evento")
    val identificadorEvento: String,
    @ColumnInfo(name = "entidad")
    val entidad: String,
    @ColumnInfo(name = "identificador_entidad")
    val identificadorEntidad: String,
    @ColumnInfo(name = "tipo_evento")
    val tipoEvento: String,
    @ColumnInfo(name = "contenido_json")
    val contenidoJson: String,
    @ColumnInfo(name = "estado")
    val estado: String = "PENDIENTE",
    @ColumnInfo(name = "intentos")
    val intentos: Int = 0,
    @ColumnInfo(name = "proximo_reintento")
    val proximoReintento: Long = 0,
    @ColumnInfo(name = "ultimo_error")
    val ultimoError: String? = null,
    @ColumnInfo(name = "creado_en")
    val creadoEn: Long,
)

@Entity(tableName = "vacas")
data class Vaca(
    @PrimaryKey
    @ColumnInfo(name = "identificador_local")
    val identificadorLocal: String,
    @ColumnInfo(name = "numero")
    val numero: String,
    @ColumnInfo(name = "nombre")
    val nombre: String,
    @ColumnInfo(name = "raza")
    val raza: String,
    @ColumnInfo(name = "edad_anos")
    val edadAnos: Int?,
    @ColumnInfo(name = "numero_tag")
    val numeroTag: String?,
    @ColumnInfo(name = "activa", defaultValue = "1")
    val activa: Boolean = true,
)

@Entity(
    tableName = "registros_ordenio",
    foreignKeys = [
        ForeignKey(
            entity = Vaca::class,
            parentColumns = ["identificador_local"],
            childColumns = ["identificador_vaca"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["identificador_vaca", "creado_en"]),
        Index(value = ["identificador_remoto"], unique = true),
    ],
)
data class RegistroOrdenio(
    @PrimaryKey
    @ColumnInfo(name = "identificador_local")
    val identificadorLocal: String,
    @ColumnInfo(name = "identificador_remoto")
    val identificadorRemoto: String? = null,
    @ColumnInfo(name = "identificador_vaca")
    val identificadorVaca: String,
    @ColumnInfo(name = "litros")
    val litros: Double,
    @ColumnInfo(name = "alimentacion")
    val alimentacion: String,
    @ColumnInfo(name = "estado_salud")
    val estadoSalud: String,
    @ColumnInfo(name = "clima")
    val clima: String,
    @ColumnInfo(name = "observaciones")
    val observaciones: String?,
    @ColumnInfo(name = "turno")
    val turno: String,
    @ColumnInfo(name = "creado_en")
    val creadoEn: Long,
    @ColumnInfo(name = "identificador_usuario")
    val identificadorUsuario: String? = null,
    @ColumnInfo(name = "identificador_tarea_ordenio")
    val identificadorTareaOrdenio: String? = null,
)
