package com.ethan.viewrecorder

import android.app.Application

/**
 *
 * @author wangxingchen01 2024/1/19
 */
class BaseApplication : Application() {

    companion object {
        const val notificationId: Int = 99999
        lateinit var appContext: Application
        var channelId = "10000" // 业务方自己定义
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
    }
}