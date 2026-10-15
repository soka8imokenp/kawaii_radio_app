package uz.kawaii.radio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import uz.kawaii.radio.ui.kit.Glyph
import uz.kawaii.radio.ui.kit.HeartIcon
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.Px
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.kit.tap
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.motionSpring

private val ITEMS = listOf(Page.Home to "Asosiy", Page.Search to "Qidiruv", Page.Chat to "Chat", Page.Liked to "Sevimli", Page.Profile to "Profil")

/** Bottom navigation as a row of key caps in one moulded rim. */
@Composable
fun NavBar(page: Page, onPage: (Page) -> Unit) {
    val sound = LocalSound.current
    val unread by LocalStore.current.chat.unread.collectAsState()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = max(8.dp, bottom))) {
        Row(
            Modifier.fillMaxWidth().tray(RoundedCornerShape(20.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            for ((key, label) in ITEMS) {
                val active = page == key
                val y by animateFloatAsState(if (active) 1f else 0f, motionSpring(800f, 30f))
                val density = LocalDensity.current.density
                Column(
                    Modifier.weight(1f).height(40.dp)
                        .graphicsLayer { translationY = y * density }
                        .raised(RoundedCornerShape(15.dp), active)
                        .tap(onClick = { if (!active) { sound.press(); onPage(key) } }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Box(Modifier.alpha(if (active) 1f else 0.75f)) {
                        when (key) {
                            Page.Home -> Px(Glyph.RADIO, 20.dp, K.ctlInk)
                            Page.Search -> Px(Glyph.SEARCH, 18.dp, K.ctlInk)
                            Page.Chat -> Box {
                                Px(Glyph.CHAT, 18.dp, K.ctlInk)
                                // unread messages: a small red LED with the count
                                if (unread > 0 && page != Page.Chat) {
                                    BasicText(
                                        if (unread > 99) "99+" else unread.toString(),
                                        Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-6).dp)
                                            .background(K.onAir, RoundedCornerShape(8.dp)).padding(horizontal = 4.dp),
                                        style = T.pixel(10.sp, androidx.compose.ui.graphics.Color.White, lh = 12.sp),
                                    )
                                }
                            }
                            Page.Liked -> HeartIcon(false, K.ctlInk)
                            Page.Profile -> Px(Glyph.USER, 18.dp, K.ctlInk)
                        }
                    }
                    BasicText(label.uppercase(), style = T.micro(10.sp, ls = 0.06.em))
                }
            }
        }
    }
}
