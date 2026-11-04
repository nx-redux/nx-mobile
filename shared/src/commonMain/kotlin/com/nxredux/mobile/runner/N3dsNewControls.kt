package com.nxredux.mobile.runner

/**
 * Controls → New 3DS Controls: whether the on-screen 3DS pad draws ZL, ZR and the C-stick. Auto draws them for
 * the games in [TITLE_IDS] / [GAME_CODES]: New 3DS-only games, games that use the New 3DS's C-stick, ZL or ZR, and
 * Circle Pad Pro games (the New 3DS's C-stick, ZL and ZR stand in for the Circle Pad Pro in those, and Azahar emulates the
 * Circle Pad Pro on ir:USER as well). A file whose title cannot be read (compressed, homebrew) counts as unlisted.
 *
 * Sources:
 *  - Wikipedia, "Circle Pad Pro": the table of supported games.
 *  - Wikipedia, "New Nintendo 3DS": games exclusive to the New 3DS (Xenoblade Chronicles 3D, Fire Emblem Warriors,
 *    Minecraft: New Nintendo 3DS Edition, Runbow Pocket) and The Binding of Isaac: Rebirth (New 3DS only).
 *  - C-stick games that are not on Wikipedia's Circle Pad Pro table: Super Smash Bros. for Nintendo 3DS (C-stick smash
 *    attacks); Hyrule Warriors Legends, verified: Nintendo UK, "Master the battlefield in Hyrule Warriors: Legends" (Feb
 *    2016): "the ability to control the camera via the C Stick" on the New 3DS; Monster Hunter XX (Double Cross, Japan
 *    only), verified: Capcom support FAQ (capcom.co.jp/support/faq/platform_nintendo3ds_monsterhunter_XX_0136973.html):
 *    supports the Circle Pad Pro on the 3DS / 3DS LL, its stick for the camera and ZL/ZR assignable. Title IDs checked
 *    against 3dsdb (BZH: JPN 17D500, USA 17EA00, EUR 17EB00, KOR 189E00; AGQ: JPN 197100).
 *  - Title IDs and product codes: 3dsdb (eShop title lists per region, github.com/hax0kartik/3dsdb, jsons/list_US, _GB,
 *    _JP, _KR); demos, updates and DLC left out. Retail-only releases missing there (Metal Gear Solid Snake Eater 3D
 *    outside Japan, Ace Combat Assault Horizon Legacy in Europe ...) match by their 3-letter game code ([GAME_CODES]),
 *    which every region shares ("AJR" of CTR-P-AJRE / AJRP / AJRJ).
 *  Not listed, no title ID or code found: Dragon Quest X (3DS), Attack on Titan: Humanity in Chains.
 */
object N3dsNewControls {
    const val AUTO = "auto"
    const val ON = "on"
    const val OFF = "off"

    /** Whether the pad draws the New 3DS controls: On / Off as set, Auto (or any other value) when [title] is listed. */
    fun resolve(setting: String?, title: N3dsTitle?): Boolean = when (setting) {
        ON -> true
        OFF -> false
        else -> listed(title)
    }

    /** Whether [title] is a listed game, by title ID or game code. */
    fun listed(title: N3dsTitle?): Boolean {
        if (title == null) return false
        return (title.titleId != null && title.titleId in TITLE_IDS) || title.gameCode in GAME_CODES
    }

    /** The 3-letter game codes of the listed games, region-free. */
    val GAME_CODES: Set<String> = setOf(
        // Circle Pad Pro games
        "AMH",   // Monster Hunter 3 Ultimate / 3G
        "AH4",   // Monster Hunter 4
        "BFG",   // Monster Hunter 4 Ultimate / 4G
        "BXX",   // Monster Hunter Generations / X
        "AC3",   // Ace Combat Assault Horizon Legacy / 3D Cross Rumble
        "BCR",   // Ace Combat Assault Horizon Legacy+ / 3D Cross Rumble+
        "ABR",   // Resident Evil: Revelations
        "AMG",   // Metal Gear Solid: Snake Eater 3D
        "AKD",   // Kid Icarus: Uprising
        "AKH",   // Kingdom Hearts 3D: Dream Drop Distance
        "AS5",   // Shin Sangoku Musou VS
        "AZC",   // Sengoku Musou Chronicle 2nd
        "ALT",   // E.X. Troopers
        "JGF",   // 3D Galaxy Force II
        "BUW",   // One Piece: Unlimited World Red
        "JNU",   // Steel Diver: Sub Wars
        "BNU",   // Senran Kagura 2: Deep Crimson
        "BC4",   // Samurai Warriors Chronicles 3
        "BCE",   // Final Fantasy Explorers
        "BC6",   // Kenka Bancho 6: Soul and Blood
        "AJR",   // The Legend of Zelda: Majora's Mask 3D
        "KFL",   // IronFall: Invasion
        "AY6",   // Code Name: S.T.E.A.M.
        "KCC",   // Cube Creator 3D
        "KTB",   // Touch Battle Tank 3D 3
        "BSR",   // Super Robot Taisen BX
        "KMJ",   // The Legend of Dark Witch 2
        "JNY",   // Noah no Yurikago (Noah's Cradle)
        "BCA",   // Metroid Prime: Federation Force
        "BGN",   // Luigi's Mansion (2018)
        // New 3DS only
        "CAF",   // Xenoblade Chronicles 3D
        "CFM",   // Fire Emblem Warriors
        "BD3",   // Minecraft: New Nintendo 3DS Edition
        "CDR",   // Runbow Pocket
        "CBR",   // The Binding of Isaac: Rebirth
        // C-stick on the New 3DS
        "AXC",   // Super Smash Bros. for Nintendo 3DS
        "AGQ",   // Monster Hunter XX
        "BZH",   // Hyrule Warriors Legends
    )

    /** The listed games' title IDs per region (3dsdb), product code and region alongside. */
    val TITLE_IDS: Set<Long> = setOf(
        0x00040000_00048100L,   // CTR-N-AMHJ JPN
        0x00040000_000AE400L,   // CTR-N-AMHE USA
        0x00040000_000B1D00L,   // CTR-P-AMHP EUR
        0x00040000_000D2E00L,   // CTR-N-AMHZ KOR
        0x00040000_0004B500L,   // CTR-N-AH4J JPN
        0x00040000_00105000L,   // CTR-P-AH4K KOR
        0x00040000_0011D700L,   // CTR-N-BFGJ JPN
        0x00040000_00126100L,   // CTR-P-BFGP EUR
        0x00040000_00126300L,   // CTR-N-BFGE USA
        0x00040000_00153200L,   // CTR-P-BFGK KOR
        0x00040000_00155400L,   // CTR-N-BXXJ JPN
        0x00040000_00185B00L,   // CTR-P-BXXP EUR
        0x00040000_00187000L,   // CTR-N-BXXE USA
        0x00040000_00197100L,   // CTR-N-AGQJ JPN
        0x00040000_00064700L,   // CTR-P-AC3J JPN
        0x00040000_00064900L,   // CTR-P-AC3E USA
        0x00040000_00157900L,   // CTR-N-BCRJ JPN
        0x00040000_00053B00L,   // CTR-N-ABRJ JPN
        0x00040000_0005EE00L,   // CTR-P-ABRP EUR
        0x00040000_00060200L,   // CTR-N-ABRE USA
        0x00040000_0009FF00L,   // CTR-P-ABRK KOR
        0x00040000_0007A000L,   // CTR-N-AMGJ JPN
        0x00040000_00030000L,   // CTR-N-AKDJ JPN
        0x00040000_00030100L,   // CTR-N-AKDE USA
        0x00040000_00030200L,   // CTR-P-AKDP EUR
        0x00040000_0004EE00L,   // CTR-P-AKHJ JPN
        0x00040000_0008D300L,   // CTR-P-AKHE USA
        0x00040000_00095500L,   // CTR-P-AKHP EUR
        0x00040000_00072A00L,   // CTR-N-AS5J JPN
        0x00040000_000A4D00L,   // CTR-N-AZCJ JPN
        0x00040000_00053700L,   // CTR-N-ALTJ JPN
        0x00040000_000C0900L,   // CTR-N-JGFP EUR
        0x00040000_000C0B00L,   // CTR-N-JGFE USA
        0x00040000_000FB900L,   // CTR-N-JGFJ JPN
        0x00040000_000F5800L,   // CTR-N-BUWJ JPN
        0x00040000_000D7C00L,   // CTR-N-JNUJ JPN
        0x00040000_000D7D00L,   // CTR-N-JNUE USA
        0x00040000_000D7E00L,   // CTR-N-JNUP EUR
        0x00040000_000FB100L,   // CTR-N-BNUJ JPN
        0x00040000_00150200L,   // CTR-P-BNUK KOR
        0x00040000_00165000L,   // CTR-N-BNUE USA
        0x00040000_00165800L,   // CTR-P-BNUP EUR
        0x00040000_0014DF00L,   // CTR-N-BC4J JPN
        0x00040000_0016F200L,   // CTR-N-BC4E USA
        0x00040000_00173B00L,   // CTR-N-BC4P EUR
        0x00040000_0010E200L,   // CTR-N-BCEJ JPN
        0x00040000_0016E700L,   // CTR-P-BCEP EUR
        0x00040000_0016ED00L,   // CTR-N-BCEE USA
        0x00040000_000F4F00L,   // CTR-N-BC6J JPN
        0x00040000_000D6E00L,   // CTR-N-AJRJ JPN
        0x00040000_00125500L,   // CTR-N-AJRE USA
        0x00040000_00125600L,   // CTR-P-AJRP EUR
        0x00040000_00185D00L,   // CTR-N-AJRK KOR
        0x00040000_0015B100L,   // CTR-N-KFLP EUR
        0x00040000_0015D800L,   // CTR-N-KFLE USA
        0x00040000_0017D000L,   // CTR-N-KFLJ JPN
        0x00040000_00132500L,   // CTR-N-AY6A EUR/JPN/KOR/USA
        0x00040000_00151100L,   // CTR-N-KCCE USA
        0x00040000_00173D00L,   // CTR-N-KCCJ JPN
        0x00040000_00175900L,   // CTR-N-KCCP EUR
        0x00040000_00162900L,   // CTR-N-KTBJ JPN
        0x00040000_00127900L,   // CTR-N-BSRJ JPN
        0x00040000_0016FA00L,   // CTR-N-KMJJ JPN
        0x00040000_00186300L,   // CTR-N-KMJP EUR
        0x00040000_00186600L,   // CTR-N-KMJE USA
        0x00040000_00170200L,   // CTR-N-JNYJ JPN
        0x00040000_001A4300L,   // CTR-N-JNYE USA
        0x00040000_001A4400L,   // CTR-N-JNYP EUR
        0x00040000_0016CE00L,   // CTR-N-BCAJ JPN
        0x00040000_0016E300L,   // CTR-N-BCAE USA
        0x00040000_00175200L,   // CTR-P-BCAP EUR
        0x00040000_001D1800L,   // CTR-P-BGNJ JPN
        0x00040000_001D1900L,   // CTR-N-BGNE USA
        0x00040000_001D1A00L,   // CTR-P-BGNP EUR
        0x00040000_001D4F00L,   // CTR-P-BGNK KOR
        0x00040000_0F700000L,   // KTR-N-CAFJ JPN
        0x00040000_0F700100L,   // KTR-N-CAFE USA
        0x00040000_0F700200L,   // KTR-P-CAFP EUR
        0x00040000_0F700400L,   // KTR-N-CAFZ KOR
        0x00040000_0F700700L,   // KTR-N-CAFY KOR
        0x00040000_0F70C100L,   // KTR-N-CFMJ JPN
        0x00040000_0F70CC00L,   // KTR-N-CFME USA
        0x00040000_0F70CD00L,   // KTR-P-CFMP EUR
        0x00040000_0F70CF00L,   // KTR-N-CFMZ KOR
        0x00040000_0F70DE00L,   // KTR-N-CFMY KOR
        0x00040000_0017CA00L,   // KTR-P-BD3P EUR
        0x00040000_0017FD00L,   // KTR-N-BD3J JPN
        0x00040000_001B8700L,   // KTR-N-BD3E USA
        0x00040000_0F708700L,   // KTR-P-CDRE USA
        0x00040000_0F708D00L,   // KTR-N-CDRP EUR
        0x00040000_0F700800L,   // KTR-N-CBRE USA
        0x00040000_0F700900L,   // KTR-N-CBRP EUR
        0x00040000_0F701700L,   // KTR-N-CBRJ JPN
        0x00040000_0F703900L,   // KTR-N-CBRK KOR
        0x00040000_000B8B00L,   // CTR-N-AXCJ JPN
        0x00040000_000EDF00L,   // CTR-N-AXCE USA
        0x00040000_000EE000L,   // CTR-P-AXCP EUR
        0x00040000_00167C00L,   // CTR-P-AXCK KOR
        0x00040000_0017D500L,   // CTR-N-BZHJ JPN
        0x00040000_0017EA00L,   // CTR-N-BZHE USA
        0x00040000_0017EB00L,   // CTR-P-BZHP EUR
        0x00040000_00189E00L,   // CTR-N-BZHK KOR
    )
}
