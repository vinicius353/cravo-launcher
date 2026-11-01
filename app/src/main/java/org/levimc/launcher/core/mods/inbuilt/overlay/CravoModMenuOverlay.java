package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.levimc.launcher.core.mods.inbuilt.ExternalModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.InbuiltModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.ui.theme.CravoTheme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A first-party CRAVO in-game panel, using the existing native module providers.
 * No legacy XML/layout or module UI is inflated. Only module APIs are reused.
 */
@SuppressWarnings("deprecation")
public final class CravoModMenuOverlay {
    public interface ModMenuCallback {
        void onModToggled(String modId, boolean enabled);
        void onButtonOpacityChanged(int opacity);
    }

    private final Activity activity;
    private final CravoTheme theme;
    private Dialog dialog;
    private ModMenuCallback callback;
    private LinearLayout results;
    private EditText search;
    private TextView status;
    private String filter = "TODOS";
    private final List<UnifiedMod> modules = new ArrayList<>();

    public CravoModMenuOverlay(Activity activity) {
        this.activity = activity;
        this.theme = new CravoTheme(activity);
    }

    public void setCallback(ModMenuCallback callback) { this.callback = callback; }
    public boolean isShowing() { return dialog != null && dialog.isShowing(); }

    public void show() {
        if (isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
        modules.clear();
        try {
            modules.addAll(InbuiltModuleProvider.load(activity));
            modules.addAll(ExternalModuleProvider.load(activity));
        } catch (Throwable error) {
            android.util.Log.w("CRAVO_INGAME", "Some native modules unavailable", error);
        }
        dialog = new Dialog(activity);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = .64f;
            window.setAttributes(attrs);
        }
        dialog.setContentView(buildUI());
        dialog.setOnDismissListener(d -> dialog = null);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(Math.min(
                    activity.getResources().getDisplayMetrics().widthPixels - dp(22), dp(740)),
                    Math.min(activity.getResources().getDisplayMetrics().heightPixels - dp(26), dp(590)));
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
        render();
    }

    public void hide() {
        if (dialog != null) {
            Dialog toDismiss = dialog;
            dialog = null;
            if (toDismiss.isShowing()) toDismiss.dismiss();
        }
    }

    private int dp(int v) { return theme.dp(v); }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private void gap(LinearLayout l, int v) {
        l.addView(new View(activity), new LinearLayout.LayoutParams(1, dp(v)));
    }

    private View buildUI() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(10));
        root.setBackground(theme.glass(18));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(header);

        TextView logo = text("C", 24, theme.accent(), true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(theme.solid(theme.accentSoft(), 12, theme.accentOutline()));
        header.addView(logo, new LinearLayout.LayoutParams(dp(41), dp(41)));
        TextView name = text("  C R A V O   /   MÓDULOS", 16, CravoTheme.TEXT, true);
        header.addView(name, new LinearLayout.LayoutParams(0, dp(41), 1));
        TextView close = text("✕", 19, CravoTheme.TEXT, true);
        close.setGravity(Gravity.CENTER);
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(41)));
        close.setOnClickListener(v -> hide());
        gap(root, 12);

        search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextSize(13);
        search.setHint("Buscar módulo…");
        search.setTextColor(CravoTheme.TEXT);
        search.setHintTextColor(CravoTheme.MUTED);
        search.setPadding(dp(13), 0, dp(13), 0);
        search.setBackground(theme.solid(0xFF17151D, 12, theme.accentOutline()));
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(43)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        gap(root, 9);

        LinearLayout chips = new LinearLayout(activity);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(chips);
        for (String item : new String[]{"TODOS", "ATIVOS", "FAVORITOS", "EXTERNOS"}) {
            boolean selected = filter.equals(item);
            TextView chip = text(item, 10, selected ? theme.onAccent() : CravoTheme.MUTED, selected);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(theme.solid(selected ? theme.accent() : 0xFF24212A, 12,
                    selected ? 0 : 0xFF39333E));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(33), 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            chips.addView(chip, lp);
            chip.setOnClickListener(v -> {
                filter = item;
                for (int i = 0; i < chips.getChildCount(); i++) {
                    TextView button = (TextView) chips.getChildAt(i);
                    boolean active = i == new String[]{"TODOS", "ATIVOS", "FAVORITOS", "EXTERNOS"}.length
                            - java.util.Arrays.asList("TODOS", "ATIVOS", "FAVORITOS", "EXTERNOS").size();
                    // repaint selected chip based on its own text
                    boolean isCurrent = button.getText().toString().equals(filter);
                    button.setTextColor(isCurrent ? theme.onAccent() : CravoTheme.MUTED);
                    button.setBackground(theme.solid(isCurrent ? theme.accent() : 0xFF24212A,
                            12, isCurrent ? 0 : 0xFF39333E));
                }
                render();
            });
        }
        gap(root, 7);

        status = text("", 11, CravoTheme.MUTED, false);
        root.addView(status);
        gap(root, 8);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);

        gap(root, 8);
        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(actions);
        action(actions, "AJUSTES", false, this::showOptions);
        action(actions, "EDITAR HUD", true, () -> {
            hide();
            InbuiltOverlayManager manager = InbuiltOverlayManager.getInstance();
            if (manager != null) {
                manager.setHudEditorMode(true);
                Toast.makeText(activity, "Editor HUD ativo. Abra o menu novamente para salvar.", Toast.LENGTH_LONG).show();
            }
        });
        return root;
    }

    private void action(LinearLayout parent, String label, boolean primary, Runnable run) {
        TextView button = text(label, 11, primary ? theme.onAccent() : theme.accent(), true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), dp(9), dp(8), dp(9));
        button.setBackground(theme.solid(primary ? theme.accent() : theme.accentSoft(), 12, 0));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        parent.addView(button, p);
        button.setOnClickListener(v -> run.run());
    }

    private void render() {
        if (results == null || status == null || search == null) return;
        results.removeAllViews();
        Set<String> favorites = InbuiltModManager.getInstance(activity).getFavoriteModKeys();
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (UnifiedMod mod : modules) {
            if (!query.isEmpty() && !mod.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
            if ("ATIVOS".equals(filter) && !mod.isEnabled()) continue;
            if ("EXTERNOS".equals(filter) && mod.getSource() != UnifiedMod.Source.EXTERNAL) continue;
            if ("FAVORITOS".equals(filter) && !favorites.contains(mod.getStableKey())) continue;
            addModule(mod, favorites);
            shown++;
        }
        status.setText(shown + " exibidos  ·  " + modules.size() + " disponíveis");
        if (shown == 0) {
            TextView empty = text("Nenhum módulo encontrado", 13, CravoTheme.MUTED, false);
            empty.setPadding(dp(15), dp(25), dp(15), dp(25));
            results.addView(empty);
        }
    }

    private void addModule(UnifiedMod mod, Set<String> favorites) {
        LinearLayout card = new LinearLayout(activity);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(11), dp(10), dp(10), dp(10));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(theme.glass(14));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(8);
        results.addView(card, cp);

        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(line);

        TextView icon = text("◆", 18, theme.accent(), true);
        icon.setGravity(Gravity.CENTER);
        line.addView(icon, new LinearLayout.LayoutParams(dp(31), dp(36)));
        LinearLayout label = new LinearLayout(activity);
        label.setOrientation(LinearLayout.VERTICAL);
        line.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        label.addView(text(mod.getName(), 14, CravoTheme.TEXT, true));
        label.addView(text(mod.getSource() == UnifiedMod.Source.EXTERNAL ? "EXTERNO" : "INTEGRADO",
                10, CravoTheme.MUTED, false));

        TextView favorite = text(favorites.contains(mod.getStableKey()) ? "★" : "☆",
                20, theme.accent(), true);
        favorite.setGravity(Gravity.CENTER);
        line.addView(favorite, new LinearLayout.LayoutParams(dp(37), dp(38)));
        favorite.setOnClickListener(v -> {
            InbuiltModManager manager = InbuiltModManager.getInstance(activity);
            manager.setModFavorite(mod.getStableKey(), !manager.getFavoriteModKeys().contains(mod.getStableKey()));
            render();
        });

        Switch toggle = new Switch(activity);
        toggle.setChecked(mod.isEnabled());
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(
                mod.isEnabled() ? theme.accent() : CravoTheme.MUTED));
        line.addView(toggle);
        toggle.setOnCheckedChangeListener((b, enabled) -> {
            try {
                mod.applyEnabled(enabled);
                if (callback != null) callback.onModToggled(mod.getId(), enabled);
                if ("ATIVOS".equals(filter)) render();
            } catch (Exception error) {
                Toast.makeText(activity, "Erro ao alterar módulo", Toast.LENGTH_SHORT).show();
                render();
            }
        });

        if (mod.hasConfig()) {
            TextView settings = text("Configurar  ›", 11, theme.accent(), true);
            settings.setPadding(dp(32), dp(5), dp(2), dp(4));
            card.addView(settings);
            settings.setOnClickListener(v -> showModuleSettings(mod));
        }
    }

    private void showModuleSettings(UnifiedMod mod) {
        if (mod.openCustomConfig()) return;
        if (mod.getConfigEntries().isEmpty()) {
            Toast.makeText(activity, "Configurações avançadas indisponíveis nesta sessão", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(14), dp(18), dp(10));
        Map<UnifiedMod.ConfigEntry, EditText> edits = new LinkedHashMap<>();
        Map<UnifiedMod.ConfigEntry, Switch> switches = new LinkedHashMap<>();
        for (UnifiedMod.ConfigEntry cfg : mod.getConfigEntries()) {
            form.addView(text(cfg.displayName, 13, CravoTheme.TEXT, true));
            gap(form, 6);
            if (cfg.type == UnifiedMod.ConfigType.TOGGLE) {
                Switch toggle = new Switch(activity);
                toggle.setChecked(Boolean.parseBoolean(cfg.currentValue));
                form.addView(toggle);
                switches.put(cfg, toggle);
            } else if (cfg.type == UnifiedMod.ConfigType.BUTTON) {
                TextView button = text("EXECUTAR", 12, theme.accent(), true);
                form.addView(button);
                button.setOnClickListener(v -> mod.updateConfig(cfg, cfg.currentValue));
            } else {
                EditText entry = new EditText(activity);
                entry.setText(cfg.currentValue);
                entry.setSingleLine(true);
                form.addView(entry);
                edits.put(cfg, entry);
            }
            gap(form, 13);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(CravoTheme.SURFACE);
        scroll.addView(form);
        new AlertDialog.Builder(activity)
                .setTitle(mod.getName())
                .setView(scroll)
                .setPositiveButton("SALVAR", (d, w) -> {
                    for (Map.Entry<UnifiedMod.ConfigEntry, Switch> e : switches.entrySet())
                        mod.updateConfig(e.getKey(), String.valueOf(e.getValue().isChecked()));
                    for (Map.Entry<UnifiedMod.ConfigEntry, EditText> e : edits.entrySet())
                        mod.updateConfig(e.getKey(), e.getValue().getText().toString());
                })
                .setNegativeButton("FECHAR", null)
                .show();
    }

    private void showOptions() {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        LinearLayout settings = new LinearLayout(activity);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setPadding(dp(18), dp(13), dp(18), dp(13));

        Switch notifications = new Switch(activity);
        notifications.setText("Notificações do menu");
        notifications.setChecked(manager.isNotificationsEnabled());
        notifications.setOnCheckedChangeListener((v, enabled) -> manager.setNotificationsEnabled(enabled));
        settings.addView(notifications);

        TextView opacity = text("Opacidade do botão: " + manager.getModMenuButtonOpacity() + "%", 13,
                CravoTheme.TEXT, false);
        settings.addView(opacity);
        SeekBar seek = new SeekBar(activity);
        seek.setMax(100);
        seek.setProgress(manager.getModMenuButtonOpacity());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean user) {
                int value = Math.max(10, progress);
                opacity.setText("Opacidade do botão: " + value + "%");
                if (user) {
                    manager.setModMenuButtonOpacity(value);
                    if (callback != null) callback.onButtonOpacityChanged(value);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        settings.addView(seek);

        if (InbuiltOverlayManager.getInstance() != null) {
            TextView finishHud = text("FINALIZAR EDIÇÃO DO HUD", 12, theme.accent(), true);
            finishHud.setPadding(dp(8), dp(9), dp(8), dp(9));
            settings.addView(finishHud);
            finishHud.setOnClickListener(v -> {
                InbuiltOverlayManager.getInstance().setHudEditorMode(false);
                Toast.makeText(activity, "HUD salvo", Toast.LENGTH_SHORT).show();
            });
        }

        new AlertDialog.Builder(activity).setTitle("CRAVO · Ajustes")
                .setView(settings).setPositiveButton("OK", null).show();
    }
}
