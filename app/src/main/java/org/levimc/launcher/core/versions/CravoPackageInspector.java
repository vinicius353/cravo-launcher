package org.levimc.launcher.core.versions;

import android.content.Context;
import android.os.Build;
import org.levimc.launcher.core.minecraft.MinecraftLauncher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Read-only compatibility report for user-owned Minecraft APK/APKS archives.
 * Checks the same critical native libraries that the game loader needs.
 */
public final class CravoPackageInspector {
    private static final String[] CRITICAL = {"libc++_shared.so", "libminecraftpe.so"};
    private static final String[] ADDITIONAL = {
            "libfmod.so", "libMediaDecoders_Android.so", "libHttpClient.Android.so"
    };

    private CravoPackageInspector() {}

    public static final class Result {
        public final boolean compatible;
        public final String description;

        Result(boolean compatible, String description) {
            this.compatible = compatible;
            this.description = description;
        }
    }

    public static Result inspect(Context context, GameVersion version) {
        if (version == null) return new Result(false, "Nenhuma versão selecionada.");
        StringBuilder report = new StringBuilder("CRAVO · Diagnóstico da instância\n\n");
        report.append("Versão: ").append(version.versionCode).append('\n');
        if (version.isInstalled) {
            return new Result(true, report + "Instalação gerenciada pelo Android: "
                    + version.packageName + "\nArquivos fornecidos pelo gerenciador de pacotes.");
        }
        if (version.versionDir == null) {
            return new Result(false, report + "ERRO: diretório local indisponível.");
        }

        File base = new File(version.versionDir, "base.apk.levi");
        if (!base.isFile() || base.length() <= 0L) {
            return new Result(false, report + "ERRO: base.apk.levi ausente ou vazio.");
        }
        List<File> packages = new ArrayList<>();
        packages.add(base);
        File[] splits = new File(version.versionDir, "splits")
                .listFiles((directory, name) -> name.endsWith(".apk.levi"));
        if (splits != null) {
            Arrays.sort(splits, (a, b) -> a.getName().compareTo(b.getName()));
            Collections.addAll(packages, splits);
        }

        String abi = MinecraftLauncher.getRuntimeAbi();
        report.append("Arquitetura principal: ").append(abi).append('\n');
        report.append("APKs encontrados: ").append(packages.size()).append('\n');
        Map<String, Set<String>> libraries = new LinkedHashMap<>();
        boolean manifest = false;
        boolean corrupt = false;
        for (File apk : packages) {
            try (ZipFile archive = new ZipFile(apk)) {
                if (apk.equals(base)) manifest = archive.getEntry("AndroidManifest.xml") != null;
                Enumeration<? extends ZipEntry> entries = archive.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String path = entry.getName();
                    if (entry.isDirectory() || !path.startsWith("lib/") || !path.endsWith(".so")) continue;
                    String[] parts = path.split("/");
                    if (parts.length != 3) continue;
                    libraries.computeIfAbsent(parts[2], name -> new LinkedHashSet<>()).add(parts[1]);
                }
            } catch (IOException error) {
                corrupt = true;
                report.append("ERRO: arquivo inválido: ").append(apk.getName())
                        .append(" (").append(error.getClass().getSimpleName()).append(")\n");
            }
        }

        File cache = MinecraftLauncher.getRuntimeLibAbiDir(context, MinecraftLauncher.getStorageProfileId(version), abi);
        if (!manifest) report.append("ERRO: manifesto Android ausente no APK principal.\n");
        report.append("Cache do runtime: ").append(cache.getAbsolutePath()).append('\n');
        for (File apk : packages) report.append("APK: ").append(apk.getAbsolutePath()).append('\n');
        report.append("\nBibliotecas do jogo:\n");
        boolean missingCritical = false;
        for (String lib : CRITICAL) {
            boolean cacheFound = new File(cache, lib).isFile() && new File(cache, lib).length() > 0;
            Set<String> availableAbis = libraries.getOrDefault(lib, Collections.emptySet());
            boolean found = cacheFound || availableAbis.contains(abi);
            report.append(found ? "[OK] " : "[FALTA] ").append(lib);
            if (cacheFound) report.append(" · cache ").append(abi);
            else if (availableAbis.contains(abi)) report.append(" · dentro do APK/APKS");
            if (!availableAbis.isEmpty()) report.append(" · ABIs: ").append(availableAbis);
            report.append('\n');
            if (!found) missingCritical = true;
        }
        for (String lib : ADDITIONAL) {
            Set<String> availableAbis = libraries.getOrDefault(lib, Collections.emptySet());
            if (!availableAbis.isEmpty()) {
                report.append("[INFO] ").append(lib).append(" · ABIs: ").append(availableAbis).append('\n');
            }
        }
        if (version.needsRepair) report.append("AVISO: a instância está marcada para reparo.\n");

        boolean compatible = !corrupt && manifest && !missingCritical && !version.needsRepair;
        if (!compatible) {
            report.append("\nNão foi possível validar todas as dependências locais.\n");
            if (missingCritical) report.append(
                    "Confirme se o APK/APKS inclui os pacotes de bibliotecas da arquitetura " + abi +
                    ". Um APK base separado de seus splits pode estar incompleto.\n");
        } else {
            report.append("\nBibliotecas essenciais presentes para tentar iniciar.\n");
        }
        report.append("A checagem é estrutural; não garante o funcionamento do runtime.");
        return new Result(compatible, report.toString());
    }
}
