package com.ganus.mobile.core.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModuloBaseDatos {
    private val migracion1a2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `vacas` (
                    `identificador_local` TEXT NOT NULL,
                    `numero` TEXT NOT NULL,
                    `nombre` TEXT NOT NULL,
                    `raza` TEXT NOT NULL,
                    `edad_anos` INTEGER,
                    `numero_tag` TEXT,
                    PRIMARY KEY(`identificador_local`)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `registros_ordenio` (
                    `identificador_local` TEXT NOT NULL,
                    `identificador_remoto` TEXT,
                    `identificador_vaca` TEXT NOT NULL,
                    `litros` REAL NOT NULL,
                    `alimentacion` TEXT NOT NULL,
                    `estado_salud` TEXT NOT NULL,
                    `clima` TEXT NOT NULL,
                    `observaciones` TEXT,
                    `turno` TEXT NOT NULL,
                    `creado_en` INTEGER NOT NULL,
                    PRIMARY KEY(`identificador_local`),
                    FOREIGN KEY(`identificador_vaca`) REFERENCES `vacas`(`identificador_local`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_registros_ordenio_identificador_vaca_creado_en` ON `registros_ordenio` (`identificador_vaca`, `creado_en`)",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_registros_ordenio_identificador_remoto` ON `registros_ordenio` (`identificador_remoto`)",
            )
        }
    }

    private val migracion2a3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `plantillas_tarea` ADD COLUMN `descripcion` TEXT")
            database.execSQL("ALTER TABLE `plantillas_tarea` ADD COLUMN `estado_plantilla` TEXT NOT NULL DEFAULT 'ACTIVA'")
            database.execSQL("ALTER TABLE `plantillas_tarea` ADD COLUMN `fecha_creacion` INTEGER")
            database.execSQL("ALTER TABLE `plantillas_tarea` ADD COLUMN `ultima_sincronizacion` INTEGER")

            database.execSQL("ALTER TABLE `campos_tarea` ADD COLUMN `descripcion` TEXT")
            database.execSQL("ALTER TABLE `campos_tarea` ADD COLUMN `valor_predeterminado` TEXT")
            database.execSQL("ALTER TABLE `campos_tarea` ADD COLUMN `visibilidad` TEXT NOT NULL DEFAULT 'VISIBLE'")

            database.execSQL("ALTER TABLE `tareas` ADD COLUMN `titulo` TEXT")
            database.execSQL("ALTER TABLE `tareas` ADD COLUMN `estado_sincronizacion` TEXT NOT NULL DEFAULT 'PENDIENTE'")

            database.execSQL("ALTER TABLE `respuestas_tarea` ADD COLUMN `estado_sincronizacion` TEXT NOT NULL DEFAULT 'PENDIENTE'")
        }
    }

    private val migracion3a4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `vacas` ADD COLUMN `activa` INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE `registros_ordenio` ADD COLUMN `identificador_usuario` TEXT")
        }
    }

    private val migracion4a5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `tareas` ADD COLUMN `tipo` TEXT NOT NULL DEFAULT 'FORMULARIO'")
            database.execSQL("ALTER TABLE `tareas` ADD COLUMN `vacas_json` TEXT NOT NULL DEFAULT '[]'")
        }
    }

    private val migracion5a6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `registros_ordenio` ADD COLUMN `identificador_tarea_ordenio` TEXT")
        }
    }

    @Provides
    @Singleton
    fun proporcionarBaseDatos(
        @ApplicationContext contexto: Context,
    ): BaseDatosGanus =
        Room.databaseBuilder(
            contexto,
            BaseDatosGanus::class.java,
            "ganus_local.db",
        ).addMigrations(migracion1a2, migracion2a3, migracion3a4, migracion4a5, migracion5a6).build()

    @Provides
    fun proporcionarEstadoSincronizacionDao(
        baseDatos: BaseDatosGanus,
    ): EstadoSincronizacionDao = baseDatos.estadoSincronizacionDao()

    @Provides
    fun proporcionarEventosRecibidosDao(
        baseDatos: BaseDatosGanus,
    ): EventosRecibidosDao = baseDatos.eventosRecibidosDao()

    @Provides
    fun proporcionarPlantillasTareaDao(
        baseDatos: BaseDatosGanus,
    ): PlantillasTareaDao = baseDatos.plantillasTareaDao()

    @Provides
    fun proporcionarTareasDao(
        baseDatos: BaseDatosGanus,
    ): TareasDao = baseDatos.tareasDao()

    @Provides
    fun proporcionarRespuestasTareaDao(
        baseDatos: BaseDatosGanus,
    ): RespuestasTareaDao = baseDatos.respuestasTareaDao()

    @Provides
    fun proporcionarAlertasDao(
        baseDatos: BaseDatosGanus,
    ): AlertasDao = baseDatos.alertasDao()

    @Provides
    fun proporcionarAdjuntosDao(
        baseDatos: BaseDatosGanus,
    ): AdjuntosDao = baseDatos.adjuntosDao()

    @Provides
    fun proporcionarEventosPendientesDao(
        baseDatos: BaseDatosGanus,
    ): EventosPendientesDao = baseDatos.eventosPendientesDao()

    @Provides
    fun proporcionarVacasDao(
        baseDatos: BaseDatosGanus,
    ): VacasDao = baseDatos.vacasDao()

    @Provides
    fun proporcionarRegistrosOrdenioDao(
        baseDatos: BaseDatosGanus,
    ): RegistrosOrdenioDao = baseDatos.registrosOrdenioDao()
}
