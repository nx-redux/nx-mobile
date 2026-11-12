package com.nxredux.mobile.achievements

import kotlin.test.Test
import kotlin.test.assertEquals

class RaRequestsTest {
    @Test fun userAgentNamesNxRedux() {
        assertEquals("NXRedux/0.5.1 (Android 14) rcheevos/12.2.1", RaRequests.userAgent("0.5.1", "Android 14"))
    }
    @Test fun login_forms() {
        assertEquals("r=login2&u=me&t=tok", RaRequests.login2("me", "tok"))
        assertEquals("r=login2&u=me&p=p%40ss%20word", RaRequests.loginWithPassword("me", "p@ss word"))
    }
    @Test fun game_forms() {
        assertEquals("r=gameid&m=abc", RaRequests.gameId("abc"))
        assertEquals("r=achievementsets&u=me&t=tok&m=abc", RaRequests.achievementSets("me", "tok", "abc"))
        assertEquals("r=startsession&u=me&t=tok&g=42&h=0&m=abc&l=${RaRequests.RCHEEVOS_VERSION}", RaRequests.startSession("me", "tok", 42, "abc"))
        assertEquals("r=allprogress&u=me&t=tok&c=5", RaRequests.allProgress("me", "tok", 5))
    }
    @Test fun kind_parses_the_fields_it_needs() {
        val k = RaRequestKind.of("r=awardachievement&u=me&t=tok&a=123&h=0&m=abc&o=60&v=sig")
        assertEquals("awardachievement", k.r); assertEquals("abc", k.m); assertEquals(123, k.a); assertEquals("me", k.u); assertEquals(null, k.g)
        assertEquals(42, RaRequestKind.of("r=startsession&u=me&t=tok&g=42").g)
        assertEquals("", RaRequestKind.of("").r)
    }
}
