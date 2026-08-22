package com.gawi.feature.today.testsupport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Required, not optional: `viewModelScope` dispatches on `Dispatchers.Main`,
 * which has no implementation on a bare JVM and throws on first use.
 *
 * Unconfined so a coroutine launched by a command runs eagerly, which keeps the
 * tests free of advance-the-scheduler calls that would say nothing about the
 * ViewModel.
 *
 * A copy of the one in the other two feature modules rather than something
 * shared. Feature modules do not depend on one another, and no test-fixtures
 * publishing is configured anywhere in this build.
 */
class MainDispatcherRule : TestWatcher() {

    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())

    override fun finished(description: Description) = Dispatchers.resetMain()
}
