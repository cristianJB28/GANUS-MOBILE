package com.ganus.mobile.core.network

private const val PAGE_SIZE = 500

suspend fun GanusApi.descargarTodasLasTareas(): List<RemoteTask> = buildList {
    var desplazamiento = 0
    do {
        val pagina = getTasks(limite = PAGE_SIZE, desplazamiento = desplazamiento).data
        addAll(pagina)
        desplazamiento += pagina.size
    } while (pagina.size == PAGE_SIZE)
}

suspend fun GanusApi.descargarTodasLasVacas(): List<RemoteCow> = buildList {
    var desplazamiento = 0
    do {
        val pagina = getCows(limite = PAGE_SIZE, desplazamiento = desplazamiento).data
        addAll(pagina)
        desplazamiento += pagina.size
    } while (pagina.size == PAGE_SIZE)
}
