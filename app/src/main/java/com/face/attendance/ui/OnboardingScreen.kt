package com.face.attendance.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sin
import kotlin.random.Random

/**
 * ASTROBOX 风格引导页 —— 深空渐变背景随滑动连续插值、光球持续漂浮视差、
 * 星空闪烁、内容弹簧入场、末页用户协议须勾选方可进入。
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pages = remember { onboardingPages() }
    val pageCount = pages.size
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    var agreementAccepted by remember { mutableStateOf(false) }

    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val baseIndex = position.coerceIn(0f, (pageCount - 1).toFloat())
    val i0 = baseIndex.toInt().coerceIn(0, pageCount - 1)
    val i1 = (i0 + 1).coerceAtMost(pageCount - 1)
    val frac = baseIndex - i0

    val bg = lerpColor(pages[i0].bgStart, pages[i1].bgStart, frac)
    val bg2 = lerpColor(pages[i0].bgEnd, pages[i1].bgEnd, frac)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bg, bg2)))
    ) {
        StarField()
        FloatingOrbs(position, pages)

        TextButton(
            onClick = onFinished,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 4.dp)
        ) {
            Text("跳过", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageOffset = (pagerState.currentPage - page).absoluteValue
            val offFrac = (pagerState.currentPage + pagerState.currentPageOffsetFraction - page)
            val isLast = page == pageCount - 1

            if (isLast) {
                OnboardingAgreementContent(
                    translationX = offFrac * 280f,
                    alpha = (1f - pageOffset.toFloat().coerceIn(0f, 1f)).coerceAtLeast(0.15f),
                    accepted = agreementAccepted,
                    onAcceptChange = { agreementAccepted = it }
                )
            } else {
                OnboardingPageContent(
                    page = pages[page],
                    translationX = offFrac * 280f,
                    alpha = (1f - pageOffset.toFloat().coerceIn(0f, 1f)).coerceAtLeast(0.15f),
                    scale = 1f - pageOffset.toFloat().coerceIn(0f, 1f) * 0.06f
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 36.dp, start = 28.dp, end = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    val selected = index == pagerState.currentPage
                    val animScale by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.55f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "dot$index"
                    )
                    val animAlpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.4f,
                        animationSpec = tween(400),
                        label = "dotA$index"
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 5.dp)
                            .size(9.dp)
                            .scale(animScale)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = animAlpha))
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            if (pagerState.currentPage == pageCount - 1) {
                Button(
                    onClick = onFinished,
                    enabled = agreementAccepted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = pages.last().bgStart,
                        disabledContainerColor = Color.White.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        if (agreementAccepted) "同意并开始使用" else "请先勾选同意协议",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                }
            } else {
                Button(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White
                    )
                ) {
                    Text("下一步", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun StarField() {
    val stars = remember { List(40) { StarData(it) } }
    val infinite = rememberInfiniteTransition(label = "stars")
    stars.forEachIndexed { idx, star ->
        val twinkle by infinite.animateFloat(
            initialValue = 0.15f,
            targetValue = star.maxAlpha,
            animationSpec = infiniteRepeatable(
                animation = tween(star.periodMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "twinkle$idx"
        )
        Box(
            Modifier
                .offset(x = star.x.dp, y = star.y.dp)
                .size(star.size.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = twinkle))
        )
    }
}

private class StarData(seed: Int) {
    private val r = Random(seed * 31 + 7)
    val x = r.nextFloat() * 380f
    val y = r.nextFloat() * 780f
    val size = 1f + r.nextFloat() * 2f
    val maxAlpha = 0.3f + r.nextFloat() * 0.5f
    val periodMs = 1500 + r.nextInt(2000)
}

@Composable
private fun FloatingOrbs(position: Float, pages: List<OnboardingPage>) {
    val orbConfigs = remember {
        listOf(
            Triple(0.16f, 0.30f, 180f),
            Triple(0.82f, 0.22f, 140f),
            Triple(0.50f, 0.74f, 220f)
        )
    }
    val infinite = rememberInfiniteTransition(label = "orbs")
    val floatPhases = orbConfigs.indices.map { idx ->
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(4000 + idx * 800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orb$idx"
        ).value
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val w = maxWidth
        val h = maxHeight
        orbConfigs.forEachIndexed { idx, (xRatio, yRatio, drift) ->
            val pageIdx = position.toInt().coerceIn(0, pages.size - 1)
            val nextIdx = (pageIdx + 1).coerceAtMost(pages.size - 1)
            val localFrac = (position - pageIdx).coerceIn(0f, 1f)
            val orbAccent = lerpColor(pages[pageIdx].accent, pages[nextIdx].accent, localFrac)
            val driftDp = (position * drift - idx * 60f).dp
            val floatY = (sin(floatPhases[idx]) * 24f).dp
            val floatX = (sin(floatPhases[idx] * 0.7f) * 16f).dp
            Box(
                Modifier
                    .offset(x = w * xRatio + driftDp + floatX, y = h * yRatio + floatY)
                    .graphicsLayer {
                        this.alpha = 0.55f + sin(floatPhases[idx]) * 0.15f
                    }
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(orbAccent.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    translationX: Float,
    alpha: Float,
    scale: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(top = 110.dp)
            .graphicsLayer {
                this.translationX = translationX
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        val infinite = rememberInfiniteTransition(label = "glow")
        val glow by infinite.animateFloat(
            initialValue = 0.12f,
            targetValue = 0.28f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowValue"
        )
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(page.accent.copy(alpha = glow))
                .border(1.dp, page.accent.copy(alpha = 0.35f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(page.symbol, fontSize = 56.sp, color = page.accent)
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = page.title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = page.subtitle,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 16.sp,
            lineHeight = 25.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingAgreementContent(
    translationX: Float,
    alpha: Float,
    accepted: Boolean,
    onAcceptChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 90.dp, bottom = 160.dp)
            .graphicsLayer {
                this.translationX = translationX
                this.alpha = alpha
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📜", fontSize = 28.sp)
            Spacer(Modifier.size(10.dp))
            Text(
                "用户协议与隐私政策",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = USER_AGREEMENT_TEXT,
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 13.sp,
                    lineHeight = 21.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAcceptChange(!accepted) }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = accepted,
                onCheckedChange = onAcceptChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color.White,
                    uncheckedColor = Color.White.copy(alpha = 0.5f),
                    checkmarkColor = Color(0xFF1A1B3D)
                )
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "我已阅读并同意《用户协议与隐私政策》",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp
            )
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = 1f
    )
}

private data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val symbol: String,
    val accent: Color,
    val bgStart: Color,
    val bgEnd: Color
)

private fun onboardingPages() = listOf(
    OnboardingPage(
        title = "刷脸即签到",
        subtitle = "走近镜头，自动识别身份并完成考勤\n无需打卡，无需接触",
        symbol = "✦",
        accent = Color(0xFF8B98FF),
        bgStart = Color(0xFF0B1026),
        bgEnd = Color(0xFF1A1B3D)
    ),
    OnboardingPage(
        title = "毫秒级识别",
        subtitle = "基于 FaceNet 神经网络\n端侧推理，离线可用，精准可靠",
        symbol = "◉",
        accent = Color(0xFF22D3EE),
        bgStart = Color(0xFF07182B),
        bgEnd = Color(0xFF0E2A3D)
    ),
    OnboardingPage(
        title = "语音播报",
        subtitle = "签到结果实时语音反馈\n多款免费音色可选，设置中随时切换",
        symbol = "♪",
        accent = Color(0xFFA78BFA),
        bgStart = Color(0xFF150B26),
        bgEnd = Color(0xFF241640)
    ),
    OnboardingPage(
        title = "智能自学习",
        subtitle = "识别稳定后自动补充特征样本\n越用越准，存储充足可持续累积",
        symbol = "✧",
        accent = Color(0xFF34D399),
        bgStart = Color(0xFF0B1E1A),
        bgEnd = Color(0xFF10302A)
    ),
    OnboardingPage(
        title = "用户协议",
        subtitle = "",
        symbol = "📜",
        accent = Color(0xFF8B98FF),
        bgStart = Color(0xFF0B1026),
        bgEnd = Color(0xFF1A1B3D)
    )
)

private const val USER_AGREEMENT_TEXT = """欢迎使用智能刷脸考勤系统（以下简称"本应用"）。在使用本应用之前，请您仔细阅读并充分理解以下用户协议与隐私政策的全部内容。当您勾选"我已阅读并同意"并点击"同意并开始使用"按钮，即表示您已阅读、理解并接受本协议的全部条款，本协议即构成您与本应用开发者之间具有法律效力的约定。如果您不同意本协议的任何内容，请勿继续使用本应用。

一、服务内容
1. 本应用是一款基于人脸识别技术的考勤管理工具，提供人脸特征录入、身份识别、考勤记录、语音播报等功能。
2. 本应用采用 FaceNet 神经网络模型在设备端侧完成人脸特征提取与比对，识别过程在本地完成，不将您的人脸图像上传至任何远程服务器。
3. 本应用提供五类语音播报引擎：Android 系统离线语音（完全不联网）、微软 Edge 在线语音、百度翻译网页语音、有道词典网页语音，以及百度智能云语音合成（需您自行申请并配置 API Key，有免费额度）；各引擎下提供多款音色，共十余款语音模型，您可以在设置中自由选择或切换。
4. 本应用在识别稳定后会自动采集人脸特征样本补充入库，以持续提升识别准确率，单人特征图存储上限约为 2GB，存储前会压缩至约 10KB。
5. 本应用开发者有权在必要时对服务内容进行调整、升级或中断，并会在应用内予以通知。

二、用户权利与义务
1. 您应确保录入的人脸信息为您本人所有，不得录入他人人脸特征进行冒名考勤。
2. 您不得利用本应用进行任何违反法律法规、公序良俗或侵犯他人合法权益的行为。
3. 您应妥善保管您的设备，防止他人未经授权使用本应用查看或修改考勤数据。
4. 您理解并同意，本应用提供的考勤记录仅供参考，不构成任何形式的法律证据，开发者不对考勤数据的准确性、完整性作出任何明示或暗示的保证。
5. 您有权随时在设置中切换语音模型、删除已录入的人员信息及相关特征样本。

三、隐私保护
1. 本应用高度重视您的隐私保护。您的人脸特征数据（经神经网络提取的向量表示）及人脸图像均存储在您本地设备的数据库中，不会自动上传至云端。
2. 语音播报功能在使用在线语音合成时，会根据您在设置中选择的引擎（微软 Edge、有道词典、百度翻译网页接口或百度智能云官方接口），将待播报的文本内容发送至对应的第三方在线服务进行合成，该过程中仅传输播报文本，不传输任何人脸图像或特征数据。如果您不希望任何数据通过网络传输，请在设置中选择"系统语音（离线）"模型。
3. 本应用不收集您的个人身份信息、设备标识符、位置信息或其他隐私数据。
4. 除法律法规要求或政府主管部门强制要求外，本应用开发者不会向任何第三方披露您的数据。
5. 当您删除人员信息时，相关人脸特征及图像数据将从本地数据库永久删除，不可恢复。

四、免责声明
1. 本应用按"现状"提供，开发者不对应用的可用性、准确性、可靠性、及时性作出任何保证。
2. 在任何情况下，开发者均不对因使用或无法使用本应用而导致的任何直接、间接、附带、特殊或后果性损害承担责任。
3. 本应用的识别准确率受光线、角度、遮挡、设备性能等多种因素影响，开发者不对识别结果的绝对准确性作出保证。
4. 因网络或第三方服务原因导致在线语音合成不可用时，本应用将自动降级至系统离线语音；百度翻译、有道词典等网页接口并非面向第三方公开授权的官方服务，开发者不对任何在线语音合成的质量、稳定性与可用性作出保证。
5. 您应自行承担使用本应用产生的所有风险，开发者不对因您使用本应用而产生的任何后果承担责任。

五、知识产权与开源许可
1. 本应用为开源软件，其源代码在 Apache License 2.0 许可证下公开发布。在遵守该许可证的前提下，您有权查看、使用、修改和再分发本应用的源代码，具体权利与义务以项目仓库附带的 LICENSE 文件为准。
2. 本应用的界面设计、图标、文字等内容由开发者（含 AI 辅助生成）创作，并随源代码在上述开源许可证下一并授权。
3. 本应用使用的 FaceNet 神经网络模型、各在线语音合成服务、Android 及 Jetpack 系统组件等第三方技术的知识产权归各自权利人所有，相关声明详见项目仓库中的 NOTICE 文件。
4. 您在再分发本应用或其修改版本时，应保留原始版权声明、许可证声明及第三方声明，并对您自行修改的部分独立承担责任。
5. 本应用中使用的"ASTROBOX"风格动画效果仅为界面视觉设计参考，不构成对任何商标或品牌权利的侵犯。

六、协议变更
1. 本应用开发者保留随时修改本协议的权利，修改后的协议将在应用内公布并自公布之日起生效。
2. 您在协议变更后继续使用本应用，即视为您同意修改后的协议。如果您不同意修改后的内容，请停止使用本应用并卸载。
3. 本协议的任何条款若被认定为无效或不可执行，不影响其余条款的效力。

七、适用法律与争议解决
1. 本协议的订立、效力、解释、履行及争议解决均适用中华人民共和国法律。
2. 因本协议或使用本应用产生的任何争议，双方应首先友好协商解决；协商不成的，任何一方均可向本应用开发者所在地有管辖权的人民法院提起诉讼。

八、其他
1. 本协议自您同意之日起生效，长期有效，直至您卸载本应用或本应用停止服务。
2. 本协议标题仅为方便阅读，不构成对条款内容的限制或解释。
3. 本协议最终解释权归本应用开发者所有。

感谢您的信任与支持。祝您使用愉快！"""
