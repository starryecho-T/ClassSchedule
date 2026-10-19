package com.classschedule.app.reminder
import org.junit.Assert.*
import org.junit.Test
class AlarmPolicyTest {
    @Test fun deniedPermissionUsesApproximateAlarm() {
        var count = 0
        AlarmPolicy.schedule(false, { fail("Exact must not be attempted") }, { count++ })
        assertEquals(1, count)
    }
    @Test fun permissionRevokedDuringSchedulingFallsBack() {
        var count = 0
        AlarmPolicy.schedule(true, { throw SecurityException("Revoked") }, { count++ })
        assertEquals(1, count)
    }
    @Test fun grantedPermissionDoesNotScheduleTwice() {
        var count = 0
        AlarmPolicy.schedule(true, { count++ }, { fail("Duplicate alarm") })
        assertEquals(1, count)
    }
}
