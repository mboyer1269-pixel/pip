package ca.michael.pip

import android.app.Application

class PipApp : Application() {
    lateinit var runtime: PipRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = PipRuntime(this)
    }
}

val android.content.Context.pip: PipRuntime
    get() = (applicationContext as PipApp).runtime
