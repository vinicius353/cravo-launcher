package org.levimc.launcher.ui.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.levimc.launcher.BuildConfig;
import org.levimc.launcher.util.LauncherStorage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * On-demand local diagnostic report. No background uploading and no READ_LOGS permission.
 * Only the current app's accessible logcat entries, saved overlay log history and xCrash logs.
 * The user chooses whether to share or copy the resulting text.
 */
public final class CravoDiagnostics {
    private static final int MAX_LOGCAT_LINES = 1400;
    private static final int MAX_REPORT_CHARS = 180000;
    private static final int MAX_SECTION_CHARS = 85000;
    private CravoDiagnostics() {}

    public static void show(Activity activity) {
        Log.i("CRAVO", "Diagnostic report requested by user");
        int padding = Math.round(18 * activity.getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, padding);
        layout.setBackgroundColor(Color.rgb(17, 16, 20));

        TextView note = new TextView(activity);
        note.setText("Relatório local do CRAVO.\nApenas registros acessíveis ao app são coletados. Revise o conteúdo antes de compartilhar: logs podem conter nomes de arquivos e informações privadas.");
        note.setTextColor(Color.rgb(225, 215, 223));
        note.setTextSize(13);
        layout.addView(note);

        TextView progress = new TextView(activity);
        progress.setText("\nColetando logs...");
        progress.setTextColor(Color.rgb(234, 114, 138));
        progress.setTextSize(13);
        layout.addView(progress);

        ScrollView scroll = new ScrollView(activity);
        TextView preview = new TextView(activity);
        preview.setTextColor(Color.rgb(177, 166, 176));
        preview.setTextSize(11);
        preview.setTextIsSelectable(true);
        preview.setPadding(0, padding, 0, padding);
        scroll.addView(preview);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.round(280 * activity.getResources().getDisplayMetrics().density)));

        final String[] reportHolder = new String[1];
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("CRAVO · Diagnóstico")
                .setView(layout)
                .setPositiveButton("Compartilhar .txt", null)
                .setNeutralButton("Copiar", null)
                .setNegativeButton("Fechar", (d, w) -> {})
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (reportHolder[0] != null) shareReport(activity, reportHolder[0]);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (reportHolder[0] == null) return;
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("CRAVO diagnostic report", reportHolder[0]));
                    Toast.makeText(activity, "Relatório copiado", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();

        new Thread(() -> {
            String report;
            try {
                report = gather(activity.getApplicationContext());
            } catch (Exception e) {
                report = "CRAVO diagnostics unavailable: " + e.getClass().getSimpleName() + "\n" + safe(e.getMessage());
            }
            final String finalReport = report;
            activity.runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                reportHolder[0] = finalReport;
                progress.setText("Relatório pronto · " + finalReport.length() + " caracteres");
                preview.setText(finalReport.substring(0, Math.min(14000, finalReport.length()))
                        + (finalReport.length() > 14000 ? "\n\n[Prévia limitada; compartilhe o .txt para o relatório completo]" : ""));
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
            });
        }, "cravo-diagnostics").start();
    }

    private static String gather(Context context) {
        StringBuilder result = new StringBuilder();
        result.append("CRAVO Launcher · Diagnostic Report\n");
        result.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append('\n');
        result.append("App: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        result.append("Android: ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append('\n');
        result.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        result.append("Process PID: ").append(android.os.Process.myPid()).append('\n');
        result.append("Note: user-initiated export, not a full system logcat.\n");
        appendSection(result, "CURRENT PROCESS LOGCAT", collectLogcat());
        File history = new File(context.getNoBackupFilesDir(), "logcat_overlay_history.txt");
        appendSection(result, "PERSISTED LAUNCHER LOG HISTORY", tailFile(history, MAX_SECTION_CHARS));
        File crashDir = LauncherStorage.getCrashLogsDir(context);
        File latestCrash = latestCrash(crashDir);
        if (latestCrash != null) {
            appendSection(result, "LAST CRASH FILE: " + latestCrash.getName(), tailFile(latestCrash, MAX_SECTION_CHARS));
        } else {
            appendSection(result, "LAST CRASH", "No saved crash file found.");
        }
        String output = result.toString();
        return output.length() > MAX_REPORT_CHARS
                ? output.substring(0, MAX_REPORT_CHARS) + "\n[Report truncated]\n" : output;
    }

    private static void appendSection(StringBuilder output, String title, String body) {
        output.append("\n\n=== ").append(title).append(" ===\n").append(safe(body));
    }

    private static File latestCrash(File folder) {
        if (folder == null || !folder.isDirectory()) return null;
        File[] files = folder.listFiles(file -> file.isFile() && !file.getName().startsWith("."));
        if (files == null || files.length == 0) return null;
        File latest = null;
        for (File f : files) {
            if (latest == null || f.lastModified() > latest.lastModified()) latest = f;
        }
        return latest;
    }

    private static String tailFile(File file, int limit) {
        if (file == null || !file.isFile()) return "No saved entries available.";
        try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, reader.length() - limit);
            reader.seek(start);
            byte[] bytes = new byte[(int) (reader.length() - start)];
            reader.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Unable to read logs: " + e.getClass().getSimpleName();
        }
    }

    private static String collectLogcat() {
        FutureTask<String> work = new FutureTask<>(() -> {
            Process process = null;
            try {
                process = new ProcessBuilder("logcat", "-d", "-v", "threadtime",
                        "--pid=" + android.os.Process.myPid()).redirectErrorStream(true).start();
                StringBuilder lines = new StringBuilder();
                int count = 0;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && count < MAX_LOGCAT_LINES
                            && lines.length() < MAX_SECTION_CHARS) {
                        lines.append(line).append('\n');
                        count++;
                    }
                }
                return lines.length() > 0 ? lines.toString() : "No accessible logcat entries.";
            } catch (Exception e) {
                return "Logcat unavailable: " + e.getClass().getSimpleName();
            } finally {
                if (process != null) process.destroy();
            }
        });
        Thread worker = new Thread(work, "cravo-logcat-read");
        worker.setDaemon(true);
        worker.start();
        try {
            return work.get(4, TimeUnit.SECONDS);
        } catch (Exception e) {
            work.cancel(true);
            return "Logcat timed out or is unavailable (Android access restrictions).";
        }
    }

    private static String safe(String text) {
        if (text == null) return "";
        // Best-effort redaction; users must still review reports before sharing.
        return text.replaceAll("(?i)(bearer\\s+)\\S+", "$1[REDACTED]")
                .replaceAll("(?i)((?:access_token|refresh_token|id_token|authorization|password|api_key|cookie|secret|x-api-key)\\s*[=:]\\s*)[^\\s,;]+", "$1[REDACTED]")
                .replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[EMAIL REDACTED]");
    }

    private static void shareReport(Activity activity, String report) {
        try {
            File folder = new File(activity.getCacheDir(), "cravo-reports");
            if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create report folder");
            File file = new File(folder, "cravo-diagnostics.txt");
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(report.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newUri(activity.getContentResolver(), "CRAVO diagnostics", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(send, "Compartilhar relatório CRAVO"));
        } catch (Exception e) {
            Toast.makeText(activity, "Não foi possível compartilhar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
