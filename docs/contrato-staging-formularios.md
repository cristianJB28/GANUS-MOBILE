# Contrato recomendado para formularios dinámicos de staging

Esta es una propuesta para publicar formularios dinámicos, no el contrato de sincronización del servidor. La app descarga tareas y vacas de GANUS Operativo y puede representar los formularios incluidos en ellas; las rutas implementadas se describen en [el contrato de la API](./api-ganus-operativo.md). La envoltura de eventos, el cursor y las rutas de publicación descritas en este documento no están implementados por esta app. También hay tres plantillas de demostración disponibles offline en **Tareas diarias**: inspección de vacas, actividad de finca y mantenimiento de equipo. Sus payloads JSON están en `EjemplosPlantillas.kt` y se convierten en filas de `plantillas_tarea` y `campos_tarea`.

## Publicar una plantilla

Staging debería enviar una plantilla con identificadores estables y una versión entera. Cada cambio publicado en la definición debe incrementar `version`; no se deben cambiar los campos de una versión ya asignada a tareas existentes.

```json
{
  "tipo_evento": "PLANTILLA_TAREA_ACTUALIZADA",
  "identificador_evento": "evt-template-001",
  "cursor": "cursor-1042",
  "data": {
    "identificador_remoto": "template-inspeccion-001",
    "identificador_plantilla": "inspeccion-vacas",
    "nombre": "Inspección de vacas",
    "descripcion": "Revisión diaria de salud y condición del animal.",
    "version": 2,
    "estado": "ACTIVA",
    "campos": [
      {
        "clave": "animal",
        "nombre": "Número de animal",
        "tipo": "TEXT",
        "orden": 1,
        "obligatorio": true,
        "descripcion": "Número o tag visible del animal.",
        "valor_predeterminado": null,
        "visibilidad": "VISIBLE"
      },
      {
        "clave": "temperatura",
        "nombre": "Temperatura (°C)",
        "tipo": "DECIMAL",
        "orden": 2,
        "obligatorio": true,
        "validaciones": {"min": 30, "max": 45}
      },
      {
        "clave": "apetito",
        "nombre": "Apetito",
        "tipo": "SELECT",
        "orden": 3,
        "obligatorio": true,
        "opciones": ["Bueno", "Regular", "Bajo"]
      }
    ]
  }
}
```

La app de ejemplo acepta opciones como lista de textos y los alias `fields`, `key`, `label`, `type`, `required`, `order`, `options` y sus equivalentes en español. Para un contrato estable de producción, se recomienda usar los nombres en español de este documento. En esta versión, el texto de la opción es también el valor que se guarda y sincroniza.

Tipos admitidos: `TEXT`, `TEXTAREA`, `NUMBER`, `DECIMAL`, `DATE`, `TIME`, `DATETIME`, `SELECT`, `MULTI_SELECT`, `BOOLEAN`, `IMAGE` y `FILE`. Un tipo desconocido debe tratarse como texto. `opciones` aplica a selección; `validaciones` debe ser un objeto JSON; `visibilidad` puede ser `VISIBLE` u `OCULTO`.

## Asignar una tarea

Una tarea debe apuntar explícitamente a la plantilla y versión que debe usar. No enviar la definición completa repetida en cada tarea; usar el evento de plantilla y su referencia versionada.

```json
{
  "tipo_evento": "TAREA_ASIGNADA",
  "identificador_evento": "evt-task-209",
  "cursor": "cursor-1043",
  "data": {
    "identificador_remoto": "task-209",
    "identificador_plantilla": "inspeccion-vacas",
    "version_plantilla": 2,
    "titulo": "Inspeccionar vacas del lote norte",
    "estado": "PENDIENTE",
    "identificador_asignado": "user-18",
    "fecha_creacion": "2026-10-29T08:30:00Z",
    "fecha_actualizacion": "2026-10-29T08:30:00Z"
  }
}
```

El cliente debe haber recibido la versión de plantilla antes de aplicar la asignación; si no, staging debe permitir reintentar/volver a descargar la plantilla. Una tarea existente conserva su versión original aunque luego se publique otra.

## Sincronizar respuestas

Enviar respuestas como una lista de valores asociados a `clave_campo`. Los valores pueden ser strings, números, booleanos, arreglos para `MULTI_SELECT` o referencias remotas de adjuntos. No enviar binarios ni URI privadas del teléfono como contenido del campo.

```json
{
  "tipo_evento": "TAREA_RESPONDIDA",
  "identificador_evento": "evt-answer-900",
  "identificador_remoto_tarea": "task-209",
  "identificador_plantilla": "inspeccion-vacas",
  "version_plantilla": 2,
  "respuestas": [
    {"clave_campo": "animal", "valor": "023", "actualizada_en": "2026-10-29T09:10:00Z"},
    {"clave_campo": "temperatura", "valor": 38.4, "actualizada_en": "2026-10-29T09:10:00Z"},
    {"clave_campo": "apetito", "valor": "BUENO", "actualizada_en": "2026-10-29T09:10:00Z"},
    {"clave_campo": "signos", "valor": ["TOS", "COJERA"], "actualizada_en": "2026-10-29T09:10:00Z"},
    {"clave_campo": "foto", "valor": {"identificador_adjunto": "file-77"}, "actualizada_en": "2026-10-29T09:10:00Z"}
  ]
}
```

El servidor debe responder con confirmación por evento/respuesta y errores por campo, para que el cliente pueda marcar como sincronizado únicamente lo confirmado. El identificador de evento debe ser idempotente para permitir reintentos tras fallos de red.

## Reglas que debe respetar el cliente

- Aplicar evento, plantilla/campos/tarea y cursor de sincronización en una misma transacción.
- Comprobar que cada clave de respuesta exista en la versión exacta asignada a la tarea.
- Validar obligatoriedad, tipo, opciones y restricciones antes de completar una tarea.
- Guardar adjuntos como archivos locales y metadatos; subirlos por el flujo de archivos acordado y reemplazar la referencia local por el identificador remoto tras la confirmación.
- Mantener plantillas/versiones antiguas mientras existan tareas o respuestas que las referencien.

Este contrato es una propuesta para el equipo de backend, no representa endpoints ya existentes. Antes de conectar Retrofit deben confirmarse con el backend los nombres definitivos, autenticación, cursor, límites de archivos y política de errores.
