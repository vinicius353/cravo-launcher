package org.levimc.launcher.core.versions;

import android.content.Context;
import android.os.Build;
import org.levimc.launcher.core.minecraft.MinecraftLauncher;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.*;

/** Read-only APK/APKS structural diagnostics. Does not alter the user's files. */
public final class CravoPackageInspector {
    private CravoPackageInspector() {}

    public static class Result {
        public final boolean compatible;
        public final String description;
        Result(boolean compatible, String description) {
            this.compatible = compatible;
            this.description = description;
        }
    }

    public static Result inspect(Context context, GameVersion version) {
        if (version == null) return new Result(false, "Nenhuma versão selecionada.");
        StringBuilder sb = new StringBuilder("CRAVO · Inspeção de instância\n\n");
        sb.append("Versão: ").append(version.versionCode).append('\n');
        if (version.isInstalled)
            return new Result(true, sb + "Instância instalada: " + version.packageName +
                    "\nOs arquivos são fornecidos pelo Android.\n");
        if (version.versionDir == null)
            return new Result(false, sb + "ERRO: pasta da versão indisponível.");

        File base = new File(version.versionDir, "base.apk.levi");
        if (!base.isFile() || base.length() == 0L)
            return new Result(false, sb + "ERRO: base.apk.levi ausente ou vazio.");
        sb.append("APK base: ").append(base.length() / 1024).append(" KiB\n");
        List<File> packages = new ArrayList<>();
        packages.add(base);
        File[] splits = new File(version.versionDir, "splits")
                .listFiles((dir, name) -> name.endsWith(".apk.levi"));
        if (splits != null) Collections.addAll(packages, splits);
        Set<String> abis = new LinkedHashSet<>();
        boolean manifest = false, mainLib = false, corrupt = false;
        for (File apk : packages) {
            try (ZipFile zip = new ZipFile(apk)) {
                if (apk.equals(base)) manifest = zip.getEntry("AndroidManifest.xml") != null;
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String path = entries.nextElement().getName();
                    if (path.startsWith("lib/") && path.endsWith("/libminecraftpe.so")) {
                        String[] parts = path.split("/");
                        if (parts.length == 3) { abis.add(parts[1]); mainLib = true; }
                    }
                }
            } catch (IOException e) {
                corrupt = true;
                sb.append("ERRO: não foi possível ler ").append(apk.getName()).append('\n');
            }
        }
        boolean abiMatch = false;
        for (String abi : Build.SUPPORTED_ABIS) if (abis.contains(abi)) abiMatch = true;
        File cache = MinecraftLauncher.getRuntimeLibDir(context, version.directoryName);
        boolean cached = containsMainLibrary(cache);
        if (!manifest) sb.append("ERRO: AndroidManifest.xml ausente.\n");
        if (!mainLib && !cached) sb.append("ERRO: libminecraftpe.so ausente.\n");
        if (!abis.isEmpty() && !abiMatch) sb.append("ERRO: arquitetura nativa incompatível.\n");
        if (version.needsRepair) sb.append("AVISO: bibliotecas marcadas para reparo.\n");
        sb.append("Splits: ").append(packages.size() - 1).append('\n');
        sb.append("ABIs disponíveis: ").append(abis).append('\n');
        sb.append("ABIs no aparelho: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');
        boolean ok = !corrupt && manifest && (mainLib || cached) &&
                (abis.isEmpty() || abiMatch) && !version.needsRepair;
        sb.append(ok ? "\nEstrutura pronta para tentar executar." :
                "\nA estrutura da instância precisa de atenção.");
        sb.append("\nA análise estrutural não garante compatibilidade com o runtime ou autenticação.");
        return new Result(ok, sb.toString());
    }

    private static boolean containsMainLibrary(File folder) {
        if (folder == null || !folder.isDirectory()) return false;
        File[] items = folder.listFiles();
        if (items == null) return false;
        for (File item : items)
            if (item.isDirectory() ? containsMainLibrary(item)
                    : item.getName().equals("libminecraftpe.so") && item.length() > 0)
                return true;
        return false;
    }
}
