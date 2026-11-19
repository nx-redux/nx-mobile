package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** A window a layout is tuned for: [w] x [h] px at [dpi]; [hingeY] while half-folded. */
data class DeviceClass(val name: String, val w: Int, val h: Int, val dpi: Int, val tablet: Boolean, val foldable: Boolean, val hingeY: Float? = null)

/** The device classes the converted layouts must place as before on (the Fold's and the Ultra's inner screens tablet-class, as the app sees them). */
val GOLDEN_DEVICES = listOf(
    DeviceClass("phone", 1080, 2520, 480, tablet = false, foldable = false),
    DeviceClass("phone_land", 2520, 1080, 480, tablet = false, foldable = false),
    DeviceClass("cover", 1248, 1972, 420, tablet = false, foldable = true),
    DeviceClass("cover_land", 1972, 1248, 420, tablet = false, foldable = true),
    DeviceClass("inner", 1848, 2448, 420, tablet = true, foldable = true),
    DeviceClass("inner_half", 1848, 2448, 420, tablet = true, foldable = true, hingeY = 1236f),
    DeviceClass("inner_land", 2448, 1848, 420, tablet = true, foldable = true),
    DeviceClass("inner_land_half", 2448, 1848, 420, tablet = true, foldable = true, hingeY = 924f),
    DeviceClass("ultra", 2256, 2504, 480, tablet = true, foldable = true),
    DeviceClass("ultra_land", 2504, 2256, 480, tablet = true, foldable = true),
    DeviceClass("ultra_half", 2256, 2504, 480, tablet = true, foldable = true, hingeY = 1252f),
    DeviceClass("ultra_land_half", 2504, 2256, 480, tablet = true, foldable = true, hingeY = 1128f),
    DeviceClass("tablet", 1440, 2304, 280, tablet = true, foldable = false),
    DeviceClass("tablet_land", 2304, 1440, 280, tablet = true, foldable = false),
    DeviceClass("flip_half", 1080, 2520, 420, tablet = false, foldable = true, hingeY = 1260f),
)

/** The consoles drawn as boxes: their golden fixture's name. */
val BOXED = linkedMapOf<SkinVariant?, String>(null to "menu", SkinVariant.N64 to "n64", SkinVariant.GB to "gb", SkinVariant.GBA to "gba",
    SkinVariant.ARCADE to "arcade", SkinVariant.DC to "dc", SkinVariant.FDS to "fds", SkinVariant.DS to "ds", SkinVariant.N3DS to "n3ds",
    SkinVariant.N3DS_REGULAR to "n3ds_regular", SkinVariant.SINGLE to "single", SkinVariant.MD to "md", SkinVariant.MD3 to "md3", SkinVariant.SATURN to "saturn", SkinVariant.PS1 to "ps1", SkinVariant.PSP to "psp", SkinVariant.GC to "gc", SkinVariant.WII_CLASSIC to "wii_classic", SkinVariant.WII_NUNCHUK to "wii_nunchuk", SkinVariant.WII_SIDEWAYS to "wii_sideways", SkinVariant.WII_UPRIGHT to "wii_upright", SkinVariant.P8 to "p8", SkinVariant.A5200 to "a5200", SkinVariant.DOOM to "doom",
    SkinVariant.SMS to "sms", SkinVariant.GG to "gg", SkinVariant.A7800 to "a7800", SkinVariant.NGP to "ngp")

/** A one-screen console's picture size, game px (null: the DS / 3DS, two screens). */
private fun pictureOf(v: SkinVariant): Pair<Float, Float>? = when (v) {
    SkinVariant.GB -> 160f to 144f
    SkinVariant.GBA -> 240f to 160f
    SkinVariant.ARCADE -> 320f to 224f
    SkinVariant.DC -> 640f to 480f
    SkinVariant.FDS -> 256f to 240f
    SkinVariant.SMS -> 256f to 192f
    SkinVariant.GG -> 160f to 144f
    SkinVariant.NGP -> 160f to 152f
    SkinVariant.DS, SkinVariant.N3DS, SkinVariant.N3DS_REGULAR -> null
    else -> 320f to 240f
}

/** [v]'s placement on [d] (the main menu's for null), with its usual picture. */
fun placeFor(pack: SkinPackManifest, v: SkinVariant?, d: DeviceClass): SkinPlacement? =
    if (v == null) SkinChoice.placeMenu(pack, d.w, d.h, d.dpi, foldable = d.foldable, tablet = d.tablet, hingeY = d.hingeY)
    else SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = pictureOf(v) == null, picture = pictureOf(v), hingeY = d.hingeY, foldable = d.foldable, variant = v)

private fun rect(r: PxRect) = buildJsonArray { add(JsonPrimitive(r.x)); add(JsonPrimitive(r.y)); add(JsonPrimitive(r.width)); add(JsonPrimitive(r.height)) }

/** What a placement must keep: its controls, pieces, glasses (window px) and its groups' ids (saved tweaks key on them). */
fun goldenOf(p: SkinPlacement?): JsonElement = if (p == null) JsonNull else buildJsonObject {
    put("controls", buildJsonArray {
        p.controls.forEach { c -> add(buildJsonObject {
            put("id", c.spec.id); put("alt", c.spec.alt ?: ""); put("x", c.x); put("y", c.y)
            put("r", c.spec.r); put("hw", c.spec.hw); put("hh", c.spec.hh)
        }) }
    })
    put("pieces", buildJsonArray {
        p.pieces.forEach { q -> add(buildJsonObject { put("dst", rect(q.dst)); put("alt", q.alt ?: ""); put("knob", q.knob); put("strip", q.strip) }) }
    })
    put("glasses", buildJsonArray { p.glasses.forEach { g -> add(buildJsonObject { put("id", g.id); put("rect", rect(g.rect)) }) } })
    put("groups", buildJsonArray { SkinGroups.of(p.spec).map { it.id }.sorted().forEach { add(JsonPrimitive(it)) } })
}

private val GOLDEN_DIR = File("src/androidHostTest/resources/skin-golden")

/** The pack's own placements, recorded from its boxes: what a change to the boxes or the box solver must keep. */
private val PACK_DIR = File("src/androidHostTest/resources/skin-pack")

fun packSnapshotExists(file: String): Boolean = File(PACK_DIR, "$file.json").exists()

fun recordPackSnapshot(file: String, pack: SkinPackManifest, v: SkinVariant?) {
    PACK_DIR.mkdirs()
    val all = buildJsonObject { GOLDEN_DEVICES.forEach { d -> put(d.name, goldenOf(placeFor(pack, v, d))) } }
    File(PACK_DIR, "$file.json").writeText(json.encodeToString(JsonElement.serializer(), all))
}

fun readPackSnapshot(file: String): JsonObject = Json.parseToJsonElement(File(PACK_DIR, "$file.json").readText()).jsonObject
private val json = Json { prettyPrint = true }

/** Writes [file]'s fixture: [v]'s placement on every [GOLDEN_DEVICES] class by the rules, every layout's boxes set aside. */
fun recordGolden(file: String, pack: SkinPackManifest, v: SkinVariant?) {
    GOLDEN_DIR.mkdirs()
    val legacy = pack.copy(layouts = pack.layouts.map { it.copy(boxes = emptyList()) })
    val all = buildJsonObject { GOLDEN_DEVICES.forEach { d -> put(d.name, goldenOf(placeFor(legacy, v, d))) } }
    File(GOLDEN_DIR, "$file.json").writeText(json.encodeToString(JsonElement.serializer(), all))
}

fun goldenExists(file: String): Boolean = File(GOLDEN_DIR, "$file.json").exists()

/**
 * Adds to [file]'s fixture the [GOLDEN_DEVICES] classes it lacks, placed from [pack] by the rules ([fromBoxes]: with its
 * boxes, for a console whose rules are already gone). The classes it has keep their recording.
 */
fun recordMissing(file: String, pack: SkinPackManifest, v: SkinVariant?, fromBoxes: Boolean) {
    val old = readGolden(file)
    val src = if (fromBoxes) pack else pack.copy(layouts = pack.layouts.map { it.copy(boxes = emptyList()) })
    val all = buildJsonObject {
        GOLDEN_DEVICES.forEach { d -> put(d.name, old[d.name] ?: goldenOf(placeFor(src, v, d))) }
    }
    File(GOLDEN_DIR, "$file.json").writeText(json.encodeToString(JsonElement.serializer(), all))
}

fun readGolden(file: String): JsonObject =
    Json.parseToJsonElement(File(GOLDEN_DIR, "$file.json").readText()).jsonObject

/**
 * [actual] places as [expected] within [tol] window px: the same controls (by id and alternate), every piece and glass
 * matched by place (order free: a box layout lists them its own way). Groups are not compared: boxes group by kind
 * ([SkinBakeTest]'s rules test), not as the old layouts' overlaps did.
 */
fun assertMatchesGolden(expected: JsonElement, actual: SkinPlacement?, tol: Float = 1f, label: String) {
    if (expected is JsonNull || actual == null) { assertEquals(expected is JsonNull, actual == null, "$label: placed / not placed"); return }
    val e = expected.jsonObject
    val got = goldenOf(actual).jsonObject
    fun f(o: JsonObject, k: String) = o.getValue(k).jsonPrimitive.float
    fun s(o: JsonObject, k: String) = o.getValue(k).jsonPrimitive.content
    fun near(a: Float, b: Float) = kotlin.math.abs(a - b) <= tol
    // controls by id and alternate (one id twice, as the sideways Wii Remote's Y cap and Y shoulder: paired by size, the
    // boxes listing them in another order)
    fun byKey(o: JsonObject) = o.getValue("controls").jsonArray.map { it.jsonObject }.groupBy { s(it, "id") + "/" + s(it, "alt") }
        .mapValues { (_, l) -> l.sortedWith(compareBy({ f(it, "r") }, { f(it, "hw") }, { f(it, "hh") })) }
    val mine = byKey(got)
    val theirs = byKey(e)
    assertEquals(theirs.keys, mine.keys, "$label: the controls")
    for ((key, list) in theirs) for ((i, c) in list.withIndex()) {
        val m = mine.getValue(key).getOrNull(i) ?: fail("$label $key: missing")
        // the touch shortcut slots: the old frame-copying rules stretched them across the frame's outer edges (a few px
        // off the even share across the glass every frame gives them now)
        val slack = if (key.startsWith("TOUCH_")) 6f else tol
        for (k in listOf("x", "y", "r", "hw", "hh")) assertTrue(kotlin.math.abs(f(c, k) - f(m, k)) <= slack, "$label $key $k: ${f(c, k)} != ${f(m, k)}")
    }
    // pieces and glasses: each expected one matched by place by one left over
    fun rectOf(o: JsonObject, k: String) = o.getValue(k).jsonArray.map { it.jsonPrimitive.float }
    fun matchAll(what: String, k: String, ex: List<JsonObject>, ac: List<JsonObject>, same: (JsonObject, JsonObject) -> Boolean) {
        assertEquals(ex.size, ac.size, "$label: the number of $what")
        val left = ac.toMutableList()
        for (o in ex) {
            val r = rectOf(o, k)
            val hit = left.indexOfFirst { a -> same(o, a) && rectOf(a, k).zip(r).all { (x, y) -> near(x, y) } }
            assertTrue(hit >= 0, "$label: no $what at $r ($o)")
            left.removeAt(hit)
        }
    }
    matchAll("pieces", "dst", e.getValue("pieces").jsonArray.map { it.jsonObject }, got.getValue("pieces").jsonArray.map { it.jsonObject }) { a, b ->
        s(a, "alt") == s(b, "alt") && a.getValue("knob").jsonPrimitive.boolean == b.getValue("knob").jsonPrimitive.boolean &&
            a.getValue("strip").jsonPrimitive.boolean == b.getValue("strip").jsonPrimitive.boolean
    }
    matchAll("glasses", "rect", e.getValue("glasses").jsonArray.map { it.jsonObject }, got.getValue("glasses").jsonArray.map { it.jsonObject }) { a, b -> s(a, "id") == s(b, "id") }
}
