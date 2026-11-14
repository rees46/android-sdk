package com.personalization.demo

import android.app.Application
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * The demo's test runner: [AndroidJUnitRunner] with one switch, for tests that need a process the demo
 * has not touched.
 *
 * [DemoApplication] initializes the demo shop in `onCreate`, and the first initialization of a process
 * decides its core (`Rees46Config.coreV2`). With the argument `cleanProcess=true` the application's
 * `onCreate` is not called, so the test makes the process's first initialization itself:
 * `-Pandroid.testInstrumentationRunnerArguments.cleanProcess=true`, or `-e cleanProcess true` with
 * `am instrument`. Without it the runner behaves as [AndroidJUnitRunner].
 */
class DemoTestRunner : AndroidJUnitRunner() {

    // Called before callApplicationOnCreate: the system passes the arguments first.
    override fun onCreate(arguments: Bundle?) {
        cleanProcess = arguments?.getString(ARG_CLEAN_PROCESS) == "true"
        super.onCreate(arguments)
    }

    override fun callApplicationOnCreate(app: Application) {
        if (cleanProcess) return
        super.callApplicationOnCreate(app)
    }

    companion object {
        const val ARG_CLEAN_PROCESS = "cleanProcess"

        /** True when the application's `onCreate` was not called in this process. */
        @Volatile
        var cleanProcess: Boolean = false
            private set
    }
}
