package com.narvive.app.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** 角色名 → 稳定渐变配色（原型屏39/角色卡的圆形头像） */
private val avatarGradients = listOf(
    0xFFDB2777 to 0xFF9D174D,
    0xFF0284C7 to 0xFF0C4A6E,
    0xFFD97706 to 0xFF92400E,
    0xFF7C3AED to 0xFF4C1D95,
    0xFF059669 to 0xFF065F46,
)

@Composable
fun RoleplayAvatar(name: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val (start, end) = avatarGradients[abs(name.hashCode()) % avatarGradients.size]
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(start), Color(end)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.34f).sp,
        )
    }
}
