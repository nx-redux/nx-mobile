package com.nxredux.mobile.runner

import android.annotation.SuppressLint
import android.app.Presentation
import android.graphics.Color
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nxredux.mobile.ui.nx.PanelPill

/**
 * The game's window on the second display: a black [FrameLayout] with a [SurfaceView] the GL thread draws part of the
 * frame on ([onSurface]), the DS stylus cursor over it ([showPen]), and a [ComposeView] above them for the game panel
 * ([showPanel]) or the Panel pill ([showPanelPill]).
 * Compose runs on the [activity]'s lifecycle.
 */
class SecondScreenPresentation(private val activity: ComponentActivity, display: Display) : Presentation(activity, display) {
    /** The surface and its size in px when created or resized; null when it goes (the caller must stop drawing first). */
    var onSurface: (Surface?, Int, Int) -> Unit = { _, _, _ -> }

    /** One finger on the surface, as 0..1 of it; [down] false when it lifts. */
    var onTouch: (x01: Float, y01: Float, down: Boolean) -> Unit = { _, _, _ -> }

    /** The Panel pill was tapped (DS/3DS split: flip the second display to the panel). */
    var onPanelPill: () -> Unit = {}

    /** Wraps the panel and the pill in the app's theme (colours, font); identity until set. */
    var theme: @Composable (@Composable () -> Unit) -> Unit = { it() }

    private var panel by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var menu by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var panelPill by mutableStateOf(false)

    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var pen: PenDot? = null   // in a layer the size of the surface, between it and the Compose view

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No key focus: a tap here must not move the buttons off the game's window (nor Back close this window).
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        setCancelable(false)
        // Edge to edge with the bars hidden: a desktop mode's taskbar (Samsung DeX) must not cover the picture.
        window?.let { w ->
            w.goImmersive()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) w.attributes = w.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
        }
        val surfaceView = SurfaceView(context).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = Unit
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
                    onSurface(holder.surface, width, height)
                override fun surfaceDestroyed(holder: SurfaceHolder) = onSurface(null, 0, 0)
            })
            // Under the panel the game gets no touches (taps on the panel's empty parts fall through to here).
            setOnTouchListener { v, e -> if (panel == null && menu == null) touch(v.width, v.height, e); true }
        }
        val composeView = ComposeView(context).apply {
            setContent {
                // A big monitor would lay out thousands of dp wide: scale up so its short side is 720 dp (handhelds unchanged).
                // The presentation is re-created when the display's metrics change, so this is read once per window.
                val size = Point().also { this@SecondScreenPresentation.display.getRealSize(it) }
                val scaled = SecondScreenScale.density(size.x, size.y, context.resources.displayMetrics.density)
                val current = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(scaled, fontScale = current.fontScale)) {
                    theme {
                        Box(Modifier.fillMaxSize()) {
                            val content = panel
                            val inGameMenu = menu
                            when {
                                inGameMenu != null -> inGameMenu()
                                content != null -> content()
                                panelPill -> PanelPill("Panel", Modifier.align(Alignment.BottomEnd).padding(10.dp)) { onPanelPill() }
                            }
                        }
                    }
                }
            }
        }
        val penLayer = FrameLayout(context)
        pen = PenDot(penLayer)
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(surfaceView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(penLayer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(composeView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        // Bars that stay (DeX's taskbar may not hide) pad the surface and the panel in: the surface size the GL path fits
        // the picture to is the visible area only.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
    }

    // Should a key or a stick still arrive here, it is the game's (the activity's).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = activity.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean = activity.dispatchGenericMotionEvent(ev)

    /** The in-game menu on this display, over the picture and the panel; null takes it away. */
    fun showMenu(content: (@Composable () -> Unit)?) {
        if (content != null) releaseTouch()
        menu = content
    }

    /** Shows [content] over the whole display (the panel; the surface under it is not drawn on), or null to take it away. */
    fun showPanel(content: (@Composable () -> Unit)?) {
        if (content != null) releaseTouch()   // the surface stops forwarding touches under the panel: a held finger lifts now
        panel = content
    }

    /** A short toast on this display (the picture's, when the game panel is on main): the panel's "Disc N of M". */
    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    /** The DS stylus cursor centred on ([cx], [cy]), px of the surface (the bottom screen is drawn here). */
    fun showPen(cx: Float, cy: Float, pressed: Boolean) {
        pen?.show(cx, cy, pressed)
    }

    /** No stylus cursor on this display. */
    fun hidePen() {
        pen?.hide()
    }

    /** Lifts a finger still held on the surface ([onTouch] with down false); nothing when none is. */
    fun releaseTouch() {
        if (pointerId != MotionEvent.INVALID_POINTER_ID) lift()
    }

    /** The Panel pill bottom-right over the surface (DS/3DS split); hidden while the panel shows. */
    fun showPanelPill(shown: Boolean) {
        panelPill = shown
    }

    private fun touch(w: Int, h: Int, e: MotionEvent) {
        if (w <= 0 || h <= 0) return
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = e.getPointerId(0)
                onTouch(e.getX(0) / w, e.getY(0) / h, true)
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointerId)
                if (i >= 0) onTouch(e.getX(i) / w, e.getY(i) / h, true)
            }
            MotionEvent.ACTION_POINTER_UP -> if (e.getPointerId(e.actionIndex) == pointerId) lift()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lift()
        }
    }

    private fun lift() {
        pointerId = MotionEvent.INVALID_POINTER_ID
        onTouch(0f, 0f, false)
    }
}
