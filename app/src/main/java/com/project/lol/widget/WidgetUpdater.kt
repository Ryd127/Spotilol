package com.project.lol.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

object WidgetUpdater {

    suspend fun push(context: Context, state: WidgetState) {
        // The explicit OfflineMode preference is the ownership boundary between Spotilol's two
        // playback engines. A service that is finishing asynchronously must not overwrite the
        // widgets/source after the user has already switched to the other engine.
        val offlineMode = context.getSharedPreferences("spotilol_prefs", Context.MODE_PRIVATE)
            .getBoolean("OfflineMode", false)
        when (state.source) {
            WidgetState.SOURCE_WEB -> if (offlineMode) return
            WidgetState.SOURCE_OFFLINE -> if (!offlineMode) return
        }

        WidgetSource.set(context, state.source)
        val version = if (state.cover != null) System.currentTimeMillis() else 0L
        state.cover?.let { writeCover(context, it) }
        val manager = GlanceAppWidgetManager(context)
        pushTo(context, manager, SpotilolPlayerWidget::class.java, state, version) { SpotilolPlayerWidget() }
        pushTo(context, manager, SpotilolArtWidget::class.java, state, version) { SpotilolArtWidget() }
    }

    private suspend fun pushTo(
        context: Context,
        manager: GlanceAppWidgetManager,
        cls: Class<out GlanceAppWidget>,
        state: WidgetState,
        version: Long,
        factory: () -> GlanceAppWidget
    ) {
        val ids = runCatching { manager.getGlanceIds(cls) }.getOrNull() ?: return
        if (ids.isEmpty()) return
        val widget = factory()
        for (id in ids) {
            runCatching {
                updateAppWidgetState(context, id) { prefs -> state.writeTo(prefs, version) }
                widget.update(context, id)
            }
        }
    }

    private suspend fun writeCover(context: Context, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        runCatching {
            FileOutputStream(coverFile(context)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }
}
