package app.plait.ui

/** Shortens long text in the middle so the part that matters at the end (a domain, a file extension) stays visible. */
object TextUtil {
    fun shortenAccount(s: String, max: Int = 26): String {
        if (s.length <= max) return s
        val at = s.lastIndexOf('@')
        if (at > 0) {
            val domain = s.substring(at)
            val room = max - domain.length - 1
            if (room >= 3) return s.substring(0, room) + "…" + domain
            // The domain alone is too long: keep the start of each side.
            return s.substring(0, (max / 2 - 1).coerceAtLeast(3)) + "…" + domain.takeLast((max / 2 - 1).coerceAtLeast(4))
        }
        return s.take(max - 1) + "…"
    }
}
