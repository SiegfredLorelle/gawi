package com.gawi.feature.settings.testsupport

import androidx.annotation.StringRes
import org.robolectric.RuntimeEnvironment

/** The resolved string, so a screen test asserts on copy by id and never by literal. */
fun string(@StringRes id: Int): String = RuntimeEnvironment.getApplication().resources.getString(id)
