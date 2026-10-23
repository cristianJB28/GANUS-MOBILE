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
- Configura `API_BASE_URL` en `gradle.properties` o en `~/.gradle/gradle.properties`; debe ser una URL HTTPS que termine en `/`. No guardes secretos en Gradle.
- El cliente usa timeouts finitos, JSON tolerante a campos desconocidos, cabecera `Accept` y autenticacion Bearer.
- Los reintentos tienen un limite y solo se aplican a `GET`, `HEAD` y `OPTIONS` para errores transitorios. Las solicitudes no idempotentes nunca se reintentan por este interceptor.
- `InMemoryAccessTokenProvider` conserva el token solo en memoria. Conectalo a tu flujo de inicio de sesion; si necesitas persistencia, usa un almacen cifrado respaldado por Android Keystore.

## Ejemplo de uso

El ejemplo `PostsViewModel` llama a `PostsRepository.getPosts()` y expone `StateFlow<PostsUiState>`. Una pantalla debe recolectar ese flujo de forma consciente del ciclo de vida y representar los estados `Loading`, `Success(posts)` y `Error(error)`; `NetworkError` conserva categorías tipadas para decidir el mensaje o la accion de reintento.

Para asignar el token recibido tras autenticar al usuario, inyecta `InMemoryAccessTokenProvider` en el componente de sesion y llama a `setAccessToken(token)`; al cerrar sesion, llama a `clearAccessToken()`. El interceptor lo agrega como `Authorization: Bearer <token>` sin registrarlo.

Los repositorios convierten respuestas y fallos mediante `safeApiCall`. La cancelacion de corrutinas se propaga sin transformarse en un error de red; el `ViewModel` cancela la carga anterior cuando se solicita una nueva.

## Pruebas

`SafeRetryInterceptorTest` verifica que un fallo temporal puede reintentar un `GET` y que un `POST` no se reintenta.
