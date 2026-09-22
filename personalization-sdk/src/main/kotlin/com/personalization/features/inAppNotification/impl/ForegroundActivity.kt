package com.personalization.features.inAppNotification.impl

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * The activity on screen, so popups can be shown without the host handing a FragmentManager over.
 *
 * Process-wide: every SDK instance shares it. It only learns about activities resumed after
 * [install], so an SDK initialized once the first activity is already on screen sees nothing until
 * the next one resumes — initialize from Application.onCreate or an activity's onCreate.
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
