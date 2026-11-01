package org.levimc.launcher.ui.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.levimc.launcher.core.auth.MsftAccountStore;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.settings.FeatureSettings;
import org.levimc.launcher.ui.theme.CravoTheme;

import java.util.List;

/** CRAVO-owned Settings and Accounts. Legacy services only, no legacy layouts. */
@SuppressWarnings("deprecation")
public final class CravoWorkspaceActivity extends BaseActivity {
    public static final String EXTRA_SECTION = "cravo_workspace_section";
    private CravoTheme theme;
    private LinearLayout body;
    private String section;

    public static Intent forSection(Context context, String section) {
        return new Intent(context, CravoWorkspaceActivity.class).putExtra(EXTRA_SECTION, section);
    }

    @Override protected boolean shouldSkipNavBar() { return true; }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        FeatureSettings.init(getApplicationContext());
        theme = new CravoTheme(this);
        section = getIntent().getStringExtra(EXTRA_SECTION);
        if (section == null) section = "settings";
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(CravoTheme.BACKGROUND);
        setContentView(screen);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(14));
        screen.addView(header);
        TextView back = text("‹", 32, CravoTheme.TEXT, false);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(46), dp(48)));
        back.setOnClickListener(v -> finish());
        TextView brand = text("C R A V O   /   " + ("accounts".equals(section) ? "CONTAS" : "AJUSTES"),
                17, CravoTheme.TEXT, true);
        header.addView(brand);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.setVerticalScrollBarEnabled(false);
        screen.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(23), dp(22), dp(23), dp(28));
        scroller.addView(body);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (body != null) render();
    }

    private int dp(int v) { return theme.dp(v); }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private void gap(int height) {
        body.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void heading(String text) {
        gap(10);
        TextView title = text(text, 12, theme.accent(), true);
        title.setLetterSpacing(.12f);
        body.addView(title);
        gap(13);
    }

    private LinearLayout tile(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(17), dp(16), dp(16), dp(15));
        box.setBackground(theme.glass(17));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(11);
        body.addView(box, p);
        box.addView(text(title, 15, CravoTheme.TEXT, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = text(subtitle, 12, CravoTheme.MUTED, false);
            detail.setPadding(0, dp(6), 0, 0);
            box.addView(detail);
        }
        return box;
    }

    private void action(String title, String sub, Runnable task) {
        LinearLayout card = tile(title + "   ›", sub);
        card.setOnClickListener(v -> task.run());
    }

    private void toggle(String label, String description, boolean checked,
                        android.widget.CompoundButton.OnCheckedChangeListener change) {
        LinearLayout card = tile(label, description);
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(
                checked ? theme.accent() : CravoTheme.MUTED));
        card.addView(sw);
        sw.setOnCheckedChangeListener((button, active) -> {
            ((android.widget.Switch) button).setThumbTintList(android.content.res.ColorStateList.valueOf(
                    active ? theme.accent() : CravoTheme.MUTED));
            change.onCheckedChanged(button, active);
        });
    }

    private void render() {
        body.removeAllViews();
        if ("accounts".equals(section)) {
            renderAccounts();
            return;
        }
        body.addView(text("Ajustes do launcher", 29, CravoTheme.TEXT, true));
        gap(5);
        body.addView(text("Suas preferências. Nenhuma alteração é aplicada diretamente ao APK do Minecraft.",
                12, CravoTheme.MUTED, false));
        heading("APARÊNCIA");

        TextView label = text("COR DE DESTAQUE", 11, CravoTheme.MUTED, true);
        body.addView(label);
        gap(9);
        LinearLayout colors = new LinearLayout(this);
        colors.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(colors);
        for (int color : new int[]{0xFFF4F4F5, 0xFFD93652, 0xFF9362CC, 0xFF318C8C, 0xFFB88942}) {
            TextView dot = text(color == theme.accent() ? "✓" : "●", 18,
                    CravoTheme.luminance(color) > .38 ? 0xFF131119 : 0xFFFFFFFF, true);
            dot.setGravity(Gravity.CENTER);
            dot.setBackground(theme.solid(color, 40, color == theme.accent() ? 0xFF99919B : 0));
            LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(dp(43), dp(43));
            d.rightMargin = dp(13);
            colors.addView(dot, d);
            dot.setOnClickListener(v -> { theme.setAccent(color); render(); });
        }
        gap(16);
        toggle("Reduzir animações", "Menos movimento e transições de interface",
                getSharedPreferences("cravo_ui", MODE_PRIVATE).getBoolean("reduce_motion", false),
                (btn, enabled) -> getSharedPreferences("cravo_ui", MODE_PRIVATE)
                        .edit().putBoolean("reduce_motion", enabled).apply());

        heading("MINECRAFT & SISTEMA");
        FeatureSettings settings = FeatureSettings.getInstance();
        toggle("Isolamento por versão", "Separar arquivos e preferências de cada instância",
                settings.isVersionIsolationEnabled(),
                (btn, enabled) -> settings.setVersionIsolationEnabled(enabled));
        toggle("Login gerenciado pelo launcher", "Gerenciamento de contas Microsoft na inicialização",
                settings.isLauncherManagedMcLoginEnabled(),
                (btn, enabled) -> settings.setLauncherManagedMcLoginEnabled(enabled));
        toggle("Serviço em primeiro plano", "Ajuda a gerenciar a execução quando o sistema minimiza o jogo",
                settings.isForegroundServiceEnabled(),
                (btn, enabled) -> settings.setForegroundServiceEnabled(enabled));
        toggle("Logcat na tela", "Exibir registros acessíveis durante o uso",
                settings.isLogcatOverlayEnabled(),
                (btn, enabled) -> settings.setLogcatOverlayEnabled(enabled));

        heading("GERENCIAMENTO");
        action("Contas Microsoft / Xbox", "Selecionar ou conectar uma conta", () ->
                startActivity(forSection(this, "accounts")));
        action("Instâncias e backups", "Gerenciar versões e restaurar cópias de segurança", () ->
                startActivity(CravoHubActivity.forSection(this, "versions")));
        action("Módulos", "Gerenciar extensões na interface CRAVO", () ->
                startActivity(new Intent(this, CravoModulesActivity.class)));
        action("Diagnóstico / Logcat", "Revisar e compartilhar relatórios locais",
                () -> CravoDiagnostics.show(this));

        heading("INFORMAÇÕES");
        action("Sobre o CRAVO", "Versão 0.3.1 e créditos de software livre", () ->
                new AlertDialog.Builder(this)
                        .setTitle("CRAVO Launcher · Alpha 0.3.1")
                        .setMessage("Interface e integração CRAVO.\n\n"
                                + "Recursos técnicos derivados de projetos LeviMC / LeviLauncher, "
                                + "mantendo licenças e atribuições no repositório.\n\n"
                                + "Este projeto não é afiliado à Mojang nem à Microsoft.")
                        .setPositiveButton("FECHAR", null).show());
        action("Ferramentas de compatibilidade", "Funções avançadas ainda não migradas",
                () -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void renderAccounts() {
        body.addView(text("Suas contas", 29, CravoTheme.TEXT, true));
        gap(6);
        body.addView(text("As contas permanecem no armazenamento privado deste aplicativo.",
                12, CravoTheme.MUTED, false));
        heading("CONTAS VINCULADAS");
        List<MsftAccountStore.MsftAccount> accounts = MsftAccountStore.list(this);
        if (accounts.isEmpty())
            tile("Nenhuma conta vinculada", "Entre com sua conta Microsoft para começar.");
        for (MsftAccountStore.MsftAccount account : accounts) {
            String display = account.xboxGamertag != null && !account.xboxGamertag.isEmpty()
                    ? account.xboxGamertag : (account.minecraftUsername != null
                    ? account.minecraftUsername : "Conta Microsoft");
            String id = account.id;
            String title = (account.active ? "✓  " : "○  ") + display;
            action(title, account.active ? "Conta ativa" : "Toque para ativar", () -> {
                try {
                    MsftAccountStore.setActive(this, id);
                    Toast.makeText(this, "Conta selecionada", Toast.LENGTH_SHORT).show();
                    render();
                } catch (Exception e) {
                    Toast.makeText(this, "Erro ao selecionar conta", Toast.LENGTH_LONG).show();
                }
            });
        }
        heading("GERENCIAMENTO");
        action("+  Adicionar conta", "Abrir autenticação Microsoft",
                () -> startActivity(new Intent(this, MsftLoginActivity.class)));
        if (!accounts.isEmpty()) {
            action("Remover conta", "Selecione qual conta deseja remover", () -> {
                String[] labels = new String[accounts.size()];
                for (int i = 0; i < accounts.size(); i++) {
                    labels[i] = accounts.get(i).xboxGamertag == null ?
                            "Conta " + (i + 1) : accounts.get(i).xboxGamertag;
                }
                new AlertDialog.Builder(this).setTitle("Remover conta local")
                        .setItems(labels, (dlg, i) ->
                                new AlertDialog.Builder(this)
                                    .setMessage("Remover os dados desta conta do CRAVO?")
                                    .setPositiveButton("REMOVER", (d, w) -> {
                                        MsftAccountStore.remove(this, accounts.get(i).id);
                                        render();
                                    })
                                    .setNegativeButton("CANCELAR", null)
                                    .show()).setNegativeButton("CANCELAR", null).show();
            });
        }
    }
}
