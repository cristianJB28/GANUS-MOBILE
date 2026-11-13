# Capa de red Android

Proyecto Android con Kotlin, Retrofit, OkHttp, Kotlin Serialization y Hilt. La implementacion sigue MVVM + Repository y mantiene los resultados de red tipados para que la capa de presentacion no dependa de excepciones HTTP.

## Estructura

```text
app/src/main/java/com/ganus/mobile/
|-- core/
|   |-- di/                 # Proveedores e instalacion de dependencias Hilt
|   `-- network/            # ApiResult, NetworkError, conectividad, interceptores
`-- feature/posts/
    |-- data/               # Retrofit API y repositorio
    |-- domain/             # Modelo Post
    `-- presentation/       # ViewModel y estado de UI
```

## Configuracion

- Java 17 y Android SDK 35.
- El manifiesto permite Internet/estado de red y desactiva trafico cleartext.
- `API_BASE_URL` apunta por defecto a `https://ganus-operativo-api.vercel.app/api/v1/`; se puede configurar en `gradle.properties` o en `~/.gradle/gradle.properties` y debe ser una URL HTTPS que termine en `/`. No guardes secretos en Gradle. El contrato de la API está en [docs/api-ganus-operativo.md](./docs/api-ganus-operativo.md).
- La compilación `debug` usa la firma de depuración de Android y no es una versión de distribución confiable para usuarios. Para generar una versión `release` firmada, proporciona `GANUS_RELEASE_STORE_FILE`, `GANUS_RELEASE_STORE_PASSWORD`, `GANUS_RELEASE_KEY_ALIAS` y `GANUS_RELEASE_KEY_PASSWORD` como variables de entorno protegidas y compila `:app:assembleRelease`. No guardes el almacén de claves ni sus contraseñas en el repositorio. Para reducir advertencias de instalación en usuarios, distribuye mediante Google Play con Play App Signing; la firma por sí sola no garantiza que Play Protect deje de mostrar avisos para instalaciones laterales o aplicaciones sin reputación.
- El cliente usa timeouts finitos, JSON tolerante a campos desconocidos, cabecera `Accept` y autenticacion Bearer.
- Los reintentos tienen un limite y solo se aplican a `GET`, `HEAD` y `OPTIONS` para errores transitorios. Las solicitudes no idempotentes nunca se reintentan por este interceptor.
- La sesión autenticada (token, identificador y nombre del usuario) se conserva cifrada con Android Keystore hasta que el usuario elige **Cerrar sesión**. La app la restaura al abrirse y no la elimina por falta de conexión; si el token vence en el servidor, las llamadas requieren iniciar sesión de nuevo.

## Ejemplo de uso

El ejemplo `PostsViewModel` llama a `PostsRepository.getPosts()` y expone `StateFlow<PostsUiState>`. Una pantalla debe recolectar ese flujo de forma consciente del ciclo de vida y representar los estados `Loading`, `Success(posts)` y `Error(error)`; `NetworkError` conserva categorías tipadas para decidir el mensaje o la accion de reintento.

`PersistentAccessTokenProvider` mantiene el token cifrado y lo agrega como `Authorization: Bearer <token>` sin registrarlo. La acción **Cerrar sesión** elimina la sesión persistida explícitamente.

Los repositorios convierten respuestas y fallos mediante `safeApiCall`. La cancelacion de corrutinas se propaga sin transformarse en un error de red; el `ViewModel` cancela la carga anterior cuando se solicita una nueva.

## Pruebas

`SafeRetryInterceptorTest` verifica que un fallo temporal puede reintentar un `GET` y que un `POST` no se reintenta.

## Base de datos local

La base SQLite local con Room está implementada en `app/src/main/java/com/ganus/mobile/core/local/`. La vista previa mantiene vacas, tareas/respuestas, alertas/evidencias y ordeños localmente; las tareas asignadas por GANUS Operativo también se guardan en Room para trabajar con sus formularios dinámicos. El esquema y la guía están en [docs/base-datos-local.md](./docs/base-datos-local.md).

La pantalla **Tareas diarias** incluye ejemplos offline de formularios dinámicos de inspección de vacas, actividad de finca y mantenimiento de equipo. La propuesta JSON para publicar formularios está en [docs/contrato-staging-formularios.md](./docs/contrato-staging-formularios.md); no es el contrato de sincronización de la API.

## Interfaz inicial

La actividad principal muestra la pantalla de acceso en `app/src/main/res/layout/activity_main.xml`, con el logotipo GM, correo, contraseña y seguridad. El logotipo también aparece en la barra superior del inicio y como icono de la aplicación instalada. La contraseña se puede mostrar u ocultar y el formulario valida el correo y la contraseña. El login usa `POST /api/v1/auth/login`; luego descarga tareas y vacas con `GET /api/v1/tasks` y `GET /api/v1/cows`. El botón **Sincronizar** vuelve a descargar tareas y vacas, además de enviar alertas, ordeños autenticados pendientes y sus evidencias. Antes de completar una tarea de ordeño, la app también sincroniza los ordeños pendientes asociados. Las respuestas y evidencias de una tarea remota se guardan primero en Room. Los archivos se suben al almacenamiento con una URL firmada y las respuestas de campos IMAGE/FILE incluyen el identificador remoto del adjunto.

La opción **Explorar pantallas (vista previa)** abre `JourneyActivity`, que contiene pantallas independientes para inicio de jornada, selección e identificación de vaca, registro de litros, información adicional, lista de tareas, historial, cuenta, detalle y resumen de tarea. Los datos de demostración se almacenan en Room solo para la vista previa. En una sesión autenticada, el historial y los registros locales se filtran por el usuario conectado. La pantalla Cuenta muestra el usuario de la vista previa, su rol de operador y permite volver a la pantalla de acceso. El botón **Alerta** guarda título, prioridad, descripción y metadatos de la evidencia localmente; los ordeños de vacas autenticadas se sincronizan con la API, mientras que los datos de vista previa permanecen locales. Si falla la carga, los archivos y respuestas permanecen en el teléfono para reintentar al guardar de nuevo la tarea; las tareas completadas ofrecen **Reintentar sincronización**. La carga de evidencias está limitada a 10 MiB por archivo. El registro de litros inicia en cero para cada vaca seleccionada, los filtros leen el estado persistido de las tareas y las pantallas respetan las áreas de estado y navegación del teléfono. El escaneo de tags todavía requiere integración.
