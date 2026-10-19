package com.classschedule.app.reminder

/** Exact-alarm access may be revoked between checking and registration. */
internal object AlarmPolicy {
    fun schedule(canExact: Boolean, exact: () -> Unit, approximate: () -> Unit) {
        if (canExact) {
            try { exact(); return } catch (_: SecurityException) { /* Fall back on revocation. */ }
        }
        approximate()
    }
}
