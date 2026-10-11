package com.face.attendance.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃容器 — 风格参照 belinwu/liquid-glass-compose。
 * 仅作布局承载，实际玻璃质感由内嵌的 [GlassBox] 提供。
 */
@Composable
fun GlassContainer(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 24.dp,
    tint: Color = Color.White,
    tintAlpha: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier) { content() }
}

/**
 * 现代液态玻璃片 — 风格参照 Apple Liquid Glass (iOS 26)。
 * 极通透半透明背板 + 顶部柔光 + 均匀液态亮边，避免金属渐变与厚重描边带来的复古拟物感。
 *
 * 设计要点：
 * - 背板低 alpha 均匀半透明，不抢内容
 * - 顶部柔光（0.22 alpha 白）模拟环境光自上而下漫射，柔和不锐利
 * - 边缘为均匀亮线（上 0.75 / 中 0.45 / 下 0.55），呈液态折射感而非金属边框
 * - 内容层独立，绝不被模糊，图标/文字清晰
 *
 * @param blur 保留签名兼容，当前实现不再使用（Compose blur 无法采样背景，反而显脏）
 * @param tint 玻璃主色（默认白，通透现代）
 * @param tintAlpha 玻璃主色基础不透明度，最终背板 alpha 在此基础上 +0.08
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    blur: Float = 0.4f,
    tint: Color = Color.White,
    tintAlpha: Float = 0.18f,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.clip(shape)) {
        // ── 背板：极通透均匀半透明 ──
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(tint.copy(alpha = (tintAlpha + 0.08f).coerceAtMost(0.4f)))
            }
        )
        // ── 顶部柔光：环境光漫射，现代通透感关键 ──
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.22f),
                        0.5f to Color.Transparent
                    )
                )
            }
        )
        // ── 内容层（清晰，不受任何后效影响） ──
        Box { content() }
        // ── 液态边缘：均匀亮线 + 上侧略亮，模拟折射果冻边 ──
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.75f),
                        0.5f to Color.White.copy(alpha = 0.45f),
                        1f to Color.White.copy(alpha = 0.55f)
                    ),
                    topLeft = Offset.Zero,
                    size = size,
                    style = Stroke(width = borderWidth.toPx())
                )
            }
        )
    }
}