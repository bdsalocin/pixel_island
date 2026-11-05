package com.bdsalocin.pixelisland

import android.app.Application
import com.bdsalocin.pixelisland.core.di.AppContainer

class PixelIslandApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
