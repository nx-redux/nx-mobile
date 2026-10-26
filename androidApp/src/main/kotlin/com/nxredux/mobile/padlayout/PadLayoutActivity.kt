package com.nxredux.mobile.padlayout

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.WiiOptions
import com.nxredux.mobile.runner.MotionSensors
import com.nxredux.mobile.runner.goImmersive
import com.nxredux.mobile.runner.windowShape
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.PadLayouts
import com.nxredux.mobile.settings.PadScreen
import com.nxredux.mobile.settings.PadShape
import com.nxredux.mobile.settings.PadSlot
import com.nxredux.mobile.ui.PadEditRequest
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.PadProfile
import com.nxredux.mobile.ui.nx.nxColorsFor
import com.nxredux.mobile.ui.padlayout.PadEditorState
import com.nxredux.mobile.ui.padlayout.PadEditorWindow
import com.nxredux.mobile.ui.padlayout.PadLayoutEditor

/**
 * Controls → Pad layout's editor, its own activity so it can hold the shape's orientation (the menu
 * follows the device). Saves straight to the settings; the menu re-reads them when it returns
 * ([com.nxredux.mobile.ui.AppState.onPadLayoutEdited]).
 */
class PadLayoutActivity : ComponentActivity() {
    private lateinit var editor: PadEditorState
    private lateinit var slot: PadSlot

    override fun onCreate(savedInstanceState: Bundle?) {
        val shapeName = intent.getStringExtra(EXTRA_SHAPE)
        val shape = PadShape.entries.firstOrNull { it.name == shapeName } ?: PadShape.PORTRAIT
        requestedOrientation = if (shape == PadShape.PORTRAIT) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        super.onCreate(savedInstanceState)
        // Full screen like the game (GameActivity): the pad is edited where it will be drawn, under no system bars.
        window.goImmersive()
        val key = intent.getStringExtra(EXTRA_KEY) ?: PadLayouts.SHARED_KEY
        val tag = intent.getStringExtra(EXTRA_TAG).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Shared"
        val settings = AndroidAppSettings(this)
        // The fullest pad a console's games can show, so every group is placeable: the New 3DS pad (C-stick, ZL / ZR),
        // a Sega console's 6-button pad (each game picks 3 or 6 buttons itself; the layout is the console's), and the
        // Wii's MOTION where the phone has the sensors its Wiimote games draw it for.
        val motion = WiiOptions.appliesTo(tag) && MotionSensors(this) { _, _ -> }.hasSensors
        val profile = if (tag.isEmpty()) PadProfile.STANDARD else PadProfile.forLayoutEditor(tag, motion)
        val screen = PadScreen.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_SCREEN) } ?: PadScreen.NARROW
        slot = PadSlot(shape, screen)
        editor = PadEditorState(PadLayouts.startingLayout(settings.padLayouts, key, slot), besidePicture = shape == PadShape.OVERLAY)
        // STYLUS / SWAP: the DS and 3DS pads beside the picture draw them in game.
        val tools = NdsOptions.appliesTo(tag) || N3dsOptions.appliesTo(tag)
        setContent {
            val colors = remember { nxColorsFor(settings.accent, settings.secondaryAccent) }
            // The window as the game reads it, again after each rotation (the cutout moves to another edge) and when the insets
            // arrive: below Android 11 the cutout is only known once they are dispatched (windowShape), as in the game.
            val config = LocalConfiguration.current
            val density = LocalDensity.current
            val cutout = WindowInsets.displayCutout
            val insetKey = listOf(cutout.getTop(density), cutout.getLeft(density, LayoutDirection.Ltr), cutout.getRight(density, LayoutDirection.Ltr))
            val editorWindow = remember(config.orientation, config.screenWidthDp, config.screenHeightDp, insetKey) {
                windowShape().let { PadEditorWindow(it.screenClass, it.ydpi, it.cutoutTop.toFloat(), it.cutoutLeft.toFloat(), it.cutoutRight.toFloat()) }
            }
            NxTheme(colors = colors) {
                BackHandler { back() }
                PadLayoutEditor(
                    shape, profile, title, editor,
                    onSave = { layout ->
                        PadLayouts.afterSave(settings.padLayouts, key, slot, layout, editor.dirty)?.let {
                            settings.padLayouts = it
                            setResult(RESULT_OK)
                        }
                        finish()
                    },
                    onClose = ::finish,
                    window = editorWindow,
                    tools = tools,
                    consoleTag = tag,
                )
            }
        }
    }

    /**
     * Folded or unfolded while editing: the other screen has its own layouts and its own pad size, so start again on
     * that screen's layout. Edits made for the old screen are dropped, as they were placed against its bounds.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val screen = PadScreen.of(newConfig.smallestScreenWidthDp.toFloat())
        if (screen == slot.screen) return
        intent.putExtra(EXTRA_SCREEN, screen.name)
        recreate()
    }

    /** While the "Discard changes?" prompt shows, Back / B keep editing; only the prompt's Discard leaves. */
    private fun back() { if (editor.confirmDiscard) editor.keepEditing() else if (editor.requestCancel()) finish() }

    /** A controller's B is Back here (the editor itself is touch-driven); MainActivity maps the same key code to B. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) { if (event.repeatCount == 0) back(); return true }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val EXTRA_KEY = "key"
        private const val EXTRA_SHAPE = "shape"
        private const val EXTRA_SCREEN = "screen"
        private const val EXTRA_TAG = "tag"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, request: PadEditRequest): Intent = Intent(context, PadLayoutActivity::class.java)
            .putExtra(EXTRA_KEY, request.consoleKey)
            .putExtra(EXTRA_SHAPE, request.slot.shape.name)
            .putExtra(EXTRA_SCREEN, request.slot.screen.name)
            .putExtra(EXTRA_TAG, request.tag)
            .putExtra(EXTRA_TITLE, request.title)
    }
}
