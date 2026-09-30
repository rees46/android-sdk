package com.personalization.features.inAppNotification.impl

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.startup.Initializer
import java.lang.ref.WeakReference

/**
 * The activity on screen, so popups can be shown without the host handing a FragmentManager over.
 *
 * Process-wide: every SDK instance shares it. It only learns about activities resumed after
 * [install], which is why [ForegroundActivityInitializer] installs it at process start; SDK
 * initialization installs it too, for hosts that turned androidx.startup off.
 */
internal object ForegroundActivity : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var application: Application? = null

    @Volatile
    private var current: WeakReference<Activity>? = null

    fun install(context: Context) {
        val app = context.applicationContext as? Application ?: return
        synchronized(this) {
            // Compared by instance rather than flagged once: tests get a fresh Application each.
            if (application === app) return
            application?.unregisterActivityLifecycleCallbacks(this)
            current = null
            application = app
            app.registerActivityLifecycleCallbacks(this)
        }
    }

    fun get(): Activity? = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    // Kept through onPause: a paused activity is still on screen (multi-window, a system dialog on
    // top). It is let go once it stops, unless another one has taken its place already.
    override fun onActivityStopped(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

/**
 * Installs [ForegroundActivity] at process start through androidx.startup, before any activity is
 * created. Without it the tracker would only start with the first SDK initialization, and a shop
 * initialized lazily or from Dart after the first activity resumed would find no activity to show
 * popups in. Hosts that remove the startup provider still get it installed on initialization.
 */
internal class ForegroundActivityInitializer : Initializer<Unit> {

    override fun create(context: Context) = ForegroundActivity.install(context)

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
