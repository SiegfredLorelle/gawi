package com.gawi.core.data.time

import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The wall clock and the zone to read it in.
 *
 * Deliberately not `java.time.Clock`: `Clock.systemDefaultZone()` resolves the
 * zone once, at construction, and this repository is a `@Singleton` that
 * outlives a flight. Reading [ZoneId.systemDefault] per call means a user who
 * travels gets logical dates in the zone they are actually in.
 *
 * Being an interface is what lets tests roll the day over, which is the only
 * way a streak reaches zero with no new event.
 */
interface DeviceClock {

    fun now(): Instant

    fun zone(): ZoneId
}

@Singleton
class SystemDeviceClock @Inject constructor() : DeviceClock {

    override fun now(): Instant = Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
