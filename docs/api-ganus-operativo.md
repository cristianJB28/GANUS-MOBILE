# API de GANUS Operativo: lo que usa esta app

Esta app habla con la API de GANUS Operativo (repositorio `ganus`, carpeta `api/`). El contrato completo, con ejemplos
y códigos de error, está en `docs/movil/README.md` de ese repositorio y en `/api/docs` de la API desplegada.
**Este documento reemplaza a `contrato-staging-formularios.md` y `base-datos-local.md` en lo que dicen de la
conexión con el servidor** (esos dos describían una conexión que todavía no existía).

- Producción de desarrollo: `https://ganus-operativo-api.vercel.app/api/v1/` (`API_BASE_URL` en `gradle.properties`).
- Autenticación: `POST auth/login` devuelve `access_token` (dura 30 días) y se envía como `Authorization: Bearer`.
  Con un 401 hay que iniciar sesión de nuevo.

## Qué llama la app

| Llamada | Cuándo |
|---|---|
| `POST auth/login` | Pantalla de acceso |
| `GET tasks`, `GET cows` | Al iniciar sesión y con **Sincronizar** (páginas de 500) |
| `POST tasks/{id}/responses` | Al guardar una tarea. Con `respuestas` vacío solo cambia el estado (así se termina una tarea de ordeño) |
| `POST milkings` | Al sincronizar y antes de terminar una tarea de ordeño: un ordeño por vaca, con el UUID del teléfono. `identificador_tarea` se envía solo cuando la tarea remota es un UUID válido. |
| `POST alerts` | Al sincronizar |
| `…/attachments/upload-url` + `PUT` al almacén + `…/attachments/confirm` | Evidencias de tareas (`attachments/…`), alertas (`alerts/{id}/attachments/…`) y ordeños (`milkings/{id}/attachments/…`) |

## Respuestas del servidor y qué hace la app

| Código | Significa | La app |
|---|---|---|
| 200 / 202 | Guardado, o recibido y asegurado | Marca como sincronizado |
| 422 | El servidor rechazó los datos y dice por qué (`detalles`) | Muestra el motivo (`mensajeParaUsuario`); reenviar igual no sirve |
| 401 | Sesión vencida | «Inicia sesión de nuevo» |
| 5xx / sin red | Falla técnica | Conserva lo local y reintenta al sincronizar |

## Reglas que conviene recordar

- **Idempotencia:** los ordeños, las alertas y los archivos llevan el UUID del teléfono; reenviarlos no duplica. Las respuestas
  de una tarea se guardan por tarea y persona (la última gana).
- **Horas:** `capturado_en`, `creada_en` y `registrado_en` son la hora real del teléfono (ISO 8601 UTC). Sin ellas, el servidor
  usa la hora en que recibe.
- **Tarea de ordeño** (`tipo: "ORDENIO"`): se termina cuando el servidor tiene el ordeño de todas sus vacas. Si falta alguno
  responde 422 `MILKING_PENDING`. Por eso la app envía los ordeños pendientes antes de terminar la tarea.
- **Vacas de la vista previa** (`vaca-023`…) no existen en el servidor: sus ordeños se quedan en el teléfono y no se envían.
- Los nombres de estado y de tipo de campo se traducen en el servidor; la app sigue usando `PENDIENTE`/`EN_CURSO`/`COMPLETADA`
  y `TEXT`/`DECIMAL`/`SELECT`…
- **Prueba sin teléfono:** `node api/scripts/simular-movil.mjs` (repositorio `ganus`) hace lo mismo que la app contra la API.
