package com.project.lol.islandbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean

object BridgeNotificationCaster {

    const val SOURCE_PACKAGE = "com.project.lol"
    private const val CHANNEL_ID = "spotilol_island_bridge"
    private const val NOTIFICATION_ID = 3101

    private const val OP_SHOW = 0
    private const val OP_END = 2
    private const val SCENE = "TRAIN"
    private const val RIGHT_TEMPLATE_PROGRESS = 2
    private const val RIGHT_TEMPLATE_TEXT_ICON = 4

    private val sceneRegistrationAttempted = AtomicBoolean(false)

    data class SourceMedia(
        val title: String,
        val artist: String,
        val positionMs: Long,
        val durationMs: Long,
        val playing: Boolean,
        val token: MediaSessionCompat.Token?,
    )

    fun fromSourceNotification(context: Context, notification: Notification): SourceMedia? {
        val token = runCatching { MediaStyle.getMediaSession(notification) }.getOrNull()
        val controller = token?.let {
            runCatching { MediaControllerCompat(context, it) }.getOrNull()
        }
        val metadata = controller?.metadata
        val state = controller?.playbackState
        val extras = notification.extras

        val title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()

        val artist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        if (title.isBlank() && artist.isBlank()) return null
        if (token == null && notification.category != Notification.CATEGORY_TRANSPORT) return null

        val duration = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L
        val playing = state?.state == PlaybackStateCompat.STATE_PLAYING ||
            state?.state == PlaybackStateCompat.STATE_BUFFERING

        return SourceMedia(
            title = title.ifBlank { "Spotilol" },
            artist = artist,
            positionMs = position,
            durationMs = duration,
            playing = playing,
            token = token,
        )
    }

    fun post(context: Context, source: SourceMedia) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)
        registerSceneOnce(context)

        val icon = createBridgeIcon(context)
        val progress = progressPercent(source.positionMs, source.durationMs)
        val hasProgress = source.durationMs > 0L &&
            source.positionMs in 1 until source.durationMs

        val launchIntent = context.packageManager.getLaunchIntentForPackage(SOURCE_PACKAGE)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.project.lol.islandbridge.R.drawable.ic_bridge)
            .setContentTitle(source.title)
            .setContentText(source.artist)
            .setSubText("Spotilol")
            .setOngoing(source.playing)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addExtras(
                buildOriginIslandExtras(
                    title = source.title,
                    artist = source.artist,
                    progress = progress,
                    hasProgress = hasProgress,
                    icon = icon,
                )
            )

        source.token?.let { token ->
            builder.setStyle(MediaStyle().setMediaSession(token))
        }
        contentIntent?.let { builder.setContentIntent(it) }
        if (hasProgress) builder.setProgress(100, progress, false)

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    fun postTest(context: Context) {
        post(
            context,
            SourceMedia(
                title = "Spotilol Island Test",
                artist = "Whitelist bridge active",
                positionMs = 35_000L,
                durationMs = 100_000L,
                playing = true,
                token = null,
            )
        )
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        runCatching {
            manager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(com.project.lol.islandbridge.R.drawable.ic_bridge)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .addExtras(Bundle().apply {
                        putInt("notification.superx.operation", OP_END)
                    })
                    .build(),
            )
        }
        manager.cancel(NOTIFICATION_ID)
    }

    internal fun progressPercent(positionMs: Long, durationMs: Long): Int {
        if (durationMs <= 0L) return 0
        return ((positionMs.coerceIn(0L, durationMs) * 100L) / durationMs)
            .toInt()
            .coerceIn(0, 100)
    }

    private fun buildOriginIslandExtras(
        title: String,
        artist: String,
        progress: Int,
        hasProgress: Boolean,
        icon: Icon?,
    ): Bundle {
        val extras = Bundle().apply {
            putInt("notification.superx.operation", OP_SHOW)
            putBoolean("notification.superx.showNotify", true)
            putInt("notification.superx.template", if (hasProgress) 2 else 1)
            putString("notification.superx.scene", SCENE)
            putInt("notification.superx.changedRecord", 0)
        }

        val base = Bundle().apply {
            putCharSequence("notification.superx.baseInfos.title", title)
            putCharSequence("notification.superx.baseInfos.content", artist)
            icon?.let { putParcelable("notification.superx.baseInfos.icon", it) }
            putInt("notification.superx.baseInfos.subInfo", 1)
            putString("notification.superx.baseInfos.subText", "Spotilol")
        }
        extras.putBundle("notification.superx.baseInfos", base)

        val capsule = Bundle().apply {
            putInt("notification.superx.capsule.state", 1)
            putCharSequence("notification.superx.capsule.content", title)
            icon?.let { putParcelable("notification.superx.capsule.icon", it) }
        }
        extras.putBundle("notification.superx.capsule", capsule)

        val infos = Bundle().apply {
            if (hasProgress) {
                putInt("notification.superx.infos.progress", progress)
                putInt("notification.superx.infos.progressColor", 0xFF1DB954.toInt())
                icon?.let {
                    putParcelableArrayList(
                        "notification.superx.infos.nodeIcon",
                        ArrayList(listOf(it, it)),
                    )
                    putParcelable("notification.superx.infos.indicatorIcon", it)
                    putInt("notification.superx.infos.indicatorLoc", 1)
                }
            } else {
                putString("notification.superx.infos.describe", "Spotilol")
                putString("notification.superx.infos.coreInfo", artist)
                icon?.let { putParcelable("notification.superx.infos.image", it) }
            }
        }
        extras.putBundle("notification.superx.infos", infos)

        extras.putBundle(
            "notification.superx.shortInfos",
            Bundle().apply {
                putString("notification.superx.shortInfos.describeShort", "Spotilol")
                putString("notification.superx.shortInfos.coreInfoShort", artist)
                icon?.let { putParcelable("notification.superx.shortInfos.image", it) }
            }
        )

        extras.putBundle(
            "notification.superx.island",
            Bundle().apply {
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
                            putInt("island.superx.rightInfo.progressValue", progress)
                            putInt("island.superx.rightInfo.progressState", 0)
                            putInt("island.superx.rightInfo.progressColor", 0xFF1DB954.toInt())
                        } else {
                            putString("island.superx.rightInfo.content", artist)
                            icon?.let { putParcelable("island.superx.rightInfo.icon", it) }
                        }
                    }
                )
            }
        )

        return extras
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Spotilol Island Bridge",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Recasts Spotilol playback for Vivo Origin Island."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun createBridgeIcon(context: Context): Icon? = runCatching {
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
        }
    }
}
