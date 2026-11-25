package com.nxredux.mobile.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MirrorRulesTest {
    @Test fun newer_side_wins_missing_side_receives_equal_does_nothing() {
        assertFalse(MirrorRules.treeIsNewer(null, null))
        assertTrue(MirrorRules.treeIsNewer(null, 10))
        assertFalse(MirrorRules.treeIsNewer(10, null))
        assertTrue(MirrorRules.treeIsNewer(10, 20))
        assertFalse(MirrorRules.treeIsNewer(20, 10))
        assertFalse(MirrorRules.treeIsNewer(10, 10))
    }
    @Test fun after_an_import_the_local_mtime_equals_the_trees_so_a_future_tree_stamp_is_not_pushed_back() {
        val treeInFuture = 4_000_000_000_000L
        assertTrue(MirrorRules.treeIsNewer(1_700_000_000_000L, treeInFuture))
        assertFalse(MirrorRules.treeIsNewer(treeInFuture, treeInFuture))   // what the importer sets
    }

    @Test fun interrupted_write_leftovers_are_transient() {
        assertTrue(MirrorRules.isTransient("Tetris.srm.part"))
        assertTrue(MirrorRules.isTransient("Tetris.state.part"))
        assertTrue(MirrorRules.isTransient("Tetris.srm.1a2b.tmp"))
        assertFalse(MirrorRules.isTransient("Tetris.srm"))
        assertFalse(MirrorRules.isTransient("Tetris.srm.20261005-120000.bak"))
        assertFalse(MirrorRules.isTransient("party.srm"))
    }

    @Test fun a_free_backup_name_never_reuses_an_existing_one() {
        assertEquals("G.sav.20261014-180000.bak", SaveCopyRules.freeBackupName("G.sav", "20261014-180000") { false })
        val taken = setOf("G.sav.20261014-180000.bak", "G.sav.20261014-180000-2.bak")
        assertEquals("G.sav.20261014-180000-3.bak", SaveCopyRules.freeBackupName("G.sav", "20261014-180000") { it in taken })
        assertTrue(SaveCopyRules.isBackupOf("G.sav.20261014-180000-3.bak", "G.sav"))
    }
}
