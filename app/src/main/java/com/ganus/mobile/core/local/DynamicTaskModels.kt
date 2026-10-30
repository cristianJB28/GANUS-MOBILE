package com.ganus.mobile.core.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

enum class TipoCampoDinamico(val valor: String) {
    TEXT("TEXT"),
    TEXTAREA("TEXTAREA"),
    NUMBER("NUMBER"),
    DECIMAL("DECIMAL"),
    DATE("DATE"),
    TIME("TIME"),
    DATETIME("DATETIME"),
    SELECT("SELECT"),
    MULTI_SELECT("MULTI_SELECT"),
    BOOLEAN("BOOLEAN"),
    IMAGE("IMAGE"),
    FILE("FILE");

    companion object {
        fun fromValue(raw: String?): TipoCampoDinamico =
            entries.firstOrNull { it.valor.equals(raw?.trim()?.uppercase(), true) }
                ?: TEXT
    }
}

data class CampoDinamico(
    val campo: CampoTarea,
    val tipo: TipoCampoDinamico = TipoCampoDinamico.fromValue(campo.tipoCampo),
    val opciones: List<String> = campo.opcionesJson
        ?.let { opcionesJson ->
            runCatching {
                json.decodeFromString<List<String>>(opcionesJson)
            }.getOrElse { emptyList() }
        }
        ?: emptyList(),
)

@Serializable
data class CampoPlantillaDto(
    @SerialName("clave")
    val clave: String? = null,
    @SerialName("key")
    val key: String? = null,
    @SerialName("nombre")
    val nombre: String? = null,
    @SerialName("label")
    val label: String? = null,
    @SerialName("etiqueta")
    val etiqueta: String? = null,
    @SerialName("titulo")
    val titulo: String? = null,
    @SerialName("tipo")
    val tipo: String? = null,
    @SerialName("type")
    val type: String? = null,
    @SerialName("obligatorio")
    val obligatorio: Boolean? = null,
    @SerialName("required")
    val required: Boolean? = null,
    @SerialName("orden")
    val orden: Int? = null,
    @SerialName("order")
    val order: Int? = null,
    @SerialName("descripcion")
    val descripcion: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("valor_predeterminado")
    val valorPredeterminado: JsonElement? = null,
    @SerialName("default")
    val default: JsonElement? = null,
    @SerialName("opciones")
    val opciones: List<String>? = null,
    @SerialName("options")
    val options: List<String>? = null,
    @SerialName("validaciones")
    val validaciones: Map<String, JsonElement>? = null,
    @SerialName("validations")
    val validations: Map<String, JsonElement>? = null,
    @SerialName("visibilidad")
    val visibilidad: String? = null,
    @SerialName("visible")
    val visible: Boolean? = null,
)

@Serializable
data class PlantillaTareaDto(
    @SerialName("identificador_remoto")
    val identificadorRemoto: String? = null,
    @SerialName("id")
    val id: String? = null,
    @SerialName("identificador_plantilla")
    val identificadorPlantilla: String? = null,
    @SerialName("nombre")
    val nombre: String? = null,
    @SerialName("titulo")
    val titulo: String? = null,
    @SerialName("descripcion")
    val descripcion: String? = null,
    @SerialName("version")
    val version: Int = 1,
    @SerialName("estado")
    val estado: String = "ACTIVA",
    @SerialName("activa")
    val activa: Boolean = true,
    @SerialName("campos")
    val campos: List<CampoPlantillaDto> = emptyList(),
    @SerialName("fields")
    val fields: List<CampoPlantillaDto> = emptyList(),
)

object DynamicTaskMapper {
    private val serializer = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parsePlantilla(jsonInput: String, fallbackIdentificador: String = "plantilla-dinamica"): PlantillaTareaDto {
        val root = serializer.parseToJsonElement(jsonInput)
        val payload = root.jsonObject["data"] ?: root
        return runCatching {
            serializer.decodeFromJsonElement<PlantillaTareaDto>(payload)
        }.getOrElse {
            val jsonObject = payload.jsonObject
            val fields = jsonObject["campos"] ?: jsonObject["fields"] ?: JsonArray(emptyList())
            val resolvedId = jsonObject["identificador_remoto"]?.jsonPrimitive?.contentOrNull
                ?: jsonObject["id"]?.jsonPrimitive?.contentOrNull
                ?: fallbackIdentificador
            val resolvedPlantillaId = jsonObject["identificador_plantilla"]?.jsonPrimitive?.contentOrNull
                ?: resolvedId
            val resolvedName = jsonObject["nombre"]?.jsonPrimitive?.contentOrNull
                ?: jsonObject["titulo"]?.jsonPrimitive?.contentOrNull
                ?: "Tarea dinámica"
            val resolvedVersion = jsonObject["version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
            val resolvedFields = fields.jsonArray.map { fieldElement ->
                val field = fieldElement.jsonObject
                CampoPlantillaDto(
                    clave = field["clave"]?.jsonPrimitive?.contentOrNull ?: field["key"]?.jsonPrimitive?.contentOrNull,
                    nombre = field["nombre"]?.jsonPrimitive?.contentOrNull ?: field["label"]?.jsonPrimitive?.contentOrNull,
                    etiqueta = field["etiqueta"]?.jsonPrimitive?.contentOrNull,
                    titulo = field["titulo"]?.jsonPrimitive?.contentOrNull,
                    tipo = field["tipo"]?.jsonPrimitive?.contentOrNull ?: field["type"]?.jsonPrimitive?.contentOrNull,
                    obligatorio = field["obligatorio"]?.jsonPrimitive?.booleanOrNull ?: field["required"]?.jsonPrimitive?.booleanOrNull,
                    orden = field["orden"]?.jsonPrimitive?.intOrNull ?: field["order"]?.jsonPrimitive?.intOrNull,
                    descripcion = field["descripcion"]?.jsonPrimitive?.contentOrNull ?: field["description"]?.jsonPrimitive?.contentOrNull,
                    valorPredeterminado = field["valor_predeterminado"] ?: field["default"],
                    opciones = field["opciones"]?.jsonArray?.map { it.jsonPrimitive.content },
                    validaciones = field["validaciones"]?.jsonObject,
                    visibilidad = field["visibilidad"]?.jsonPrimitive?.contentOrNull,
                )
            }
            PlantillaTareaDto(
                identificadorRemoto = resolvedId,
                identificadorPlantilla = resolvedPlantillaId,
                nombre = resolvedName,
                titulo = resolvedName,
                version = resolvedVersion,
                estado = jsonObject["estado"]?.jsonPrimitive?.contentOrNull ?: "ACTIVA",
                activa = jsonObject["activa"]?.jsonPrimitive?.booleanOrNull ?: true,
                campos = resolvedFields,
                fields = resolvedFields,
            )
        }
    }

    fun aPlantillaLocal(dto: PlantillaTareaDto): PlantillaTarea =
        PlantillaTarea(
            identificadorPlantilla = dto.identificadorPlantilla
                ?: dto.identificadorRemoto
                ?: dto.id
                ?: "plantilla-dinamica",
            version = dto.version,
            identificadorRemoto = dto.identificadorRemoto ?: dto.id ?: dto.identificadorPlantilla ?: "plantilla-dinamica",
            titulo = dto.nombre ?: dto.titulo ?: "Tarea dinámica",
            activa = dto.activa,
            recibidaEn = System.currentTimeMillis(),
            descripcion = dto.descripcion,
            estadoPlantilla = dto.estado.ifBlank { "ACTIVA" },
            fechaCreacion = System.currentTimeMillis(),
            ultimaSincronizacion = System.currentTimeMillis(),
        )

    fun aCamposLocales(
        identificadorPlantilla: String,
        version: Int,
        dto: PlantillaTareaDto,
    ): List<CampoTarea> {
        val sourceFields = if (dto.campos.isNotEmpty()) dto.campos else dto.fields
        return sourceFields.mapIndexed { index, campo ->
            val normalizedType = campo.tipo ?: campo.type ?: "TEXT"
            val normalizedKey = campo.clave ?: campo.key ?: "campo_${index + 1}"
            val etiqueta = campo.nombre ?: campo.label ?: campo.etiqueta ?: campo.titulo ?: normalizedKey
            val descripcion = campo.descripcion ?: campo.description
            val defaultValue = campo.valorPredeterminado ?: campo.default
            val opciones = (campo.opciones ?: campo.options).orEmpty().ifEmpty { null }
            val obligatorio = campo.obligatorio ?: campo.required ?: false
            val orden = campo.orden ?: campo.order ?: index + 1
            val visibilidad = campo.visibilidad ?: if (campo.visible == false) "OCULTO" else "VISIBLE"
            CampoTarea(
                identificadorPlantilla = identificadorPlantilla,
                version = version,
                claveCampo = normalizedKey,
                etiqueta = etiqueta,
                tipoCampo = normalizedType.uppercase(),
                obligatorio = obligatorio,
                orden = orden,
                opcionesJson = opciones?.let { serializer.encodeToString(it) },
                validacionesJson = serializer.encodeToString(campo.validaciones ?: campo.validations ?: emptyMap()),
                descripcion = descripcion,
                valorPredeterminado = defaultValue?.let { value ->
                    value.jsonPrimitiveOrNull()?.contentOrNull ?: serializer.encodeToString(value)
                },
                visibilidad = visibilidad,
            )
        }
    }

    private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    fun respuestaDesdeValor(
        identificadorTarea: String,
        identificadorPlantilla: String,
        versionPlantilla: Int,
        claveCampo: String,
        valor: String?,
        actualizadoEn: Long = System.currentTimeMillis(),
    ): RespuestaTarea {
        val normalized = valor.orEmpty()
        val parsedNumber = normalized.toDoubleOrNull()
        val parsedBoolean = normalized.lowercase().let { it == "true" || it == "1" || it == "yes" }
        val tipo = TipoCampoDinamico.fromValue(
            // No se usa aquí; la respuesta conserva el valor textualmente y la lógica de validación
            "TEXT",
        )
        return RespuestaTarea(
            identificadorTarea = identificadorTarea,
            identificadorPlantilla = identificadorPlantilla,
            versionPlantilla = versionPlantilla,
            claveCampo = claveCampo,
            valorTexto = normalized,
            valorNumero = if (parsedNumber != null) parsedNumber else null,
            valorBooleano = if (normalized.isNotBlank()) parsedBoolean else null,
            valorFecha = null,
            valorJson = if (normalized.isBlank()) null else normalized,
            actualizadaEn = actualizadoEn,
        )
    }
}
