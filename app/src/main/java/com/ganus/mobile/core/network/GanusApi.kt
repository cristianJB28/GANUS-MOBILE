package com.ganus.mobile.core.network

import com.ganus.mobile.core.local.CampoPlantillaDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GanusApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("tasks")
    suspend fun getTasks(
        @Query("limite") limite: Int = 500,
        @Query("desplazamiento") desplazamiento: Int = 0,
    ): TaskListResponse

    @GET("cows")
    suspend fun getCows(
        @Query("limite") limite: Int = 500,
        @Query("desplazamiento") desplazamiento: Int = 0,
    ): CowListResponse

    @POST("tasks/{taskId}/responses")
    suspend fun saveTaskResponses(
        @Path("taskId") taskId: String,
        @Body request: SaveTaskResponsesRequest,
    )

    @POST("alerts")
    suspend fun createAlert(
        @Body request: CreateAlertRequest,
    ): CreateAlertResponse

    @POST("alerts/{alertId}/attachments/upload-url")
    suspend fun createAlertAttachmentUploadUrl(
        @Path("alertId") alertId: String,
        @Body request: AlertAttachmentUploadUrlRequest,
    ): AttachmentUploadUrlResponse

    @POST("alerts/{alertId}/attachments/confirm")
    suspend fun confirmAlertAttachment(
        @Path("alertId") alertId: String,
        @Body request: ConfirmAlertAttachmentRequest,
    )

    @POST("attachments/upload-url")
    suspend fun createAttachmentUploadUrl(
        @Body request: AttachmentUploadUrlRequest,
    ): AttachmentUploadUrlResponse

    @POST("attachments/confirm")
    suspend fun confirmAttachment(
        @Body request: ConfirmAttachmentRequest,
    )
}

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    @SerialName("access_token")
    val accessToken: String,
    val usuario: AuthenticatedUser,
)

@Serializable
data class AuthenticatedUser(
    val identificador: String,
    val email: String,
    val nombre: String,
    val rol: String,
)

@Serializable
data class TaskListResponse(
    val data: List<RemoteTask>,
)

@Serializable
data class CowListResponse(
    val data: List<RemoteCow>,
)

@Serializable
data class RemoteCow(
    @SerialName("identificador_remoto")
    val identificadorRemoto: String,
    val numero: String,
    val nombre: String,
    val raza: String,
    @SerialName("edad_anos")
    val edadAnos: Int? = null,
    @SerialName("numero_tag")
    val numeroTag: String? = null,
)

@Serializable
data class RemoteTask(
    @SerialName("identificador_remoto")
    val identificadorRemoto: String,
    @SerialName("identificador_plantilla")
    val identificadorPlantilla: String,
    @SerialName("version_plantilla")
    val versionPlantilla: Int,
    val titulo: String,
    val estado: String,
    val tipo: String = "FORMULARIO",
    @SerialName("identificador_asignado")
    val identificadorAsignado: String,
    val vacas: List<RemoteCow> = emptyList(),
    val campos: List<CampoPlantillaDto> = emptyList(),
    val respuestas: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class SaveTaskResponsesRequest(
    @SerialName("identificador_evento")
    val identificadorEvento: String,
    val estado: String,
    val respuestas: Map<String, JsonElement>,
)

@Serializable
data class CreateAlertRequest(
    @SerialName("identificador_remoto")
    val alertId: String,
    val titulo: String,
    val descripcion: String,
    val prioridad: String,
)

@Serializable
data class CreateAlertResponse(
    val data: RemoteAlert,
    val repetido: Boolean = false,
)

@Serializable
data class RemoteAlert(
    @SerialName("identificador_remoto")
    val alertId: String,
    val titulo: String,
    val descripcion: String,
    val prioridad: String,
    val estado: String,
)

@Serializable
data class AttachmentUploadUrlRequest(
    @SerialName("identificador_remoto_tarea")
    val taskId: String,
    @SerialName("identificador_adjunto")
    val attachmentId: String,
    @SerialName("nombre_archivo")
    val fileName: String,
    @SerialName("tipo_mime")
    val mimeType: String,
    @SerialName("tamano_bytes")
    val sizeBytes: Long,
)

@Serializable
data class AlertAttachmentUploadUrlRequest(
    @SerialName("identificador_adjunto")
    val attachmentId: String,
    @SerialName("nombre_archivo")
    val fileName: String,
    @SerialName("tipo_mime")
    val mimeType: String,
    @SerialName("tamano_bytes")
    val sizeBytes: Long,
)

@Serializable
data class AttachmentUploadUrlResponse(
    val data: SignedAttachmentUpload,
)

@Serializable
data class SignedAttachmentUpload(
    @SerialName("identificador_adjunto")
    val attachmentId: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("upload_url")
    val uploadUrl: String,
    val metodo: String,
    val headers: Map<String, String>,
)

@Serializable
data class ConfirmAttachmentRequest(
    @SerialName("identificador_adjunto")
    val attachmentId: String,
    @SerialName("identificador_remoto_tarea")
    val taskId: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("nombre_archivo")
    val fileName: String,
    @SerialName("tipo_mime")
    val mimeType: String,
    @SerialName("tamano_bytes")
    val sizeBytes: Long,
)

@Serializable
data class ConfirmAlertAttachmentRequest(
    @SerialName("identificador_adjunto")
    val attachmentId: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("nombre_archivo")
    val fileName: String,
    @SerialName("tipo_mime")
    val mimeType: String,
    @SerialName("tamano_bytes")
    val sizeBytes: Long,
)
