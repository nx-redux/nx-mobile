package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.layout.DeviceTraits
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.nxredux.mobile.layout.portraitBandPx
import com.nxredux.mobile.runner.Posture
import com.nxredux.mobile.skin.SkinChoice
import com.nxredux.mobile.skin.SkinPlacement
import kotlin.math.roundToInt

/** The portrait pad area is sized to this many centimetres so a clamp-on controller fits over it. */
const val PAD_PORTRAIT_CM = 5.8f

/**
 * True for a portrait layout (the narrow gutters, portrait sizes and art): a portrait window (or a skin's portrait-shaped
 * glass); false in landscape. Whether a pad is drawn below is [LocalPadBelow].
 */
val LocalPortrait = compositionLocalOf { false }

/** True while something sits below the screen: a pad skin round it, or a clip-on controller's room in game. */
val LocalPadBelow = compositionLocalOf { false }

/** True while the screen is inside a pad skin's frame: the frame is already a margin, so pages take the phone inset ([nxPageInset]). */
val LocalInSkin = compositionLocalOf { false }

/** The skin's glass the main menu goes in on a two-screen layout (else its only glass). */
private const val MENU_GLASS = "menu"
/** The two-screen layout's top glass: the top screen ([HandheldShell]'s top). */
private const val TOP_GLASS = "menutop"

/**
 * The screen's vertical centre in window px: the app's area under the status bar, down to the pad band or the bottom
 * without one, for content centred on the screen rather than its own box ([NxCarousel]'s game list, the Carousel Home
 * sideways). Null until measured.
 */
val LocalScreenCentreY = compositionLocalOf<Float?> { null }

/**
 * The measured app area (the whole window inside the safe insets, the pad included): the Grid's tablet-class test; the
 * screens lay out to their own body.
 */
val LocalWindowSize = compositionLocalOf { DpSize.Zero }

/**
 * The screen's own area when it is smaller than [LocalWindowSize]: a pad skin's glass. Null when the screen fills the
 * window. Whether pages split in two ([splitWindow]) is decided on it, not the window: a landscape skin's glass between
 * the side columns is near square, too narrow for two panes.
 */
val LocalScreenArea = compositionLocalOf<DpSize?> { null }

/** The area two-pane decisions ([settingsSplit] and the column browsers) read: the skin's glass, else the window. */
@Composable
fun splitWindow(): DpSize = LocalScreenArea.current ?: LocalWindowSize.current

/** The device posture in window px (a skin's hinge), supplied by the host; [Posture.Flat] where there is no fold (and on iOS). */
val LocalPosture = compositionLocalOf<Posture> { Posture.Flat }

/**
 * Whether the main menu tries its skin ([HandheldShell]): a skin is picked ([skinPicked]) and the pad shown
 * ([showPad], [com.nxredux.mobile.ui.AppState.menuPadShown]), with no controller connected ([controllerConnected]: any
 * gamepad, a clip-on and built-in controls too, as in a game); half-folded ([tabletop]) only for its two-screen layout
 * ([canTwo]). It shows when one of the skin's menu layouts also fits the window.
 */
fun menuSkinShown(skinPicked: Boolean, showPad: Boolean, controllerConnected: Boolean, canTwo: Boolean, tabletop: Boolean): Boolean =
    skinPicked && showPad && !controllerConnected && (canTwo || !tabletop)

/**
 * The main menu's frame. [skin] (a pad skin's pack, NX Carbon) with [showPad] and no [controllerConnected]
 * ([menuSkinShown]): the skin's menu layout, the whole device
 * drawn round the screen, which fills its glass ([skinPad] draws it and takes its buttons); half-folded only its
 * two-screen layout, split at the hinge. Everywhere else (no skin, the pad off, a controller, or no menu layout fits) the screen fills
 * the area, with no on-screen pad. The caller wraps this in the safe-drawing insets.
 */
@Composable
fun HandheldShell(
    screen: @Composable (modifier: Modifier) -> Unit,
    controllerConnected: Boolean = false,
    showPad: Boolean = true,
    skin: SkinPackSource? = null,
    skinPad: @Composable (Modifier, SkinPlacement, ImageBitmap, ImageBitmap?) -> Unit = { _, _, _, _ -> },
    tablet: Boolean = false,
    twoScreens: Boolean = true,
    top: (@Composable (Modifier) -> Unit)? = null,
    onTwoScreens: (Boolean) -> Unit = {},
) {
    val colors = LocalNxColors.current
    val density = LocalDensity.current
    val ydpi = rememberDisplayYdpi()
    val posture = LocalPosture.current
    var topInWindow by remember { mutableStateOf<Int?>(null) }   // the shell's top in window px, for the hinge
    // The screen and pad move between the branches below rather than being rebuilt: a rotation or a fold keeps their
    // lazy states, glides, animations and focus. The latest lambdas are read through, so the movable content stays one.
    val currentScreen by rememberUpdatedState(screen)
    val movableScreen = remember { movableContentOf { m: Modifier -> currentScreen(m) } }
    var screenCentreY by remember { mutableStateOf<Float?>(null) }
    val screenSlot = Modifier.fillMaxSize().onGloballyPositioned { screenCentreY = it.positionInWindow().y + it.size.height / 2f }
    CompositionLocalProvider(LocalScreenCentreY provides screenCentreY) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(colors.background)
                .onGloballyPositioned { topInWindow = it.positionInWindow().y.roundToInt() },
        ) {
            val portrait = maxHeight > maxWidth
            val window = DpSize(maxWidth, maxHeight)
            // The skin's menu layout for this area (flat, portrait or landscape, with the pad shown only), its picture and its pieces' picture.
            // Half-folded only the two-screen layout (a tablet-class window: the Fold's inner screen) keeps the skin, split at the hinge.
            val hingeY = (posture as? Posture.Tabletop)?.let { (it.hingeTop + it.hingeBottom) / 2f }
            val canTwo = top != null && tablet && twoScreens && portrait
            val skinPlaced = if (skin != null && menuSkinShown(skinPicked = true, showPad, controllerConnected, canTwo, tabletop = posture is Posture.Tabletop)) {
                val w = constraints.maxWidth
                // the skin runs up under the cutout (the shell starts below it), its screen too: the frame keeps its own top
                // margin and the glass reaches under the camera; the page inside clears it ([cutoutPad] below)
                val above = topInWindow ?: 0
                val h = constraints.maxHeight + above
                val dpi = (density.density * 160f).roundToInt()
                remember(skin, w, h, dpi, canTwo, hingeY) {
                    SkinChoice.placeMenu(skin.manifest, w, h, dpi, foldable = DeviceTraits.canHalfFold, tablet = tablet, hingeY = hingeY, twoScreens = canTwo)
                        ?.takeIf { posture !is Posture.Tabletop || it.glasses.any { g -> g.id == TOP_GLASS } }?.let { p ->
                        val parts = p.spec.parts?.let { skin.picture(it) ?: return@let null }
                        skin.picture(p.spec.image)?.takeIf { p.spec.parts == null || parts != null }?.let { Triple(p, it, parts) }
                    }
                }
            } else null
            val twoUp = skinPlaced?.first?.glasses?.any { it.id == TOP_GLASS } == true && top != null
            val report by rememberUpdatedState(onTwoScreens)
            DisposableEffect(twoUp) {
                report(twoUp)
                onDispose { report(false) }
            }
            if (posture is Posture.Tabletop && topInWindow == null) {
                // The first frame in tabletop: the hinge split needs the shell's position, so compose nothing rather than a
                // branch that is swapped out a frame later.
            } else if (skinPlaced != null) {
                // The skin round the screen: the screen exactly over the glass's hole.
                // Placed for the window from its top: drawn that far up, over the bars' strip.
                val (placement, picture, parts) = skinPlaced
                val above = topInWindow ?: 0
                // two screens: the menu in the touch glass, the [top] screen in the frame above the hinge
                val menuGlass = placement.glasses.firstOrNull { it.id == MENU_GLASS } ?: placement.glasses.first()
                val g = menuGlass.rect
                val topPlaced = placement.glasses.firstOrNull { it.id == TOP_GLASS }?.takeIf { top != null }
                val topGlass = topPlaced?.rect
                // the pages are drawn over the skin: clipped to the glass's rounded corners, as the frame's hole is cut
                fun corner(px: Float) = RoundedCornerShape(with(density) { px.toDp() })
                val gx = g.x.roundToInt()
                val gy = g.y.roundToInt()
                val gw = (g.x + g.width).roundToInt() - gx
                val gh = (g.y + g.height).roundToInt() - gy
                val skinW = constraints.maxWidth
                val skinH = constraints.maxHeight + above
                skinPad(
                    Modifier.layout { m, c ->
                        val p = m.measure(Constraints.fixed(skinW, skinH))
                        layout(c.maxWidth, c.maxHeight) { p.place(0, -above) }
                    },
                    placement, picture, parts,
                )
                Box(
                    Modifier.layout { m, _ ->
                        val p = m.measure(Constraints.fixed(gw, gh))
                        layout(gw, gh) { p.place(gx, gy - above) }
                    }.clip(corner(menuGlass.corner)),
                ) {
                    // the pages lay out for the glass's own shape (landscape: a wide glass between the side columns)
                    // the glass reaches under the camera cutout: the page starts below it (the shell's own top, [above])
                    val cutoutPad = with(density) { (above - g.y).coerceAtLeast(0f).toDp() }
                    val glass = with(density) { DpSize(gw.toDp(), (gh.toFloat() - cutoutPad.toPx()).coerceAtLeast(0f).toDp()) }
                    CompositionLocalProvider(
                        LocalPortrait provides (gh > gw), LocalPadBelow provides true, LocalWindowSize provides window, LocalInSkin provides true,
                        LocalScreenArea provides glass,
                    ) {
                        Box(Modifier.fillMaxSize().padding(top = cutoutPad)) { movableScreen(screenSlot) }
                    }
                }
                if (topGlass != null && top != null) {
                    val tx = topGlass.x.roundToInt()
                    val ty = topGlass.y.roundToInt()
                    val tw = (topGlass.x + topGlass.width).roundToInt() - tx
                    val th = (topGlass.y + topGlass.height).roundToInt() - ty
                    Box(
                        Modifier.layout { m, _ ->
                            val p = m.measure(Constraints.fixed(tw, th))
                            layout(tw, th) { p.place(tx, ty - above) }
                        }.clip(corner(topPlaced?.corner ?: 0f)),
                    ) {
                        val cutoutPad = with(density) { (above - topGlass.y).coerceAtLeast(0f).toDp() }
                        CompositionLocalProvider(LocalPortrait provides (th > tw), LocalWindowSize provides window, LocalInSkin provides true) {
                            Box(Modifier.fillMaxSize().padding(top = cutoutPad)) { top(Modifier.fillMaxSize()) }
                        }
                    }
                }
            } else {
                // No skin here (Pad skin Off, Menu pad Off, a controller connected, or none of its menu layouts fits): the screen fills the area.
                CompositionLocalProvider(LocalPortrait provides portrait, LocalPadBelow provides false, LocalWindowSize provides window) { movableScreen(screenSlot) }
            }
        }
    }
}
