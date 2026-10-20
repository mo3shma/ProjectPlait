package app.plait

import app.plait.audio.WhisperTranscriber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MixedSpeechTest {
    @Test fun repetitionLoopsCollapse() {
        // Taken from Whisper's own output on a mixed Arabic and English recording.
        assertEquals("Let's start. Saba-ha", WhisperTranscriber.cleanRepeats("Let's start. Saba-ha-ha-ha-ha-ha-ha-ha-ha-"))
        assertEquals("محطة جيلا مراتا بيلا بي", WhisperTranscriber.cleanRepeats("محطة جيلا مراتا بيلا بيلا بيلا بيلا بيلا بيلا بي"))
        assertEquals("we need to we need to ship it", WhisperTranscriber.cleanRepeats("we need to we need to ship it"))
        assertEquals("go now", WhisperTranscriber.cleanRepeats("go now now now now"))
    }

    @Test fun mixedLanguagePairs() {
        assertEquals(listOf("ar", "en"), WhisperTranscriber.pair("ar+en"))
        assertNull(WhisperTranscriber.pair("ar"))
        assertNull(WhisperTranscriber.pair("auto"))
        assertEquals("ar", WhisperTranscriber.langCode("<|ar|>"))
        assertEquals("en", WhisperTranscriber.langCode("en"))
    }
}
