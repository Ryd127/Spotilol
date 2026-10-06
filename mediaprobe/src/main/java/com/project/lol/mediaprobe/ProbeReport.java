package com.project.lol.mediaprobe;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProbeReport {
    private static final String SPOTIFY = "com.spotify.music";
    private static final String VIVO_MUSIC_WIDGET = "com.vivo.musicwidgetmix";

    private static final Set<String> LONG_METADATA_KEYS = new HashSet<>(Arrays.asList(
            MediaMetadata.METADATA_KEY_DURATION,
            MediaMetadata.METADATA_KEY_YEAR,
            MediaMetadata.METADATA_KEY_TRACK_NUMBER,
            MediaMetadata.METADATA_KEY_NUM_TRACKS,
            MediaMetadata.METADATA_KEY_DISC_NUMBER,
            MediaMetadata.METADATA_KEY_BT_FOLDER_TYPE,
            MediaMetadata.METADATA_KEY_ADVERTISEMENT,
            MediaMetadata.METADATA_KEY_DOWNLOAD_STATUS
    ));

    private ProbeReport() {}

    public static String capture(Context context, ComponentName listenerComponent) {
        StringBuilder out = new StringBuilder(32_768);
        line(out, "============================================================");
        line(out, "MediaProbe - официальный Spotify / OriginOS");
        line(out, "============================================================");
        line(out, "Время: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()));
        line(out, "Устройство: " + Build.MANUFACTURER + " " + Build.MODEL);
        line(out, "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        line(out, "Build: " + Build.DISPLAY);
        line(out, "");

        dumpPackage(context, out, SPOTIFY);
        line(out, "");

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        boolean listenerGranted = notificationManager != null
                && notificationManager.isNotificationListenerAccessGranted(listenerComponent);
        line(out, "Доступ к уведомлениям MediaProbe: " + (listenerGranted ? "РАЗРЕШЁН" : "НЕ РАЗРЕШЁН"));
        line(out, "");

        dumpSessions(context, out, listenerComponent);
        line(out, "");
        dumpNotifications(out);

        line(out, "");
        line(out, "============================================================");
        line(out, "КОНЕЦ ОТЧЁТА");
        line(out, "============================================================");
        return out.toString();
    }

    private static void dumpPackage(Context context, StringBuilder out, String packageName) {
        section(out, "ПАКЕТ " + packageName);
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            line(out, "label=" + pm.getApplicationLabel(info.applicationInfo));
            line(out, "versionName=" + info.versionName);
            line(out, "versionCode=" + info.getLongVersionCode());
            line(out, "uid=" + info.applicationInfo.uid);
            line(out, "sourceDir=" + info.applicationInfo.sourceDir);
            line(out, "targetSdk=" + info.applicationInfo.targetSdkVersion);

            if (info.signingInfo != null) {
                Signature[] signers = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
                for (int i = 0; i < signers.length; i++) {
                    line(out, "signer[" + i + "].sha256=" + sha256(signers[i].toByteArray()));
                }
            }
        } catch (Throwable t) {
            line(out, "ОШИБКА чтения пакета: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void dumpSessions(Context context, StringBuilder out, ComponentName listenerComponent) {
        section(out, "ACTIVE MEDIA SESSIONS");
        MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
        if (manager == null) {
            line(out, "MediaSessionManager недоступен.");
            return;
        }

        List<MediaController> controllers;
        try {
            controllers = manager.getActiveSessions(listenerComponent);
        } catch (Throwable t) {
            line(out, "Не удалось получить активные сессии: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            line(out, "Проверь разрешение «Доступ к уведомлениям» для MediaProbe.");
            return;
        }

        line(out, "Количество активных сессий: " + controllers.size());
        for (int i = 0; i < controllers.size(); i++) {
            MediaController controller = controllers.get(i);
            line(out, "  [" + i + "] " + controller.getPackageName() + " raw=" + controller);
        }

        int spotifyIndex = 0;
        for (MediaController controller : controllers) {
            if (!SPOTIFY.equals(controller.getPackageName())) {
                continue;
            }
            line(out, "");
            section(out, "SPOTIFY MEDIA SESSION #" + (++spotifyIndex));
            dumpController(out, controller);
        }

        if (spotifyIndex == 0) {
            line(out, "");
            line(out, "Активная сессия com.spotify.music НЕ НАЙДЕНА.");
            line(out, "Оставь трек официального Spotify играющим и сними отчёт ещё раз.");
        }
    }

    private static void dumpController(StringBuilder out, MediaController c) {
        line(out, "package=" + c.getPackageName());
        line(out, "rawController=" + c);
        line(out, "sessionToken=" + c.getSessionToken());
        line(out, "flags=0x" + Long.toHexString(c.getFlags()));
        line(out, "ratingType=" + c.getRatingType());
        line(out, "sessionActivity=" + describePendingIntent(c.getSessionActivity()));

        Bundle extras = c.getExtras();
        line(out, "sessionExtras:");
        dumpBundle(out, "  ", extras, 0);

        MediaController.PlaybackInfo pi = c.getPlaybackInfo();
        line(out, "playbackInfo:");
        if (pi == null) {
            line(out, "  null");
        } else {
            line(out, "  playbackType=" + pi.getPlaybackType());
            line(out, "  volumeControl=" + pi.getVolumeControl());
            line(out, "  maxVolume=" + pi.getMaxVolume());
            line(out, "  currentVolume=" + pi.getCurrentVolume());
            AudioAttributes aa = pi.getAudioAttributes();
            if (aa != null) {
                line(out, "  audioAttributes.usage=" + aa.getUsage());
                line(out, "  audioAttributes.contentType=" + aa.getContentType());
                line(out, "  audioAttributes.flags=0x" + Integer.toHexString(aa.getFlags()));
                line(out, "  audioAttributes.raw=" + aa);
            }
        }

        line(out, "playbackState:");
        dumpPlaybackState(out, c.getPlaybackState());

        line(out, "metadata:");
        dumpMetadata(out, c.getMetadata());

        CharSequence queueTitle = c.getQueueTitle();
        line(out, "queueTitle=" + safe(queueTitle));

        List<MediaSession.QueueItem> queue = c.getQueue();
        if (queue == null) {
            line(out, "queue=null");
        } else {
            line(out, "queue.size=" + queue.size());
            int limit = Math.min(queue.size(), 100);
            for (int i = 0; i < limit; i++) {
                MediaSession.QueueItem item = queue.get(i);
                line(out, "  queue[" + i + "].id=" + item.getQueueId());
                dumpDescription(out, "    ", item.getDescription());
            }
            if (queue.size() > limit) {
                line(out, "  ... ещё " + (queue.size() - limit) + " элементов");
            }
        }
    }

    private static void dumpPlaybackState(StringBuilder out, PlaybackState state) {
        if (state == null) {
            line(out, "  null");
            return;
        }
        line(out, "  state=" + stateName(state.getState()) + "(" + state.getState() + ")");
        line(out, "  position=" + state.getPosition());
        line(out, "  bufferedPosition=" + state.getBufferedPosition());
        line(out, "  speed=" + state.getPlaybackSpeed());
        line(out, "  updateTime=" + state.getLastPositionUpdateTime());
        line(out, "  activeQueueItemId=" + state.getActiveQueueItemId());
        line(out, "  actions=0x" + Long.toHexString(state.getActions()));
        line(out, "  actionsDecoded=" + decodeActions(state.getActions()));
        line(out, "  errorCode=" + state.getErrorCode());
        line(out, "  errorMessage=" + safe(state.getErrorMessage()));
        line(out, "  extras:");
        dumpBundle(out, "    ", state.getExtras(), 0);

        List<PlaybackState.CustomAction> customActions = state.getCustomActions();
        line(out, "  customActions.size=" + customActions.size());
        for (int i = 0; i < customActions.size(); i++) {
            PlaybackState.CustomAction action = customActions.get(i);
            line(out, "    [" + i + "] action=" + action.getAction());
            line(out, "        name=" + safe(action.getName()));
            line(out, "        iconRes=" + action.getIcon());
            line(out, "        extras:");
            dumpBundle(out, "          ", action.getExtras(), 0);
        }
    }

    private static void dumpMetadata(StringBuilder out, MediaMetadata metadata) {
        if (metadata == null) {
            line(out, "  null");
            return;
        }

        List<String> keys = new ArrayList<>(metadata.keySet());
        Collections.sort(keys);
        line(out, "  keyCount=" + keys.size());
        for (String key : keys) {
            line(out, "  KEY " + key + " = " + describeMetadataValue(metadata, key));
        }

        line(out, "  description:");
        dumpDescription(out, "    ", metadata.getDescription());
    }

    private static String describeMetadataValue(MediaMetadata metadata, String key) {
        try {
            Bitmap bitmap = metadata.getBitmap(key);
            if (bitmap != null) {
                return describeBitmap(bitmap);
            }
        } catch (Throwable ignored) {}

        try {
            Rating rating = metadata.getRating(key);
            if (rating != null) {
                return describeRating(rating);
            }
        } catch (Throwable ignored) {}

        if (LONG_METADATA_KEYS.contains(key)) {
            try {
                return "LONG(" + metadata.getLong(key) + ")";
            } catch (Throwable ignored) {}
        }

        try {
            CharSequence text = metadata.getText(key);
            if (text != null) {
                return text.getClass().getSimpleName() + "(\\\"" + oneLine(text.toString()) + "\\")";
            }
        } catch (Throwable ignored) {}

        try {
            String value = metadata.getString(key);
            if (value != null) {
                return "STRING(\\\"" + oneLine(value) + "\\")";
            }
        } catch (Throwable ignored) {}

        return "<ключ присутствует, тип не распознан публичным API>";
    }

    private static void dumpDescription(StringBuilder out, String indent, MediaDescription d) {
        if (d == null) {
            line(out, indent + "null");
            return;
        }
        line(out, indent + "mediaId=" + d.getMediaId());
        line(out, indent + "title=" + safe(d.getTitle()));
        line(out, indent + "subtitle=" + safe(d.getSubtitle()));
        line(out, indent + "description=" + safe(d.getDescription()));
        line(out, indent + "iconBitmap=" + describeBitmap(d.getIconBitmap()));
        line(out, indent + "iconUri=" + d.getIconUri());
        line(out, indent + "mediaUri=" + d.getMediaUri());
        line(out, indent + "extras:");
        dumpBundle(out, indent + "  ", d.getExtras(), 0);
    }

    private static void dumpNotifications(StringBuilder out) {
        section(out, "ACTIVE NOTIFICATIONS");
        StatusBarNotification[] notifications = ProbeNotificationListener.currentNotifications();
        if (notifications == null) {
            line(out, "NotificationListenerService ещё не подключён.");
            line(out, "Вернись в MediaProbe после выдачи доступа и нажми «Снять снимок» ещё раз.");
            return;
        }

        line(out, "Всего активных уведомлений: " + notifications.length);
        int matched = 0;
        for (StatusBarNotification sbn : notifications) {
            String pkg = sbn.getPackageName();
            if (!SPOTIFY.equals(pkg) && !VIVO_MUSIC_WIDGET.equals(pkg)) {
                continue;
            }
            matched++;
            line(out, "");
            section(out, "NOTIFICATION " + pkg + " #" + matched);
            dumpNotification(out, sbn);
        }

        if (matched == 0) {
            line(out, "Не найдено активных уведомлений Spotify/MusicWidgetMix.");
        }
    }

    private static void dumpNotification(StringBuilder out, StatusBarNotification sbn) {
        line(out, "package=" + sbn.getPackageName());
        line(out, "opPkg=" + sbn.getOpPkg());
        line(out, "id=" + sbn.getId());
        line(out, "tag=" + sbn.getTag());
        line(out, "key=" + sbn.getKey());
        line(out, "groupKey=" + sbn.getGroupKey());
        line(out, "postTime=" + sbn.getPostTime());
        line(out, "user=" + sbn.getUser());

        Notification n = sbn.getNotification();
        if (n == null) {
            line(out, "notification=null");
            return;
        }

        line(out, "channelId=" + n.getChannelId());
        line(out, "category=" + n.category);
        line(out, "flags=0x" + Integer.toHexString(n.flags));
        line(out, "visibility=" + n.visibility);
        line(out, "color=0x" + Integer.toHexString(n.color));
        line(out, "when=" + n.when);
        line(out, "timeoutAfter=" + n.getTimeoutAfter());
        line(out, "group=" + n.getGroup());
        line(out, "sortKey=" + n.getSortKey());
        line(out, "number=" + n.number);
        line(out, "groupAlertBehavior=" + n.getGroupAlertBehavior());
        line(out, "badgeIconType=" + n.getBadgeIconType());
        line(out, "smallIcon=" + describeIcon(n.getSmallIcon()));
        line(out, "largeIcon=" + describeIcon(n.getLargeIcon()));
        line(out, "contentIntent=" + describePendingIntent(n.contentIntent));
        line(out, "deleteIntent=" + describePendingIntent(n.deleteIntent));

        Notification.Action[] actions = n.actions;
        if (actions == null) {
            line(out, "actions=null");
        } else {
            line(out, "actions.size=" + actions.length);
            for (int i = 0; i < actions.length; i++) {
                Notification.Action action = actions[i];
                line(out, "  action[" + i + "].title=" + safe(action.title));
                line(out, "  action[" + i + "].icon=" + describeIcon(action.getIcon()));
                line(out, "  action[" + i + "].intent=" + describePendingIntent(action.actionIntent));
                line(out, "  action[" + i + "].semanticAction=" + action.getSemanticAction());
                line(out, "  action[" + i + "].allowGeneratedReplies=" + action.getAllowGeneratedReplies());
                if (Build.VERSION.SDK_INT >= 29) {
                    line(out, "  action[" + i + "].contextual=" + action.isContextual());
                }
                if (action.getRemoteInputs() != null) {
                    line(out, "  action[" + i + "].remoteInputs=" + action.getRemoteInputs().length);
                    for (int j = 0; j < action.getRemoteInputs().length; j++) {
                        android.app.RemoteInput ri = action.getRemoteInputs()[j];
                        line(out, "    remoteInput[" + j + "].resultKey=" + ri.getResultKey());
                        line(out, "    remoteInput[" + j + "].label=" + safe(ri.getLabel()));
                        line(out, "    remoteInput[" + j + "].choices=" + Arrays.toString(ri.getChoices()));
                    }
                }
                line(out, "  action[" + i + "].extras:");
                dumpBundle(out, "    ", action.getExtras(), 0);
            }
        }

        line(out, "extras:");
        dumpBundle(out, "  ", n.extras, 0);
    }

    private static void dumpBundle(StringBuilder out, String indent, Bundle bundle, int depth) {
        if (bundle == null) {
            line(out, indent + "null");
            return;
        }
        if (depth > 5) {
            line(out, indent + "<максимальная глубина>");
            return;
        }

        List<String> keys = new ArrayList<>(bundle.keySet());
        Collections.sort(keys);
        line(out, indent + "keys=" + keys.size());
        for (String key : keys) {
            Object value;
            try {
                value = bundle.get(key);
            } catch (Throwable t) {
                line(out, indent + key + "=<ошибка чтения " + t.getClass().getSimpleName() + ">");
                continue;
            }

            if (value instanceof Bundle) {
                line(out, indent + key + "=BUNDLE");
                dumpBundle(out, indent + "  ", (Bundle) value, depth + 1);
            } else {
                line(out, indent + key + "=" + describeValue(value));
            }
        }
    }

    private static String describeValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Bitmap) return describeBitmap((Bitmap) value);
        if (value instanceof Icon) return describeIcon((Icon) value);
        if (value instanceof Uri) return "URI(" + value + ")";
        if (value instanceof PendingIntent) return describePendingIntent((PendingIntent) value);
        if (value instanceof MediaSession.Token) return "MEDIA_SESSION_TOKEN(" + value + ")";
        if (value instanceof Rating) return describeRating((Rating) value);
        if (value instanceof MediaDescription) {
            MediaDescription d = (MediaDescription) value;
            return "MEDIA_DESCRIPTION(mediaId=" + d.getMediaId()
                    + ", title=" + safe(d.getTitle())
                    + ", icon=" + describeBitmap(d.getIconBitmap())
                    + ", iconUri=" + d.getIconUri()
                    + ", mediaUri=" + d.getMediaUri() + ")";
        }
        if (value instanceof CharSequence) {
            return value.getClass().getSimpleName() + "(\\\"" + oneLine(value.toString()) + "\\")";
        }
        if (value instanceof int[]) return "int[]" + Arrays.toString((int[]) value);
        if (value instanceof long[]) return "long[]" + Arrays.toString((long[]) value);
        if (value instanceof boolean[]) return "boolean[]" + Arrays.toString((boolean[]) value);
        if (value instanceof String[]) return "String[]" + Arrays.toString((String[]) value);
        if (value instanceof CharSequence[]) return "CharSequence[]" + Arrays.toString((CharSequence[]) value);
        if (value instanceof Parcelable[]) {
            Parcelable[] array = (Parcelable[]) value;
            return value.getClass().getComponentType().getSimpleName() + "[] length=" + array.length;
        }
        return value.getClass().getName() + "(" + oneLine(String.valueOf(value)) + ")";
    }

    private static String describeBitmap(Bitmap bitmap) {
        if (bitmap == null) return "null";
        String config = bitmap.getConfig() == null ? "null" : bitmap.getConfig().name();
        return "BITMAP(" + bitmap.getWidth() + "x" + bitmap.getHeight()
                + ", config=" + config
                + ", bytes=" + bitmap.getAllocationByteCount()
                + ", alpha=" + bitmap.hasAlpha() + ")";
    }

    private static String describeIcon(Icon icon) {
        if (icon == null) return "null";
        StringBuilder s = new StringBuilder();
        s.append("ICON(type=").append(iconTypeName(icon.getType())).append("/").append(icon.getType());
        try {
            if (icon.getType() == Icon.TYPE_BITMAP || icon.getType() == Icon.TYPE_ADAPTIVE_BITMAP) {
                s.append(", ").append(describeBitmap(icon.getBitmap()));
            } else if (icon.getType() == Icon.TYPE_RESOURCE) {
                s.append(", pkg=").append(icon.getResPackage()).append(", resId=0x")
                        .append(Integer.toHexString(icon.getResId()));
            } else if (icon.getType() == Icon.TYPE_URI
                    || (Build.VERSION.SDK_INT >= 30 && icon.getType() == Icon.TYPE_URI_ADAPTIVE_BITMAP)) {
                s.append(", uri=").append(icon.getUri());
            }
        } catch (Throwable t) {
            s.append(", detailError=").append(t.getClass().getSimpleName());
        }
        s.append(")");
        return s.toString();
    }

    private static String iconTypeName(int type) {
        switch (type) {
            case Icon.TYPE_BITMAP: return "BITMAP";
            case Icon.TYPE_RESOURCE: return "RESOURCE";
            case Icon.TYPE_DATA: return "DATA";
            case Icon.TYPE_URI: return "URI";
            case Icon.TYPE_ADAPTIVE_BITMAP: return "ADAPTIVE_BITMAP";
            default:
                if (Build.VERSION.SDK_INT >= 30 && type == Icon.TYPE_URI_ADAPTIVE_BITMAP) {
                    return "URI_ADAPTIVE_BITMAP";
                }
                return "UNKNOWN";
        }
    }

    private static String describeRating(Rating rating) {
        if (rating == null) return "null";
        StringBuilder s = new StringBuilder("RATING(style=").append(rating.getRatingStyle())
                .append(", rated=").append(rating.isRated());
        if (rating.isRated()) {
            switch (rating.getRatingStyle()) {
                case Rating.RATING_HEART:
                    s.append(", heart=").append(rating.hasHeart());
                    break;
                case Rating.RATING_THUMB_UP_DOWN:
                    s.append(", thumbUp=").append(rating.isThumbUp());
                    break;
                case Rating.RATING_3_STARS:
                case Rating.RATING_4_STARS:
                case Rating.RATING_5_STARS:
                    s.append(", stars=").append(rating.getStarRating());
                    break;
                case Rating.RATING_PERCENTAGE:
                    s.append(", percent=").append(rating.getPercentRating());
                    break;
                default:
                    break;
            }
        }
        return s.append(")").toString();
    }

    private static String describePendingIntent(PendingIntent pendingIntent) {
        if (pendingIntent == null) return "null";
        return "PENDING_INTENT(" + pendingIntent + ")";
    }

    private static String decodeActions(long actions) {
        List<String> names = new ArrayList<>();
        addAction(names, actions, PlaybackState.ACTION_STOP, "STOP");
        addAction(names, actions, PlaybackState.ACTION_PAUSE, "PAUSE");
        addAction(names, actions, PlaybackState.ACTION_PLAY, "PLAY");
        addAction(names, actions, PlaybackState.ACTION_REWIND, "REWIND");
        addAction(names, actions, PlaybackState.ACTION_SKIP_TO_PREVIOUS, "PREVIOUS");
        addAction(names, actions, PlaybackState.ACTION_SKIP_TO_NEXT, "NEXT");
        addAction(names, actions, PlaybackState.ACTION_FAST_FORWARD, "FAST_FORWARD");
        addAction(names, actions, PlaybackState.ACTION_SET_RATING, "SET_RATING");
        addAction(names, actions, PlaybackState.ACTION_SEEK_TO, "SEEK_TO");
        addAction(names, actions, PlaybackState.ACTION_PLAY_PAUSE, "PLAY_PAUSE");
        addAction(names, actions, PlaybackState.ACTION_PLAY_FROM_MEDIA_ID, "PLAY_FROM_MEDIA_ID");
        addAction(names, actions, PlaybackState.ACTION_PLAY_FROM_SEARCH, "PLAY_FROM_SEARCH");
        addAction(names, actions, PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM, "SKIP_TO_QUEUE_ITEM");
        addAction(names, actions, PlaybackState.ACTION_PLAY_FROM_URI, "PLAY_FROM_URI");
        addAction(names, actions, PlaybackState.ACTION_PREPARE, "PREPARE");
        addAction(names, actions, PlaybackState.ACTION_PREPARE_FROM_MEDIA_ID, "PREPARE_FROM_MEDIA_ID");
        addAction(names, actions, PlaybackState.ACTION_PREPARE_FROM_SEARCH, "PREPARE_FROM_SEARCH");
        addAction(names, actions, PlaybackState.ACTION_PREPARE_FROM_URI, "PREPARE_FROM_URI");
        addAction(names, actions, PlaybackState.ACTION_SET_REPEAT_MODE, "SET_REPEAT_MODE");
        addAction(names, actions, PlaybackState.ACTION_SET_SHUFFLE_MODE, "SET_SHUFFLE_MODE");
        return names.toString();
    }

    private static void addAction(List<String> out, long actions, long flag, String name) {
        if ((actions & flag) != 0) out.add(name);
    }

    private static String stateName(int state) {
        switch (state) {
            case PlaybackState.STATE_NONE: return "NONE";
            case PlaybackState.STATE_STOPPED: return "STOPPED";
            case PlaybackState.STATE_PAUSED: return "PAUSED";
            case PlaybackState.STATE_PLAYING: return "PLAYING";
            case PlaybackState.STATE_FAST_FORWARDING: return "FAST_FORWARDING";
            case PlaybackState.STATE_REWINDING: return "REWINDING";
            case PlaybackState.STATE_BUFFERING: return "BUFFERING";
            case PlaybackState.STATE_ERROR: return "ERROR";
            case PlaybackState.STATE_CONNECTING: return "CONNECTING";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS: return "SKIPPING_TO_PREVIOUS";
            case PlaybackState.STATE_SKIPPING_TO_NEXT: return "SKIPPING_TO_NEXT";
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM: return "SKIPPING_TO_QUEUE_ITEM";
            default: return "UNKNOWN";
        }
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder s = new StringBuilder(digest.length * 3);
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) s.append(':');
                s.append(String.format(Locale.US, "%02X", digest[i]));
            }
            return s.toString();
        } catch (Throwable t) {
            return "<ошибка SHA-256: " + t.getClass().getSimpleName() + ">";
        }
    }

    private static String safe(CharSequence value) {
        return value == null ? "null" : oneLine(value.toString());
    }

    private static String oneLine(String value) {
        if (value == null) return "null";
        String clean = value.replace("\r", "\\r").replace("\n", "\\n");
        if (clean.length() > 2000) {
            return clean.substring(0, 2000) + "...<обрезано>";
        }
        return clean;
    }

    private static void section(StringBuilder out, String title) {
        line(out, "---------------- " + title + " ----------------");
    }

    private static void line(StringBuilder out, String value) {
        out.append(value == null ? "null" : value).append('\n');
    }
}
