package com.ganus.mobile.core.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        EstadoSincronizacion::class,
        EventoRecibido::class,
        PlantillaTarea::class,
        CampoTarea::class,
        Tarea::class,
        RespuestaTarea::class,
        Alerta::class,
        Adjunto::class,
        EventoPendiente::class,
        Vaca::class,
        RegistroOrdenio::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class BaseDatosGanus : RoomDatabase() {
    abstract fun estadoSincronizacionDao(): EstadoSincronizacionDao
    abstract fun eventosRecibidosDao(): EventosRecibidosDao
    abstract fun plantillasTareaDao(): PlantillasTareaDao
    abstract fun tareasDao(): TareasDao
    abstract fun respuestasTareaDao(): RespuestasTareaDao
    abstract fun alertasDao(): AlertasDao
    abstract fun adjuntosDao(): AdjuntosDao
    abstract fun eventosPendientesDao(): EventosPendientesDao
    abstract fun vacasDao(): VacasDao
    abstract fun registrosOrdenioDao(): RegistrosOrdenioDao
}
