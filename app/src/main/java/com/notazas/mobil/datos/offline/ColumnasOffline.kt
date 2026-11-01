package com.notazas.mobil.datos.offline

/**
 * IDENTIDADE DAS COLUMNAS: o nome NON é único (pode haber un exame "U1" e un
 * traballo "U1" no mesmo trimestre), así que nunca se debe usar un mapa
 * `nome -> columna`. A identidade é o `uid`; só nos snapshots vellos, sen uid,
 * recúrrese a (tipo + nome).
 */

/** Chave estable dunha columna dentro do seu trimestre. */
fun SnapshotColumna.chave(): String = uid?.takeIf { it.isNotEmpty() } ?: "$tipo|$nome"

/** Id numérico sintético da columna en modo offline (derívase da chave, non do nome). */
fun idColumnaOffline(columna: SnapshotColumna): Int = columna.chave().hashCode()

/**
 * Posición en `columnas` da columna á que se refire unha nota. Se a nota trae `columna_uid`,
 * búscase SÓ por uid (se non hai coincidencia, -1: nunca se cae ao nome). Se non o trae
 * (snapshot vello), polo nome.
 */
fun indiceColumnaDaNota(columnas: List<SnapshotColumna>, columnaUid: String?, columnaNome: String): Int =
    if (!columnaUid.isNullOrEmpty()) columnas.indexOfFirst { it.uid == columnaUid }
    else columnas.indexOfFirst { it.nome == columnaNome }

fun SnapshotNota.indiceColumna(columnas: List<SnapshotColumna>): Int =
    indiceColumnaDaNota(columnas, columna_uid, columna)

/** Recuperación elixida por unha nota: por uid; sen uid, polo nome entre as de tipo "recuperacion". */
fun indiceRecuperacionElixida(columnas: List<SnapshotColumna>, uid: String?, nome: String?): Int = when {
    !uid.isNullOrEmpty() -> columnas.indexOfFirst { it.uid == uid }
    !nome.isNullOrEmpty() -> columnas.indexOfFirst { it.nome == nome && it.tipo == "recuperacion" }
    else -> -1
}

/** Exame orixinal dunha recuperación: por uid; sen uid, polo nome entre as de tipo "exame". */
fun indiceExameOrixinal(columnas: List<SnapshotColumna>, uid: String?, nome: String?): Int = when {
    !uid.isNullOrEmpty() -> columnas.indexOfFirst { it.uid == uid }
    !nome.isNullOrEmpty() -> columnas.indexOfFirst { it.nome == nome && it.tipo == "exame" }
    else -> -1
}

/** Columna (calquera tipo, incluídas as recuperacións) polo id sintético. */
fun columnaOfflinePorId(entrada: CacheAsignacionEntrada, trimestreNome: String, id: Int): SnapshotColumna? =
    entrada.snapshot.trimestres[trimestreNome]?.columnas?.firstOrNull { idColumnaOffline(it) == id }

/** A nota dun alumno nunha columna concreta (identificada pola súa chave), ou null. */
fun SnapshotTrimestre.notaDe(claveAlumno: String, columna: SnapshotColumna): SnapshotNota? =
    notas.firstOrNull { it.alumno == claveAlumno && columnas.getOrNull(it.indiceColumna(columnas))?.chave() == columna.chave() }

/** Mesmo que [notaDe], pero devolve as notas que NON son esa (para substituíla). */
fun SnapshotTrimestre.notasSenEsta(claveAlumno: String, columna: SnapshotColumna): List<SnapshotNota> =
    notas.filterNot { it.alumno == claveAlumno && columnas.getOrNull(it.indiceColumna(columnas))?.chave() == columna.chave() }
