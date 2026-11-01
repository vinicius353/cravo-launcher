package org.levimc.launcher.ui.activities;

import android.os.Bundle;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.levimc.launcher.core.mods.config.ModConfigManager;
import org.levimc.launcher.ui.theme.CravoTheme;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CRAVO-owned JSON module configuration view with atomic save and backups. */
@SuppressWarnings("deprecation")
public final class CravoModConfigActivity extends BaseActivity {
    public static final String EXTRA_MOD_ROOT = "cravo_mod_root";
    public static final String EXTRA_MOD_NAME = "cravo_mod_name";
    private CravoTheme theme;
    private final ModConfigManager config = new ModConfigManager();
    private final List<ModConfigManager.ConfigFile> files = new ArrayList<>();
    private ModConfigManager.ConfigFile selected;
    private JsonElement original;
    private LinearLayout body;
    private String modName;
    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private final Map<String, Switch> toggles = new LinkedHashMap<>();

    @Override protected boolean shouldSkipNavBar() { return true; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        theme = new CravoTheme(this);
        modName = getIntent().getStringExtra(EXTRA_MOD_NAME);
        String root = getIntent().getStringExtra(EXTRA_MOD_ROOT);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(CravoTheme.BACKGROUND);
        setContentView(screen);

        LinearLayout head = new LinearLayout(this);
        head.setPadding(dp(14), dp(16), dp(17), dp(14));
        head.setGravity(Gravity.CENTER_VERTICAL);
        screen.addView(head);
        TextView back = text("‹", 32, CravoTheme.TEXT, false);
        back.setGravity(Gravity.CENTER);
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(42)));
        back.setOnClickListener(v -> finish());
        head.addView(text("C R A V O   /   CONFIGURAÇÃO", 16, CravoTheme.TEXT, true));

        ScrollView scroll = new ScrollView(this);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(18), dp(22), dp(25));
        scroll.addView(body);

        if (root == null || root.isEmpty()) { error("Diretório do módulo não encontrado."); return; }
        files.addAll(config.scanConfigFiles(new File(root)));
        if (files.isEmpty()) { error("Este módulo não oferece arquivos de configuração editáveis."); return; }
        load(files.get(0));
    }

    private int dp(int n) { return theme.dp(n); }
    private TextView text(String text, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(color);
        v.setTextSize(size);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private void gap(int dp) { body.addView(new View(this), new LinearLayout.LayoutParams(1, dp(dp))); }

    private void error(String s) {
        body.removeAllViews();
        body.addView(text(s, 15, CravoTheme.MUTED, false));
    }

    private void load(ModConfigManager.ConfigFile file) {
        try {
            ModConfigManager.LoadedConfig loaded = config.load(file);
            selected = file;
            original = loaded.getValue();
            render();
        } catch (Exception error) {
            error("Erro lendo o módulo: " + error.getMessage());
        }
    }

    private void render() {
        body.removeAllViews();
        fields.clear();
        toggles.clear();
        body.addView(text(modName == null ? "Configuração" : modName, 29, CravoTheme.TEXT, true));
        gap(10);
        body.addView(text("O CRAVO salva uma cópia de segurança antes de escrever alterações.",
                12, CravoTheme.MUTED, false));
        gap(20);
        if (files.size() > 1) {
            body.addView(text("ARQUIVOS DE CONFIGURAÇÃO", 11, theme.accent(), true));
            gap(8);
            for (ModConfigManager.ConfigFile file : files) {
                TextView item = text((file == selected ? "✓  " : "○  ") + file.getDisplayName(),
                        13, file == selected ? theme.accent() : CravoTheme.TEXT, true);
                item.setPadding(dp(13), dp(10), dp(13), dp(10));
                item.setBackground(theme.glass(12));
                LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(-1, -2);
                param.bottomMargin = dp(8);
                body.addView(item, param);
                item.setOnClickListener(v -> load(file));
            }
            gap(15);
        }
        body.addView(text("EDITAR · " + selected.getDisplayName(), 11, theme.accent(), true));
        gap(13);
        if (original == null) { error("Arquivo de configuração vazio."); return; }
        if (original.isJsonObject()) {
            for (Map.Entry<String, JsonElement> field : original.getAsJsonObject().entrySet()) {
                String key = field.getKey();
                JsonElement value = field.getValue();
                fieldEditor(key, value);
            }
        } else fieldEditor("root", original);
        gap(14);
        TextView save = text("SALVAR CONFIGURAÇÃO", 14, theme.onAccent(), true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(17), dp(15), dp(17), dp(15));
        save.setBackground(theme.solid(theme.accent(), 14, 0));
        body.addView(save);
        save.setOnClickListener(v -> save());
    }

    private void fieldEditor(String key, JsonElement value) {
        body.addView(text(key, 13, CravoTheme.TEXT, true));
        gap(7);
        if (value != null && value.isJsonPrimitive() &&
                value.getAsJsonPrimitive().isBoolean()) {
            Switch sw = new Switch(this);
            sw.setChecked(value.getAsBoolean());
            body.addView(sw);
            toggles.put(key, sw);
        } else {
            EditText input = new EditText(this);
            input.setText(value == null || value.isJsonNull() ? "" :
                    (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ?
                            value.getAsString() : value.toString()));
            input.setTextColor(CravoTheme.TEXT);
            input.setTextSize(13);
            input.setBackground(theme.glass(11));
            input.setPadding(dp(13), dp(12), dp(13), dp(12));
            if (value != null && (value.isJsonObject() || value.isJsonArray())) {
                input.setSingleLine(false);
                input.setMinLines(3);
                input.setMaxLines(8);
            } else input.setSingleLine(true);
            body.addView(input, new LinearLayout.LayoutParams(-1, -2));
            fields.put(key, input);
        }
        gap(18);
    }

    private void save() {
        if (selected == null || original == null) return;
        try {
            JsonElement value = original.deepCopy();
            if (!value.isJsonObject() && fields.containsKey("root")) {
                value = JsonParser.parseString(fields.get("root").getText().toString());
            } else if (value.isJsonObject()) {
                JsonObject json = value.getAsJsonObject();
                for (Map.Entry<String, Switch> e : toggles.entrySet())
                    json.addProperty(e.getKey(), e.getValue().isChecked());
                for (Map.Entry<String, EditText> e : fields.entrySet()) {
                    String key = e.getKey(), input = e.getValue().getText().toString();
                    JsonElement previous = json.get(key);
                    if (previous != null && previous.isJsonPrimitive() &&
                            previous.getAsJsonPrimitive().isString()) {
                        json.addProperty(key, input);
                    } else {
                        // Numbers and nested values remain JSON-typed, never converted silently to strings.
                        json.add(key, JsonParser.parseString(input));
                    }
                }
            }
            config.save(selected, value);
            Toast.makeText(this, "Configuração salva. Backup criado.", Toast.LENGTH_LONG).show();
            load(selected);
        } catch (Exception error) {
            Toast.makeText(this, "Valor inválido: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
