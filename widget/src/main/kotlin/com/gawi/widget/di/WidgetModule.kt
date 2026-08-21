package com.gawi.widget.di

import com.gawi.core.data.projection.ProjectionListener
import com.gawi.widget.GlanceProjectionListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The one binding this module contributes to the app's graph.
 *
 * `:core:data` declares [ProjectionListener] and deliberately binds nothing, so
 * this is what closes the graph — and what makes the module rule hold, since the
 * Glance dependency stays on this side of `widget → core` (architecture §2).
 * `:app` depends on `:widget`, which is how Hilt finds it.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetModule {

    /**
     * Unscoped: it holds nothing but the application context. The state it
     * pushes lives in the repository singleton that calls it.
     */
    @Binds
    abstract fun projectionListener(implementation: GlanceProjectionListener): ProjectionListener
}
