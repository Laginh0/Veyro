package com.veyro.p2p.ui.i18n

import com.veyro.p2p.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class VeyroI18nTest {
    @Test
    fun portugueseLeavesTextUnchanged() {
        assertEquals(
            "Ativar ecossistema contínuo",
            VeyroI18n.translate("Ativar ecossistema contínuo", AppLanguage.PORTUGUESE)
        )
    }

    @Test
    fun englishTranslatesStaticInterfaceText() {
        assertEquals(
            "Enable continuous ecosystem",
            VeyroI18n.translate("Ativar ecossistema contínuo", AppLanguage.ENGLISH)
        )
        assertEquals(
            "Nearby client initialized.",
            VeyroI18n.translate("Cliente Nearby inicializado.", AppLanguage.ENGLISH)
        )
    }

    @Test
    fun englishTranslatesDynamicStatusWithoutChangingDeviceName() {
        assertEquals(
            "Connected to Pixel 9",
            VeyroI18n.translate("Conectado a Pixel 9", AppLanguage.ENGLISH)
        )
        assertEquals(
            "15 of 15 enabled",
            VeyroI18n.translate("15 de 15 ativos", AppLanguage.ENGLISH)
        )
    }

    @Test
    fun englishTranslatesContextualPermissionConsent() {
        assertEquals(
            "Remote control access required",
            VeyroI18n.translate(
                "Acesso de controle remoto necessário",
                AppLanguage.ENGLISH
            )
        )
        assertEquals(
            "Keep disabled",
            VeyroI18n.translate("Manter desativado", AppLanguage.ENGLISH)
        )
    }

    @Test
    fun englishTranslatesDesktopInteropStatusWithoutChangingPeerName() {
        assertEquals(
            "Secure channel active with Surface Laptop.",
            VeyroI18n.translate(
                "Canal seguro ativo com Surface Laptop.",
                AppLanguage.ENGLISH
            )
        )
        assertEquals(
            "Channel with Surface Laptop closed; BLE remains available.",
            VeyroI18n.translate(
                "Canal com Surface Laptop encerrado; BLE permanece disponível.",
                AppLanguage.ENGLISH
            )
        )
    }
}
