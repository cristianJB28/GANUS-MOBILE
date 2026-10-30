package com.ganus.mobile.core.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ganus.mobile.core.network.RemoteTask
import kotlinx.serialization.json.Json

class DynamicTaskModelsTest {
    @Test
    fun bundledTemplatesKeepTheirDynamicFieldCountsAndTypes() {
        val expected = listOf(6, 8, 7)
        val templates = EjemplosPlantillas.todas.map { DynamicTaskMapper.parsePlantilla(it) }
        val fields = templates.map { template ->
            DynamicTaskMapper.aCamposLocales(
                identificadorPlantilla = requireNotNull(template.identificadorPlantilla),
                version = template.version,
                dto = template,
            )
        }

        assertEquals(expected, fields.map { it.size })
        assertTrue(fields[1].any { it.tipoCampo == "IMAGE" })
        assertTrue(fields[1].any { it.tipoCampo == "FILE" })
        assertTrue(fields.flatten().any { it.tipoCampo == "MULTI_SELECT" })
    }

    @Test
    fun parserReadsTemplateInsideStagingEventEnvelope() {
        val template = DynamicTaskMapper.parsePlantilla(
            """
                {
                  "tipo_evento": "PLANTILLA_TAREA_ACTUALIZADA",
                  "data": {
                    "identificador_plantilla": "checklist",
                    "identificador_remoto": "remote-checklist",
                    "nombre": "Checklist",
                    "version": 4,
                    "campos": [
                      {"clave": "ok", "nombre": "¿Está bien?", "tipo": "BOOLEAN", "orden": 1, "obligatorio": true}
                    ]
                  }
                }
            """.trimIndent(),
        )

        assertEquals("checklist", template.identificadorPlantilla)
        assertEquals(4, template.version)
        assertEquals("BOOLEAN", DynamicTaskMapper.aCamposLocales("checklist", 4, template).single().tipoCampo)
    }

    @Test
    fun remoteTaskAcceptsNumericValidationValuesAndDefaults() {
        val json = Json { ignoreUnknownKeys = true }
        val task = json.decodeFromString<RemoteTask>(
            """
                {
                  "identificador_remoto": "task-1",
                  "identificador_plantilla": "checklist",
                  "version_plantilla": 1,
                  "titulo": "Inspección",
                  "estado": "PENDIENTE",
                  "identificador_asignado": "user-1",
                  "campos": [
                    {
                      "clave": "temperatura",
                      "nombre": "Temperatura",
                      "tipo": "DECIMAL",
                      "validaciones": {"min": 30, "max": 45}
                    },
                    {
                      "clave": "cantidad",
                      "nombre": "Cantidad",
                      "tipo": "NUMBER",
                      "valor_predeterminado": 3
                    }
                  ],
                  "respuestas": {}
                }
            """.trimIndent(),
        )

        val campos = DynamicTaskMapper.aCamposLocales(
            identificadorPlantilla = "checklist",
            version = 1,
            dto = com.ganus.mobile.core.local.PlantillaTareaDto(
                identificadorPlantilla = "checklist",
                campos = task.campos,
            ),
        )

        assertEquals("""{"min":30,"max":45}""", campos.first().validacionesJson)
        assertEquals("3", campos.last().valorPredeterminado)
    }
}
