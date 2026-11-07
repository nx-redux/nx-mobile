package com.nxredux.mobile.skinlayout

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nxredux.mobile.layout.DeviceTraits
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.runner.Console
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.PadSkinPack
import com.nxredux.mobile.runner.SystemButtonKeys
import com.nxredux.mobile.runner.WiiPad
import com.nxredux.mobile.runner.goImmersive
import com.nxredux.mobile.runner.windowShape
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.skin.DcGames
import com.nxredux.mobile.skin.SkinChoice
import com.nxredux.mobile.skin.SkinPlacement
import com.nxredux.mobile.skin.SkinTweak
import com.nxredux.mobile.skin.SkinTweaks
import com.nxredux.mobile.ui.SkinEditRequest
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.PadProfile
import com.nxredux.mobile.ui.nx.nxColorsFor
import com.nxredux.mobile.ui.skinlayout.SkinEditorState
import com.nxredux.mobile.ui.skinlayout.SkinLayoutEditor

/**
 * Appearance → Button layout's editor: one console's pad skin buttons, held in the requested orientation, laid out as a
 * game would lay them out on this screen (the console's default emulator, its default stick or d-pad). Saves the tweaks
 * for that layout straight to the settings; the menu re-reads them when it returns
 * ([com.nxredux.mobile.ui.AppState.onSkinLayoutEdited]).
 */
class SkinLayoutActivity : ComponentActivity() {
    private var editor: SkinEditorState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val landscape = intent.getBooleanExtra(EXTRA_LANDSCAPE, false)
        requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        super.onCreate(savedInstanceState)
        // Full screen like the game: the buttons are edited where they will be drawn, under no system bars.
        window.goImmersive()
        val console = CoreRegistry.consoles.firstOrNull { it.id == intent.getStringExtra(EXTRA_CONSOLE) } ?: return finish()
        val wiiPad = WiiPad.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_WII_PAD) }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: console.displayName
        val settings = AndroidAppSettings(this)
        // the pad theme's pack, as the game reads it; NX Carbon when the theme has none or it can't be read
        val pack = settings.padTheme.skin?.let { PadSkinPack.builtIn(this, it) } ?: PadSkinPack.builtIn(this, PadTheme.NX_CARBON.skin!!)
            ?: return noLayout()
        val tag = console.defaultTagFor(settings.defaultTags[console.id])
        val bare = !settings.padSkin
        setContent {
            val colors = remember { nxColorsFor(settings.accent, settings.secondaryAccent) }
            NxTheme(colors = colors) {
                BackHandler { back() }
                BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    // until the window has turned to the asked orientation, nothing to place
                    if (w <= 0 || h <= 0 || (w > h) != landscape) return@BoxWithConstraints
                    // a new window size (folded, unfolded) is another layout: start again on it
                    val made = remember(w, h) { place(pack, console, tag, wiiPad, w, h, settings.skinTweaks) }
                    if (made == null) { LaunchedEffect(Unit) { noLayout() }; return@BoxWithConstraints }
                    val (state, spec) = made
                    SideEffect { editor = state }
                    SkinLayoutEditor(
                        state, spec.picture, spec.parts, bare, backdrop = null, title = title,
                        onSave = { tweaks -> save(console.id, spec.layoutId, tweaks); setResult(RESULT_OK); finish() },
                        onClose = ::finish,
                    )
                }
            }
        }
    }

    /** The placed layout's pictures and id, beside the editor's state. */
    private class Placed(val layoutId: String, val picture: ImageBitmap, val parts: ImageBitmap?)

    /**
     * [console]'s layout for a [w] x [h] window as the game places it ([SkinChoice.placeClosest], so a tight window still
     * opens), its stick or d-pad as the game starts it, with the stored tweaks for it; null when none fits or its picture
     * can't be read.
     */
    private fun place(pack: PadSkinPack, console: Console, tag: String, wiiPad: WiiPad?, w: Int, h: Int, stored: String): Pair<SkinEditorState, Placed>? {
        val base = CoreRegistry.baseTag(tag)
        val nds = NdsOptions.appliesTo(tag)
        val ds = nds || N3dsOptions.appliesTo(tag)
        // the 3DS: the New 3DS pad (ZL / ZR, the C-stick), the fullest one its games show
        val profile = PadProfile.forTag(tag, n3dsNew = true, wii = wiiPad)
        val variant = SkinChoice.variantFor(base, nds, arcadeSet = false, gameCube = profile.kind == PadProfile.Kind.GC, wiiPad = wiiPad, n3dsNew = profile.cStick)
        // a one-screen console's glass at 4:3, as the game places it before its first frame
        val picture = if (ds) null else (4f / 3f * 240f) to 240f
        val placed: SkinPlacement = SkinChoice.placeClosest(
            pack.manifest, w, h, resources.displayMetrics.densityDpi, windowShape().screenClass.isTablet, ds, picture,
            hingeY = null, topInset = 0, foldable = DeviceTraits.canHalfFold, variant = variant,
        ) ?: return null
        val stick = profile.stickByDefault || DcGames.stickFirst(base, arcadeSet = false) || profile.kind == PadProfile.Kind.GC
        val image = pack.picture(placed.spec.image)?.asImageBitmap() ?: return null
        val parts = placed.spec.parts?.let { pack.picture(it) }?.asImageBitmap()
        val initial = SkinTweaks.decode(stored)[console.id]?.get(placed.spec.id).orEmpty()
        return SkinEditorState(placed.forStick(stick), initial, w.toFloat(), h.toFloat()) to Placed(placed.spec.id, image, parts)
    }

    /** [tweaks] as [consoleId]'s for [layoutId]; its other layouts and every other console's stay as stored. */
    private fun save(consoleId: String, layoutId: String, tweaks: Map<String, SkinTweak>) {
        val settings = AndroidAppSettings(this)
        val all = SkinTweaks.decode(settings.skinTweaks)
        val mine = all[consoleId].orEmpty().let { if (tweaks.isEmpty()) it - layoutId else it + (layoutId to tweaks) }
        settings.skinTweaks = SkinTweaks.encode(if (mine.isEmpty()) all - consoleId else all + (consoleId to mine))
    }

    /** No layout fits this window (or the pack can't be read): say so and leave. */
    private fun noLayout() {
        Toast.makeText(this, "No button layout fits this screen", Toast.LENGTH_SHORT).show()
        finish()
    }

    /** While the "Discard changes?" prompt shows, Back / B keep editing; only the prompt's Discard leaves. */
    private fun back() {
        val e = editor ?: return finish()
        if (e.confirmDiscard) e.keepEditing() else if (e.requestCancel()) finish()
    }

    /** A bound SELECT / START / MENU button as its role ([SystemButtonKeys]), as in the menu and games. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = super.dispatchKeyEvent(SystemButtonKeys.translate(this, event))

    /** A controller's B is Back here (the editor itself is touch-driven); MainActivity maps the same key code to B. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) { if (event.repeatCount == 0) back(); return true }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val EXTRA_CONSOLE = "console"
        private const val EXTRA_LANDSCAPE = "landscape"
        private const val EXTRA_WII_PAD = "wii_pad"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, request: SkinEditRequest, title: String): Intent = Intent(context, SkinLayoutActivity::class.java)
            .putExtra(EXTRA_CONSOLE, request.consoleId)
            .putExtra(EXTRA_LANDSCAPE, request.landscape)
            .putExtra(EXTRA_WII_PAD, request.wiiPad?.name)
            .putExtra(EXTRA_TITLE, title)
    }
}
