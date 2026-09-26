package com.gawi.widget.testsupport

import android.content.Context
import android.content.Intent

/**
 * What `openAppAction` falls back to under test: this module's manifest
 * declares three receivers and no activity, so the package manager resolves
 * nothing under Robolectric and the fallback is what the tree actually carries.
 */
fun launchIntent(context: Context): Intent = Intent(Intent.ACTION_MAIN)
    .addCategory(Intent.CATEGORY_LAUNCHER)
    .setPackage(context.packageName)
