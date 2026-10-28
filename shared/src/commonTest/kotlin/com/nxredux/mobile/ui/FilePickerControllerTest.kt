package com.nxredux.mobile.ui

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.FakeLocalFiles
import com.nxredux.mobile.storage.LocalEntry
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.LocalVolume
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nav_icon_file
import com.nxredux.mobile.shared.res.nav_icon_folder
import com.nxredux.mobile.shared.res.nav_icon_new_folder
import com.nxredux.mobile.shared.res.nav_icon_up
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FilePickerControllerTest {
    private val internal = LocalVolume("Internal storage", "/storage/emulated/0", primary = true)
    private val card = LocalVolume("SD card", "/storage/1234-ABCD")
    private val root = "/storage/emulated/0"

    private fun files() = FakeLocalFiles(
        volumes = listOf(internal, card),
        dirs = listOf("$root/NXRedux", "$root/NXRedux/Roms", "$root/.hidden", "$root/beta", "$root/Alpha", "/storage/1234-ABCD/Games"),
        files = listOf("$root/saves.zip", "$root/B.ZIP", "$root/notes.txt", "$root/.old.zip"),
    )

    private class Harness(scope: CoroutineScope, val files: FakeLocalFiles) {
        val origin: Screen = Screen.PickFolder
        val screen = MutableStateFlow(origin)
        val selection = MutableStateFlow(ListSelection(0, 1))
        val error = MutableStateFlow<String?>(null)
        val picker = FilePickerController(scope, screen, selection, error, files)
        val results = ArrayList<ContentRef?>()
        fun open(mode: FilePickMode, home: ContentRef? = null) = picker.open(mode, home) { results += it }
        val page: Screen.FilePicker get() = assertIs(screen.value)
        val labels: List<String> get() = page.rows.map { it.label }
        fun press(button: NxButton) { picker.onButton(screen.value, button) }
        /** Highlights the row labelled [label] and presses A. */
        fun choose(label: String) {
            val at = labels.indexOf(label)
            assertTrue(at >= 0, "no $label in $labels")
            selection.value = selection.value.copy(index = at)
            press(NxButton.A)
        }
        /** Types [text] into the open New folder prompt and presses A. */
        fun confirm(text: String) { picker.draft(text); press(NxButton.A) }
    }

    private fun TestScope.harness(files: FakeLocalFiles = files()) = Harness(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), files)

    @Test fun a_picker_replaced_by_another_screen_is_dropped_and_the_next_pick_opens() = runTest {
        val h = harness()
        h.open(FilePickMode.Home)
        h.screen.value = Screen.PickFolder   // something replaced the picker without finish()
        h.open(FilePickMode.Home)
        assertEquals(listOf<ContentRef?>(null), h.results)   // the first callback got null
        assertIs<Screen.FilePicker>(h.screen.value)
    }

    @Test fun without_a_file_home_it_starts_at_the_volume_list_even_with_one_volume() = runTest {
        val h = harness(FakeLocalFiles(volumes = listOf(internal)))
        h.open(FilePickMode.Home)
        assertNull(h.page.dir)
        assertEquals("Storage", h.page.title)
        assertEquals(listOf("Internal storage"), h.labels)
    }

    @Test fun folders_sort_case_insensitively_before_the_action_rows_and_dot_entries_hide() = runTest {
        val h = harness()
        h.open(FilePickMode.ImportFolder)   // a pick the storage root still offers "Use this folder" for
        h.choose("Internal storage")
        assertEquals("Internal storage", h.page.title)
        assertEquals(listOf("..", "Alpha", "beta", "NXRedux", "B.ZIP", "notes.txt", "saves.zip", "Use this folder", "Create new folder"), h.labels)
        assertEquals(ListSelection(1, 9), h.selection.value)   // the first folder, not "..", whose A would leave
    }

    @Test fun the_home_picker_says_what_a_folder_is() {
        val other = "Has other files: games found in it will be sorted into Roms"
        assertEquals("Empty folder", homeFolderStatus(emptyList()))
        assertEquals("Empty folder", homeFolderStatus(listOf(LocalEntry(".nomedia", false))))
        assertEquals("NX Redux folder", homeFolderStatus(listOf(LocalEntry("Roms", true), LocalEntry("Bios", true), LocalEntry(".userdata", true))))
        assertEquals(other, homeFolderStatus(listOf(LocalEntry("Roms", true), LocalEntry("Games", true))))
        // A home after a reinstall: the cheat download and the saves are its own folders too.
        val reinstalled = listOf("Roms", "Bios", "Collections", "Cheats", "Saves", ".userdata").map { LocalEntry(it, true) }
        assertEquals("NX Redux folder", homeFolderStatus(reinstalled))
        assertEquals(other, homeFolderStatus(listOf(LocalEntry("Tetris.gb", false))))
    }

    // The home picker shows the folder it is in (the one "Use this folder" picks); other pickers show nothing.
    @Test fun the_home_picker_shows_the_current_folders_status() = runTest {
        val h = harness()
        h.open(FilePickMode.Home)
        assertNull(h.page.status)                                            // the volume list
        h.choose("Internal storage")
        assertEquals(INTERNAL_ROOT_STATUS, h.page.status)                    // the phone's root is never a home
        h.choose("NXRedux")
        assertEquals("NX Redux folder", h.page.status)
        h.press(NxButton.B); h.choose("Alpha")
        assertEquals("Empty folder", h.page.status)
        val e = harness()
        e.open(FilePickMode.Extra)
        e.choose("Internal storage")
        assertNull(e.page.status)
    }

    @Test fun import_zip_lists_folders_then_zips_with_no_action_row_and_a_zip_picks() = runTest {
        val h = harness()
        h.open(FilePickMode.ImportZip)
        h.choose("Internal storage")
        assertEquals(listOf("..", "Alpha", "beta", "NXRedux", "B.ZIP", "saves.zip"), h.labels)
        h.choose("saves.zip")
        assertEquals(listOf<ContentRef?>(FileRefs.of("$root/saves.zip")), h.results)
        assertSame(h.origin, h.screen.value)
        assertEquals(ListSelection(0, 1), h.selection.value)   // the origin's selection is back
    }

    @Test fun a_file_home_starts_home_and_extra_in_its_parent_and_the_rest_in_the_home() = runTest {
        val home = FileRefs.of("$root/NXRedux")
        harness().apply { open(FilePickMode.Extra, home); assertEquals(root, page.dir) }
        harness().apply { open(FilePickMode.Home, home); assertEquals(root, page.dir) }
        harness().apply {
            open(FilePickMode.ImportFolder, home)
            assertEquals("$root/NXRedux", page.dir)
            assertEquals("Internal storage/NXRedux", page.title)
            assertEquals(listOf("..", "Roms", "Use this folder", "Create new folder"), labels)
        }
        harness().apply { open(FilePickMode.Export("x.zip"), home); assertEquals("$root/NXRedux", page.dir) }
        harness().apply { open(FilePickMode.Home, FileRefs.of(root)); assertNull(page.dir) }   // a volume-root home has no parent on a volume
        harness().apply { open(FilePickMode.Home, ContentRef("${"content://com.android.externalstorage.documents/tree/"}primary%3ANXRedux")); assertNull(page.dir) }
    }

    @Test fun b_goes_up_to_the_volumes_then_cancels_back_where_it_started() = runTest {
        val h = harness()
        h.open(FilePickMode.ImportFolder, FileRefs.of("$root/NXRedux"))
        h.press(NxButton.B)
        assertEquals(root, h.page.dir)
        assertEquals("NXRedux", h.page.rows[h.selection.value.index].label)    // on the folder it came up from
        h.press(NxButton.B)
        assertNull(h.page.dir)
        assertEquals("Internal storage", h.page.rows[h.selection.value.index].label)
        h.press(NxButton.B)
        assertSame(h.origin, h.screen.value)
        assertEquals(listOf<ContentRef?>(null), h.results)
    }

    // ".." (below the action row) goes up as B does: to the parent on the folder it came from, then to the volumes.
    @Test fun the_up_row_goes_up_like_b() = runTest {
        val h = harness()
        h.open(FilePickMode.ImportFolder, FileRefs.of("$root/NXRedux"))
        h.choose("..")
        assertEquals(root, h.page.dir)
        assertEquals("NXRedux", h.page.rows[h.selection.value.index].label)
        h.choose("..")
        assertNull(h.page.dir)
        assertEquals("Internal storage", h.page.rows[h.selection.value.index].label)
        assertTrue(h.results.isEmpty())
    }

    // A folder pick lists the files too (dimmed, after the folders), so a ROMs folder looks like one; A on a file does nothing.
    @Test fun a_folder_pick_shows_files_but_only_folders_open() = runTest {
        val h = harness()
        h.open(FilePickMode.Extra)
        h.choose("Internal storage")
        assertEquals(PickerRow.File("notes.txt"), h.page.rows.first { it.label == "notes.txt" })
        h.choose("notes.txt")
        assertEquals(root, h.page.dir)
        assertTrue(h.results.isEmpty())
    }

    // Only folders (and the action rows) take the selection: UP and DOWN skip the files, wrapping as before.
    @Test fun up_and_down_skip_the_files() = runTest {
        val h = harness()
        h.open(FilePickMode.Extra)
        h.choose("Internal storage")
        val last = h.labels.indexOf("NXRedux")
        h.selection.value = h.selection.value.copy(index = last)
        h.press(NxButton.DOWN)
        assertEquals(h.labels.indexOf("Create new folder"), h.selection.value.index)   // past the files to the actions (the storage root: no Use this folder)
        h.press(NxButton.UP)
        assertEquals(last, h.selection.value.index)                       // and back over them
    }

    // LEFT or RIGHT takes the highlight from the entries to the actions under them and back to the entry it left, wrapping.
    @Test fun right_and_left_move_between_the_entries_and_the_actions() = runTest {
        val h = harness()
        h.open(FilePickMode.Extra)
        h.choose("Internal storage")
        val beta = h.labels.indexOf("beta")
        h.selection.value = h.selection.value.copy(index = beta)
        h.press(NxButton.RIGHT)
        assertEquals(h.labels.indexOf("Create new folder"), h.selection.value.index)
        h.press(NxButton.LEFT)
        assertEquals(beta, h.selection.value.index)
        h.press(NxButton.RIGHT); h.press(NxButton.RIGHT)   // wraps: RIGHT again goes back to the entries
        assertEquals(beta, h.selection.value.index)
    }

    @Test fun an_empty_or_unreadable_folder_shows_nothing_here_above_the_action_rows() = runTest {
        val h = harness()
        h.open(FilePickMode.Extra)
        h.choose("Internal storage"); h.choose("Alpha")
        assertEquals(listOf("..", "Nothing here", "Use this folder", "Create new folder"), h.labels)
        h.choose("Nothing here")
        assertTrue(h.results.isEmpty())
        assertEquals("$root/Alpha", h.page.dir)
    }

    @Test fun new_folder_refuses_bad_and_taken_names_and_keeps_the_prompt_open() = runTest {
        val h = harness()
        h.open(FilePickMode.Home)
        h.choose("Internal storage")
        h.press(NxButton.X)
        assertEquals("", h.page.prompt)
        for (bad in listOf("", "  ", ".", "..", "a/b")) {
            h.confirm(bad)
            assertEquals("That name can't be used.", h.error.value, "'$bad'")
            h.error.value = null
            assertTrue(h.page.prompt != null)
        }
        h.confirm("nxredux")                                                  // another case of NXRedux
        assertEquals("A folder with that name already exists.", h.error.value)
        h.error.value = null
        assertTrue(h.page.prompt != null)
        h.confirm("  Games  ")
        assertTrue("$root/Games" in h.files.dirs)
        assertEquals("$root/Games", h.page.dir)                               // success opens the new folder
        assertNull(h.page.prompt)
    }

    // The Create new folder row opens the same prompt as X (a pad's d-pad reaches it without knowing the button).
    @Test fun the_create_new_folder_row_opens_the_new_folder_prompt() = runTest {
        val h = harness()
        h.open(FilePickMode.Extra)
        h.choose("Internal storage")
        h.choose("Create new folder")
        assertEquals("", h.page.prompt)
        h.confirm("Games")
        assertEquals("$root/Games", h.page.dir)
    }

    @Test fun a_failed_mkdir_says_so_and_stays_in_the_folder() = runTest {
        val h = harness()
        h.files.mkdirFails = true
        h.open(FilePickMode.Home)
        h.choose("Internal storage")
        h.press(NxButton.X); h.confirm("New")
        assertEquals("Could not create the folder.", h.error.value)
        assertEquals(root, h.page.dir)
        assertNull(h.page.prompt)
    }

    @Test fun b_closes_the_prompt_and_no_new_folder_at_the_volumes_or_in_import_zip() = runTest {
        val h = harness()
        h.open(FilePickMode.ImportZip)
        h.press(NxButton.X); assertNull(h.page.prompt)                        // the volume list
        h.choose("Internal storage")
        h.press(NxButton.X); assertNull(h.page.prompt)                        // Import a zip
        val e = harness()
        e.open(FilePickMode.Extra); e.choose("Internal storage")
        e.press(NxButton.X); e.press(NxButton.B)
        assertNull(e.page.prompt); assertEquals(root, e.page.dir)
    }

    // The phone's own storage root is never a home (a folder in it is); an SD card's root is (a handheld's card).
    @Test fun the_home_pick_refuses_the_internal_storage_root_but_takes_a_cards() = runTest {
        val h = harness()
        h.open(FilePickMode.Home)
        h.choose("Internal storage")
        assertEquals(INTERNAL_ROOT_STATUS, h.page.status)
        assertFalse("Use this folder" in h.labels)                          // no pick there, only its folders
        h.press(NxButton.B); h.choose("SD card"); h.choose("Use this folder")
        assertEquals(listOf<ContentRef?>(FileRefs.of("/storage/1234-ABCD")), h.results)
        val e = harness()                                                    // an extra folder alike
        e.open(FilePickMode.Extra)
        e.choose("Internal storage")
        assertFalse("Use this folder" in e.labels)
        e.choose("Alpha")
        assertTrue("Use this folder" in e.labels)
    }

    @Test fun the_home_pick_refuses_a_folder_that_takes_no_writes() = runTest {
        val h = harness()
        h.files.unwritable += "$root/NXRedux"
        h.open(FilePickMode.Home)
        h.choose("Internal storage"); h.choose("NXRedux"); h.choose("Use this folder")
        assertEquals("That folder can't be written to. Pick a folder on your phone's storage or SD card.", h.error.value)
        assertTrue(h.results.isEmpty())
        assertEquals("$root/NXRedux", h.page.dir)
        h.error.value = null
        h.press(NxButton.B); h.choose("Alpha"); h.choose("Use this folder")
        assertEquals(listOf<ContentRef?>(FileRefs.of("$root/Alpha")), h.results)
        assertSame(h.origin, h.screen.value)
    }

    @Test fun extra_and_import_folder_pick_the_folder_shown() = runTest {
        for (mode in listOf(FilePickMode.Extra, FilePickMode.ImportFolder)) {
            val h = harness()
            h.open(mode); h.choose("SD card"); h.choose("Games")
            assertEquals("SD card/Games", h.page.title)
            h.choose("Use this folder")
            assertEquals(listOf<ContentRef?>(FileRefs.of("/storage/1234-ABCD/Games")), h.results)
        }
    }

    @Test fun export_asks_before_replacing_a_file_of_that_name() = runTest {
        val h = harness()
        h.open(FilePickMode.Export("SAVES.zip"))
        h.choose("Internal storage"); h.choose("Save here")
        assertEquals("$root/SAVES.zip", h.page.replace)                       // saves.zip is there in another case
        h.press(NxButton.B)
        assertNull(h.page.replace)
        assertTrue(h.results.isEmpty())
        h.choose("Save here"); h.press(NxButton.A)                            // Replace
        assertEquals(listOf<ContentRef?>(FileRefs.of("$root/SAVES.zip")), h.results)
        val fresh = harness()
        fresh.open(FilePickMode.Export("SAVES.zip"))
        fresh.choose("Internal storage"); fresh.choose("Alpha"); fresh.choose("Save here")
        assertEquals(listOf<ContentRef?>(FileRefs.of("$root/Alpha/SAVES.zip")), fresh.results)   // nothing to replace: no question
    }

    // Folders and volumes open like folders: they carry the folder icon; the action, a zip and the empty line do not.
    @Test fun folder_and_volume_rows_carry_the_folder_icon() {
        val rows = pickerListRows(listOf(
            PickerRow.Action("Use this folder"), PickerRow.NewFolder, PickerRow.Up, PickerRow.Volume(internal), PickerRow.Folder("Roms"), PickerRow.Zip("a.zip"),
            PickerRow.File("a.gba"), PickerRow.Empty,
        ))
        assertEquals(
            listOf(null, Res.drawable.nav_icon_new_folder, Res.drawable.nav_icon_up, Res.drawable.nav_icon_folder, Res.drawable.nav_icon_folder, null, Res.drawable.nav_icon_file, null),
            rows.map { it.icon },
        )
        assertEquals(listOf(true, true, true, true, true, true, false, false), rows.map { it.enabled })
    }

    // The action rows stay pinned above the list (a gap between): Use this folder and Create new folder, none in Import a zip.
    @Test fun the_action_rows_are_pinned() {
        assertEquals(2, pickerPinnedRows(listOf(PickerRow.Up, PickerRow.Folder("A"), PickerRow.Action("Use this folder"), PickerRow.NewFolder)))
        assertEquals(0, pickerPinnedRows(listOf(PickerRow.Up, PickerRow.Folder("A"), PickerRow.Zip("a.zip"))))
        assertEquals(0, pickerPinnedRows(listOf(PickerRow.Volume(internal))))
    }
}
