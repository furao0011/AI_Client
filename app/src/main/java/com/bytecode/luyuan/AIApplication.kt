package com.bytecode.luyuan

import android.app.Application
import com.bytecode.luyuan.data.AppContainer
import com.bytecode.luyuan.data.DefaultAppContainer

class AIApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
