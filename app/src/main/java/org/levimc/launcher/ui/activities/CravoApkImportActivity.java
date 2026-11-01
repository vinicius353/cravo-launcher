package org.levimc.launcher.ui.activities;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.core.versions.VersionManager;
import org.levimc.launcher.ui.theme.CravoTheme;
import org.levimc.launcher.util.ApkInstaller;
import org.levimc.launcher.util.ApkUtils;
import org.levimc.launcher.util.LauncherStorage;
import org.levimc.launcher.util.StorageAccess;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CRAVO-native, self-contained local APK/APKS import flow.
 * Uses an Android document picker, read-only analysis, and copies into an isolated instance;
 * never rewrites the selected original.
 */
public final class CravoApkImportActivity extends BaseActivity {
    private CravoTheme theme;
    private EditText instanceName;
    private TextView status, fileLabel, importButton;
    private ProgressBar progress;
    private Uri source;
    private boolean recognized;
    private boolean busy;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<Intent> picker;

    @Override protected boolean shouldSkipNavBar() { return true; }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        theme = new CravoTheme(this);
        picker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        Uri uri = data.getData();
                        if (uri != null) {
                            StorageAccess.retainReadPermission(this, data, uri);
                            inspect(uri);
                        }
                    }
                });

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(23), dp(18), dp(23), dp(20));
        outer.setBackgroundColor(CravoTheme.BACKGROUND);
        setContentView(outer);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        outer.addView(header);
        TextView back = text("‹", 32, CravoTheme.TEXT, false);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(46), dp(45)));
        back.setOnClickListener(v -> finish());
        header.addView(text("C R A V O   /   IMPORTAR", 16, CravoTheme.TEXT, true));
        gap(outer, 37);
        outer.addView(text("Seu APK.\nSua instância.", 34, CravoTheme.TEXT, true));
        gap(outer, 13);
        outer.addView(text("Selecione um arquivo .apk ou .apks que você já possui. O CRAVO mantém o original intacto e cria uma cópia para a instância.",
                13, CravoTheme.MUTED, false));
        gap(outer, 30);
        fileLabel = text("NENHUM ARQUIVO SELECIONADO", 12, theme.accent(), true);
        outer.addView(fileLabel);
        gap(outer, 12);
        TextView selectFile = action("◫  ESCOLHER APK / APKS", false);
        outer.addView(selectFile, new LinearLayout.LayoutParams(-1, dp(58)));
        selectFile.setOnClickListener(v -> {
            if (busy) return;
            Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            open.addCategory(Intent.CATEGORY_OPENABLE);
            open.setType("*/*");
            picker.launch(open);
        });
        gap(outer, 25);
        outer.addView(text("NOME DA INSTÂNCIA", 11, CravoTheme.MUTED, true));
        gap(outer, 9);
        instanceName = new EditText(this);
        instanceName.setTextColor(CravoTheme.TEXT);
        instanceName.setTextSize(15);
        instanceName.setSingleLine(true);
        instanceName.setHintTextColor(CravoTheme.MUTED);
        instanceName.setHint("Minecraft_1.21.XXX");
        instanceName.setPadding(dp(17), dp(10), dp(17), dp(10));
        instanceName.setBackground(theme.glass(14));
        outer.addView(instanceName, new LinearLayout.LayoutParams(-1, dp(53)));
        gap(outer, 18);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(theme.accent()));
        outer.addView(progress, new LinearLayout.LayoutParams(-1, dp(7)));
        gap(outer, 17);
        status = text("Selecione um arquivo para começar.", 12, CravoTheme.MUTED, false);
        outer.addView(status);
        gap(outer, 17);
        importButton = action("IMPORTAR INSTÂNCIA", true);
        outer.addView(importButton, new LinearLayout.LayoutParams(-1, dp(59)));
        importButton.setOnClickListener(v -> performImport());
        refreshButton();
    }

    private int dp(int n) { return theme.dp(n); }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView action(String title, boolean primary) {
        TextView v = text(title, 15, primary ? theme.onAccent() : theme.accent(), true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(theme.solid(primary ? theme.accent() : theme.accentSoft(), 14,
                primary ? 0 : theme.accentOutline()));
        return v;
    }

    private void gap(LinearLayout root, int height) {
        root.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return cursor.getString(i);
            }
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }

    private void inspect(Uri uri) {
        String fileName = displayName(uri);
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".apk") && !lower.endsWith(".apks")) {
            status.setText("Formato inválido. Escolha um arquivo APK ou APKS.");
            recognized = false;
            source = null;
            refreshButton();
            return;
        }
        busy = true;
        recognized = false;
        source = null;
        fileLabel.setText(fileName);
        status.setText("Analisando os metadados locais do pacote…");
        refreshButton();
        boolean apks = lower.endsWith(".apks");
        executor.execute(() -> {
            String result = apks
                    ? ApkUtils.extractMinecraftVersionNameFromApksUri(this, uri)
                    : ApkUtils.extractMinecraftVersionNameFromUri(this, uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                recognized = result != null && !"Error Apk".equals(result);
                if (recognized) {
                    source = uri;
                    instanceName.setText(result);
                    status.setText("Pacote Minecraft reconhecido. Confira o nome antes de importar.");
                } else {
                    status.setText("Não consegui identificar este arquivo como um pacote Minecraft. Confira o arquivo, a estrutura e a versão.");
                }
                refreshButton();
            });
        });
    }

    private void refreshButton() {
        if (importButton == null) return;
        boolean ready = recognized && !busy;
        importButton.setEnabled(ready);
        importButton.setAlpha(ready ? 1f : .36f);
    }

    private void performImport() {
        if (!recognized || busy || source == null) return;
        String name = instanceName.getText().toString().trim();
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{1,87}")) {
            status.setText("Nome inválido. Use letras, números, ponto, hífen ou underline (2–88 caracteres).");
            return;
        }
        VersionManager vm = VersionManager.get(this);
        for (GameVersion v : vm.getCustomVersions()) {
            if (name.equals(v.directoryName)) {
                status.setText("Já existe uma instância com esse nome. Escolha outro para não sobrescrever arquivos.");
                return;
            }
        }
        // Existing instance directories may not yet be loaded by VersionManager.
        if (new java.io.File(LauncherStorage.getMinecraftRoot(this), LauncherStorage.sanitizeProfileId(name)).exists()) {
            status.setText("Esta pasta já existe. Escolha outro nome para preservar seus dados.");
            return;
        }

        busy = true;
        refreshButton();
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        status.setText("Copiando arquivo para a instância CRAVO…");
        ApkInstaller installer = new ApkInstaller(this, executor, new ApkInstaller.InstallCallback() {
            @Override public void onProgress(int value) {
                if (isFinishing() || isDestroyed()) return;
                progress.setProgress(value);
                if (value >= 90) status.setText("Finalizando bibliotecas e metadados…");
            }
            @Override public void onSuccess(String version) {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                VersionManager manager = VersionManager.get(CravoApkImportActivity.this);
                manager.loadAllVersions();
                for (GameVersion v : manager.getCustomVersions()) {
                    if (name.equals(v.directoryName)) {
                        manager.selectVersion(v);
                        break;
                    }
                }
                progress.setProgress(100);
                status.setText("Instância importada: " + name +
                        ". A execução depende da compatibilidade do arquivo e do runtime.");
                importButton.setText("CONCLUÍDO · VOLTAR");
                recognized = false;
                importButton.setEnabled(true);
                importButton.setAlpha(1f);
                importButton.setOnClickListener(v -> finish());
                Toast.makeText(CravoApkImportActivity.this, "Instância importada", Toast.LENGTH_LONG).show();
            }
            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                progress.setVisibility(View.GONE);
                status.setText("Falha ao importar: " + message);
                refreshButton();
            }
        });
        installer.install(source, name);
    }

    @Override protected void onDestroy() {
        // The file belongs to the app; the chosen original URI is never modified.
        if (!busy) executor.shutdown();
        super.onDestroy();
    }
}
