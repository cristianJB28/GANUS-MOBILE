# Rutas de API usadas por la app

La app consume GANUS Operativo; no apunta a Render. La URL base predeterminada es:

```text
https://ganus-operativo-api.vercel.app/api/v1/
```

Se configura mediante `API_BASE_URL` en `gradle.properties` o en la configuración local de Gradle. Retrofit concatena estas rutas relativas y agrega el token Bearer después del inicio de sesión. El contrato completo está en [api-ganus-operativo.md](./api-ganus-operativo.md).

| Método | Ruta relativa | Uso |
|---|---|---|
| `POST` | `auth/login` | Inicia sesión y obtiene el token. |
| `GET` | `tasks` | Descarga tareas asignadas; la sincronización pagina en bloques de 500. |
| `GET` | `cows` | Descarga vacas; la sincronización pagina en bloques de 500. |
| `POST` | `tasks/{taskId}/responses` | Envía respuestas y estado de una tarea. |
| `POST` | `milkings` | Sincroniza ordeños de vacas autenticadas con identificadores idempotentes. |
| `POST` | `alerts` | Sincroniza alertas pendientes. |
| `POST` | `attachments/upload-url` | Solicita URL firmada para evidencia de tarea. |
| `POST` | `attachments/confirm` | Confirma evidencia de tarea cargada. |
| `POST` | `alerts/{alertId}/attachments/upload-url` | Solicita URL firmada para evidencia de alerta. |
| `POST` | `alerts/{alertId}/attachments/confirm` | Confirma evidencia de alerta cargada. |
| `POST` | `milkings/{milkingId}/attachments/upload-url` | Solicita URL firmada para evidencia de ordeño. |
| `POST` | `milkings/{milkingId}/attachments/confirm` | Confirma evidencia de ordeño cargada. |

La carga del archivo se realiza con `PUT` directamente a la URL firmada devuelta por la API; después se confirma mediante la ruta correspondiente. No se implementa sincronización incremental por cursor. Las publicaciones `GET /posts` no forman parte del contrato de GANUS Operativo.
