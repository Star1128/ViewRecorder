package com.ethan.viewrecorder

import android.app.Application

/**
 *
 * @author wangxingchen01 2024/1/19
 */
class BaseApplication : Application() {

    companion object {
        lateinit var appContext: Application
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
    }
}