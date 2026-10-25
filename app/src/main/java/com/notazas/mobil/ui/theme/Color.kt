package com.notazas.mobil.ui.theme

import androidx.compose.ui.graphics.Color

// Tokens do sistema de deseño "Notazas Core" (mesmos que na web).
// Esquema claro únicamente — o deseño non contempla modo escuro.

val Primary = Color(0xFF006C49)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF10B981)
val OnPrimaryContainer = Color(0xFF00422B)
val InversePrimary = Color(0xFF4EDEA3)

val Secondary = Color(0xFF0058BE)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFF2170E4)
val OnSecondaryContainer = Color(0xFFFEFCFF)

val Tertiary = Color(0xFF842BD2)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFC487FF)
val OnTertiaryContainer = Color(0xFF550093)

val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF93000A)

val Background = Color(0xFFF4FBF4)
val OnBackground = Color(0xFF161D19)
val Surface = Color(0xFFF4FBF4)
val OnSurface = Color(0xFF161D19)
val SurfaceVariant = Color(0xFFDDE4DD)
val OnSurfaceVariant = Color(0xFF3C4A42)
val SurfaceTint = Color(0xFF006C49)

val Outline = Color(0xFF6C7A71)
val OutlineVariant = Color(0xFFBBCABF)
val InverseSurface = Color(0xFF2B322D)
val InverseOnSurface = Color(0xFFEBF3EB)

val SurfaceDim = Color(0xFFD4DCD5)
val SurfaceBright = Color(0xFFF4FBF4)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFEEF6EE)
val SurfaceContainer = Color(0xFFE8F0E9)
val SurfaceContainerHigh = Color(0xFFE3EAE3)
val SurfaceContainerHighest = Color(0xFFDDE4DD)

// Cores de categoría (badges/columnas do caderno). Non son roles
// estándar de Material 3 — úsanse directamente nas pantallas que
// as precisen (ExameAzul = secondary, TraballoMorado = tertiary).
val ExameAzul = Secondary
val ExameAzulContainer = SecondaryContainer
val TraballoMorado = Tertiary
val TraballoMoradoContainer = TertiaryContainer
// "Ámbar cálido" para Actitude: non vén nos tokens do deseño (só se
// describe como concepto), valor propio a validar visualmente.
val ActitudeAmbar = Color(0xFF8A5300)
val ActitudeAmbarContainer = Color(0xFFFFDDB3)
