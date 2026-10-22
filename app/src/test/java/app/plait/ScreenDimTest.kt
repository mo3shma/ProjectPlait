package app.plait

import app.plait.ui.ScreenDim
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenDimTest {
    @Test fun strongestRegisteredScrimWinsAndUnregisterDropsIt() {
        assertEquals(0f, ScreenDim.alpha, 0f)
        val a = { 0.2f }
        val b = { 0.55f }
        ScreenDim.register(a); ScreenDim.register(b)
        assertEquals(0.55f, ScreenDim.alpha, 0f)
        ScreenDim.unregister(b)
        assertEquals(0.2f, ScreenDim.alpha, 0f)
        ScreenDim.unregister(a)
        assertEquals(0f, ScreenDim.alpha, 0f)
    }

    @Test fun fadeMultiplierFadesOutByAboutHalfDim() {
        assertEquals(1f, ScreenDim.fadeMultiplier(0f), 0f)
        assertEquals(0f, ScreenDim.fadeMultiplier(0.45f), 0f)
        assertEquals(0f, ScreenDim.fadeMultiplier(0.55f), 0f)
        assertEquals(0.5f, ScreenDim.fadeMultiplier(0.2f), 0.001f)
    }
}
