package com.ganus.mobile.core.local

object EjemplosPlantillas {
    val inspeccionVacas = """
        {
          "identificador_remoto": "demo-template-inspeccion-vacas",
          "identificador_plantilla": "inspeccion-vacas",
          "nombre": "Inspección de vacas",
          "descripcion": "Revisión diaria de salud y condición del animal.",
          "version": 1,
          "estado": "ACTIVA",
          "campos": [
            {"clave":"animal","nombre":"Número de animal","tipo":"TEXT","obligatorio":true,"orden":1},
            {"clave":"fecha_revision","nombre":"Fecha de revisión","tipo":"DATE","obligatorio":true,"orden":2},
            {"clave":"temperatura","nombre":"Temperatura (°C)","tipo":"DECIMAL","obligatorio":true,"orden":3,"validaciones":{"min":"30","max":"45"}},
            {"clave":"apetito","nombre":"Apetito","tipo":"SELECT","obligatorio":true,"orden":4,"opciones":["Bueno","Regular","Bajo"]},
            {"clave":"signos","nombre":"Signos observados","tipo":"MULTI_SELECT","obligatorio":false,"orden":5,"opciones":["Tos","Cojera","Secreción","Ninguno"]},
            {"clave":"observaciones","nombre":"Observaciones","tipo":"TEXTAREA","obligatorio":false,"orden":6}
          ]
        }
    """.trimIndent()

    val actividadGranja = """
        {
          "identificador_remoto": "demo-template-actividad-granja",
          "identificador_plantilla": "actividad-granja",
          "nombre": "Actividad en la finca",
          "descripcion": "Registro operativo con evidencia fotográfica o documental.",
          "version": 1,
          "estado": "ACTIVA",
          "campos": [
            {"clave":"actividad","nombre":"Actividad realizada","tipo":"TEXT","obligatorio":true,"orden":1},
            {"clave":"detalle","nombre":"Detalle del trabajo","tipo":"TEXTAREA","obligatorio":true,"orden":2},
            {"clave":"hora_inicio","nombre":"Hora de inicio","tipo":"TIME","obligatorio":true,"orden":3},
            {"clave":"duracion_minutos","nombre":"Duración (minutos)","tipo":"NUMBER","obligatorio":true,"orden":4},
            {"clave":"materiales","nombre":"Materiales utilizados","tipo":"MULTI_SELECT","obligatorio":false,"orden":5,"opciones":["Guantes","Desinfectante","Herramientas","Alimento"]},
            {"clave":"requiere_seguimiento","nombre":"¿Requiere seguimiento?","tipo":"BOOLEAN","obligatorio":true,"orden":6},
            {"clave":"foto","nombre":"Foto de evidencia","tipo":"IMAGE","obligatorio":false,"orden":7},
            {"clave":"documento","nombre":"Archivo de soporte","tipo":"FILE","obligatorio":false,"orden":8}
          ]
        }
    """.trimIndent()

    val mantenimientoEquipo = """
        {
          "identificador_remoto": "demo-template-mantenimiento-equipo",
          "identificador_plantilla": "mantenimiento-equipo",
          "nombre": "Mantenimiento de equipo",
          "descripcion": "Lista de chequeo para equipos de ordeño e instalaciones.",
          "version": 1,
          "estado": "ACTIVA",
          "campos": [
            {"clave":"equipo","nombre":"Equipo revisado","tipo":"SELECT","obligatorio":true,"orden":1,"opciones":["Bomba de vacío","Tanque frío","Bebedero","Cerca eléctrica"]},
            {"clave":"estado_general","nombre":"Estado general","tipo":"SELECT","obligatorio":true,"orden":2,"opciones":["Operativo","Requiere atención","Fuera de servicio"]},
            {"clave":"componentes","nombre":"Componentes revisados","tipo":"MULTI_SELECT","obligatorio":true,"orden":3,"opciones":["Motor","Mangueras","Conexiones","Filtros","Cableado"]},
            {"clave":"fecha_hora","nombre":"Fecha y hora de revisión","tipo":"DATETIME","obligatorio":true,"orden":4},
            {"clave":"repuestos","nombre":"Cantidad de repuestos usados","tipo":"NUMBER","obligatorio":false,"orden":5,"valor_predeterminado":"0"},
            {"clave":"funciona","nombre":"¿Quedó funcionando?","tipo":"BOOLEAN","obligatorio":true,"orden":6},
            {"clave":"comentarios","nombre":"Comentarios del mantenimiento","tipo":"TEXTAREA","obligatorio":false,"orden":7}
          ]
        }
    """.trimIndent()

    val todas = listOf(inspeccionVacas, actividadGranja, mantenimientoEquipo)
}
