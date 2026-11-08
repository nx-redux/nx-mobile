package com.nxredux.mobile.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppReleaseTest {

    private fun releaseJson(
        tag: String = "v0.3.0",
        asset: String? = "NXReduxMobile-v0.3.0.apk",
        digest: String? = "sha256:ABC123",
        prerelease: Boolean = false,
        draft: Boolean = false,
        url: String? = null,
    ): String {
        val body = "A new Minimal layout: just big names, no art.\\r\\n\\r\\nMore details at https://nxredux.com/reference/release-notes/mobile" +
            "\\r\\n\\r\\nSide-load NXReduxMobile-v0.3.0.apk on a phone.\\r\\n\\r\\n**Full Changelog**: https://github.com/x/y/compare/v0.2.0...v0.3.0"
        val digestField = digest?.let { """"digest": "$it",""" } ?: ""
        val assets = asset?.let {
            """{"name": "$it", "size": 1234567, $digestField "browser_download_url": "${url ?: "https://github.com/x/y/releases/download/$tag/$it"}"}"""
        } ?: ""
        return """{"tag_name": "$tag", "name": "$tag", "body": "$body", "draft": $draft, "prerelease": $prerelease, "assets": [$assets]}"""
    }

    @Test fun a_tag_becomes_a_version_code() {
        assertEquals(3_000L, tagVersionCode("v0.3.0"))
        assertEquals(1_002_003L, tagVersionCode("v1.2.3"))
        assertNull(tagVersionCode("v0.4.0-rc1"))
        assertNull(tagVersionCode("0.3.0"))
        assertNull(tagVersionCode("v1000.0.0"))
    }

    @Test fun the_first_paragraph_is_the_notes() {
        assertEquals("A new Minimal layout.", firstParagraph("A new Minimal layout.\n\nMore details at x"))
        assertEquals("A new Minimal layout.", firstParagraph("A new Minimal layout.\r\n\r\nMore details at x"))
        assertEquals("line one line two", firstParagraph("line one\nline two\n\nnext"))
    }

    @Test fun markdown_is_stripped() {
        assertEquals("Bold and docs and code", firstParagraph("**Bold** and [docs](https://x) and `code`"))
    }

    @Test fun boilerplate_first_paragraphs_give_no_notes() {
        assertEquals("", firstParagraph("Side-load NXReduxMobile-v0.4.0.apk on a phone"))
        assertEquals("", firstParagraph("## What's Changed\n* a thing"))
        assertEquals("", firstParagraph("**Full Changelog**: https://github.com/x/y/compare/a...b"))
        assertEquals("", firstParagraph("  \n\n "))
    }

    @Test fun the_live_release_shape_parses() {
        val release = assertNotNull(parseLatestRelease(releaseJson(), dev = false))
        assertEquals("0.3.0", release.version)
        assertEquals(3_000L, release.versionCode)
        assertEquals("A new Minimal layout: just big names, no art.", release.notes)
        assertEquals("https://github.com/x/y/releases/download/v0.3.0/NXReduxMobile-v0.3.0.apk", release.apkUrl)
        assertEquals(1_234_567L, release.apkSize)
        assertEquals("abc123", release.sha256)
        assertEquals("NXReduxMobile-v0.3.0.apk", release.apkName)
    }

    @Test fun the_dev_asset_name_cannot_leave_the_download_folder() {
        assertNull(parseLatestRelease(releaseJson(tag = "v0.3.1", asset = "NXReduxMobile-v0.3.1/../../x-dev.apk"), dev = true))
        assertNull(parseLatestRelease(releaseJson(tag = "v0.3.1", asset = "NXReduxMobile-v0.3.1\\\\x-dev.apk"), dev = true))
    }

    @Test fun the_dev_flavor_wants_the_dev_asset() {
        assertNull(parseLatestRelease(releaseJson(), dev = true))
        val dev = parseLatestRelease(releaseJson(tag = "v0.3.1", asset = "NXReduxMobile-v0.3.1-dev.apk"), dev = true)
        assertEquals("0.3.1", assertNotNull(dev).version)
        assertNull(parseLatestRelease(releaseJson(tag = "v0.3.1", asset = "NXReduxMobile-v0.3.1-dev.apk"), dev = false))
    }

    @Test fun unusable_releases_give_null() {
        assertNull(parseLatestRelease(releaseJson(asset = null), dev = false))
        assertNull(parseLatestRelease("not json", dev = false))
        assertNull(parseLatestRelease("{}", dev = false))
    }

    @Test fun drafts_prereleases_and_other_tags_have_no_asset() {
        assertEquals(LatestRelease.NoAsset, readLatestRelease(releaseJson(prerelease = true), dev = false))
        assertEquals(LatestRelease.NoAsset, readLatestRelease(releaseJson(draft = true), dev = false))
        assertEquals(LatestRelease.NoAsset, readLatestRelease(releaseJson(tag = "v0.4.0-rc1"), dev = false))
        assertEquals(LatestRelease.NoAsset, readLatestRelease(releaseJson(tag = "nightly"), dev = false))
    }

    @Test fun a_published_release_without_this_builds_apk_has_no_asset() {
        assertEquals(LatestRelease.NoAsset, readLatestRelease(releaseJson(), dev = true))
        assertEquals(LatestRelease.NoAsset, readLatestRelease(releaseJson(asset = null), dev = false))
        val found = assertIs<LatestRelease.Found>(readLatestRelease(releaseJson(), dev = false))
        assertEquals("0.3.0", found.release.version)
    }

    @Test fun unusable_feeds_stay_unusable() {
        assertEquals(LatestRelease.Unusable, readLatestRelease("<html>", dev = false))
        assertEquals(LatestRelease.Unusable, readLatestRelease("{}", dev = false))
    }

    @Test fun a_missing_digest_leaves_the_hash_null() {
        assertNull(assertNotNull(parseLatestRelease(releaseJson(digest = null), dev = false)).sha256)
    }

    @Test fun a_digest_of_another_algorithm_leaves_the_hash_null() {
        assertNull(assertNotNull(parseLatestRelease(releaseJson(digest = "sha512:ABC123"), dev = false)).sha256)
        assertNull(assertNotNull(parseLatestRelease(releaseJson(digest = "ABC123"), dev = false)).sha256)
    }

    @Test fun github_release_downloads_are_allowed() {
        assertTrue(downloadUrlAllowed("https://github.com/nx-redux/nx-mobile/releases/download/v0.3.0/NXReduxMobile-v0.3.0.apk", local = false))
        assertTrue(downloadUrlAllowed("https://objects.githubusercontent.com/github-production-release-asset/1/2?sig=x", local = false))
        assertTrue(downloadUrlAllowed("https://release-assets.githubusercontent.com/github-production-release-asset/1/2?sp=r", local = false))
        assertTrue(downloadUrlAllowed("https://GitHub.com/x/y/releases/download/v1.0.0/a.apk", local = false))
        assertTrue(downloadUrlAllowed("https://github.com:443/x.apk", local = false))
    }

    @Test fun other_schemes_and_hosts_are_refused() {
        for (url in listOf(
            "http://github.com/x.apk",
            "ftp://github.com/x.apk",
            "file:///sdcard/x.apk",
            "https://example.com/x.apk",
            "https://github.com.evil.example/x.apk",
            "https://evilgithub.com/x.apk",
            "https://api.github.com/x.apk",
            "https://evil.example@github.com/x.apk",
            "https://github.com@evil.example/x.apk",
            "https://github.com\\@evil.example/x.apk",
            "https://github.com:8443/x.apk",
            "https://github.com",
            "github.com/x.apk",
            "",
        )) assertFalse(downloadUrlAllowed(url, local = false), url)
    }

    @Test fun loopback_is_allowed_only_for_a_local_feed() {
        assertFalse(downloadUrlAllowed("http://localhost:8000/x.apk", local = false))
        assertFalse(downloadUrlAllowed("http://127.0.0.1:8000/x.apk", local = false))
        assertTrue(downloadUrlAllowed("http://localhost:8000/x.apk", local = true))
        assertTrue(downloadUrlAllowed("http://127.0.0.1:8000/x.apk", local = true))
        assertFalse(downloadUrlAllowed("http://192.168.1.2:8000/x.apk", local = true))
        assertFalse(downloadUrlAllowed("http://github.com/x.apk", local = true))
        assertTrue(downloadUrlAllowed("https://github.com/x.apk", local = true))
    }

    @Test fun a_loopback_feed_is_local() {
        assertTrue(loopbackUrl("http://localhost:8000/latest.json"))
        assertTrue(loopbackUrl("http://127.0.0.1:8000/latest.json"))
        assertFalse(loopbackUrl("https://api.github.com/repos/nx-redux/nx-mobile/releases/latest"))
        assertFalse(loopbackUrl("http://localhost.evil.example/latest.json"))
    }

    @Test fun a_check_time_in_the_future_counts_as_due() {
        val now = 10_000_000_000L
        assertTrue(UpdatePolicy.checkDue(now, lastSuccess = now + 1, lastFailure = 0))
        assertTrue(UpdatePolicy.checkDue(now, lastSuccess = 0, lastFailure = now + 1))
        assertFalse(UpdatePolicy.checkDue(now, lastSuccess = now + 1, lastFailure = now - 1))
    }

    @Test fun the_check_runs_daily_and_retries_hourly_after_a_failure() {
        val now = 10_000_000_000L
        assertFalse(UpdatePolicy.checkDue(now, lastSuccess = now - 86_400_000 + 1, lastFailure = 0))
        assertTrue(UpdatePolicy.checkDue(now, lastSuccess = now - 86_400_000, lastFailure = 0))
        assertFalse(UpdatePolicy.checkDue(now, lastSuccess = 0, lastFailure = now - 59 * 60_000L))
        assertTrue(UpdatePolicy.checkDue(now, lastSuccess = 0, lastFailure = now - 60 * 60_000L))
    }

    @Test fun the_popup_wants_a_newer_release_that_was_not_silenced() {
        val release = AppRelease("0.4.0", 4_000, "", "u", 1, null, "NXReduxMobile-v0.4.0.apk")
        assertTrue(UpdatePolicy.shouldPopup(release, installedCode = 3_000, silenced = null))
        assertFalse(UpdatePolicy.shouldPopup(release, installedCode = 4_000, silenced = null))
        assertFalse(UpdatePolicy.shouldPopup(release, installedCode = 3_000, silenced = "0.4.0"))
        assertTrue(UpdatePolicy.shouldPopup(release.copy(version = "0.4.1", versionCode = 4_001), 3_000, silenced = "0.4.0"))
    }
}
