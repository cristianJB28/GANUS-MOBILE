package com.ganus.mobile

import android.app.Application
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GanusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                permitirCaptura(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                permitirCaptura(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                permitirCaptura(activity)
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        fun permitirCaptura(activity: Activity) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                marcarContenidoNoSensible(activity.window.decorView)
            }
        }

        private fun marcarContenidoNoSensible(view: View) {
            view.contentSensitivity = View.CONTENT_SENSITIVITY_NOT_SENSITIVE
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    marcarContenidoNoSensible(view.getChildAt(index))
                }
            }
        }
    }
}
