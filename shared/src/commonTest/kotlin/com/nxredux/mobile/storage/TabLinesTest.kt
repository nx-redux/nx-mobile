package com.nxredux.mobile.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class TabLinesTest {
    @Test fun pairsSkipLinesWithoutAKeyOrValue() =
        assertEquals(mapOf("a" to "1", "c" to "3"), TabLines.parsePairs("a\t1\n\tx\nb\t\nno tab\r\nc\t3") { it })

    @Test fun pairsNormaliseEachSideAndALaterLineWins() =
        assertEquals(mapOf("a" to "TWO"), TabLines.parsePairs(" a \tone\na\t two ", key = String::trim) { it.trim().uppercase() })

    @Test fun aBomDoesNotStickToTheFirstKey() =
        assertEquals(mapOf("a" to "1"), TabLines.parsePairs("﻿a\t1\n") { it })

    @Test fun renderSortsByKey() = assertEquals("a\t1\nb\t2\n", TabLines.render(mapOf("b" to "2", "a" to "1")))

    @Test fun optionalValues() =
        assertEquals(listOf("p" to "v", "q" to null, "r" to null), TabLines.parseOptional("﻿p\tv\n\nq\nr\t\n  \n"))

    @Test fun lineWithAndWithoutValue() {
        assertEquals("p\tv\n", TabLines.line("p", "v"))
        assertEquals("p\n", TabLines.line("p", null))
    }

    @Test fun mapKeyKeepsTheRestOfTheLine() {
        assertEquals("B\tx\ty", TabLines.mapKey("a\tx\ty") { "B" })
        assertEquals("B", TabLines.mapKey("a") { "B" })
        assertEquals("a\tx", TabLines.mapKey("a\tx") { null })
    }
}
