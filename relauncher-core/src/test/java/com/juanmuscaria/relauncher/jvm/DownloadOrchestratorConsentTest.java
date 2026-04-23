// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadOrchestratorConsentTest {

    @AfterEach
    void clearSysProp() {
        System.clearProperty("relauncher.java.autoDownload");
    }

    @Test
    void systemPropTrueGrantsConsentWithoutDialog() {
        System.setProperty("relauncher.java.autoDownload", "true");
        var d = DownloadOrchestrator.checkConsent(
            /*clientWithTinyFd*/ true, /*configAutoDownload*/ false, /*prompter*/ () -> false);
        assertEquals(DownloadOrchestrator.ConsentDecision.GRANTED, d);
    }

    @Test
    void systemPropFalseDeniesConsentWithoutDialog() {
        System.setProperty("relauncher.java.autoDownload", "false");
        var d = DownloadOrchestrator.checkConsent(
            /*clientWithTinyFd*/ true, /*configAutoDownload*/ true, /*prompter*/ () -> true);
        assertEquals(DownloadOrchestrator.ConsentDecision.DENIED, d);
    }

    @Test
    void clientWithTinyFdShowsDialogWhenSysPropUnset() {
        var prompted = new boolean[]{false};
        var d = DownloadOrchestrator.checkConsent(
            true, false, () -> {
                prompted[0] = true;
                return true;
            });
        assertTrue(prompted[0], "prompter should have been invoked");
        assertEquals(DownloadOrchestrator.ConsentDecision.GRANTED, d);
    }

    @Test
    void clientWithTinyFdRespectsPromptDenial() {
        var d = DownloadOrchestrator.checkConsent(
            true, false, () -> false);
        assertEquals(DownloadOrchestrator.ConsentDecision.DENIED, d);
    }

    @Test
    void serverFallsBackToConfigWhenSysPropUnset() {
        var granted = DownloadOrchestrator.checkConsent(
            /*clientWithTinyFd*/ false, /*configAutoDownload*/ true, /*prompter*/ () -> false);
        assertEquals(DownloadOrchestrator.ConsentDecision.GRANTED, granted);

        var denied = DownloadOrchestrator.checkConsent(
            false, false, () -> false);
        assertEquals(DownloadOrchestrator.ConsentDecision.DENIED, denied);
    }

    @Test
    void sysPropInvalidValueFallsThrough() {
        System.setProperty("relauncher.java.autoDownload", "maybe");
        var d = DownloadOrchestrator.checkConsent(
            false, true, () -> false);
        assertEquals(DownloadOrchestrator.ConsentDecision.GRANTED, d);
    }
}
