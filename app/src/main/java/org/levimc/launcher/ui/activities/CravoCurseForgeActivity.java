package org.levimc.launcher.ui.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.levimc.launcher.core.curseforge.CurseForgeClient;
import org.levimc.launcher.core.curseforge.models.Content;
import org.levimc.launcher.core.curseforge.models.ContentSearchResponse;
import org.levimc.launcher.ui.theme.CravoTheme;

import java.util.List;
import java.util.Locale;

/** Own CRAVO-native CurseForge browsing UI using only the authorized public API client. */
public final class CravoCurseForgeActivity extends BaseActivity {
    private static final int PAGE_SIZE = 20;
    private static final String WEBSITE = "https://www.curseforge.com/minecraft-bedrock";
    private CravoTheme theme;
    private LinearLayout body, tabs, nav;
    private EditText search;
    private TextView title;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchAction = () -> { page = 0; load(); };
    private int category = 0, page = 0, requestId = 0;
    private boolean nextAvailable;

    @Override protected boolean shouldSkipNavBar() { return true; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        theme = new CravoTheme(this);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(CravoTheme.BACKGROUND);
        setContentView(screen);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(15), dp(13), dp(17), dp(13));
        screen.addView(head);
        TextView back = text("‹", 32, CravoTheme.TEXT, false);
        back.setGravity(Gravity.CENTER);
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(42)));
        back.setOnClickListener(v -> finish());
        head.addView(text("C R A V O  /  CURSEFORGE", 17, CravoTheme.TEXT, true));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Pesquisar add-ons e texturas");
        search.setTextColor(CravoTheme.TEXT);
        search.setHintTextColor(CravoTheme.MUTED);
        search.setTextSize(14);
        search.setPadding(dp(14), dp(8), dp(14), dp(8));
        search.setBackground(theme.glass(13));
        LinearLayout.LayoutParams queryLp = new LinearLayout.LayoutParams(-1, dp(50));
        queryLp.setMargins(dp(22), dp(10), dp(22), dp(11));
        screen.addView(search, queryLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(searchAction);
                handler.postDelayed(searchAction, 440L);
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        tabs = new LinearLayout(this);
        tabs.setPadding(dp(18), 0, dp(18), 0);
        screen.addView(tabs);
        drawTabs();

        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        screen.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(17), dp(22), dp(18));
        scroller.addView(body);

        nav = new LinearLayout(this);
        nav.setPadding(dp(21), dp(7), dp(21), dp(13));
        nav.setGravity(Gravity.CENTER);
        screen.addView(nav);
        load();
    }

    private int dp(int n) { return theme.dp(n); }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void gap(int h) { body.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h))); }

    private void drawTabs() {
        tabs.removeAllViews();
        String[] labels = {"TODOS", "ADDONS", "MAPAS", "TEXTURAS"};
        int[] ids = {0, 4984, 6913, 6929};
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            boolean selected = category == id;
            TextView chip = text(labels[i], 10, selected ? theme.onAccent() : CravoTheme.MUTED, selected);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(theme.solid(selected ? theme.accent() : 0xFF242028,
                    12, selected ? 0 : 0xFF463844));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(37), 1);
            params.setMargins(dp(3), 0, dp(3), 0);
            tabs.addView(chip, params);
            chip.setOnClickListener(v -> { category = id; page = 0; drawTabs(); load(); });
        }
    }

    private void title(String text) {
        body.addView(text(text, 22, CravoTheme.TEXT, true));
        gap(13);
    }

    private void card(String heading, String description, Runnable onClick) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(17), dp(17), dp(17), dp(16));
        tile.setBackground(theme.glass(16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(12);
        body.addView(tile, p);
        tile.addView(text(heading + "  ›", 16, CravoTheme.TEXT, true));
        TextView t = text(description, 12, CravoTheme.MUTED, false);
        t.setPadding(0, dp(7), 0, 0);
        tile.addView(t);
        tile.setOnClickListener(v -> onClick.run());
    }

    private void openOfficial() { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE))); }

    private void load() {
        if (body == null) return;
        final int thisRequest = ++requestId;
        body.removeAllViews();
        nav.removeAllViews();
        title("Descobrir conteúdo");
        if (!CurseForgeClient.isConfigured()) {
            card("Integração não configurada",
                    "O CRAVO ainda precisa de uma chave de API CurseForge autorizada. " +
                    "Nenhuma chave será incluída no código ou exposta em logs.", this::openOfficial);
            card("Explorar no navegador", "Abrir o catálogo oficial de Minecraft Bedrock", this::openOfficial);
            return;
        }
        TextView loading = text("Buscando projetos…", 13, theme.accent(), false);
        loading.setGravity(Gravity.CENTER);
        body.addView(loading);
        CurseForgeClient.getInstance().searchContent(
                search.getText().toString().trim(), category, "", page * PAGE_SIZE, PAGE_SIZE,
                CurseForgeClient.SORT_POPULARITY, "desc", new CurseForgeClient.CurseForgeCallback<ContentSearchResponse>() {
                    @Override public void onSuccess(ContentSearchResponse response) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed() || thisRequest != requestId) return;
                            body.removeAllViews();
                            title("Descobrir conteúdo");
                            List<Content> results = response == null ? null : response.data;
                            nextAvailable = results != null && results.size() >= PAGE_SIZE;
                            if (results == null || results.isEmpty()) {
                                card("Nenhum resultado", "Tente outra pesquisa ou categoria", () -> {});
                            } else for (Content item : results) {
                                String detail = (item.summary == null ? "Projeto da comunidade" : item.summary);
                                if (detail.length() > 230) detail = detail.substring(0, 230) + "…";
                                card(item.name == null ? "Projeto sem nome" : item.name, detail, () -> {
                                    String url = item.links == null ? null : item.links.websiteUrl;
                                    if (url == null || !url.startsWith("https://www.curseforge.com/")) {
                                        Toast.makeText(CravoCurseForgeActivity.this,
                                                "Página do projeto indisponível", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                                });
                            }
                            pagination();
                        });
                    }

                    @Override public void onError(Throwable t) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed() || thisRequest != requestId) return;
                            body.removeAllViews();
                            title("CurseForge indisponível");
                            String description = "Não foi possível carregar o catálogo.";
                            if (t instanceof CurseForgeClient.CurseForgeHttpException) {
                                int status = ((CurseForgeClient.CurseForgeHttpException)t).getStatusCode();
                                if (status == 401 || status == 403)
                                    description = "A chave de API não foi autorizada pelo CurseForge (HTTP " + status + ").";
                                else if (status == 429)
                                    description = "Limite de consultas da API atingido (HTTP 429).";
                                else description = "Erro HTTP " + status + " na API.";
                            }
                            card("Abrir CurseForge oficial", description + "\nToque para acessar o navegador.",
                                    CravoCurseForgeActivity.this::openOfficial);
                            card("Tentar novamente", "Refazer a consulta", CravoCurseForgeActivity.this::load);
                        });
                    }
                });
    }

    private void pagination() {
        nav.removeAllViews();
        TextView previous = text("‹  ANTERIOR", 12, page > 0 ? theme.accent() : CravoTheme.MUTED, true);
        previous.setGravity(Gravity.CENTER);
        nav.addView(previous, new LinearLayout.LayoutParams(0, dp(40), 1));
        previous.setOnClickListener(v -> { if (page > 0) { page--; load(); } });
        TextView label = text(String.valueOf(page + 1), 13, CravoTheme.TEXT, true);
        label.setGravity(Gravity.CENTER);
        nav.addView(label, new LinearLayout.LayoutParams(dp(42), dp(40)));
        TextView next = text("PRÓXIMA  ›", 12, nextAvailable ? theme.accent() : CravoTheme.MUTED, true);
        next.setGravity(Gravity.CENTER);
        nav.addView(next, new LinearLayout.LayoutParams(0, dp(40), 1));
        next.setOnClickListener(v -> { if (nextAvailable) { page++; load(); } });
    }

    @Override protected void onDestroy() {
        requestId++;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
