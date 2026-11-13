# Base de datos local y sincronización con GANUS Operativo

## Estado actual

La aplicación incluye una base local SQLite administrada con Room, actualmente en versión 6. `BaseDatosGanus` contiene once tablas y `ModuloBaseDatos` la registra en Hilt. La base se crea como `ganus_local.db` y las migraciones conservan los datos entre versiones.

La interfaz está conectada a Room para operar offline: vacas, tareas, respuestas, alertas, metadatos de evidencias y registros de ordeño se leen o guardan localmente. El inicio de sesión descarga tareas y vacas de GANUS Operativo; las alertas, las respuestas de tareas y los ordeños de vacas autenticadas se sincronizan con la API. Al iniciar, la lista de vista previa también puede cargar tres tareas de ejemplo con plantillas dinámicas. El formulario se construye con los campos y su orden guardados en Room; no requiere una columna por campo y valida obligatoriedad y tipos antes de completar. Al adjuntar evidencia se puede elegir un archivo compatible desde el selector del sistema. Las escrituras offline se agregan en la misma transacción a `eventos_pendientes`.

## Firma para distribución Android

Las compilaciones `debug` se firman con el certificado de depuración local y no son adecuadas como firma de distribución. El tipo `release` acepta un almacén y contraseñas por variables de entorno (`GANUS_RELEASE_STORE_FILE`, `GANUS_RELEASE_STORE_PASSWORD`, `GANUS_RELEASE_KEY_ALIAS`, `GANUS_RELEASE_KEY_PASSWORD`); no se deben incluir las claves privadas en Git. La firma permite identificar al editor y actualizar la aplicación con la misma identidad, pero no puede eliminar por sí sola los avisos de Google Play Protect al instalar APKs por fuera de una tienda. Para distribuir a usuarios y obtener verificación/reputación, publica la app en Google Play usando Play App Signing. El flujo de acceso de esta vista previa tampoco es autenticación real todavía.

## Tablas implementadas

| Tabla local | Uso |
|---|---|
| `estado_sincronizacion` | Guarda el cursor y la última sincronización correcta de cada flujo. |
| `eventos_recibidos` | Conserva eventos descargados antes de aplicarlos; el identificador de evento evita procesar duplicados. |
| `plantillas_tarea` | Guarda las versiones publicadas de cada formulario; `(identificador_plantilla, version)` identifica cada versión. |
| `campos_tarea` | Define los campos de cada versión: clave, etiqueta, tipo, obligatoriedad, orden, opciones y validaciones. |
| `tareas` | Guarda cada tarea asignada y su estado, vinculada a la versión exacta de su plantilla. |
| `respuestas_tarea` | Guarda la respuesta de cada tarea/campo en una columna tipada, sin crear columnas nuevas por formulario. |
| `alertas` | Guarda título, descripción, prioridad, estado, fechas y los identificadores local y remoto de cada alerta. |
| `adjuntos` | Guarda metadatos y URI local de una foto o video asociado a una alerta, tarea u ordeño; el archivo no se guarda dentro de SQLite. |
| `eventos_pendientes` | Mantiene los eventos locales que esperan envío o reintento al servidor. |
| `vacas` | Mantiene el catálogo local de animales disponible sin conexión. |
| `registros_ordenio` | Conserva litros y datos relacionados con cada ordeño, asociados a una vaca. |

Las clases, propiedades públicas, funciones DAO y nombres de tabla están en español. Los valores de estado son constantes de dominio almacenadas como texto, por ejemplo `ABIERTA`, `PENDIENTE`, `EN_CURSO`, `COMPLETADA`, `SINCRONIZADA` y `FALLIDA`; antes de recibir datos del servidor, se debe acordar el vocabulario definitivo.

## Tareas con campos dinámicos

Los campos de un formulario son filas de `campos_tarea`, no columnas nuevas de una tabla. Si staging publica una tarea con campos distintos, se almacena una nueva versión de `plantillas_tarea` y sus correspondientes filas de `campos_tarea`.

Por ejemplo, la versión 1 puede incluir `temperatura` (número), `apetito` (opción) y `observaciones` (texto). La versión 2 puede añadir `foto`. Una tarea ya asignada sigue vinculada a la versión 1; las tareas nuevas pueden usar la versión 2. Esto evita reinterpretar respuestas existentes.

`respuestas_tarea` admite texto, número, booleano, fecha y JSON. El formulario dinámico valida el tipo, la obligatoriedad, opciones y rangos numéricos disponibles, y el repositorio comprueba que cada clave de respuesta pertenezca a la versión de plantilla asignada a esa tarea.

## Alcance actual de sincronización

La app implementa login, descarga de tareas y vacas, envío de respuestas/estado de tareas, alertas, ordeños autenticados y carga de evidencias mediante URL firmada. Los ordeños de vacas de vista previa permanecen locales. Tampoco se implementó descarga incremental por eventos/cursor ni una cola de trabajo para enviar todos los tipos de `eventos_pendientes`. Consulta [el contrato de GANUS Operativo](./api-ganus-operativo.md) para las rutas, respuestas y reglas de sincronización.

El contrato de formularios de staging está en [contrato-staging-formularios.md](./contrato-staging-formularios.md) como propuesta independiente; la sincronización implementada con el servidor está descrita en [api-ganus-operativo.md](./api-ganus-operativo.md).
