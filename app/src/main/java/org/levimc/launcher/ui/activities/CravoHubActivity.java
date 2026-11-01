package org.levimc.launcher.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import org.levimc.launcher.ui.theme.CravoTheme;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.core.versions.VersionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * CRAVO-native navigation for content, mods and versions.
 * This Activity owns its UI and keeps the existing tools isolated as back-end routes.
 */
public final class CravoHubActivity extends BaseActivity {
    public static final String EXTRA_SECTION = "cravo_section";
    private static final int BG = 0xFF0C0B0E;
    private static final int TEXT = 0xFFF4EDF3;
    private static final int MUTED = 0xFFAA9BA7;
    private CravoTheme theme;

    private LinearLayout content;
    private String section;

    public static Intent forSection(Context context, String section) {
        return new Intent(context, CravoHubActivity.class).putExtra(EXTRA_SECTION, section);
    }

    @Override protected boolean shouldSkipNavBar() { return true; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        theme = new CravoTheme(this);
        section = getIntent().getStringExtra(EXTRA_SECTION);
        if (section == null) section = "packs";

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BG);
        setContentView(screen);

        LinearLayout header = new LinearLayout(this);
        header.setPadding(dp(22), dp(20), dp(22), dp(16));
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        screen.addView(header);

        TextView back = text("‹", 31, TEXT, false);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(35), dp(38)));
        back.setOnClickListener(v -> finish());

        TextView brand = text("C R A V O", 19, TEXT, true);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0, -2, 1);
        brandParams.leftMargin = dp(13);
        header.addView(brand, brandParams);
        TextView alpha = text("ALPHA 0.3.1", 10, theme.accent(), true);
        alpha.setPadding(dp(12), dp(7), dp(12), dp(7));
        alpha.setBackground(glass(13));
        header.addView(alpha);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(30), dp(24), dp(25));
        scroll.addView(content);
        render();
    }

    private int dp(float d) { return Math.round(getResources().getDisplayMetrics().density * d); }

    private TextView text(String str, float sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(str);
        view.setTextColor(color);
        view.setTextSize(sp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable glass(int radius) { return theme.glass(radius); }

    private void spacing(int dp) {
        content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private void heading(String caption, String headline, String description) {
        TextView overline = text(caption, 11, theme.accent(), true);
        overline.setLetterSpacing(.15f);
        content.addView(overline);
        spacing(12);
        content.addView(text(headline, 32, TEXT, true));
        spacing(9);
        content.addView(text(description, 13, MUTED, false));
        spacing(30);
    }

    private void card(String name, String description, Runnable run) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(19), dp(18), dp(18), dp(18));
        tile.setBackground(glass(18));
        TextView title = text(name + "   ›", 17, TEXT, true);
        tile.addView(title);
        View gap = new View(this);
        tile.addView(gap, new LinearLayout.LayoutParams(1, dp(8)));
        tile.addView(text(description, 12, MUTED, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(13);
        content.addView(tile, lp);
        tile.setOnClickListener(v -> run.run());
        tile.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                v.animate().scaleX(.98f).scaleY(.98f).setDuration(100).start();
            else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP ||
                    event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            return false;
        });
    }

    private void render() {
        content.removeAllViews();
        if ("mods".equals(section)) {
            heading("CRAVO / MÓDULOS", "Módulos", "Gerencie extensões e ferramentas disponíveis.");
            card("Gerenciar módulos", "Painel CRAVO: listar, ativar, configurar e importar.", 
                    () -> startActivity(new Intent(this, CravoModulesActivity.class)));
            card("Mods externos", "Consultar e importar mods externos compatíveis.",
                    () -> startActivity(new Intent(this, ExternalModsActivity.class)));
            card("CurseForge", "Procurar add-ons e pacotes no catálogo.",
                    () -> startActivity(new Intent(this, CurseForgeActivity.class)));
            card("Diagnóstico", "Copiar ou compartilhar registros de erro.", () -> CravoDiagnostics.show(this));
            return;
        }
        if ("versions".equals(section)) {
            heading("CRAVO / INSTÂNCIAS", "Suas versões", "Escolha sua instalação do Minecraft Bedrock.");
            VersionManager manager = VersionManager.getIfInitialized();
            if (manager != null) {
                List<GameVersion> versions = new ArrayList<>();
                if (manager.getInstalledVersions() != null) versions.addAll(manager.getInstalledVersions());
                if (manager.getCustomVersions() != null) versions.addAll(manager.getCustomVersions());
                for (GameVersion version : versions) {
                    String display = version.displayName != null && !version.displayName.isEmpty() ?
                            version.displayName : version.directoryName;
                    String secondary = "Minecraft " + version.versionCode + (version.isInstalled ? " · instalado" : " · importado");
                    if (!version.isInstalled) {
                        card("Inspecionar · " + display,
                                "Verificar APK base, splits, arquitetura e bibliotecas  ›",
                                () -> {
                                    org.levimc.launcher.core.versions.CravoPackageInspector.Result inspection =
                                        org.levimc.launcher.core.versions.CravoPackageInspector.inspect(this, version);
                                    new android.app.AlertDialog.Builder(this)
                                        .setTitle(inspection.compatible ? "Estrutura compatível" : "Problema na instância")
                                        .setMessage(inspection.description)
                                        .setPositiveButton("OK", null)
                                        .setNeutralButton("Copiar relatório", (d,w) -> {
                                            android.content.ClipboardManager clipboard =
                                                (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                                            if (clipboard != null) clipboard.setPrimaryClip(
                                                android.content.ClipData.newPlainText("CRAVO inspection", inspection.description));
                                        })
                                        .show();
                                });
                    }
                    card(display, secondary, () -> {
                        manager.selectVersion(version);
                        Toast.makeText(this, "Versão selecionada: " + display, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                if (versions.isEmpty()) {
                    card("Nenhuma versão encontrada", "Importe um APK/APKS do Minecraft para começar.", () -> {});
                }
            } else {
                card("Carregando versões", "Volte e abra esta tela novamente.", () -> {});
            }
            card("+  Importar APK / APKS", "Selecionar um arquivo do seu armazenamento.", () -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("CRAVO_IMPORT_APK", true);
                startActivity(intent);
                finish();
            });
            card("Backup e restauração", "Exportar ou restaurar instâncias de outras instalações.",
                    () -> startActivity(new Intent(this, InstancesActivity.class)));
            return;
        }
        heading("CRAVO / BIBLIOTECA", "Packs & mundos", "Gerencie o conteúdo da instância selecionada.");
        card("Pacotes de textura", "Resource packs instalados no seu Minecraft.",
                () -> openContent(ContentListActivity.TYPE_RESOURCE_PACKS));
        card("Pacotes de comportamento", "Add-ons e behavior packs.",
                () -> openContent(ContentListActivity.TYPE_BEHAVIOR_PACKS));
        card("Mundos", "Mundos salvos na instância atual.",
                () -> openContent(ContentListActivity.TYPE_WORLDS));
        card("Compatibilidade de conteúdos", "Ferramentas legadas para funções ainda não migradas.",
                () -> startActivity(new Intent(this, ContentManagementActivity.class)));
        card("CurseForge", "Descobrir novos projetos e abrir o catálogo oficial.",
                () -> startActivity(new Intent(this, CurseForgeActivity.class)));
    }

    private void openContent(int type) {
        if (VersionManager.getIfInitialized() == null ||
                VersionManager.getIfInitialized().getSelectedVersion() == null) {
            Toast.makeText(this, "Selecione uma versão do Minecraft primeiro", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, CravoLibraryActivity.class);
        intent.putExtra(ContentListActivity.EXTRA_CONTENT_TYPE, type);
        startActivity(intent);
    }
}
