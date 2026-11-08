package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect

/**
 * The in-game menu's room over this skin when it sits in [glass]: the glass and the touch shortcut strip right under it
 * (the strip hides while the menu is open, so the menu takes its band down to the frame's foot). A strip elsewhere (the
 * DS's, under its touch screen) leaves the glass as it is.
 */
fun SkinPlacement.menuRect(glass: PlacedGlass): PxRect {
    val g = glass.rect
    val bottom = g.y + g.height
    val under = controls.filter { c ->
        c.spec.id.startsWith(STRIP_SLOT) && c.x >= g.x && c.x <= g.x + g.width &&
            c.y > bottom && c.y - c.spec.hh - bottom <= 2f * c.spec.hh   // right under it, not past another screen
    }
    if (under.isEmpty()) return g
    return PxRect(g.x, g.y, g.width, under.maxOf { it.y + it.spec.hh } - g.y)
}

/**
 * The in-game menu's room over this skin, window px; null with no glass: the two screens' room where they stand one
 * above the other ([stackedMenuRoom]), otherwise the biggest glass ([menuRect]: with the strip under it), never across
 * the frames between screens side by side.
 */
fun SkinPlacement.menuRoom(): PxRect? =
    stackedMenuRoom() ?: glasses.maxByOrNull { it.rect.width * it.rect.height }?.let(::menuRect)

/**
 * DS / 3DS on a portrait phone (user): the top screen alone being too short for the menu, the top screen's width from
 * its top down to just above the shoulder buttons (L / R, ZL / ZR under it), the frame's foot ([menuFrame]) and a gap
 * clear of them; the touch screen under it is hidden while the menu is open ([underMenu]). Only the phone's own layouts
 * ([SkinFamily.LARGE]): the unfolded Fold and a tablet give the top screen half the screen, so the menu stays in it.
 * Null otherwise.
 */
fun SkinPlacement.stackedMenuRoom(): PxRect? {
    if (spec.family != SkinFamily.LARGE) return null
    val top = glass(GlassScreen.TOP)?.rect ?: return null
    if (glass(GlassScreen.TOUCH) == null) return null
    val below = top.y + top.height
    val clear = controls.filter { it.spec.id in SHOULDERS && it.y > below }.minOfOrNull { it.y - it.spec.hh } ?: return null
    val foot = clear - (frameFoot() + SHOULDER_GAP) * k
    if (foot <= below) return null
    return PxRect(top.x, top.y, top.width, foot - top.y)
}

/** The frame's bottom border, picture px ([menuFrame] draws it under the room); 0 without a frame. */
private fun SkinPlacement.frameFoot(): Float = spec.frame?.let { (it.h - it.glass.y - it.glass.h).toFloat() } ?: 0f

/** The shoulder buttons the stretched menu stops above. */
private val SHOULDERS = setOf("L", "R", "ZL", "ZR")

/** Between the stretched menu's frame and the shoulders under it, picture px. */
private const val SHOULDER_GAP = 32f

/**
 * The skin as drawn under the menu stretched over [room] ([stackedMenuRoom]): one glass the room's size (the top
 * screen's corners), the shell drawn over the rest, so the touch screen is hidden; the pieces and controls the room
 * reaches left out (the top screen's frame, the touch screen's); and the layout's own frame round it ([menuFrame]).
 */
fun SkinPlacement.underMenu(room: PxRect): SkinPlacement {
    fun inside(r: PxRect) = r.x + r.width > room.x + 1f && r.x < room.x + room.width - 1f && r.y + r.height > room.y + 1f && r.y < room.y + room.height - 1f
    val corner = glass(GlassScreen.TOP)?.corner ?: 0f
    return copy(
        glasses = listOf(PlacedGlass(MENU_GLASS_ID, GlassScreen.TOP, room, glasses.firstOrNull()?.scale ?: k, corner)),
        pieces = pieces.filterNot { inside(it.dst) } + menuFrame(room),
        controls = controls.filterNot { it.x in room.x..(room.x + room.width) && it.y in room.y..(room.y + room.height) },
    )
}

/**
 * The layout's screen frame ([SkinLayoutSpec.frame]) round [room], window px, as the solver slices it: corners as drawn,
 * edges stretched from the 2 px slice at their middle, the middle left open; each border as thick as the art's own, so the
 * solid foot starts right at the room's foot (no see-through band under the menu). None without a frame.
 */
fun SkinPlacement.menuFrame(room: PxRect): List<SkinPiece> {
    val f = spec.frame ?: return emptyList()
    val g = f.glass
    val bl = g.x.toFloat(); val bt = g.y.toFloat(); val br = (f.w - g.x - g.w).toFloat(); val bb = (f.h - g.y - g.h).toFloat()
    val gc = g.corner.toFloat()
    val cl = maxOf(bl + gc, f.corner.toFloat()); val ct = maxOf(bt + gc, f.corner.toFloat())
    val cr = maxOf(br + gc, f.corner.toFloat()); val cb = maxOf(bb + gc, f.corner.toFloat())
    val sx0 = f.sx.toFloat(); val sy0 = f.sy.toFloat()
    val colsSrc = listOf(sx0 to cl, sx0 + f.w / 2f - 1f to 2f, sx0 + f.w - cr to cr)
    val rowsSrc = listOf(sy0 to ct, sy0 + f.h / 2f - 1f to 2f, sy0 + f.h - cb to cb)
    val fx = room.x - bl * k; val fy = room.y - bt * k
    val fw = room.width + (bl + br) * k; val fh = room.height + (bt + bb) * k
    val colsDst = listOf(fx to cl * k, fx + cl * k to fw - (cl + cr) * k, fx + fw - cr * k to cr * k)
    val rowsDst = listOf(fy to ct * k, fy + ct * k to fh - (ct + cb) * k, fy + fh - cb * k to cb * k)
    return buildList {
        for (j in 0..2) for (i in 0..2) {
            if (i == 1 && j == 1) continue
            val (dX, dW) = colsDst[i]; val (dY, dH) = rowsDst[j]
            if (dW <= 0f || dH <= 0f) continue
            add(SkinPiece(PxRect(colsSrc[i].first, rowsSrc[j].first, colsSrc[i].second, rowsSrc[j].second), PxRect(dX, dY, dW, dH), piece = MENU_FRAME_PIECE))
        }
    }
}

/** [menuFrame]'s slices' [SkinPiece.piece]: none of the layout's own pieces. */
const val MENU_FRAME_PIECE = -2

/** The stretched menu's one glass ([underMenu]). */
private const val MENU_GLASS_ID = "menu"

/** The touch shortcut slots' control ids: TOUCH_1..TOUCH_4. */
private const val STRIP_SLOT = "TOUCH_"
