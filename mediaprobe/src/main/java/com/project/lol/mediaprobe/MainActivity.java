package com.project.lol.mediaprobe;

import android.service.notification.NotificationListenerService;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ComponentName listenerComponent;
    private TextView statusView;
    private TextView reportView;
    private Button snapshotButton;
    private Button saveButton;
    private Button copyButton;
    private String lastReport = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listenerComponent = new ComponentName(this, ProbeNotificationListener.class);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (hasNotificationAccess()) {
            try {
                NotificationListenerService.requestRebind(listenerComponent);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        int pad = dp(18);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(16, 16, 16));

        TextView title = new TextView(this);
        title.setText("MediaProbe - официальный Spotify");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title, matchWrap());

        TextView help = new TextView(this);
        help.setText(
                "1. Оставь официальный Spotify установленным.\n" +
                "2. Включи любой трек и оставь его играть.\n" +
                "3. Выдай MediaProbe «Доступ к уведомлениям».\n" +
                "4. Открой Origin Island, чтобы обложка точно появилась.\n" +
                "5. Вернись сюда и нажми «Снять снимок Spotify».\n" +
                "6. Сохрани отчёт и пришли TXT в ChatGPT."
        );
        help.setTextSize(15);
        help.setTextColor(Color.LTGRAY);
        help.setPadding(0, 0, 0, dp(12));
        root.addView(help, matchWrap());

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setTextColor(Color.WHITE);
        statusView.setPadding(0, dp(4), 0, dp(12));
        root.addView(statusView, matchWrap());

        Button accessButton = new Button(this);
        accessButton.setText("Открыть «Доступ к уведомлениям»");
        accessButton.setAllCaps(false);
        accessButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Throwable t) {
                Toast.makeText(this, "Не удалось открыть настройки: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        root.addView(accessButton, matchWrap());

        snapshotButton = new Button(this);
        snapshotButton.setText("Снять снимок Spotify");
        snapshotButton.setAllCaps(false);
        snapshotButton.setOnClickListener(v -> capture());
        root.addView(snapshotButton, matchWrap());

        saveButton = new Button(this);
        saveButton.setText("Сохранить отчёт в Загрузки");
        saveButton.setAllCaps(false);
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> saveReport());
        root.addView(saveButton, matchWrap());

        copyButton = new Button(this);
        copyButton.setText("Скопировать отчёт");
        copyButton.setAllCaps(false);
        copyButton.setEnabled(false);
        copyButton.setOnClickListener(v -> copyReport());
        root.addView(copyButton, matchWrap());

        TextView reportTitle = new TextView(this);
        reportTitle.setText("Отчёт:");
        reportTitle.setTextSize(17);
        reportTitle.setTextColor(Color.WHITE);
        reportTitle.setPadding(0, dp(14), 0, dp(6));
        root.addView(reportTitle, matchWrap());

        reportView = new TextView(this);
        reportView.setText("Снимок ещё не сделан.");
        reportView.setTextSize(12);
        reportView.setTextColor(Color.rgb(220, 220, 220));
        reportView.setTextIsSelectable(true);
        reportView.setTypeface(android.graphics.Typeface.MONOSPACE);
        reportView.setPadding(dp(10), dp(10), dp(10), dp(10));
        reportView.setBackgroundColor(Color.rgb(28, 28, 28));

        ScrollView reportScroll = new ScrollView(this);
        reportScroll.addView(reportView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        scrollParams.topMargin = dp(4);
        root.addView(reportScroll, scrollParams);

        return root;
    }

    private void refreshStatus() {
        boolean granted = hasNotificationAccess();
        boolean spotifyInstalled;
        String spotifyVersion = "";
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                    .getPackageInfo("com.spotify.music", 0);
            spotifyInstalled = true;
            spotifyVersion = info.versionName == null ? "" : info.versionName;
        } catch (Throwable t) {
            spotifyInstalled = false;
        }

        StringBuilder s = new StringBuilder();
        s.append("Доступ к уведомлениям: ")
                .append(granted ? "РАЗРЕШЁН ✓" : "НЕ РАЗРЕШЁН ✗")
                .append("\n");
        s.append("Официальный Spotify: ")
                .append(spotifyInstalled ? "НАЙДЕН ✓" : "НЕ НАЙДЕН ✗");
        if (!spotifyVersion.isEmpty()) {
            s.append(" (").append(spotifyVersion).append(")");
        }
        statusView.setText(s.toString());
        statusView.setTextColor(granted && spotifyInstalled
                ? Color.rgb(120, 220, 140)
                : Color.rgb(255, 180, 90));
    }

    private boolean hasNotificationAccess() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        return manager != null && manager.isNotificationListenerAccessGranted(listenerComponent);
    }

    private void capture() {
        if (!hasNotificationAccess()) {
            Toast.makeText(
                    this,
                    "Сначала выдай MediaProbe «Доступ к уведомлениям».",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        snapshotButton.setEnabled(false);
        saveButton.setEnabled(false);
        copyButton.setEnabled(false);
        reportView.setText("Снимаю MediaSession и уведомления официального Spotify...");

        executor.execute(() -> {
            String report;
            try {
                report = ProbeReport.capture(this, listenerComponent);
            } catch (Throwable t) {
                report = "КРИТИЧЕСКАЯ ОШИБКА MediaProbe\n"
                        + t.getClass().getName() + ": " + t.getMessage() + "\n";
                for (StackTraceElement e : t.getStackTrace()) {
                    report += "  at " + e + "\n";
                }
            }

            String finalReport = report;
            runOnUiThread(() -> {
                lastReport = finalReport;
                reportView.setText(finalReport);
                snapshotButton.setEnabled(true);
                saveButton.setEnabled(true);
                copyButton.setEnabled(true);
                Toast.makeText(this, "Снимок готов.", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void copyReport() {
        if (lastReport.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("MediaProbe Spotify", lastReport));
            Toast.makeText(this, "Отчёт скопирован.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveReport() {
        if (lastReport.isEmpty()) return;

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String filename = "mediaprobe-official-spotify-" + stamp + ".txt";

        executor.execute(() -> {
            String result;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                    values.put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/MediaProbe"
                    );
                    values.put(MediaStore.Downloads.IS_PENDING, 1);

                    Uri uri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            values
                    );
                    if (uri == null) {
                        throw new IllegalStateException("MediaStore вернул null");
                    }

                    try (OutputStream os = getContentResolver().openOutputStream(uri, "w")) {
                        if (os == null) throw new IllegalStateException("openOutputStream вернул null");
                        os.write(lastReport.getBytes(StandardCharsets.UTF_8));
                    }

                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                    result = "Сохранено: Загрузки/MediaProbe/" + filename;
                } else {
                    File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MediaProbe");
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new IllegalStateException("Не удалось создать папку");
                    }
                    File out = new File(dir, filename);
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        fos.write(lastReport.getBytes(StandardCharsets.UTF_8));
                    }
                    result = "Сохранено: " + out.getAbsolutePath();
                }
            } catch (Throwable t) {
                result = "Ошибка сохранения: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            }

            String finalResult = result;
            runOnUiThread(() -> Toast.makeText(this, finalResult, Toast.LENGTH_LONG).show());
        });
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
