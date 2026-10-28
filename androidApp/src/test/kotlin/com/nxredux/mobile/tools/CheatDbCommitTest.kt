package com.nxredux.mobile.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheatDbCommitTest {
    private val sha = "6f1ed002a5e0d9b5d7c1c3e8f9a0b1c2d3e4f5a6"

    @Test fun takes_the_newest_commits_own_sha_ahead_of_its_tree() {
        val json = """[{"sha":"$sha","node_id":"C_x","commit":{"author":{"name":"a","date":"2026-10-20T10:00:00Z"},"committer":{"date":"2026-10-20T10:00:00Z"},
            "tree":{"sha":"1111111111111111111111111111111111111111","url":"u"}},"parents":[{"sha":"2222222222222222222222222222222222222222"}]}]"""
        assertEquals(sha, CheatDbFiles.parseChtCommit(json))
    }

    @Test fun pretty_printed_and_upper_case_ids_come_back_lower_case() {
        assertEquals(sha, CheatDbFiles.parseChtCommit("[\n  {\n    \"sha\": \"${sha.uppercase()}\"\n  }\n]"))
    }

    @Test fun an_empty_list_or_an_error_body_has_no_commit() {
        assertNull(CheatDbFiles.parseChtCommit("[]"))
        assertNull(CheatDbFiles.parseChtCommit("""{"message":"API rate limit exceeded","documentation_url":"x"}"""))
        assertNull(CheatDbFiles.parseChtCommit(""))
    }
}
