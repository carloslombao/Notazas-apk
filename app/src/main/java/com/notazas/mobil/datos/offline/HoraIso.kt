package com.notazas.mobil.datos.offline

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Formato EXACTO de datetime.utcnow().isoformat() en Python: sen "Z"
 * nin offset, microsegundos a 6 díxitos (ex. "2026-10-08T14:23:01.123456").
 * O servidor compara estas cadeas con datetime.fromisoformat() ao
 * decidir que versión dun snapshot é máis recente — un formato
 * distinto rompería esa comparación.
 */
fun horaIsoUtcEstiloPython(): String {
    val agora = Instant.now()
    val formatador = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC)
    val base = formatador.format(agora)
    val microsegundos = (agora.nano / 1000).toString().padStart(6, '0')
    return "$base.$microsegundos"
}
