package com.project.lol.service

import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.project.lol.R
import com.project.lol.util.Logger
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Best-effort integration with vivo/iQOO Origin Island (OriginOS 6).
 *
 * Origin Island is not part of the public Android SDK. The wire keys used here are based on the
 * publicly reverse-engineered notification.superx.* protocol documented by OriginOS Toolkit /
 * CunnyPlayground. Unsupported ROMs simply ignore these extras.
 */
object VivoOriginIsland {
    private const val TAG = "VivoOriginIsland"

    internal const val OP_SHOW = 0
    internal const val OP_END = 2
    internal const val SCENE = "TRAIN"
    internal const val RIGHT_TEMPLATE_PROGRESS = 2
    internal const val RIGHT_TEMPLATE_TEXT_ICON = 4

    private val sceneRegistrationAttempted = AtomicBoolean(false)

    fun isSupportedDevice(): Boolean {
        val brand = Build.BRAND.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        return brand.contains("vivo", ignoreCase = true) ||
            brand.contains("iqoo", ignoreCase = true) ||
            manufacturer.contains("vivo", ignoreCase = true) ||
            manufacturer.contains("iqoo", ignoreCase = true)
    }

    fun applyTo(
        context: Context,
        builder: NotificationCompat.Builder,
        title: String,
        artist: String,
        positionMs: Long,
        durationMs: Long,
        accentColor: Int,
    ) {
        if (!isSupportedDevice()) return

        registerSceneOnce(context)

        val icon = runCatching {
            Icon.createWithResource(context, R.mipmap.ic_launcher)
        }.getOrNull()

        builder.addExtras(
            buildShowExtras(
                title = title.ifBlank { context.getString(R.string.app_name) },
                artist = artist,
                appLabel = context.getString(R.string.app_name),
                positionMs = positionMs,
                durationMs = durationMs,
                accentColor = accentColor,
                icon = icon,
            )
        )
    }

    /**
     * Send OriginOS the graceful-unmount operation before the normal Android notification cancel.
     * The caller still owns the final cancel/stopForeground.
     */
    fun unmount(context: Context, notificationId: Int, channelId: String) {
        if (!isSupportedDevice()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            val end = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .addExtras(buildEndExtras())
                .build()
            manager.notify(notificationId, end)
        }.onFailure {
            Logger.d(TAG, "Origin Island unmount hint failed: ${it.message}")
        }
    }

    internal fun usesProgressTemplate(positionMs: Long, durationMs: Long): Boolean {
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, if (safeDuration > 0L) safeDuration else Long.MAX_VALUE)
        return safeDuration > 0L && safePosition in 1 until safeDuration
    }

    internal fun progressPercent(positionMs: Long, durationMs: Long): Int {
        if (durationMs <= 0L) return 0
        val safePosition = positionMs.coerceIn(0L, durationMs)
        return ((safePosition * 100L) / durationMs).toInt().coerceIn(0, 100)
    }

    internal fun buildShowExtras(
        title: String,
        artist: String,
        appLabel: String,
        positionMs: Long,
        durationMs: Long,
        accentColor: Int,
        icon: Icon? = null,
    ): Bundle {
        val hasProgress = usesProgressTemplate(positionMs, durationMs)
        val progressPercent = progressPercent(positionMs, durationMs)

        val extras = Bundle().apply {
            putInt("notification.superx.operation", OP_SHOW)
            putBoolean("notification.superx.showNotify", true)
            putInt("notification.superx.template", if (hasProgress) 2 else 1)
            putString("notification.superx.scene", SCENE)
            putInt("notification.superx.changedRecord", 0)
        }

        val baseInfos = Bundle().apply {
            putCharSequence("notification.superx.baseInfos.title", title)
            putCharSequence("notification.superx.baseInfos.content", artist)
            icon?.let { putParcelable("notification.superx.baseInfos.icon", it) }
            putInt("notification.superx.baseInfos.subInfo", 1)
            putString("notification.superx.baseInfos.subText", appLabel)
        }
        extras.putBundle("notification.superx.baseInfos", baseInfos)

        val capsule = Bundle().apply {
            putInt("notification.superx.capsule.state", 1)
            putCharSequence("notification.superx.capsule.content", title)
            icon?.let { putParcelable("notification.superx.capsule.icon", it) }
        }
        extras.putBundle("notification.superx.capsule", capsule)

        val infos = Bundle().apply {
            if (hasProgress) {
                putInt("notification.superx.infos.progress", progressPercent)
                putInt("notification.superx.infos.progressColor", accentColor)
                icon?.let {
                    putParcelableArrayList(
                        "notification.superx.infos.nodeIcon",
                        ArrayList(listOf(it, it)),
                    )
                    putParcelable("notification.superx.infos.indicatorIcon", it)
                    putInt("notification.superx.infos.indicatorLoc", 1)
                }
            } else {
                putString("notification.superx.infos.describe", appLabel)
                putString("notification.superx.infos.coreInfo", artist)
                icon?.let { putParcelable("notification.superx.infos.image", it) }
            }
        }
        extras.putBundle("notification.superx.infos", infos)

        val shortInfos = Bundle().apply {
            putString("notification.superx.shortInfos.describeShort", appLabel)
            putString("notification.superx.shortInfos.coreInfoShort", artist)
            icon?.let { putParcelable("notification.superx.shortInfos.image", it) }
        }
        extras.putBundle("notification.superx.shortInfos", shortInfos)

        val island = Bundle().apply {
            putInt("island.superx.leftTemplate", 1)
            putInt(
                "island.superx.rightTemplate",
                if (hasProgress) RIGHT_TEMPLATE_PROGRESS else RIGHT_TEMPLATE_TEXT_ICON,
            )

            putBundle(
                "island.superx.leftInfo",
                Bundle().apply {
                    putString("island.superx.leftInfo.content", title)
                    icon?.let { putParcelable("island.superx.leftInfo.icon", it) }
                }
            )

            putBundle(
                "island.superx.rightInfo",
                Bundle().apply {
                    if (hasProgress) {
                        putInt("island.superx.rightInfo.progressValue", progressPercent)
                        putInt("island.superx.rightInfo.progressState", 0)
                        putInt("island.superx.rightInfo.progressColor", accentColor)
                    } else {
                        putString("island.superx.rightInfo.content", artist)
                        icon?.let { putParcelable("island.superx.rightInfo.icon", it) }
                    }
                }
            )
        }
        extras.putBundle("notification.superx.island", island)

        return extras
    }

    internal fun buildEndExtras(): Bundle = Bundle().apply {
        putInt("notification.superx.operation", OP_END)
    }

    private fun registerSceneOnce(context: Context) {
        if (!sceneRegistrationAttempted.compareAndSet(false, true)) return

        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            val method = NotificationManager::class.java.getMethod(
                "setSuperXInfosSceneList",
                MutableList::class.java,
                MutableList::class.java,
                MutableList::class.java,
                MutableList::class.java,
            )
            method.invoke(
                manager,
                arrayListOf(SCENE),
                arrayListOf("true"),
                arrayListOf(context.packageName),
                arrayListOf("true"),
            )
            Logger.i(TAG, "Origin Island scene registration requested")
        }.onFailure {
            // Expected on non-vivo ROMs and on builds where the hidden method is inaccessible.
            Logger.d(TAG, "Origin Island scene registration unavailable: ${it.javaClass.simpleName}")
        }
    }
}
