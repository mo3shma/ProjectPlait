package app.plait

import app.plait.ai.AiHarness
import app.plait.data.brief.DescriptionCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiHarnessTest {
    @Test fun jsonIsFoundInsideChatter() {
        val o = AiHarness.parseJson("Sure! ```json\n{\"purpose\": \"Plan the launch\", \"agenda\": [\"Bugs\"]}\n```")
        assertEquals("Plan the launch", o!!.getString("purpose"))
        assertNull(AiHarness.parseJson("no json here"))
    }

    @Test fun fieldsAreCheckedAgainstTheSource() {
        val src = "Weekly sync with Karim about the Q4 launch"
        assertEquals("Plan the Q4 launch with Karim", AiHarness.checked("plan the Q4 launch with Karim", src, 14))
        assertNull(AiHarness.checked("color: #1a73e8 !important;", src, 14))
        assertNull(AiHarness.checked("Review budget with Sarah", src, 14))
        assertNull(AiHarness.checked("Budget is 5000", src, 14))
        assertEquals("One two three…", AiHarness.checked("one two three four five", src, 3))
    }

    @Test fun styleSheetsNeverReachTheBrief() {
        val html = "<html><head><style>a { color: #1a73e8 !important; }</style></head><body><p>Plan the roadmap</p></body></html>"
        assertEquals("Plan the roadmap", DescriptionCleaner.clean(html))
        assertEquals("Plan the roadmap", DescriptionCleaner.clean("color: #1a73e8 !important;\nPlan the roadmap"))
    }
}
