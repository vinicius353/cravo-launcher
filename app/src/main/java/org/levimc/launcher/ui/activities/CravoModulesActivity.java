package org.levimc.launcher.ui.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import org.levimc.launcher.core.mods.FileHandler;
import org.levimc.launcher.core.mods.Mod;
import org.levimc.launcher.core.mods.inbuilt.InbuiltModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.versions.VersionManager;
import org.levimc.launcher.ui.theme.CravoTheme;
import org.levimc.launcher.ui.views.MainViewModel;
import org.levimc.launcher.ui.views.MainViewModelFactory;
import org.levimc.launcher.util.StorageAccess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * First-party CRAVO modules screen. The complete view tree is owned by CRAVO;
 * only module state and import services are reused from the launcher runtime.
 */
@SuppressWarnings("deprecation")
public final class CravoModulesActivity extends BaseActivity {
    private CravoTheme palette;
    private LinearLayout results, filters, root;
    private TextView countLabel;
    private EditText search;
    private String category = "TODOS";
    private final List<UnifiedMod> builtins = new ArrayList<>();
    private final List<Mod> nativePackages = new ArrayList<>();
    private MainViewModel viewModel;
    private FileHandler handler;
    private ActivityResultLauncher<Intent> picker;
    private boolean reducedMotion;

    @Override protected boolean shouldSkipNavBar() { return true; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = new CravoTheme(this);
        reducedMotion = getSharedPreferences("cravo_ui", MODE_PRIVATE).getBoolean("reduce_motion", false);
        viewModel = new ViewModelProvider(this, new MainViewModelFactory(getApplication())).get(MainViewModel.class);
        if (VersionManager.get(this).getSelectedVersion() != null)
            viewModel.setCurrentVersion(VersionManager.get(this).getSelectedVersion());
        handler = new FileHandler(this, viewModel, VersionManager.get(this));
        picker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            Intent data = result.getData();
            for (Uri uri : StorageAccess.selectedUris(data)) StorageAccess.retainReadPermission(this, data, uri);
            handler.processIncomingFilesWithConfirmation(data, new FileHandler.FileOperationCallback() {
                @Override public void onSuccess(int count) {
                    Toast.makeText(CravoModulesActivity.this, count + " arquivo(s) importado(s)", Toast.LENGTH_SHORT).show();
                    viewModel.reloadMods();
                }
                @Override public void onError(String message) {
                    Toast.makeText(CravoModulesActivity.this, message, Toast.LENGTH_LONG).show();
                }
                @Override public void onProgressUpdate(int progress) {}
            }, true);
        });
        buildScreen();
        viewModel.getModsLiveData().observe(this, mods -> {
            nativePackages.clear();
            if (mods != null) nativePackages.addAll(mods);
            render();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (palette != null) {
            builtins.clear();
            try {
                builtins.addAll(InbuiltModuleProvider.load(this));
            } catch (Exception e) {
                android.util.Log.w("CRAVO_MODULES", "Unable to enumerate native modules", e);
            }
            render();
        }
    }

    private int dp(int n) { return palette.dp(n); }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void space(LinearLayout column, int height) {
        column.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void pad(LinearLayout view, int h, int v) {
        view.setPadding(dp(h), dp(v), dp(h), dp(v));
    }

    private void buildScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CravoTheme.BACKGROUND);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        pad(header, 20, 12);
        root.addView(header);

        TextView back = text("‹", 33, CravoTheme.TEXT, false);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(46)));
        back.setOnClickListener(v -> finish());

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.leftMargin = dp(7);
        header.addView(titles, titleLp);
        TextView kicker = text("CRAVO / MODULES", 10, palette.accent(), true);
        kicker.setLetterSpacing(.12f);
        titles.addView(kicker);
        titles.addView(text("Módulos", 24, CravoTheme.TEXT, true));

        TextView importBtn = text("+ IMPORTAR", 11, palette.onAccent(), true);
        importBtn.setGravity(Gravity.CENTER);
        importBtn.setBackground(palette.solid(palette.accent(), 11, 0));
        importBtn.setPadding(dp(12), dp(12), dp(12), dp(12));
        header.addView(importBtn);
        importBtn.setOnClickListener(v -> picker.launch(StorageAccess.downloadsPicker(true)));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setHint("Buscar módulos…");
        search.setTextColor(CravoTheme.TEXT);
        search.setHintTextColor(CravoTheme.MUTED);
        search.setPadding(dp(17), 0, dp(17), 0);
        search.setBackground(palette.glass(13));
        LinearLayout.LayoutParams queryLp = new LinearLayout.LayoutParams(-1, dp(49));
        queryLp.setMargins(dp(21), dp(8), dp(21), dp(13));
        root.addView(search, queryLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams f = new LinearLayout.LayoutParams(-1, -2);
        f.setMargins(dp(12), 0, dp(12), dp(10));
        root.addView(filters, f);

        countLabel = text("Carregando…", 12, CravoTheme.MUTED, false);
        countLabel.setPadding(dp(24), dp(5), dp(20), dp(11));
        root.addView(countLabel);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        pad(results, 21, 8);
        scroll.addView(results);

        TextView footer = text("CRAVO 0.3.1 · Módulos instalados e integrados", 11, CravoTheme.MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(9), dp(8), dp(16));
        root.addView(footer);
    }

    private void makeFilters() {
        filters.removeAllViews();
        for (String item : new String[]{"TODOS", "ATIVOS", "NATIVOS", "EXTERNOS"}) {
            boolean selected = category.equals(item);
            TextView tab = text(item, 10, selected ? palette.onAccent() : CravoTheme.MUTED, selected);
            tab.setGravity(Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setBackground(palette.solid(selected ? palette.accent() : 0xFF211F26, 30,
                    selected ? 0 : 0xFF39323D));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1);
            params.setMargins(dp(3), 0, dp(3), 0);
            filters.addView(tab, params);
            tab.setOnClickListener(v -> { category = item; render(); });
        }
    }

    private boolean passes(String label, boolean enabled, boolean external) {
        if (category.equals("ATIVOS") && !enabled) return false;
        if (category.equals("NATIVOS") && external) return false;
        if (category.equals("EXTERNOS") && !external) return false;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        return query.isEmpty() || label.toLowerCase(Locale.ROOT).contains(query);
    }

    private void render() {
        if (results == null || search == null) return;
        makeFilters();
        results.removeAllViews();
        int total = builtins.size() + nativePackages.size();
        int enabled = 0, visible = 0;
        for (UnifiedMod mod : builtins) if (mod.isEnabled()) enabled++;
        for (Mod mod : nativePackages) if (mod.isEnabled()) enabled++;
        for (UnifiedMod mod : builtins) {
            if (!passes(mod.getName(), mod.isEnabled(), false)) continue;
            addBuiltin(mod);
            visible++;
        }
        for (Mod mod : nativePackages) {
            String name = mod.getDisplayName() == null ? mod.getFileName() : mod.getDisplayName();
            if (!passes(name, mod.isEnabled(), true)) continue;
            addExternal(mod, name);
            visible++;
        }
        countLabel.setText(total + " módulos  ·  " + enabled + " ativos  ·  " + visible + " exibidos");
        if (visible == 0) {
            space(results, 35);
            TextView empty = text("Nenhum módulo encontrado.\nExperimente outro filtro ou importe uma extensão.", 13, CravoTheme.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            results.addView(empty);
        }
    }

    private LinearLayout moduleCard(String title, String subtitle, boolean enabled) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        pad(card, 16, 13);
        card.setBackground(palette.glass(18));
        LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-1, -2);
        margin.bottomMargin = dp(12);
        results.addView(card, margin);
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(line);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        labels.addView(text(title, 16, CravoTheme.TEXT, true));
        space(labels, 5);
        labels.addView(text(subtitle, 12, CravoTheme.MUTED, false));
        return card;
    }

    private void addBuiltin(UnifiedMod mod) {
        LinearLayout card = moduleCard(mod.getName(),
                mod.getDescription().isEmpty() ? "Módulo integrado" : mod.getDescription(), mod.isEnabled());
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(controls);
        if (mod.hasConfig()) {
            TextView cfg = text("CONFIGURAR", 11, palette.accent(), true);
            cfg.setPadding(dp(10), dp(13), dp(10), dp(8));
            controls.addView(cfg);
            cfg.setOnClickListener(v -> showSettings(mod));
        }
        Switch toggle = new Switch(this);
        toggle.setChecked(mod.isEnabled());
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(
                toggle.isChecked() ? palette.accent() : CravoTheme.MUTED));
        controls.addView(toggle);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            try {
                mod.applyEnabled(checked);
                button.setThumbTintList(android.content.res.ColorStateList.valueOf(
                        checked ? palette.accent() : CravoTheme.MUTED));
                countLabel.setText("Preferência atualizada · " + mod.getName());
            } catch (Exception error) {
                button.setOnCheckedChangeListener(null);
                button.setChecked(!checked);
                Toast.makeText(this, "Não foi possível alterar o módulo: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addExternal(Mod mod, String name) {
        LinearLayout card = moduleCard(name, "Extensão instalada · " + mod.getFileName(), mod.isEnabled());
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        card.addView(controls);
        if (mod.hasEditableConfig()) {
            TextView config = text("CONFIGURAR", 11, palette.accent(), true);
            config.setPadding(dp(10), dp(12), dp(10), dp(8));
            controls.addView(config);
            config.setOnClickListener(v -> {
                Intent intent = new Intent(this, ModConfigActivity.class);
                intent.putExtra("mod_filename", mod.getId());
                startActivity(intent);
            });
        }
        Switch toggle = new Switch(this);
        toggle.setChecked(mod.isEnabled());
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(
                mod.isEnabled() ? palette.accent() : CravoTheme.MUTED));
        controls.addView(toggle);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            viewModel.setModEnabled(mod.getId(), checked);
            mod.setEnabled(checked);
            button.setThumbTintList(android.content.res.ColorStateList.valueOf(
                    checked ? palette.accent() : CravoTheme.MUTED));
            countLabel.setText("Módulo alterado · " + name);
        });
    }

    private void showSettings(UnifiedMod mod) {
        if (mod.openCustomConfig()) return;
        if (mod.getConfigEntries().isEmpty()) {
            Toast.makeText(this, "Nenhum ajuste disponível para este módulo", Toast.LENGTH_SHORT).show();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(14), dp(20), dp(14));
        form.setBackgroundColor(CravoTheme.SURFACE);
        scroll.addView(form);
        Map<UnifiedMod.ConfigEntry, android.widget.TextView> inputs = new LinkedHashMap<>();
        Map<UnifiedMod.ConfigEntry, Switch> toggles = new LinkedHashMap<>();
        for (UnifiedMod.ConfigEntry cfg : mod.getConfigEntries()) {
            form.addView(text(cfg.displayName, 14, CravoTheme.TEXT, true));
            space(form, 7);
            if (cfg.type == UnifiedMod.ConfigType.BUTTON) {
                TextView button = text("ABRIR CONTROLE", 12, palette.accent(), true);
                button.setPadding(dp(8), dp(10), dp(8), dp(12));
                form.addView(button);
                button.setOnClickListener(v -> mod.updateConfig(cfg, cfg.currentValue));
            } else if (cfg.type == UnifiedMod.ConfigType.TOGGLE) {
                Switch sw = new Switch(this);
                sw.setChecked(Boolean.parseBoolean(cfg.currentValue));
                form.addView(sw);
                toggles.put(cfg, sw);
            } else if (cfg.type == UnifiedMod.ConfigType.SLIDER_INT || cfg.type == UnifiedMod.ConfigType.SLIDER_FLOAT) {
                EditText input = new EditText(this);
                input.setSingleLine(true);
                input.setText(cfg.currentValue);
                input.setTextColor(CravoTheme.TEXT);
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                    (cfg.type == UnifiedMod.ConfigType.SLIDER_FLOAT ? android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
                form.addView(input);
                inputs.put(cfg, input);
            } else {
                EditText input = new EditText(this);
                input.setSingleLine(true);
                input.setText(cfg.currentValue);
                input.setTextColor(CravoTheme.TEXT);
                form.addView(input);
                inputs.put(cfg, input);
            }
            space(form, 19);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(mod.getName())
                .setView(scroll)
                .setPositiveButton("SALVAR", null)
                .setNegativeButton("CANCELAR", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    for (Map.Entry<UnifiedMod.ConfigEntry, Switch> item : toggles.entrySet())
                        mod.updateConfig(item.getKey(), String.valueOf(item.getValue().isChecked()));
                    for (Map.Entry<UnifiedMod.ConfigEntry, android.widget.TextView> item : inputs.entrySet()) {
                        String value = item.getValue().getText().toString();
                        UnifiedMod.ConfigEntry cfg = item.getKey();
                        if (cfg.type == UnifiedMod.ConfigType.SLIDER_INT ||
                                cfg.type == UnifiedMod.ConfigType.SLIDER_FLOAT) {
                            try {
                                double number = Double.parseDouble(value);
                                if (!cfg.minValue.isEmpty() && number < Double.parseDouble(cfg.minValue))
                                    throw new NumberFormatException("below minimum");
                                if (!cfg.maxValue.isEmpty() && number > Double.parseDouble(cfg.maxValue))
                                    throw new NumberFormatException("above maximum");
                            } catch (NumberFormatException error) {
                                Toast.makeText(this, "Valor inválido em " + cfg.displayName, Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                        mod.updateConfig(cfg, value);
                    }
                    Toast.makeText(this, "Configurações atualizadas", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }));
        dialog.show();
    }
}
