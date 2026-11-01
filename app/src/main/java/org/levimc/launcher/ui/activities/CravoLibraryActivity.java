package org.levimc.launcher.ui.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.levimc.launcher.core.content.ContentItem;
import org.levimc.launcher.core.content.ContentManager;
import org.levimc.launcher.core.content.ResourcePackItem;
import org.levimc.launcher.core.content.ResourcePackManager;
import org.levimc.launcher.core.content.WorldItem;
import org.levimc.launcher.core.content.WorldManager;
import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.core.versions.VersionManager;
import org.levimc.launcher.settings.FeatureSettings;
import org.levimc.launcher.ui.theme.CravoTheme;
import org.levimc.launcher.util.LauncherStorage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Native CRAVO world/resource/behavior-pack library, backed by ContentManager. */
public final class CravoLibraryActivity extends BaseActivity {
    private CravoTheme theme;
    private ContentManager manager;
    private LinearLayout body, tabs;
    private TextView heading, count;
    private int kind = ContentListActivity.TYPE_WORLDS;
    private final List<ContentItem> items = new ArrayList<>();
    private ActivityResultLauncher<Intent> importFile;
    private ActivityResultLauncher<Intent> exportFile;
    private ContentItem pendingExport;

    @Override protected boolean shouldSkipNavBar() { return true; }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        theme = new CravoTheme(this);
        manager = ContentManager.getInstance(this);
        kind = getIntent().getIntExtra(ContentListActivity.EXTRA_CONTENT_TYPE, ContentListActivity.TYPE_WORLDS);
        importFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            Uri uri = result.getData().getData();
            if (uri == null) return;
            if (kind == ContentListActivity.TYPE_WORLDS) manager.importWorld(uri, worldCallback("Mundo importado"));
            else manager.importResourcePack(uri, packCallback("Pacote importado"));
        });
        exportFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null || pendingExport == null) return;
            Uri uri = result.getData().getData();
            if (uri == null) return;
            if (pendingExport instanceof WorldItem)
                manager.exportWorld((WorldItem) pendingExport, uri, worldCallback("Mundo exportado"));
            else if (pendingExport instanceof ResourcePackItem)
                manager.exportResourcePack((ResourcePackItem) pendingExport, uri, packCallback("Pacote exportado"));
            pendingExport = null;
        });
        buildScreen();
        manager.getWorldsLiveData().observe(this, worlds -> {
            if (kind == ContentListActivity.TYPE_WORLDS) showItems(worlds);
        });
        manager.getResourcePacksLiveData().observe(this, packs -> {
            if (kind == ContentListActivity.TYPE_RESOURCE_PACKS) showItems(packs);
        });
        manager.getBehaviorPacksLiveData().observe(this, packs -> {
            if (kind == ContentListActivity.TYPE_BEHAVIOR_PACKS) showItems(packs);
        });
        manager.getSkinPacksLiveData().observe(this, packs -> {
            if (kind == ContentListActivity.TYPE_SKIN_PACKS) showItems(packs);
        });
        prepareDirectories();
    }

    @Override protected void onResume() {
        super.onResume();
        if (manager != null && body != null) prepareDirectories();
    }

    private void prepareDirectories() {
        GameVersion selected = VersionManager.get(this).getSelectedVersion();
        if (selected == null) { if (body != null) empty("Selecione uma versão do Minecraft para ver conteúdos."); return; }
        FeatureSettings.StorageType storage;
        try {
            String pref = getSharedPreferences("content_management", MODE_PRIVATE)
                    .getString("storage_type", "INTERNAL");
            storage = FeatureSettings.StorageType.valueOf(pref);
        } catch (Exception e) { storage = FeatureSettings.StorageType.INTERNAL; }
        storage = LauncherStorage.normalizeContentStorageType(storage, selected.versionIsolation);
        File root = LauncherStorage.getContentGameDataDir(this, selected.getStorageProfileId(), storage);
        manager.setCurrentVersion(selected);
        manager.setStorageDirectories(
                new File(root, "minecraftWorlds"), new File(root, "resource_packs"),
                new File(root, "behavior_packs"), new File(root, "skin_packs"),
                new File(root, "Screenshots"), new File(root, "minecraftpe"));
        load();
    }

    private void load() {
        switch (kind) {
            case ContentListActivity.TYPE_WORLDS: manager.refreshWorlds(); break;
            case ContentListActivity.TYPE_RESOURCE_PACKS: manager.refreshResourcePacks(); break;
            case ContentListActivity.TYPE_BEHAVIOR_PACKS: manager.refreshBehaviorPacks(); break;
            case ContentListActivity.TYPE_SKIN_PACKS: manager.refreshSkinPacks(); break;
            default: break;
        }
    }

    private int dp(int v) { return theme.dp(v); }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private void gap(int v) { body.addView(new View(this), new LinearLayout.LayoutParams(1, dp(v))); }

    private void buildScreen() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(CravoTheme.BACKGROUND);
        setContentView(screen);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(12), dp(18), dp(10));
        screen.addView(bar);
        TextView back = text("‹", 32, CravoTheme.TEXT, false);
        back.setGravity(Gravity.CENTER);
        bar.addView(back, new LinearLayout.LayoutParams(dp(47), dp(45)));
        back.setOnClickListener(v -> finish());
        TextView title = text("C R A V O  /  BIBLIOTECA", 16, CravoTheme.TEXT, true);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView plus = text("+", 26, theme.accent(), true);
        bar.addView(plus, new LinearLayout.LayoutParams(dp(38), -2));
        plus.setGravity(Gravity.CENTER);
        plus.setOnClickListener(v -> importContent());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(20), dp(22), dp(22));
        scroll.addView(body);
        heading = text("Biblioteca", 28, CravoTheme.TEXT, true);
        body.addView(heading);
        gap(14);
        tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        body.addView(tabs);
        gap(18);
        count = text("Carregando…", 12, CravoTheme.MUTED, false);
        body.addView(count);
        gap(10);
        renderTabs();
    }

    private void renderTabs() {
        tabs.removeAllViews();
        for (int index = 0; index < 4; index++) {
            int[] kinds = {ContentListActivity.TYPE_WORLDS, ContentListActivity.TYPE_RESOURCE_PACKS,
                    ContentListActivity.TYPE_BEHAVIOR_PACKS, ContentListActivity.TYPE_SKIN_PACKS};
            String[] labels = {"MUNDOS", "TEXTURAS", "ADDONS", "SKINS"};
            int type = kinds[index];
            boolean active = kind == type;
            TextView chip = text(labels[index], 10,
                    active ? theme.onAccent() : CravoTheme.MUTED, active);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(theme.solid(active ? theme.accent() : 0xFF242128, 12,
                    active ? 0 : 0xFF413541));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(39), 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            tabs.addView(chip, lp);
            chip.setOnClickListener(v -> {
                kind = type;
                renderTabs();
                empty("Carregando conteúdo…");
                load();
            });
        }
    }

    private void empty(String message) {
        if (body == null) return;
        while (body.getChildCount() > 5) body.removeViewAt(5);
        count.setText(message);
    }

    private void showItems(List<? extends ContentItem> incoming) {
        if (body == null) return;
        while (body.getChildCount() > 5) body.removeViewAt(5);
        items.clear();
        if (incoming != null) items.addAll(incoming);
        count.setText(items.size() + " itens encontrados");
        if (items.isEmpty()) {
            TextView empty = text("Nenhum conteúdo aqui.\nUse + para importar um arquivo compatível.",
                    13, CravoTheme.MUTED, false);
            empty.setPadding(dp(10), dp(36), dp(10), dp(30));
            empty.setGravity(Gravity.CENTER);
            body.addView(empty);
            return;
        }
        for (ContentItem item : items) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(17), dp(16), dp(16), dp(14));
            tile.setBackground(theme.glass(16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(11);
            body.addView(tile, lp);
            tile.addView(text(item.getName(), 16, CravoTheme.TEXT, true));
            TextView detail = text(item.getFormattedSize() + " · " + item.getFormattedLastModified(),
                    11, CravoTheme.MUTED, false);
            detail.setPadding(0, dp(6), 0, 0);
            tile.addView(detail);
            tile.setOnClickListener(v -> details(item));
        }
    }

    private void details(ContentItem item) {
        new AlertDialog.Builder(this)
                .setTitle(item.getName())
                .setMessage("Tipo: " + item.getType() +
                        "\nTamanho: " + item.getFormattedSize() +
                        "\nModificado: " + item.getFormattedLastModified() +
                        "\n\n" + item.getDescription())
                .setNeutralButton("EXPORTAR", (d, w) -> export(item))
                .setNegativeButton("FECHAR", null)
                .setPositiveButton("EXCLUIR", (d, w) -> confirmDelete(item))
                .show();
    }

    private void importContent() {
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        open.addCategory(Intent.CATEGORY_OPENABLE);
        open.setType("*/*");
        importFile.launch(open);
    }

    private void export(ContentItem item) {
        pendingExport = item;
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType("application/zip");
        create.putExtra(Intent.EXTRA_TITLE, item.getName() +
                (item instanceof WorldItem ? ".mcworld" : ".mcpack"));
        exportFile.launch(create);
    }

    private void confirmDelete(ContentItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir " + item.getName() + "?")
                .setMessage("Esta ação exclui o conteúdo selecionado. Faça um backup antes se precisar dele.")
                .setPositiveButton("EXCLUIR", (d, w) -> {
                    if (item instanceof WorldItem) manager.deleteWorld((WorldItem)item, worldCallback("Mundo excluído"));
                    else if (item instanceof ResourcePackItem)
                        manager.deleteResourcePack((ResourcePackItem)item, packCallback("Pacote excluído"));
                })
                .setNegativeButton("CANCELAR", null).show();
    }

    private void notifyResult(String message, boolean success) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        if (success) runOnUiThread(this::load);
    }

    private WorldManager.WorldOperationCallback worldCallback(String successMessage) {
        return new WorldManager.WorldOperationCallback() {
            @Override public void onSuccess(String message) { notifyResult(successMessage, true); }
            @Override public void onError(String message) { notifyResult(message, false); }
            @Override public void onProgress(int progress) {}
        };
    }

    private ResourcePackManager.PackOperationCallback packCallback(String successMessage) {
        return new ResourcePackManager.PackOperationCallback() {
            @Override public void onSuccess(String message) { notifyResult(successMessage, true); }
            @Override public void onError(String message) { notifyResult(message, false); }
            @Override public void onProgress(int progress) {}
        };
    }
}
