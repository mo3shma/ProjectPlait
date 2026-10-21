package app.plait

import app.plait.ai.AiPrefs
import app.plait.audio.pickSpeechId
import app.plait.ui.screens.betterVoiceVisible
import app.plait.ui.screens.speechPickFor
import app.plait.ai.textChoiceOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAfterTest {
    private fun has(vararg ids: String): (String) -> Boolean = { it in ids }

    @Test fun chosenSpeechModelWinsWhenInstalled() {
        assertEquals("speech-whisper-base", pickSpeechId("speech-whisper-base", has("speech-whisper-base", "speech-whisper-turbo")))
    }

    @Test fun otherwiseTheLargestInstalledIsUsed() {
        assertEquals("speech-whisper-turbo", pickSpeechId(null, has("speech-whisper-tiny", "speech-whisper-turbo", "speech-whisper-small")))
        assertEquals("speech-whisper-small", pickSpeechId("speech-whisper-turbo", has("speech-whisper-tiny", "speech-whisper-small")))
        assertEquals("speech-whisper-base", pickSpeechId(null, has("speech-whisper-tiny", "speech-whisper-base")))
    }

    @Test fun noSpeechModelMeansNoTranscription() {
        assertNull(pickSpeechId(null, has()))
        assertNull(pickSpeechId("speech-whisper-turbo", has()))
    }

    @Test fun suggestionHidesOnlyWhenBothAreThereOrDismissed() {
        assertTrue(betterVoiceVisible(false, speechInstalled = false, qwenInstalled = false))
        assertTrue(betterVoiceVisible(false, speechInstalled = true, qwenInstalled = false))
        assertTrue(betterVoiceVisible(false, speechInstalled = false, qwenInstalled = true))
        assertFalse(betterVoiceVisible(false, speechInstalled = true, qwenInstalled = true))
        assertFalse(betterVoiceVisible(true, speechInstalled = false, qwenInstalled = false))
    }

    @Test fun recommendedWhisperFollowsMemory() {
        assertEquals("speech-whisper-turbo", speechPickFor(7_800_000_000L))
        assertEquals("speech-whisper-small", speechPickFor(6_000_000_000L))
        assertEquals("speech-whisper-base", speechPickFor(4_000_000_000L))
    }

    @Test fun freshInstallUsesPlaitsOwnRulesButSavedChoicesStay() {
        assertEquals(AiPrefs.TextChoice.Rules, textChoiceOf(null))
        assertEquals(AiPrefs.TextChoice.Rules, textChoiceOf("garbage"))
        assertEquals(AiPrefs.TextChoice.Auto, textChoiceOf("Auto"))
        assertEquals(AiPrefs.TextChoice.Local, textChoiceOf("Local"))
        assertEquals(AiPrefs.TextChoice.Nano, textChoiceOf("Nano"))
    }
}
