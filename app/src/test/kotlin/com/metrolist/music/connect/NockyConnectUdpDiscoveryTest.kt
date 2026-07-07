package com.metrolist.music.connect

import org.junit.Assert.assertTrue
import org.junit.Test

class NockyConnectUdpDiscoveryTest {
    @Test
    fun messageIdsIncludePrefix() {
        val messageId = NockyConnectUdpDiscovery.nextDiscoveryMessageId("android-hello")

        assertTrue(messageId.startsWith("android-hello-"))
    }
}
