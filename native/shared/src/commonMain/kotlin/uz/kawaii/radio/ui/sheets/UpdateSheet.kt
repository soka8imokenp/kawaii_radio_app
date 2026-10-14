package uz.kawaii.radio.ui.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.kawaii.radio.InstallResult
import uz.kawaii.radio.data.UpdateInfo
import uz.kawaii.radio.ui.LocalStore
import uz.kawaii.radio.ui.home.rememberInfiniteTime
import uz.kawaii.radio.ui.kit.Key
import uz.kawaii.radio.ui.kit.Led
import uz.kawaii.radio.ui.kit.lcd
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.lcdShadow
import kotlin.math.roundToInt

private enum class Phase { Ready, Downloading, Permission, Done, Error }
private const val BLOCKS = 22
private fun mb(b: Long) = ((b / 1_048_576.0) * 10).roundToInt().let { "${it / 10}.${it % 10}" }

@Composable
fun UpdateSheet(info: UpdateInfo?, onBusy: (Boolean) -> Unit, onClose: () -> Unit) {
    val store = LocalStore.current
    val updater = store.platform.updater
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(info) }
    if (info != null) shown = info
    val u = shown

    var phase by remember { mutableStateOf(Phase.Ready) }
    var done by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(0L) }
    var file by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f

    // a fresh panel for every release shown
    LaunchedEffect(info?.build) { if (info != null) { phase = Phase.Ready; done = 0; total = 0; error = "" } }
    LaunchedEffect(phase) { onBusy(phase == Phase.Downloading) }

    fun install(path: String) {
        phase = try {
            if (updater.install(path) == InstallResult.NeedsPermission) Phase.Permission else Phase.Done
        } catch (e: Throwable) {
            error = e.message ?: "O'rnatuvchini ochib bo'lmadi"
            Phase.Error
        }
    }

    fun start() {
        val up = u ?: return
        phase = Phase.Downloading
        error = ""
        done = 0
        total = up.size
        scope.launch {
            try {
                val path = updater.download(up.apkUrl, up.build, up.size) { d, t -> done = d; total = t }
                file = path
                install(path)
            } catch (e: Throwable) {
                error = e.message ?: "Internet aloqasini tekshiring"
                phase = Phase.Error
            }
        }
    }

    // back from the "Install unknown apps" settings page: continue by itself once allowed
    LaunchedEffect(phase) {
        if (phase != Phase.Permission) return@LaunchedEffect
        store.resumed.collect { if (updater.canInstall()) install(file) }
    }

    BaseSheet(
        open = info != null,
        onClose = { if (phase != Phase.Downloading) onClose() },
        scrim = Color(30, 32, 40, (0.45f * 255).toInt()),
        radius = 30.dp,
        dismissable = phase != Phase.Downloading,
        stiffness = 360f,
        damping = 34f,
    ) {
        if (u == null) return@BaseSheet
        // header and keys stay put; the middle scrolls, so the keys never slip below the screen
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.size(48.dp, 5.dp).tray(CircleShape)) }

            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Led(true, if (phase == Phase.Error) K.ledRed else K.ledGreen, if (phase == Phase.Downloading) 1.6f else 1f)
                BasicText(
                    when (phase) { Phase.Error -> "XATOLIK"; Phase.Done -> "TAYYOR"; Phase.Permission -> "RUXSAT KERAK"; else -> "YANGILANISH" },
                    Modifier.padding(start = 8.dp).weight(1f), style = T.micro(),
                )
                BasicText("KR-01 · OTA", style = T.micro(10.sp, K.inkSoft))
            }
            BasicText(
                when (phase) { Phase.Done -> "O'rnatishga tayyor!"; Phase.Error -> "Yangilab bo'lmadi"; Phase.Permission -> "Bir qadam qoldi"; else -> "Yangi versiya chiqdi!" },
                Modifier.padding(top = 8.dp),
                style = T.pixel(24.sp, K.ink, weight = FontWeight.SemiBold, lh = 28.sp),
            )

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            // LCD with version readout, cassette and pixel progress
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().tray(RoundedCornerShape(22.dp)).padding(6.dp)) {
                Column(
                    Modifier.fillMaxWidth().lcd(RoundedCornerShape(16.dp)).border(2.dp, K.lcdBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    val ls = lcdShadow()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BasicText("HOZIR v${u.current}", style = T.pixel(14.sp, K.lcdInk, lh = 14.sp).copy(shadow = ls))
                        BasicText("→ v${u.version}", style = T.pixel(14.sp, K.lcdInk, lh = 14.sp).copy(shadow = ls))
                    }
                    Box(Modifier.padding(top = 12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Cassette(phase == Phase.Downloading, progress)
                    }
                    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        val lit = (progress * BLOCKS).roundToInt()
                        for (i in 0 until BLOCKS) {
                            val c by animateColorAsState(if (i < lit) Color(38, 46, 30, 224) else Color(38, 46, 30, 26), tween(200))
                            val pop = remember { Animatable(1f) }
                            LaunchedEffect(lit == i + 1) {
                                if (lit == i + 1) { pop.animateTo(1.35f, tween(100)); pop.animateTo(1f, tween(100)) }
                            }
                            Box(Modifier.weight(1f).height(12.dp).graphicsLayer { scaleY = pop.value }.background(c, RoundedCornerShape(1.dp)))
                        }
                    }
                    Row(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        BasicText("${(progress * 100).roundToInt()}%", style = T.pixel(26.sp, K.lcdInk, lh = 26.sp).copy(shadow = ls))
                        BasicText(
                            if (total > 0) "${mb(done)} / ${mb(total)} MB" else "${mb(u.size)} MB",
                            style = T.pixel(13.sp, K.lcdInk, lh = 13.sp).copy(shadow = ls),
                        )
                    }
                }
            }

            if (phase == Phase.Permission) {
                BasicText(
                    "Android ilovaga yangilanishni o'rnatishga ruxsat so'rayapti. «Ruxsat berish»ni bosing, ochilgan oynada «Shu manbadan ruxsat berish»ni yoqing va orqaga qayting: o'rnatish o'zi davom etadi.",
                    Modifier.padding(top = 12.dp).fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                    style = T.pixel(13.5.sp, K.ink, lh = 18.sp),
                )
            }
            if (phase == Phase.Error && error.isNotEmpty()) {
                BasicText(
                    error,
                    Modifier.padding(top = 12.dp).fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                    style = T.pixel(13.sp, K.ledRed, lh = 17.sp),
                )
            }

            // what's new (the permission hint takes its place, so the next step is obvious)
            if (phase != Phase.Permission) Column(Modifier.padding(top = 12.dp).fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                BasicText("NIMALAR YANGI", style = T.micro(10.sp, K.inkSoft))
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    u.notes.forEachIndexed { i, n ->
                        val a = remember(u.build) { Animatable(0f) }
                        LaunchedEffect(u.build) { delay(150L + i * 60L); a.animateTo(1f, tween(250)) }
                        Row(Modifier.graphicsLayer { alpha = a.value; translationX = (a.value - 1) * 8 * density }) {
                            Box(Modifier.padding(top = 6.dp).size(5.dp).background(K.ink, RoundedCornerShape(1.dp)))
                            BasicText(n, Modifier.padding(start = 8.dp), style = T.pixel(13.5.sp, K.ink, lh = 18.sp))
                        }
                    }
                }
            }

            }

            // physical keys
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().tray(RoundedCornerShape(20.dp)).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Key(RoundedCornerShape(15.dp), Modifier.weight(1f).height(48.dp), onPress = { if (phase != Phase.Downloading) onClose() }) {
                    BasicText(if (phase == Phase.Downloading) "KUTING…" else "KEYINROQ", style = T.micro())
                }
                AnimatedContent(
                    (phase == Phase.Permission) to (phase == Phase.Done),
                    Modifier.weight(1.4f),
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
                ) { (perm, ok) ->
                    when {
                        perm -> Key(RoundedCornerShape(15.dp), Modifier.fillMaxWidth().height(48.dp), onPress = {
                            runCatching { updater.openInstallSettings() }.onFailure { error = it.message ?: ""; phase = Phase.Error }
                        }) {
                            Led(true, K.ledRed, 1.4f)
                            BasicText("RUXSAT BERISH", style = T.micro())
                        }
                        ok -> Key(RoundedCornerShape(15.dp), Modifier.fillMaxWidth().height(48.dp), onPress = { install(file) }) {
                            Led(true)
                            BasicText("O'RNATISH", style = T.micro())
                        }
                        else -> Key(
                            RoundedCornerShape(15.dp), Modifier.fillMaxWidth().height(48.dp),
                            active = phase == Phase.Downloading,
                            onPress = { if (phase != Phase.Downloading) start() },
                        ) {
                            DownloadGlyph()
                            BasicText(
                                when (phase) { Phase.Downloading -> "YUKLANMOQDA"; Phase.Error -> "QAYTA URINISH"; else -> "YUKLAB OLISH" },
                                style = T.micro(),
                            )
                        }
                    }
                }
            }
            if (phase == Phase.Done) {
                BasicText(
                    "Android o'rnatish oynasida «O'rnatish» tugmasini bosing",
                    Modifier.padding(top = 8.dp).fillMaxWidth(),
                    style = T.pixel(11.5.sp, K.inkSoft).copy(textAlign = TextAlign.Center),
                )
            }
        }
    }
}

@Composable
private fun DownloadGlyph() {
    Canvas(Modifier.size(14.dp)) {
        scale(size.width / 16f, pivot = Offset.Zero) {
            drawPath(
                Path().apply {
                    moveTo(7f, 1f); lineTo(9f, 1f); lineTo(9f, 9f); lineTo(12f, 9f); lineTo(8f, 13f); lineTo(4f, 9f); lineTo(7f, 9f); close()
                    moveTo(2f, 13f); lineTo(14f, 13f); lineTo(14f, 15f); lineTo(2f, 15f); close()
                },
                K.ctlInk,
            )
        }
    }
}

/** Cassette with two reels: they spin while the update streams in, tape moves from the left reel to the right. */
@Composable
private fun Cassette(spinning: Boolean, progress: Float) {
    val time = rememberInfiniteTime()
    val spinStart = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(spinning) { spinStart.floatValue = time.value }
    val ink = K.lcdInk
    Canvas(Modifier.size(170.dp, 64.dp)) {
        val d = density
        // body
        drawRoundRect(ink, style = Stroke(2 * d), cornerRadius = CornerRadius(10 * d))
        // tape between the reels
        drawRect(ink.copy(alpha = 0.6f), Offset(52 * d, size.height / 2 - d), Size(size.width - 104 * d, 2 * d))
        // bottom notch
        val nw = 54 * d
        val nl = (size.width - nw) / 2
        val nt = size.height - 6 * d
        drawPath(
            Path().apply { moveTo(nl, size.height + d); lineTo(nl, nt + 4 * d); quadraticTo(nl, nt, nl + 4 * d, nt); lineTo(nl + nw - 4 * d, nt); quadraticTo(nl + nw, nt, nl + nw, nt + 4 * d); lineTo(nl + nw, size.height + d) },
            ink, style = Stroke(2 * d),
        )
        for ((side, cx) in listOf(-1 to 12 * d + 23 * d, 1 to size.width - 12 * d - 23 * d)) {
            val tape = if (side < 0) 1 - progress else progress
            val cy = size.height / 2
            drawCircle(Color(0xFF2F3A24).copy(alpha = 0.85f), (22 + tape * 22) * d / 2, Offset(cx, cy))
            val period = if (side < 0) 1.1f else 0.8f
            val angle = if (spinning) ((time.value - spinStart.floatValue) / period % 1f) * 360f else 0f
            translate(cx, cy) {
                rotate(angle, Offset.Zero) {
                    val k = 26 * d / 26f
                    drawCircle(Color(0xFFD7E0B6), 11 * k, Offset.Zero)
                    drawCircle(ink, 11 * k, Offset.Zero, style = Stroke(1.6f * k))
                    for (a in 0 until 6) {
                        rotate(a * 60f, Offset.Zero) {
                            drawRoundRect(ink, Offset(-1.4f * k, -10f * k), Size(2.8f * k, 4.2f * k), CornerRadius(0.6f * k))
                        }
                    }
                    drawCircle(ink, 3.2f * k, Offset.Zero)
                }
            }
        }
    }
}
