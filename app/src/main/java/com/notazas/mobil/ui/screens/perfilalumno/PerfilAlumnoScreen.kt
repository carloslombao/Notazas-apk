package com.notazas.mobil.ui.screens.perfilalumno

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.notazas.mobil.datos.modelos.ActitudeSemanalOut
import com.notazas.mobil.datos.modelos.AspectoActitudeOut
import com.notazas.mobil.datos.modelos.FamiliaOut
import com.notazas.mobil.datos.modelos.FicheiroAlumnoOut
import com.notazas.mobil.datos.modelos.MateriaEvolucionOut
import com.notazas.mobil.datos.modelos.PerfilAlumnoOut
import com.notazas.mobil.datos.modelos.TitorOut
import com.notazas.mobil.datos.rede.ApiClient
import com.notazas.mobil.ui.componentes.FilaTelefono

@Composable
fun PerfilAlumnoScreen(
    alumnoId: Int,
    onVoltar: () -> Unit,
    viewModel: PerfilAlumnoViewModel = viewModel()
) {
    val estado = viewModel.estado

    LaunchedEffect(alumnoId) { viewModel.cargar(alumnoId) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onVoltar) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                "Ficha do alumno",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        when {
            estado.cargando -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            estado.erro != null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(estado.erro, color = MaterialTheme.colorScheme.error)
                }
            }

            estado.perfil != null -> {
                ContidoPerfil(
                    alumnoId = alumnoId,
                    perfil = estado.perfil,
                    aspectos = estado.aspectosActitude,
                    evolucionActitude = estado.evolucionActitude
                )
            }
        }
    }
}

@Composable
private fun ContidoPerfil(
    alumnoId: Int,
    perfil: PerfilAlumnoOut,
    aspectos: List<AspectoActitudeOut>,
    evolucionActitude: List<ActitudeSemanalOut>
) {
    var pestanaSeleccionada by remember { mutableIntStateOf(0) } // 0 = Actitude, 1 = Notas

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TarxetaResumo(alumnoId = alumnoId, perfil = perfil)

        Spacer(Modifier.height(12.dp))
        TarxetaFamilia(perfil.familia)

        Spacer(Modifier.height(16.dp))
        SegmentoPestanas(
            seleccionada = pestanaSeleccionada,
            onSeleccionar = { pestanaSeleccionada = it }
        )

        Spacer(Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            Column(Modifier.padding(16.dp)) {
                if (pestanaSeleccionada == 0) {
                    Text(
                        "Evolución actitudinal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Media semanal (escala 1-5)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    GraficoActitude(evolucion = evolucionActitude, aspectos = aspectos)
                } else {
                    perfil.evolucion.forEach { materia ->
                        TarxetaMateria(materia)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (perfil.evolucion.isEmpty()) {
                        Text("Sen materias rexistradas.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Entregas e exames", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        if (perfil.ficheiros.isEmpty()) {
            Text(
                "Aínda non hai entregas subidas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            perfil.ficheiros.forEach { ficheiro ->
                FilaFicheiro(ficheiro)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TarxetaResumo(alumnoId: Int, perfil: PerfilAlumnoOut) {


    val mediaXeral = perfil.evolucion
        .flatMap { it.por_trimestre.values }
        .filterNotNull()
        .let { if (it.isEmpty()) null else it.average() }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (perfil.ten_foto) {
                    AsyncImage(
                        model = ApiClient.urlFotoAlumno(alumnoId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    )
                } else {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${perfil.apelidos}, ${perfil.nome}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(perfil.grupo, style = MaterialTheme.typography.bodyMedium)
                }
            }


            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "MEDIA XERAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        mediaXeral?.let { "%.1f / 10".format(it) } ?: "— / 10",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                OutlinedButton(onClick = { }, enabled = false) {
                    Text("Informe")
                }
            }
        }
    }
}

@Composable
private fun TarxetaFamilia(familia: FamiliaOut) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Familia",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(8.dp))
            DespregableFamilia(familia)
        }
    }
}

@Composable
private fun DespregableFamilia(familia: FamiliaOut) {
    if (familia.nai == null && familia.pai == null) {
        Text(
            "Sen datos de contacto rexistrados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        familia.nai?.let { FilaTitor(it) }
        familia.pai?.let { FilaTitor(it) }
    }
}

@Composable
private fun FilaTitor(titor: TitorOut) {
    Column {
        Text(
            "${titor.relacion} · ${titor.nome}",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        )
        titor.telefono?.takeIf { it.isNotBlank() }?.let { FilaTelefono(nomeTitor = "${titor.relacion} · ${titor.nome}", telefono = it) }
        titor.email?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SegmentoPestanas(seleccionada: Int, onSeleccionar: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(50))
            .padding(4.dp)
    ) {
        listOf("Actitude Semanal", "Notas por Materia").forEachIndexed { i, etiqueta ->
            val activo = i == seleccionada
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (activo) MaterialTheme.colorScheme.surfaceContainerLowest else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSeleccionar(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    etiqueta,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TarxetaMateria(materia: MateriaEvolucionOut) {
    Column(Modifier.fillMaxWidth()) {
        Text(materia.materia, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            materia.por_trimestre.forEach { (nomeTrimestre, valor) ->
                Column {
                    Text(nomeTrimestre, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        valor?.let { "%.1f".format(it) } ?: "—",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaFicheiro(ficheiro: FicheiroAlumnoOut) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (ficheiro.tipo == "exame") Icons.Filled.Description else Icons.Filled.Assignment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (ficheiro.tipo == "exame") "Exame" else "Traballo",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    ficheiro.data_subida.take(10),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
