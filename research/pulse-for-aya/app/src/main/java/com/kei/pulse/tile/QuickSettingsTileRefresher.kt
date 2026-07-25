package com.kei.pulse.tile

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService

object QuickSettingsTileRefresher {

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        PerformanceTileService.refreshActiveTile()
        requestListeningState(appContext)
        Handler(Looper.getMainLooper()).postDelayed(
            {
                PerformanceTileService.refreshActiveTile()
                requestListeningState(appContext)
            },
            300L,
        )
        Handler(Looper.getMainLooper()).postDelayed(
            {
                PerformanceTileService.refreshActiveTile()
                requestListeningState(appContext)
            },
            1_000L,
        )
    }

    private fun requestListeningState(context: Context) {
        TileService.requestListeningState(
            context,
            ComponentName(context, PerformanceTileService::class.java),
        )
    }
}
