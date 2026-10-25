package com.notazas.mobil.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// "rounded" do sistema de deseño: sm=4dp, DEFAULT=8dp, md=12dp,
// lg=16dp, xl=24dp, full=pill. Mapeado aos 5 niveis de Material 3.
val NotazasShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),      // botóns, campos de entrada
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),     // tarxetas e contedores
    extraLarge = RoundedCornerShape(24.dp) // seccións grandes
)

val FormaPill = RoundedCornerShape(percent = 50) // chips/insignias de estado
