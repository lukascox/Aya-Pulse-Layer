package com.kei.pulse.tile

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.kei.pulse.R

enum class QuickSettingsTileAddResult {
    ADDED,
    ALREADY_ADDED,
    NOT_ADDED,
    UNAVAILABLE,
    ERROR,
}

object QuickSettingsTilePrompt {

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun request(activity: Activity, onResult: (QuickSettingsTileAddResult) -> Unit) {
        if (!isSupported) {
            onResult(QuickSettingsTileAddResult.UNAVAILABLE)
            return
        }
        requestApi33(activity, onResult)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestApi33(activity: Activity, onResult: (QuickSettingsTileAddResult) -> Unit) {
        val statusBarManager = activity.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onResult(QuickSettingsTileAddResult.UNAVAILABLE)
            return
        }

        statusBarManager.requestAddTileService(
            ComponentName(activity, PerformanceTileService::class.java),
            activity.getString(R.string.tile_label),
            Icon.createWithResource(activity, R.drawable.ic_tile_underclock),
            activity.mainExecutor,
        ) { result ->
            onResult(result.toTileAddResult())
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun Int.toTileAddResult(): QuickSettingsTileAddResult {
        return when (this) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> QuickSettingsTileAddResult.ADDED
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> QuickSettingsTileAddResult.ALREADY_ADDED
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> QuickSettingsTileAddResult.NOT_ADDED
            else -> QuickSettingsTileAddResult.ERROR
        }
    }
}
