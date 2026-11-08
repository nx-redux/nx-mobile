package com.nxredux.mobile.skin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A pad skin pack's `skin.json`: the pack's [id] and [name] and its [layouts], each one rendered picture cut into fixed
 * and repeating slices ([SliceNode]) that the app assembles to any window ([SkinSolver]). All lengths are in the
 * picture's own px, rendered at [refDpi] (the Fold's density); a window of another density scales them by
 * `densityDpi / refDpi`, so the controls keep one physical size everywhere.
 */
@Serializable
data class SkinPackManifest(
    val format: Int,
    val id: String,
    val name: String,
    val refDpi: Int = 420,
    val layouts: List<SkinLayoutSpec>,
) {
    companion object {
        const val FORMAT = 1
        private val json = Json { ignoreUnknownKeys = true }

        /** The manifest in [text]; null when it is not one this app reads (bad JSON, a newer [format]). */
        fun parse(text: String): SkinPackManifest? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()?.takeIf { it.format <= FORMAT }
    }
}

/** Which shape of device a layout draws. */
@Serializable
enum class SkinFamily {
    /** Portrait tablet / unfolded Fold: two shells joined by a hinge, top screen above it, touch screen and controls below. */
    @SerialName("tablet") TABLET,
    /** Portrait phone: one tall glass (both DS screens, or one picture), controls below. */
    @SerialName("stacked") STACKED,
    /** Shorter portrait phone (the Fold cover): big top screen, small touch screen between L and R. */
    @SerialName("large") LARGE,
    /** Portrait foldable phone half-folded (Flip flex mode): top screen above the hinge, touch screen and controls below. */
    @SerialName("flex") FLEX,
    /** Landscape: a centre console with the screens, a grip each side with the controls. */
    @SerialName("grip") GRIP,
}

/**
 * DS: two screens plus MIC and SWAP. SINGLE: one glass for any one-screen console, no MIC or SWAP. MENU: the main
 * menu's pad, one glass the menu fills and the pad buttons only (no MIC, SWAP or SAVE / LOAD / REWIND / FAST).
 */
@Serializable
enum class SkinVariant {
    @SerialName("ds") DS,
    @SerialName("single") SINGLE,
    @SerialName("menu") MENU,
    /** Game Boy, Game Boy Color, Super Game Boy: d-pad, A B, TA TB (on X Y), SELECT START MENU; no shoulders. */
    @SerialName("gb") GB,
    /** Game Boy Advance: the GB set with L R and TL TR (on L2 R2). */
    @SerialName("gba") GBA,
    /** Arcade (FBNeo): d-pad, A B X Y, L R with TB TA (turbo B / A, the app's; no core input) beside them, COIN START MENU. */
    @SerialName("arcade") ARCADE,
    /** Dreamcast discs: the stick and d-pad (or a swap between them), A B X Y as Dreamcast letters, L R triggers (L2 R2), START MENU. */
    @SerialName("dc") DC,
    /** Famicom Disk System: the GB set (d-pad, A B, TA TB, SELECT START MENU) and DISK (L: fceumm's one-press disk flip). */
    @SerialName("fds") FDS,
    /**
     * Nintendo 3DS: the DS set (two screens, A B X Y, L R, SELECT START, MENU MIC SWAP) with the Circle Pad (the stick,
     * with the d-pad), a C-stick nub (`CSTICK`, the right analog) and ZL ZR (L2 R2).
     */
    @SerialName("3ds") N3DS,
    /** The regular 3DS (New 3DS Controls off): the 3DS layouts without ZL / ZR and the C-stick nub. */
    @SerialName("3ds_regular") N3DS_REGULAR,
    /**
     * Nintendo 64: the stick and d-pad (or a swap between them), the C buttons (`CU` `CL` `CR` `CD`) in the face diamond's
     * places, B / A in their own tilted well, Z (L2) and L beside or under each other, R, START with MENU beside it.
     */
    @SerialName("n64") N64,
    /**
     * GameCube: the stick and d-pad (or a swap between them), A B X Y in the diamond (X top, Y left, B bottom, A right),
     * L R the analog triggers (L2 R2), Z (R: R1) beside or above R, the C-stick nub (`CSTICK`, the right analog), START.
     */
    @SerialName("gc") GC,
    /**
     * The Wii's Classic Controller: the left stick and d-pad (or a swap between them), A B X Y in the diamond, L ZL / ZR R
     * (L R L2 R2), the right stick (`CSTICK`), − HOME + (SELECT HOME START).
     */
    @SerialName("wii_classic") WII_CLASSIC,
    /** The Wii Remote with the Nunchuk: the stick, the Remote's 1 2 B A, SHAKE N Z / C SHAKE, − +, HOME, MOTION. */
    @SerialName("wii_nunchuk") WII_NUNCHUK,
    /** The Wii Remote held sideways: the d-pad, the 1 2 B A diamond, B (the trigger) and SHAKE shoulders, − +, HOME, MOTION. */
    @SerialName("wii_sideways") WII_SIDEWAYS,
    /** The Wii Remote held upright: the sideways picture, wired to the upright binds (1 2 B A on X Y B A). */
    @SerialName("wii_upright") WII_UPRIGHT,
    /** The PlayStation (and the PS2: the same skin): the d-pad (the left stick by a swap cap), △ ○ × □, L2 L1 / R1 R2, the right stick nub, L3 R3. */
    @SerialName("ps1") PS1,
    /** The PSP: △ ○ × □, L / R, its one analog nub (first; the d-pad by a swap cap), SELECT START. */
    @SerialName("psp") PSP,
    /** PICO-8: the d-pad, O and X as a tilted pair, START (its pause). */
    @SerialName("p8") P8,
    /** The Atari 5200: the analog joystick (first), FIRE 1 / 2, * #, PAUSE START, KEYPAD (the core's on-screen keypad). */
    @SerialName("a5200") A5200,
    /** Doom (PrBoom's Classic pad): the d-pad, A B X Y, L R with L2 R2 (weapons), SELECT START, L3 R3. */
    @SerialName("doom") DOOM,
}

/**
 * One layout: [image] (screens punched out) of [width] x [height] px, cut by [tree]. [glasses] are the screens' spots;
 * [scales] the screen scales to try in order, one value per glass (window px per game px; 0 = as big as fits). The
 * [controls] are the hit areas, in the picture's px.
 *
 * A layout built from pieces has a [frame]: then [image] is only the background (shell and board, cut by [tree] into
 * plain strips that stretch, no glasses), and the screen frame and the [pieces] (the buttons) come from the [parts]
 * picture, each placed by its own rule over it. [refW] x [refH] is the full render the frame, the pieces and the
 * [controls] were measured in.
 */
@Serializable
data class SkinLayoutSpec(
    val id: String,
    val family: SkinFamily,
    val variant: SkinVariant,
    val image: String,
    val width: Int,
    val height: Int,
    val tree: SliceNode,
    val glasses: List<GlassSpec>,
    val scales: List<List<Float>>,
    val controls: List<ControlSpec>,
    val parts: String? = null,
    val refW: Int = 0,
    val refH: Int = 0,
    val frame: FrameSpec? = null,
    val pieces: List<PieceSpec> = emptyList(),
    /**
     * Only for a window at least this tall in picture px (its height over the scale): a layout that needs real extra
     * height (the Dreamcast's stick above the d-pad on a tablet), tried before its siblings without it.
     */
    val minRefH: Int = 0,
)

/**
 * The screen frame of a layout built from pieces: [sx], [sy], [w] x [h] in the parts picture, at [x], [y] in the full
 * render, round the screen's spot [glass] (frame px). The screen keeps its shape (a game's picture, else [glass]'s own)
 * at the biggest size that fits the room the full render gives the frame (its own margins to the sides and top, and to
 * the pad below), and the frame keeps its border round it: corners of at least [corner] px as drawn, each straight edge
 * stretched from a 2 px slice at its middle. Centred in that room. [fill]: the frame takes the whole room instead (the
 * screen is then all of it inside the border; the game places its picture in there by its own rules). [strip]: the
 * on-screen buttons under the screen. [side], [top], [bottom]: the room's margins to the window's sides, top and
 * bottom in render px, when they differ from the frame's own place in the render (-1: its own). [hinge]: half-folded,
 * the room ends this far above the fold instead (-1: a layout not made for half-folding).
 */
@Serializable
data class FrameSpec(
    val sx: Int,
    val sy: Int,
    val w: Int,
    val h: Int,
    val x: Int,
    val y: Int,
    val glass: GlassSpec,
    val corner: Int = 0,
    val strip: StripSpec? = null,
    val fill: Boolean = false,
    val side: Int = -1,
    val top: Int = -1,
    val bottom: Int = -1,
    val hinge: Int = -1,
)

/**
 * The frame's on-screen buttons (SAVE LOAD REWIND FAST): an even share of the screen's width each, centred [y] px above
 * the frame's bottom, [hh] each way tall; each one's label ([labels], in the parts picture) centred on its share.
 */
@Serializable
data class StripSpec(
    val y: Int,
    val hh: Int,
    val labels: List<StripLabel>,
)

/** One on-screen button of a [StripSpec]: its control [id] and its label, [sx], [sy], [w] x [h] in the parts picture. */
@Serializable
data class StripLabel(
    val id: String,
    val sx: Int = 0,
    val sy: Int = 0,
    val w: Int = 0,
    val h: Int = 0,
)

/** Which window edge a piece keeps its distance to across. */
@Serializable
enum class PieceAnchor {
    @SerialName("left") LEFT,
    @SerialName("centre") CENTRE,
    @SerialName("right") RIGHT,
}

/** Which window edge a piece keeps its distance to up and down: the top, the bottom, or the middle (half of what the
 * window adds in height above it). */
@Serializable
enum class PieceLevel {
    @SerialName("top") TOP,
    @SerialName("middle") MIDDLE,
    @SerialName("bottom") BOTTOM,
}

/**
 * A piece (a button with its ring): [sx], [sy], [w] x [h] in the parts picture, at [x], [y] in the full render. It keeps
 * its size and its distance to its [level] edge (the bottom unless said) and, across, to its [anchor] edge (or the middle), plus [inward] px
 * further in from that edge where the window is wide enough ([SkinSolver.inwardShare]). A piece with a [glass] (the
 * large layout's touch screen in its frame) has that screen at its spot (piece px), at its drawn size. [scale]: drawn
 * at that much of its size in the parts picture (the landscape menu's smaller pads), [x], [y] its drawn top-left.
 * [strip]: on-screen buttons under its [glass] (the large layout's touch screen), as a frame's strip, [StripSpec.y]
 * from the piece's bottom.
 */
@Serializable
data class PieceSpec(
    val sx: Int,
    val sy: Int,
    val w: Int,
    val h: Int,
    val x: Int,
    val y: Int,
    val anchor: PieceAnchor,
    val inward: Int = 0,
    val glass: GlassSpec? = null,
    val level: PieceLevel = PieceLevel.BOTTOM,
    val scale: Float = 1f,
    val strip: StripSpec? = null,
    /** Picture px it may move up where a shorter top frame frees that height ([SkinSolver.placePieces] topScreen). */
    val rise: Int = 0,
    /** Its share of the height a window has over the layout's (0..1), in place of its [level]'s; negative: the level's. */
    val drop: Float = -1f,
    /**
     * More than 0: on a window wider than the render the piece grows with the room between the side clusters (its
     * glass's shape kept, upward, clear of the hinge), drawn nine-sliced with corners this many picture px.
     */
    val grow: Int = 0,
    /** The stick's knob: drawn by the app at the thumb's offset from the stick's centre, not with the still pieces. */
    val knob: Boolean = false,
    /** A [knob]'s stick: the stick control's id (`STICK`, or the 3DS C-stick's `CSTICK`). */
    val knobOf: String = "STICK",
    /** As [ControlSpec.alt]: shown only with the stick ("stick") or the d-pad ("dpad"); null: always. */
    val alt: String? = null,
)

/** How a [SliceNode] cuts its box: into side-by-side columns or stacked rows. */
@Serializable
enum class SliceSplit {
    @SerialName("cols") COLS,
    @SerialName("rows") ROWS,
}

/**
 * A box of the picture: a leaf (no [split]) drawn as is, or cut along [split] into [parts]. Leaves are copied 1:1 where
 * their part is fixed and tiled where it repeats; nothing is ever stretched.
 */
@Serializable
data class SliceNode(
    val split: SliceSplit? = null,
    val parts: List<SlicePart> = emptyList(),
)

/**
 * A [len] px slice of its parent along the parent's split. [mode]:
 * - `fixed`: keeps its length;
 * - `repeat`: shares what the window adds or takes away in proportion to its [weight] (tiled, may shrink to nothing);
 * - `glass:<id>`: shares the growth of that glass along this axis (so the glass meets its size exactly);
 * - `hinge`: fixed, and its middle lands on the window's fold when the device is half-folded;
 * - `crop`: a repeat that, taking away, shows its own bottom part at its size (its top cut off, never squeezed): a tall
 *   board's extra content under a hinge, hidden on a shorter window.
 * [node] cuts the slice further (null: a leaf).
 */
@Serializable
data class SlicePart(
    val len: Int,
    val mode: String = FIXED,
    val node: SliceNode? = null,
    val weight: Float = 1f,
    /** A repeat that only grows (a hairline of plain border that widens when the glass is narrower). */
    val shrink: Boolean = true,
) {
    val isRepeat get() = mode == REPEAT || mode == CROP
    val isCrop get() = mode == CROP
    val glass: String? get() = mode.takeIf { it.startsWith(GLASS) }?.removePrefix(GLASS)
    val isHinge get() = mode == HINGE

    companion object {
        const val FIXED = "fixed"
        const val REPEAT = "repeat"
        const val GLASS = "glass:"
        const val HINGE = "hinge"
        const val CROP = "crop"
    }
}

/** What a glass shows. */
@Serializable
enum class GlassScreen {
    /** The DS top screen. */
    @SerialName("top") TOP,
    /** The DS touch screen. */
    @SerialName("touch") TOUCH,
    /** Both DS screens stacked in one glass, top over touch. */
    @SerialName("stack") STACK,
    /** A one-screen console's picture. */
    @SerialName("picture") PICTURE,
    /** The game panel: a one-screen game's bottom screen (the fold board), sized by its piece. */
    @SerialName("panel") PANEL,
}

/** A screen spot: [x], [y], [w], [h] in the picture's px, showing [screen]; [corner]: its rounded corners' radius (picture px). */
@Serializable
data class GlassSpec(
    val id: String,
    val screen: GlassScreen,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val corner: Float = 0f,
)

/** A hit area's shape. */
@Serializable
enum class ControlShape {
    /** Circle of radius [ControlSpec.r]. */
    @SerialName("circle") CIRCLE,
    /** Rectangle [ControlSpec.hw] x [ControlSpec.hh] each way from the centre. */
    @SerialName("rect") RECT,
    /** The d-pad: a square [ControlSpec.r] each way, eight directions by angle. */
    @SerialName("dpad") DPAD,
    /** The analog stick: a circle of radius [ControlSpec.r] (its ring), the knob moving [ControlSpec.travel] at most. */
    @SerialName("stick") STICK,
}

/**
 * A control centred at [x], [y] (picture px). [id] is the app's name for it: A B X Y L R START SELECT MENU, DPAD, MIC,
 * SWAP, SAVE, LOAD, REWIND, FAST. [r] / [hw] / [hh] are the touch area; the cap itself (shaded while held) is [capR]
 * for a circle (0: three quarters of [r]), [hw] x [hh] for a rect, and arms [arm] each way across for the d-pad (0: a
 * third of [r]). In a layout built from pieces, [anchor], [inward] and [level] place it like its piece (else it is where its slice
 * lands). [label]: a word the app writes under it (the skin's picture has none there), its cap height [labelH] centred
 * [labelDy] below the control's centre, and [labelDx] across (beside the button: right-aligned left of it when
 * negative, left-aligned right of it when positive).
 */
@Serializable
data class ControlSpec(
    val id: String,
    val shape: ControlShape,
    val x: Float,
    val y: Float,
    val r: Float = 0f,
    val hw: Float = 0f,
    val hh: Float = 0f,
    val capR: Float = 0f,
    val arm: Float = 0f,
    val anchor: PieceAnchor? = null,
    val inward: Int = 0,
    val label: String? = null,
    val labelDy: Float = 0f,
    val labelDx: Float = 0f,
    val labelH: Float = 0f,
    val level: PieceLevel = PieceLevel.BOTTOM,
    /** Picture px it may move up where a shorter top frame frees that height, with its piece ([PieceSpec.rise]). */
    val rise: Int = 0,
    /** Its share of the extra height in place of its [level]'s, with its piece ([PieceSpec.drop]); negative: the level's. */
    val drop: Float = -1f,
    /** A [ControlShape.STICK]: how far (picture px) the thumb goes from where it landed for full tilt. */
    val travel: Float = 0f,
    /** A [ControlShape.STICK]: how far (picture px) the drawn knob moves at full tilt (a big knob in its hole: a little); 0: [travel]. */
    val knobTravel: Float = 0f,
    /** Shown only with the stick ("stick") or only with the d-pad ("dpad") on a layout that swaps them; null: always. */
    val alt: String? = null,
)
