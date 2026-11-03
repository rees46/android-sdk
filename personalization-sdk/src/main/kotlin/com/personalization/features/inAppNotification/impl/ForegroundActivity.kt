package com.personalization.features.inAppNotification.impl

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.startup.Initializer
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The activity on screen, so popups can be shown without the host handing a FragmentManager over.
 *
 * Process-wide: every SDK instance shares it. It only learns about activities resumed after
 * [install], which is why [ForegroundActivityInitializer] installs it at process start; SDK
 * initialization installs it too, for hosts that turned androidx.startup off.
 */
internal object ForegroundActivity : Application.ActivityLifecycleCallbacks {

    /**
     * Who else follows the activities: popups v3 learn when the app goes to the background and
     * back, and move a popup to the next activity when the one it was shown in is destroyed.
     * Called on the main thread.
     */
    interface Listener {
        fun onActivityResumed(activity: Activity) = Unit

        fun onActivityDestroyed(activity: Activity) = Unit

        /** The first activity started — the app came to the foreground. */
        fun onForeground() = Unit

        /** No activity is started any more, and not because one is being recreated. */
        fun onBackground() = Unit
    }

    @Volatile
    private var application: Application? = null

    @Volatile
    private var current: WeakReference<Activity>? = null

    private val listeners = CopyOnWriteArrayList<Listener>()

    /** Started activities; a rotation stops one and starts the next, which is not a trip to the background. */
    private var started = 0
    private var recreating = false

    /** Whether an activity of the app is started. */
    val isInForeground: Boolean get() = started > 0

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun install(context: Context) {
        val app = context.applicationContext as? Application ?: return
        synchronized(this) {
            // Compared by instance rather than flagged once: tests get a fresh Application each.
            if (application === app) return
            application?.unregisterActivityLifecycleCallbacks(this)
            current = null
            started = 0
            recreating = false
            application = app
            app.registerActivityLifecycleCallbacks(this)
        }
    }

    fun get(): Activity? = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
        listeners.forEach { it.onActivityResumed(activity) }
    }

    // Kept through onPause: a paused activity is still on screen (multi-window, a system dialog on
    // top). It is let go once it stops, unless another one has taken its place already.
    override fun onActivityStopped(activity: Activity) {
        if (current?.get() === activity) current = null
        started = (started - 1).coerceAtLeast(0)
        if (started > 0) return
        if (activity.isChangingConfigurations) {
            recreating = true
        } else {
            listeners.forEach { it.onBackground() }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
        listeners.forEach { it.onActivityDestroyed(activity) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        started++
        if (started != 1) return
        if (recreating) {
            recreating = false
        } else {
            listeners.forEach { it.onForeground() }
        }
    }

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
