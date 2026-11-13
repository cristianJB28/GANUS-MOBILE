package com.ganus.mobile.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Comprueba que la app habla el contrato de la API de GANUS Operativo (docs/movil/README.md del repositorio ganus):
 * los nombres de los campos que envía y los que lee, y los mensajes cuando el servidor rechaza algo.
 */
class ContratoApiGanusTest {
    // Igual que NetworkModule: tolera campos nuevos y no envía los valores por defecto
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Test
    fun `un ordenio se envia con los nombres que espera la API`() {
        val cuerpo = json.parseToJsonElement(
            json.encodeToString(
                CreateMilkingRequest(
                    milkingId = "7b1f0c9e-0000-4000-8000-000000000001",
                    cowId = "5d0e8a10-0000-4000-8000-000000000002",
                    taskId = "3a2b1c00-0000-4000-8000-000000000003",
                    litros = 12.5,
                    alimentacion = "Normal",
                    estadoSalud = "Buena",
                    clima = "Soleado",
                    turno = "MANANA",
                    observaciones = "Tranquila",
                    registradoEn = "2026-11-12T10:30:00.000Z",
                ),
            ),
        ).jsonObject
        assertEquals("7b1f0c9e-0000-4000-8000-000000000001", cuerpo["identificador_remoto"]!!.jsonPrimitive.content)
        assertEquals("5d0e8a10-0000-4000-8000-000000000002", cuerpo["identificador_vaca"]!!.jsonPrimitive.content)
        assertEquals("3a2b1c00-0000-4000-8000-000000000003", cuerpo["identificador_tarea"]!!.jsonPrimitive.content)
        assertEquals(12.5, cuerpo["litros"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals("Buena", cuerpo["estado_salud"]!!.jsonPrimitive.content)
        assertEquals("2026-11-12T10:30:00.000Z", cuerpo["registrado_en"]!!.jsonPrimitive.content)
    }

    @Test
    fun `los campos opcionales vacios no viajan`() {
        val cuerpo = json.parseToJsonElement(
            json.encodeToString(
                CreateMilkingRequest(
                    milkingId = "7b1f0c9e-0000-4000-8000-000000000001",
                    cowId = "5d0e8a10-0000-4000-8000-000000000002",
                    litros = 3.0,
                ),
            ),
        ).jsonObject
        assertFalse(cuerpo.containsKey("identificador_tarea"))
        assertFalse(cuerpo.containsKey("registrado_en"))
        assertFalse(cuerpo.containsKey("observaciones"))
    }

    @Test
    fun `omite el identificador de tarea si no es un uuid`() {
        val taskId = "task-209".uuidOrNull()
        val cuerpo = json.parseToJsonElement(
            json.encodeToString(
                CreateMilkingRequest(
                    milkingId = "7b1f0c9e-0000-4000-8000-000000000001",
                    cowId = "5d0e8a10-0000-4000-8000-000000000002",
                    taskId = taskId,
                    litros = 3.0,
                ),
            ),
        ).jsonObject
        assertEquals(null, taskId)
        assertFalse(cuerpo.containsKey("identificador_tarea"))
        assertEquals(
            "3a2b1c00-0000-4000-8000-000000000003",
            "3a2b1c00-0000-4000-8000-000000000003".uuidOrNull(),
        )
    }

    @Test
    fun `las respuestas de una tarea pueden ir vacias y llevan la hora del telefono`() {
        val cuerpo = json.parseToJsonElement(
            json.encodeToString(
                SaveTaskResponsesRequest(
                    identificadorEvento = "e1",
                    estado = "COMPLETADA",
                    respuestas = emptyMap(),
                    capturadoEn = "2026-11-12T10:30:00.000Z",
                ),
            ),
        ).jsonObject
        assertEquals("COMPLETADA", cuerpo["estado"]!!.jsonPrimitive.content)
        assertTrue(cuerpo["respuestas"]!!.jsonObject.isEmpty())
        assertEquals("2026-11-12T10:30:00.000Z", cuerpo["capturado_en"]!!.jsonPrimitive.content)
        val conValor = json.encodeToString(
            SaveTaskResponsesRequest("e2", "EN_CURSO", mapOf("temperatura" to JsonPrimitive(38.5))),
        )
        assertTrue(conValor.contains("\"temperatura\":38.5"))
        assertFalse(conValor.contains("capturado_en"))
    }

    @Test
    fun `lee la respuesta de la API al registrar un ordenio y al descargar vacas`() {
        val ordenio = json.decodeFromString<CreateMilkingResponse>(
            """{"data":{"identificador_remoto":"abc","estado_servidor":"PERSISTED"},"repetido":true}""",
        )
        assertEquals("PERSISTED", ordenio.data.estadoServidor)
        assertTrue(ordenio.repetido)

        val vacas = json.decodeFromString<CowListResponse>(
            """{"data":[{"identificador_remoto":"v1","numero":"CO-001","nombre":"Luna","raza":"Holstein","edad_anos":6,"numero_tag":"T1"},
                         {"identificador_remoto":"v2","numero":"CO-002","nombre":"Estrella","raza":"Jersey"}]}""",
        )
        assertEquals(2, vacas.data.size)
        assertEquals(6, vacas.data[0].edadAnos)
        assertEquals(null, vacas.data[1].edadAnos)   // sin fecha de nacimiento la API no manda el campo
    }

    @Test
    fun `lee el inicio de sesion y una tarea con su formulario`() {
        val login = json.decodeFromString<LoginResponse>(
            """{"access_token":"tok","usuario":{"identificador":"u1","email":"a@b.test","nombre":"Ana","rol":"app"}}""",
        )
        assertEquals("tok", login.accessToken)
        assertEquals("app", login.usuario.rol)

        val tareas = json.decodeFromString<TaskListResponse>(
            """{"data":[{"identificador_remoto":"t1","identificador_plantilla":"f1","version_plantilla":2,
                "titulo":"Inspección · T-1","estado":"PENDIENTE","tipo":"ORDENIO","identificador_asignado":"u1",
                "vacas":[{"identificador_remoto":"v1","numero":"CO-001","nombre":"Luna","raza":"Holstein"}],
                "campos":[{"clave":"temperatura","nombre":"Temperatura","tipo":"DECIMAL","orden":1,"obligatorio":true,
                           "validaciones":{"min":30,"max":45},"visibilidad":"VISIBLE"}],
                "respuestas":{"temperatura":38.5}}]}""",
        )
        val t = tareas.data.single()
        assertEquals("ORDENIO", t.tipo)
        assertEquals(1, t.vacas.size)
        assertEquals("temperatura", t.campos.single().clave)
        assertEquals(38.5, t.respuestas["temperatura"]!!.jsonPrimitive.content.toDouble(), 0.0)
    }

    // ------------------------------------------------------------- mensajes de error

    private fun http(codigo: Int, cuerpo: String) =
        HttpException(Response.error<Any>(codigo, cuerpo.toResponseBody("application/json".toMediaType())))

    @Test
    fun `un 422 muestra por que el servidor rechazo los datos`() {
        val texto = http(
            422,
            """{"codigo":"REJECTED","mensaje":"El servidor rechazó los datos.","detalles":[
                {"codigo":"MILKING_PENDING","mensaje":"Faltan 1 vaca por ordeñar.","campo":"task_status"}]}""",
        ).mensajeParaUsuario()
        assertTrue(texto, texto.contains("Faltan 1 vaca por ordeñar."))
    }

    @Test
    fun `un 422 muestra validaciones en el formato de NestJS`() {
        val texto = http(
            422,
            """{"statusCode":422,"message":["identificador_tarea must be a UUID"],"error":"Unprocessable Entity"}""",
        ).mensajeParaUsuario()
        assertTrue(texto, texto.contains("identificador_tarea must be a UUID"))
    }

    @Test
    fun `un 401 pide iniciar sesion de nuevo y un 500 dice que se reintentara`() {
        assertEquals("Tu sesión venció. Inicia sesión de nuevo.", http(401, """{"codigo":"UNAUTHORIZED"}""").mensajeParaUsuario())
        assertTrue(http(503, "").mensajeParaUsuario().contains("reintentará"))
    }

    @Test
    fun `un error que no es de la API usa su propio mensaje`() {
        assertEquals("sin red", java.io.IOException("sin red").mensajeParaUsuario())
        assertEquals("error desconocido", RuntimeException().mensajeParaUsuario())
    }
}
