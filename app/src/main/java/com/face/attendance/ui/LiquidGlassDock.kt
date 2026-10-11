package com.face.attendance.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.face.attendance.glass.GlassBox
import com.face.attendance.glass.GlassContainer
import kotlin.math.abs

data class DockItem(
    val label: String,
    val icon: ImageVector
)

/**
 * 底部导航栏 — 苹果 Dock 栏风格动画 + liquid-glass 玻璃质感。
 *
 * 动画设计（参照 macOS Dock / iOS）：
 * - 选中项放大 1.16，相邻项按距离衰减放大（1.10 / 1.04），呈水波放大效果
 * - 选中项下方圆点指示器淡入，未选中淡出
 * - 全程高阻尼 spring，回弹柔和顺滑
 * 玻璃质感由 [GlassContainer] + [GlassBox] 提供（参照 Android Liquid Glass 项目）。
 */
@Composable
fun LiquidGlassDock(
    items: List<DockItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 省流模式：单层近不透明背景，去除玻璃多层渐变/描边与放大动画，降低 GPU 合成开销
    if (com.face.attendance.util.PerformanceManager.lowPerformanceMode) {
        Row(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                AppleNavTab(
                    item = item,
                    selected = index == selectedIndex,
                    magnification = 1f,
                    onClick = { onSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        return
    }
    GlassContainer(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(28.dp),
            blur = 0.5f,
            tint = Color.White,
            tintAlpha = 0.12f
        ) {
            Row(
                Modifier.fillMaxWidth().fillMaxHeight().padding(6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val dist = abs(index - selectedIndex)
                    val magnification = when (dist) {
                        0 -> 1.16f
                        1 -> 1.10f
                        2 -> 1.04f
                        else -> 1.0f
                    }
                    AppleNavTab(
                        item = item,
                        selected = index == selectedIndex,
                        magnification = magnification,
                        onClick = { onSelected(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.AppleNavTab(
    item: DockItem,
    selected: Boolean,
    magnification: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 放大动画：高阻尼 spring，模拟 dock 弹性回弹
    val scale by animateFloatAsState(
        targetValue = magnification,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "magnify"
    )
    // 指示点淡入淡出
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
        label = "indicator"
    )
    // 指示点尺寸动画（0 → 4dp）
    val dotSize by animateFloatAsState(
        targetValue = if (selected) 4f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "dot"
    )
    val iconTint = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = scale; scaleY = scale
            }
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            // 苹果风格底部指示圆点
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(dotSize.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = indicatorAlpha))
            )
        }
    }
}