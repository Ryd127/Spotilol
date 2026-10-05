package com.project.lol.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
 *
 * Important: the island payload is posted as its own ordinary notification, separate from
 * Spotilol's MediaStyle foreground notification. This mirrors the known working reference caster.
 */
object VivoOriginIsland {
    private const val TAG = "VivoOriginIsland"

    internal const val CHANNEL_ID = "spotilol_origin_island"
    internal const val NOTIFICATION_ID = 3001

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

    fun post(
        context: Context,
        title: String,
        artist: String,
        positionMs: Long,
        durationMs: Long,
        accentColor: Int,
    ) {
        if (!isSupportedDevice()) return
        if (title.isBlank() && artist.isBlank()) {
            cancel(context)
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)
        registerSceneOnce(context)

        // A single self-contained Icon instance is deliberately reused in every nested bundle.
        // This matches the known working OriginOS caster path more closely than resource Icons.
        val sourceIcon = createLauncherIcon(context)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val safeTitle = title.ifBlank { context.getString(R.string.app_name) }
        val extras = buildShowExtras(
            title = safeTitle,
            artist = artist,
            appLabel = context.getString(R.string.app_name),
            positionMs = positionMs,
            durationMs = durationMs,
            accentColor = accentColor,
            icon = sourceIcon,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(safeTitle)
            .setContentText(artist)
            .setSubText(context.getString(R.string.app_name))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(accentColor)
            .addExtras(extras)

        contentIntent?.let { builder.setContentIntent(it) }

        if (usesProgressTemplate(positionMs, durationMs)) {
            builder.setProgress(100, progressPercent(positionMs, durationMs), false)
        }

        runCatching {
            manager.notify(NOTIFICATION_ID, builder.build())
        }.onFailure {
            Logger.w(TAG, "Origin Island notification post failed: ${it.message}")
        }
    }

    /**
     * Unmount the OriginOS pill first, then remove only the dedicated island notification.
     * Spotilol's normal MediaStyle notification is managed separately by the media service.
     */
    fun cancel(context: Context) {
        if (!isSupportedDevice()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)

        runCatching {
            val end = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .addExtras(buildEndExtras())
                .build()
            manager.notify(NOTIFICATION_ID, end)
        }.onFailure {
            Logger.d(TAG, "Origin Island unmount hint failed: ${it.message}")
        }

        manager.cancel(NOTIFICATION_ID)
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

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Spotilol Origin Island",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Provides playback information to vivo Origin Island."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun createLauncherIcon(context: Context): Icon? = runCatching {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, 96, 96)
        drawable.draw(canvas)
        Icon.createWithBitmap(bitmap)
    }.getOrNull()

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
            // Hidden API access is ROM-dependent. The notification payload itself remains safe.
            Logger.d(TAG, "Origin Island scene registration unavailable: ${it.javaClass.simpleName}")
        }
    }
}
