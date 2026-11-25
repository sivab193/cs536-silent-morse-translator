package edu.purdue.cs536.morse

/** Requires a candidate state to remain stable for [delayMs] before accepting it. */
class SignalDebouncer(private val delayMs: Long = 40L) {
    var value: Boolean = false
        private set
    private var candidate = false
    private var candidateSinceMs = 0L

    fun update(rawValue: Boolean, nowMs: Long): Boolean {
        if (rawValue != candidate) {
            candidate = rawValue
            candidateSinceMs = nowMs
        }
        if (candidate != value && nowMs - candidateSinceMs >= delayMs) value = candidate
        return value
    }

    fun reset() {
        value = false
        candidate = false
        candidateSinceMs = 0L
    }
}
