package org.levimc.launcher.ui.views;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Native CRAVO dashboard, layered over the existing launcher UI.
 * Every launcher action is delegated to MainActivity to retain the existing flows.
 */
public final class CravoDashboard extends LinearLayout {
    public interface Actions {
        void play();
        void pickVersion();
        void importApk();
        void openVersions();
        void openMods();
        void openContent();
        void openCurseForge();
        void openSettings();
        void openAccounts();
        void openDiagnostics();
        void openAdvanced();
    }

    private static final int BG = Color.rgb(12, 11, 14);
    private static final int SURFACE = Color.rgb(26, 24, 29);
    private static final int SURFACE_HI = Color.rgb(34, 29, 35);
    private static final int WHITE = Color.rgb(246, 242, 246);
    private static final int MUTED = Color.rgb(165, 153, 164);
    private static final int LINE = Color.rgb(54, 45, 54);
    private final Activity activity;
    private final Actions actions;
    private final SharedPreferences preferences;
    private final LinearLayout body;
    private final LinearLayout navigation;
    private String page = "home";
    private String currentVersion = "Nenhuma versão selecionada";
    private boolean playReady;
    private int accent;

    public CravoDashboard(Activity activity, Actions actions) {
        super(activity);
        this.activity = activity;
        this.actions = actions;
        this.preferences = activity.getSharedPreferences("cravo_ui", Context.MODE_PRIVATE);
        accent = preferences.getInt("accent", Color.rgb(217, 54, 82));
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        setClickable(true);
        setFocusable(true);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(23), dp(15), dp(23), dp(12));
        addView(header, new LayoutParams(-1, dp(67)));

        TextView monogram = text("C", 19, WHITE, true);
        monogram.setGravity(Gravity.CENTER);
        monogram.setBackground(round(Color.rgb(53, 27, 37), 10, 0, 0));
        header.addView(monogram, new LayoutParams(dp(36), dp(36)));
        TextView name = text("C R A V O", 19, WHITE, true);
        LayoutParams nameLp = new LayoutParams(0, -2, 1);
        nameLp.leftMargin = dp(11);
        header.addView(name, nameLp);
        TextView alpha = text("ALPHA 0.2", 10, accent, true);
        alpha.setPadding(dp(12), dp(7), dp(12), dp(7));
        alpha.setBackground(round(Color.rgb(37, 24, 31), 24, 1, Color.rgb(82, 41, 56)));
        header.addView(alpha);

        View separator = new View(activity);
        separator.setBackgroundColor(Color.rgb(37, 32, 38));
        addView(separator, new LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(activity);
        body.setOrientation(VERTICAL);
        body.setPadding(dp(24), dp(28), dp(24), dp(26));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, 0, 1));

        navigation = new LinearLayout(activity);
        navigation.setOrientation(HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(8), dp(8), dp(8), dp(11));
        navigation.setBackgroundColor(Color.rgb(18, 16, 21));
        addView(navigation, new LayoutParams(-1, dp(77)));
        render();
    }

    public void updateVersion(String displayName, boolean ready) {
        currentVersion = (displayName == null || displayName.trim().isEmpty())
                ? "Nenhuma versão selecionada" : displayName;
        playReady = ready;
        if ("home".equals(page)) render();
    }

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable round(int color, int radius, int stroke, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(radius));
        d.setColor(color);
        if (stroke > 0) d.setStroke(dp(stroke), strokeColor);
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(activity);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private void addSpace(LinearLayout parent, int height) {
        parent.addView(new View(activity), new LayoutParams(1, dp(height)));
    }

    private void label(String s) {
        TextView v = text(s, 11, accent, true);
        v.setLetterSpacing(.16f);
        body.addView(v);
    }

    private void title(String name, String subtitle) {
        label("CRAVO / " + name.toUpperCase());
        addSpace(body, 12);
        TextView t = text(name, 30, WHITE, true);
        body.addView(t);
        addSpace(body, 5);
        TextView s = text(subtitle, 13, MUTED, false);
        body.addView(s);
        addSpace(body, 26);
    }

    private TextView card(String title, String subtitle, Runnable onClick) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(VERTICAL);
        panel.setPadding(dp(18), dp(17), dp(18), dp(17));
        panel.setBackground(round(SURFACE, 16, 1, LINE));
        TextView heading = text(title, 16, WHITE, true);
        panel.addView(heading);
        addSpace(panel, 7);
        TextView desc = text(subtitle, 12, MUTED, false);
        panel.addView(desc);
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setOnClickListener(v -> onClick.run());
        LayoutParams params = new LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        body.addView(panel, params);
        return heading;
    }

    private void action(String label, Runnable onClick, boolean primary) {
        TextView v = text(label, 16, primary ? WHITE : accent, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(15), dp(14), dp(15), dp(14));
        v.setBackground(round(primary ? accent : Color.rgb(43, 27, 36), 15, primary ? 0 : 1, Color.rgb(83, 46, 58)));
        v.setOnClickListener(w -> onClick.run());
        LayoutParams lp = new LayoutParams(-1, dp(primary ? 60 : 51));
        lp.bottomMargin = dp(12);
        body.addView(v, lp);
    }

    private void setPage(String next) {
        page = next;
        render();
    }

    private void render() {
        body.removeAllViews();
        navigation.removeAllViews();
        switch (page) {
            case "profiles": renderProfiles(); break;
            case "packs": renderPacks(); break;
            case "settings": renderSettings(); break;
            case "modules": renderModules(); break;
            case "appearance": renderAppearance(); break;
            default: renderHome(); break;
        }
        addNavigation();
    }

    private void renderHome() {
        label("BEDROCK. DO SEU JEITO.");
        addSpace(body, 17);
        TextView slogan = text("Seu jogo.\nSua marca.", 39, WHITE, true);
        slogan.setLineSpacing(dp(1), 1f);
        body.addView(slogan);
        addSpace(body, 13);
        body.addView(text("Prepare o perfil. Entre na partida.", 13, MUTED, false));
        addSpace(body, 29);
        TextView selected = text("PERFIL SELECIONADO", 11, MUTED, true);
        selected.setLetterSpacing(.12f);
        body.addView(selected);
        addSpace(body, 13);
        card("⚔  " + preferences.getString("profile", "PvP essencial"),
                "Preferência visual · configurações em desenvolvimento  ›", () -> setPage("profiles"));

        LinearLayout shortcuts = new LinearLayout(activity);
        shortcuts.setOrientation(HORIZONTAL);
        LayoutParams rowParams = new LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(18);
        body.addView(shortcuts, rowParams);
        shortcut(shortcuts, "☷", "Módulos", "Gerencie seus mods", () -> setPage("modules"), true);
        shortcut(shortcuts, "◉", "Aparência", "Personalize a UI", () -> setPage("appearance"), false);

        addSpace(body, 10);
        label("MINECRAFT BEDROCK");
        addSpace(body, 10);
        card("⬡  " + currentVersion, "Toque para escolher outra versão  ›", actions::pickVersion);
        action("▷   JOGAR", actions::play, true);
        if (!playReady) {
            body.addView(text("Carregando gerenciador de versões...", 11, MUTED, false));
        }
        addSpace(body, 7);
        TextView foot = text("CRAVO · baseado no LeviLauncher", 11, MUTED, false);
        foot.setGravity(Gravity.CENTER);
        body.addView(foot);
    }

    private void shortcut(LinearLayout parent, String glyph, String heading, String desc, Runnable onClick, boolean first) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(VERTICAL);
        tile.setPadding(dp(16), dp(15), dp(12), dp(15));
        tile.setBackground(round(SURFACE, 15, 1, LINE));
        LayoutParams p = new LayoutParams(0, dp(119), 1f);
        if (first) p.rightMargin = dp(12);
        parent.addView(tile, p);
        tile.addView(text(glyph, 23, accent, true));
        addSpace(tile, 8);
        tile.addView(text(heading, 15, WHITE, true));
        addSpace(tile, 7);
        tile.addView(text(desc, 11, MUTED, false));
        tile.setOnClickListener(v -> onClick.run());
    }

    private void renderProfiles() {
        title("Perfis", "Organize suas preferências por tipo de partida.");
        String chosen = preferences.getString("profile", "PvP essencial");
        for (String option : new String[]{"PvP essencial", "Equilibrado", "Personalizado"}) {
            card((option.equals(chosen) ? "✓  " : "○  ") + option,
                    "Identificação visual; ajustes específicos chegam depois.",
                    () -> { preferences.edit().putString("profile", option).apply(); render(); });
        }
        action("+   Gerenciar versões", actions::openVersions, false);
        action("Importar APK / APKS", actions::importApk, false);
    }

    private void renderPacks() {
        title("Packs", "Seu conteúdo de Minecraft, em um só lugar.");
        card("Resource & Behavior Packs", "Abrir gerenciador de conteúdos  ›", actions::openContent);
        card("CurseForge", "Explorar o catálogo integrado existente  ›", actions::openCurseForge);
        card("Módulos nativos", "Gerenciar mods e extensões instaladas  ›", actions::openMods);
    }

    private void renderModules() {
        title("Módulos", "Ferramentas instaladas e atalhos do launcher.");
        card("Gerenciar mods", "Abrir a lista nativa de mods  ›", actions::openMods);
        card("Versões", "Escolher instância ou importar um APK  ›", actions::openVersions);
        card("Logcat / diagnóstico", "Gerar relatório para compartilhar  ›", actions::openDiagnostics);
    }

    private void renderAppearance() {
        title("Aparência", "A identidade escura do CRAVO.");
        body.addView(text("COR DE DESTAQUE", 11, MUTED, true));
        addSpace(body, 15);
        LinearLayout dots = new LinearLayout(activity);
        body.addView(dots);
        for (int color : new int[]{0xFFD93652, 0xFF9362CC, 0xFF318C8C, 0xFFB88942, 0xFF737985}) {
            TextView dot = text(color == accent ? "✓" : "●", 18, WHITE, true);
            dot.setGravity(Gravity.CENTER);
            dot.setBackground(round(color, 24, 0, 0));
            LayoutParams p = new LayoutParams(dp(42), dp(42));
            p.rightMargin = dp(12);
            dots.addView(dot, p);
            dot.setOnClickListener(v -> {
                accent = color;
                preferences.edit().putInt("accent", color).apply();
                render();
            });
        }
        addSpace(body, 23);
        card("Dark Mode", "Visual escuro padrão · ativo", () -> {});
        action("Voltar para início", () -> setPage("home"), false);
    }

    private void renderSettings() {
        title("Ajustes", "Configurações e ferramentas de suporte.");
        card("🧾  Diagnóstico / Logcat", "Visualize, copie ou compartilhe os registros  ›", actions::openDiagnostics);
        card("Minecraft / Instâncias", "Selecionar ou importar versões  ›", actions::openVersions);
        card("Contas", "Gerenciar conta Microsoft / Xbox  ›", actions::openAccounts);
        card("Configurações avançadas", "Abrir ajustes existentes do LeviLauncher  ›", actions::openSettings);
        card("Ferramentas originais", "Acessar a interface anterior quando necessário  ›", actions::openAdvanced);
        addSpace(body, 5);
        body.addView(text("CRAVO Alpha 0.2 · Apache 2.0 · LeviMC credits preserved", 11, MUTED, false));
    }

    private void addNavigation() {
        for (String[] item : new String[][]{
                {"home", "⌂", "Início"}, {"profiles", "▱", "Perfis"},
                {"packs", "⬡", "Packs"}, {"settings", "⚙", "Ajustes"}}) {
            String key = item[0];
            boolean active = key.equals(page) || ("modules".equals(page) && "home".equals(key)) ||
                    ("appearance".equals(page) && "home".equals(key));
            LinearLayout tab = new LinearLayout(activity);
            tab.setOrientation(VERTICAL);
            tab.setGravity(Gravity.CENTER);
            if (active) tab.setBackground(round(Color.rgb(48, 27, 38), 12, 0, 0));
            LayoutParams p = new LayoutParams(0, -1, 1);
            p.leftMargin = dp(2);
            p.rightMargin = dp(2);
            navigation.addView(tab, p);
            TextView glyph = text(item[1], 20, active ? accent : MUTED, true);
            glyph.setGravity(Gravity.CENTER);
            tab.addView(glyph);
            addSpace(tab, 2);
            TextView caption = text(item[2], 11, active ? accent : MUTED, active);
            caption.setGravity(Gravity.CENTER);
            tab.addView(caption);
            tab.setOnClickListener(v -> setPage(key));
        }
    }
}
