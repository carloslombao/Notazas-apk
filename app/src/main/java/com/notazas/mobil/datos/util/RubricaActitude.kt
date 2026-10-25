package com.notazas.mobil.datos.util

/**
 * Resumo da Rúbrica de actitude do centro (mesmos textos que
 * RESUMOS_RUBRICA de AttitudeModal.js na web). A clave é o nome do
 * aspecto; se o profesor crea aspectos propios non hai texto e a app
 * mostra só "Nivel N de 5".
 */
private val RESUMOS_RUBRICA: Map<String, Map<Int, String>> = mapOf(
    "Participación" to mapOf(
        5 to "Participa sempre, iniciativa propia",
        4 to "Participa habitualmente",
        3 to "Participa bastantes veces",
        2 to "Só ocasionalmente",
        1 to "Só se llo piden individualmente"
    ),
    "Comportamento" to mapOf(
        5 to "Sempre axeitado, respecta as normas",
        4 to "Case sempre axeitado, poucas desviacións",
        3 to "Adoita ser axeitado, bastantes desviacións",
        2 to "Non se comporta ben a maior parte do tempo",
        1 to "Moitas condutas disruptivas"
    ),
    "Atención" to mapOf(
        5 to "Sempre atento, escoita activa",
        4 to "Atento a maior parte da clase",
        3 to "Atento só unha parte, distráese bastante",
        2 to "Só atento se está motivado",
        1 to "Case nunca presta atención"
    ),
    "Traballo en Clase/Casa" to mapOf(
        5 to "Sempre completa, en prazo e correcto",
        4 to "Completa a maioría, correccións leves",
        3 to "Completa bastantes veces, tarda",
        2 to "Completa poucas veces",
        1 to "Case nunca completa as tarefas"
    )
)

fun resumoRubrica(nomeAspecto: String, nivel: Int): String? =
    RESUMOS_RUBRICA[nomeAspecto]?.get(nivel)
