package org.levimc.launcher.core.minecraft

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.levimc.launcher.core.versions.GameVersion
import org.levimc.launcher.util.LauncherStorage
import org.levimc.launcher.util.NativeBridgeHelper
import org.levimc.launcher.util.NativeLibraryPublisher
import org.levimc.launcher.util.NativeImageGuard
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

class GamePackageManager private constructor(
    private val context: Context,
    private val version: GameVersion?,
    private val launchTrace: LaunchTrace?,
    private val progressListener: MinecraftRuntimePreparer.ProgressListener?
) {

    private val packageContext: Context
    private val assetManager: AssetManager
    private val nativeLibDir: String
    private val applicationInfo: ApplicationInfo

    private val knownPackages = arrayOf(MinecraftLauncher.MC_PACKAGE_NAME)

    private val requiredLibs = buildList {
        add("libc++_shared.so")
        add("libfmod.so")
        add("libMediaDecoders_Android.so")
        if (shouldLoadMallocRedirect()) {
            add("libMallocRedirect.so")
        }
        add("libminecraftpe.so")
    }.toTypedArray()

    private val optionalLibs = arrayOf(
        "libHttpClient.Android.so",
    )

    private val extractableLibs = requiredLibs + optionalLibs

    private val systemLoadedLibs = arrayOf(
        "libPlayFabMultiplayer.so",
        "libmaesdk.so",
        "libgxcore.so",
    )

    data class LibraryLoadResult(
        val name: String,
        val fileName: String,
        val source: String,
        val loaded: Boolean,
        val durationMs: Long,
        val detail: String? = null
    )

    init {
        report("GamePackageManager init started")
        // Imported instances are independent of Android-installed game packages.
        // Keep using the imported base/split APK files without rewriting any game APK.
        if (version != null && !version.isInstalled) {
            val gameBase = File(version.versionDir, "base.apk.levi")
            if (!gameBase.isFile || gameBase.length() == 0L) {
                throw IllegalStateException("CRAVO_IMPORT_BASE_MISSING: base APK not found for " +
                    version.directoryName)
            }
            try {
                java.util.zip.ZipFile(gameBase).use { archive ->
                    if (archive.getEntry("AndroidManifest.xml") == null)
                        throw IllegalStateException("CRAVO_IMPORT_MANIFEST_MISSING")
                }
            } catch (e: java.util.zip.ZipException) {
                throw IllegalStateException("CRAVO_IMPORT_ZIP_INVALID", e)
            }
            report("Using imported local package: " + gameBase.name)
            packageContext = context
            applicationInfo = MinecraftLauncher(context).createFakeApplicationInfo(
                version, MinecraftLauncher.MC_PACKAGE_NAME)
            nativeLibDir = applicationInfo.nativeLibraryDir
        } else {
            val packageName = detectGamePackage()
                ?: throw IllegalStateException("CRAVO_INSTALLED_GAME_MISSING")
            report("Detected installed Minecraft package: $packageName")
            packageContext = context.createPackageContext(
                packageName,
                Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
            )
            applicationInfo = packageContext.applicationInfo
            nativeLibDir = resolveNativeLibDir()
        }
        
        extractLibraries()
        report("Creating AssetManager")
        assetManager = createAssetManager()
        report("AssetManager ready")
        setupSecurityProvider()
        report("GamePackageManager init finished")
    }

    private fun detectGamePackage(): String? {
        return knownPackages.firstOrNull { isPackageInstalled(it) }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun resolveNativeLibDir(): String {
        val profileId = if (version != null) {
            MinecraftLauncher.getStorageProfileId(version)
        } else {
            LauncherStorage.INSTALLED_MINECRAFT_PROFILE_ID
        }
        val cacheLibDir = MinecraftLauncher.getRuntimeLibAbiDir(context, profileId, getDeviceAbi())
        cacheLibDir.mkdirs()
        return cacheLibDir.absolutePath
    }

    private fun getDeviceAbi(): String = MinecraftLauncher.getRuntimeAbi()

    private fun extractLibraries() {
        report("Preparing Minecraft library cache")
        val outputDir = File(nativeLibDir)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val apkFiles = collectApkFiles()
        report("Native ABI: ${getDeviceAbi()}; cache: ${outputDir.absolutePath}")
        apkFiles.forEach { report("Native source APK: ${it.absolutePath}") }
        val manifestString = buildExtractionManifest(apkFiles)
        val manifestFile = File(outputDir, ".extraction_manifest")
        val markerMatches = try {
            manifestFile.isFile && manifestFile.readText() == manifestString
        } catch (_: Exception) {
            false
        }
        val cacheRequiredLibs = getCacheRequiredLibs()
        var allPresent = markerMatches
        if (allPresent) {
            for (lib in cacheRequiredLibs) {
                val file = File(outputDir, lib)
                if (!file.exists() || file.length() == 0L) {
                    allPresent = false
                    break
                }
            }
        }
        
        if (allPresent) {
            report("Minecraft library cache hit: ${outputDir.absolutePath}")
            for (lib in extractableLibs) {
                try {
                    val file = File(outputDir, lib)
                    if (file.exists()) {
                        ensureReadOnly(file)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed ensureReadOnly: ${e.message}")
                }
            }
            return
        }
        report("Minecraft library cache miss: extracting libraries")

        if (version != null && !version.isInstalled) {
            val deviceAbi = getDeviceAbi()
            // Inspect every imported split: some APKS tools rename the ABI package
            // without keeping 'arm' or 'x86' in its filename.
            apkFiles.forEach { extractFromApk(it.absolutePath, outputDir, deviceAbi) }
            // Do NOT copy a 32-bit library to a 64-bit cache (or vice versa).
            // A missing library is diagnosed below, not silently substituted.
            val missingAfterExtraction = cacheRequiredLibs.filter {
                !File(outputDir, it).isFile || File(outputDir, it).length() <= 0L
            }
            if (missingAfterExtraction.isNotEmpty()) {
                report("CRAVO native check: required ${missingAfterExtraction.joinToString()} absent for $deviceAbi")
                report("Imported APKs scanned: ${apkFiles.joinToString { it.name }}")
            }
        } else {
            val appInfo = packageContext.applicationInfo
            if (File(appInfo.nativeLibraryDir).exists()) {
                copyFromNativeDir(appInfo.nativeLibraryDir, outputDir)
            }
            val apkPaths = apkFiles.map { it.absolutePath }
            apkPaths.forEach { extractFromApk(it, outputDir, getDeviceAbi()) }
        }
        verifyLibraries(outputDir)

        if (cacheRequiredLibs.all { File(outputDir, it).let { f -> f.exists() && f.length() > 0 } }) {
            try {
                manifestFile.writeText(manifestString)
                File(outputDir, ".extraction_marker").delete()
                report("Minecraft library cache manifest written")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write extraction manifest: ${e.message}")
                report("Failed to write extraction manifest: ${e.message}")
            }
        }
    }

    private fun collectApkFiles(): List<File> {
        val paths = mutableListOf<String>()
        if (version != null && !version.isInstalled) {
            applicationInfo.sourceDir?.let { paths.add(it) }
            applicationInfo.splitSourceDirs?.let { paths.addAll(it) }
        } else {
            val appInfo = packageContext.applicationInfo
            appInfo.sourceDir?.let { paths.add(it) }
            appInfo.splitPublicSourceDirs?.let { paths.addAll(it) }
        }

        return paths
            .map { File(it) }
            .filter {
                if (it.exists()) {
                    true
                } else {
                    Log.w(TAG, "APK file not found: ${it.absolutePath}")
                    report("APK file not found: ${it.absolutePath}")
                    false
                }
            }
            .sortedBy { it.absolutePath }
    }

    private fun buildExtractionManifest(apkFiles: List<File>): String {
        return buildString {
            append("extractor=").append(EXTRACTOR_VERSION).append('\n')
            append("abi=").append(getDeviceAbi()).append('\n')
            append("token=").append(NativeImageGuard.TOKEN).append('\n')
            append("mode=").append(if (version != null && !version.isInstalled) "isolated" else "installed").append('\n')
            append("http=").append(shouldLoadHttpClient()).append('\n')
            append("malloc=").append(shouldLoadMallocRedirect()).append('\n')
            apkFiles.forEach { file ->
                append("apk=")
                    .append(file.absolutePath)
                    .append('|')
                    .append(file.length())
                    .append('|')
                    .append(file.lastModified())
                    .append('\n')
            }
        }
    }

    private fun publishLibrary(input: InputStream, output: File) {
        NativeLibraryPublisher.publish(input, output) { staged -> processNativeImage(staged, true) }
    }

    private fun copyFromNativeDir(sourceDir: String, destDir: File) {
        extractableLibs.forEach { lib ->
            val source = File(sourceDir, lib)
            if (source.isFile && source.length() > 0) {
                source.inputStream().use { publishLibrary(it, File(destDir, lib)) }
                report("Copied $lib from ${source.absolutePath}")
            }
        }
    }

    private fun extractFromApk(apkPath: String, outputDir: File, abi: String) {
        try {
            ZipFile(apkPath).use { zip ->
                extractableLibs.forEach { lib ->
                    val entry = zip.getEntry("lib/$abi/$lib") ?: return@forEach
                    if (entry.isDirectory) return@forEach
                    val output = File(outputDir, lib)
                    try {
                        zip.getInputStream(entry).use { publishLibrary(it, output) }
                    } catch (error: Exception) {
                        throw IOException("CRAVO_NATIVE_PREPARE_FAILED: $apkPath!/${entry.name} -> " +
                            "${output.absolutePath}: ${error.message}", error)
                    }
                    report("Prepared $apkPath!/${entry.name} -> ${output.absolutePath}")
                }
            }
        } catch (error: Exception) {
            report(error.message ?: "Native extraction failed: $apkPath")
            // Do not replace an extraction/preparation failure with a later missing-library error.
            throw error
        }
    }

    private fun ensureReadOnly(file: File) {
        if (!file.isFile) {
            throw IOException("Expected regular file: ${file.absolutePath}")
        }
        if (!file.setReadable(true, true) && !file.canRead()) {
            throw IOException("Failed to mark file readable: ${file.absolutePath}")
        }
        if (!file.setReadOnly() && file.canWrite()) {
            throw IOException("Failed to keep file read-only: ${file.absolutePath}")
        }
    }

    private fun verifyLibraries(dir: File) {
        val missing = getCacheRequiredLibs().filterNot {
            File(dir, it).let { f -> f.isFile && f.length() > 0L }
        }
        if (missing.isNotEmpty()) {
            Log.w(TAG, "Missing libraries in $dir: ${missing.joinToString()}")
            report("CRAVO: Missing native libraries: ${missing.joinToString()}")
        }
        if (version != null && !version.isInstalled) {
            val critical = listOf("libc++_shared.so", "libminecraftpe.so").filter {
                File(dir, it).let { f -> !f.isFile || f.length() == 0L }
            }
            if (critical.isNotEmpty()) {
                throw IllegalStateException(
                    "CRAVO_NATIVE_LIB_MISSING: ${critical.joinToString()} (" +
                        getDeviceAbi() + "). The selected APK/APKS is missing these " +
                        "libraries for this architecture, or extraction failed. " +
                        "Check the imported APKS splits and the CRAVO instance inspector.")
            }
        }
    }

    private fun processNativeImage(file: File, force: Boolean = false): Boolean {
        if (!file.name.endsWith(".so")) {
            return true
        }
        return if (force) {
            NativeImageGuard.processRequired(file)
        } else {
            NativeImageGuard.processIfNeeded(file)
        }
    }

    private fun processNativeImages(dir: File) {
        NativeImageGuard.processDirectory(dir)
    }

    private fun getCacheRequiredLibs(): Array<String> {
        return if (shouldLoadHttpClient()) {
            requiredLibs + optionalLibs
        } else {
            requiredLibs
        }
    }

    fun shouldLoadMallocRedirect(): Boolean {
        val versionCode = version?.versionCode ?: try {
            context.packageManager.getPackageInfo(MinecraftLauncher.MC_PACKAGE_NAME, 0).versionName
        } catch (_: Exception) {
            null
        }
        return versionCode != null && isVersionAtLeast(versionCode, "1.26.70.20")
    }

    private fun shouldLoadHttpClient(): Boolean {
        val versionCode = version?.versionCode ?: return false
        val targetVersion = if (versionCode.contains("beta")) "1.21.130.20" else "1.21.130"
        return isVersionAtLeast(versionCode, targetVersion)
    }

    private fun isVersionAtLeast(currentVersion: String, targetVersion: String): Boolean {
        return try {
            val current = currentVersion.replace(Regex("[^0-9.]"), "").split(".")
            val target = targetVersion.split(".")
            val maxLength = maxOf(current.size, target.size)

            for (i in 0 until maxLength) {
                val currentPart = current.getOrNull(i)?.toIntOrNull() ?: 0
                val targetPart = target.getOrNull(i)?.toIntOrNull() ?: 0

                if (currentPart > targetPart) return true
                if (currentPart < targetPart) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun logFileOperation(action: String, lib: String, extra: String? = null, e: Exception? = null) {
        val message = buildString {
            append("$action $lib")
            if (extra != null) append(" $extra")
            if (e != null) append(": ${e.message}")
        }
        if (e != null) Log.w(TAG, message)
    }

    private fun report(message: String) {
        if (progressListener != null) {
            progressListener.onLog(message)
        } else {
            launchTrace?.mark(message)
        }
    }

    private fun createAssetManager(): AssetManager {
        val assets = AssetManager::class.java.newInstance()
        val addAssetPathMethod = AssetManager::class.java.getMethod("addAssetPath", String::class.java)

        val paths = mutableListOf<String>()
        
        if (version != null && !version.isInstalled) {
            val baseApk = File(applicationInfo.sourceDir)
            if (baseApk.exists()) {
                paths.add(applicationInfo.sourceDir)
            } else {
                Log.w(TAG, "Base APK for assets not found: ${applicationInfo.sourceDir}")
            }
            applicationInfo.splitSourceDirs?.forEach {
                if (File(it).exists()) {
                    paths.add(it)
                } else {
                    Log.w(TAG, "Split APK for assets not found: $it")
                }
            }
        } else {
            paths.add(packageContext.packageResourcePath)
            val splitPath = packageContext.packageResourcePath.replace("base.apk", "split_install_pack.apk")
            if (File(splitPath).exists()) paths.add(splitPath)
        }
        
        paths.add(context.packageResourcePath)

        paths.forEach { path ->
            try {
                addAssetPathMethod.invoke(assets, path)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add asset path $path: ${e.message}")
            }
        }
        return assets
    }

    private fun setupSecurityProvider() {
        try {
            java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1)
        } catch (e: Exception) {
            Log.w(TAG, "Conscrypt init failed: ${e.message}")
        }
    }

    fun resolveLibraryPath(name: String): String? {
        val libFile = File(nativeLibDir, toLibraryFileName(name))
        return if (libFile.exists() && libFile.length() > 0) {
            libFile.absolutePath
        } else {
            null
        }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun loadLibrary(name: String): Boolean {
        return loadLibraryDetailed(name).loaded
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun loadLibraryDetailed(name: String): LibraryLoadResult {
        val fileName = toLibraryFileName(name)
        val normalizedName = normalizeLibraryName(name)
        val startedAt = SystemClock.elapsedRealtime()

        if (systemLoadedLibs.contains(fileName)) {
            val source = if (normalizedName == "gxcore") {
                "launcher bundled bootstrap"
            } else {
                "launcher bundled library"
            }
            try {
                if (normalizedName == "gxcore") {
                    if (!NativeBridgeHelper.bootstrapGxCore()) {
                        val detail = "gxcore bootstrap failed"
                        Log.e(TAG, "Failed to load $fileName from $source: $detail")
                        return LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
                    }
                } else {
                    System.loadLibrary(normalizedName)
                }
                return LibraryLoadResult(
                    normalizedName,
                    fileName,
                    source,
                    true,
                    elapsedSince(startedAt)
                )
            } catch (e: UnsatisfiedLinkError) {
                val detail = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "Failed to load $fileName from $source: $detail")
                return LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
            } catch (e: Exception) {
                val detail = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "Failed to load $fileName from $source: $detail")
                return LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
            }
        }

        val resolvedPath = resolveLibraryPath(name)
        val libFile = resolvedPath?.let(::File) ?: File(nativeLibDir, fileName)
        val source = "Minecraft extracted library cache"
        return if (libFile.exists() && libFile.length() > 0) {
            try {
                ensureReadOnly(libFile)
                System.load(libFile.absolutePath)
                LibraryLoadResult(normalizedName, fileName, source, true, elapsedSince(startedAt), libFile.absolutePath)
            } catch (e: UnsatisfiedLinkError) {
                val detail = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "Failed to load $fileName from ${libFile.absolutePath}: $detail")
                LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
            } catch (e: Exception) {
                val detail = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "Failed to load $fileName from ${libFile.absolutePath}: $detail")
                LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
            }
        } else {
            val detail = "$fileName not found in $nativeLibDir"
            Log.w(TAG, detail)
            LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
        }
    }

    fun loadAllLibraries(
        excludeLibs: Set<String> = emptySet(),
        trace: LaunchTrace? = null,
        listener: MinecraftRuntimePreparer.ProgressListener? = null,
        progressStart: Int = 46,
        progressEnd: Int = 74,
        excludeReasons: Map<String, String> = emptyMap()
    ): List<LibraryLoadResult> {
        val allLibs = requiredLibs + systemLoadedLibs
        val loadableLibs = allLibs.filterNot { lib ->
            val libName = normalizeLibraryName(lib)
            excludeLibs.contains(libName) || excludeLibs.contains(lib)
        }
        val total = loadableLibs.size.coerceAtLeast(1)
        var loadIndex = 0
        val results = mutableListOf<LibraryLoadResult>()

        allLibs.forEach { lib ->
            val libName = normalizeLibraryName(lib)
            if (excludeLibs.contains(libName) || excludeLibs.contains(lib)) {
                val reason = excludeReasons[libName]
                    ?: excludeReasons[lib]
                    ?: "not required in this launch path"
                listener?.onLog("Skipped native library: $lib")
                trace?.mark("System.load skipped", "$lib - $reason")
                return@forEach
            }

            loadIndex += 1
            val progress = progressStart + ((progressEnd - progressStart) * (loadIndex - 1) / total)
            listener?.onProgress(progress, "Loading Minecraft", "$loadIndex/$total")
            listener?.onLog("Loading native library: $lib")
            trace?.mark("System.load started", lib)

            val result = loadLibraryDetailed(libName)
            results.add(result)
            val detail = formatLoadResult(result)
            trace?.mark(
                if (result.loaded) "System.load finished" else "System.load failed",
                detail
            )
            if (!result.loaded) {
                Log.e(TAG, "Failed to load bundle library $libName: ${result.detail ?: "unknown error"}")
                listener?.onLog("Failed to load native library: ${result.fileName}")
            } else {
                listener?.onLog("Loaded native library: ${result.fileName}")
            }
        }

        return results
    }

    private fun toLibraryFileName(name: String): String {
        return if (name.startsWith("lib") && name.endsWith(".so")) name else "lib${normalizeLibraryName(name)}.so"
    }

    private fun normalizeLibraryName(name: String): String {
        return name.removePrefix("lib").removeSuffix(".so")
    }

    private fun elapsedSince(startedAt: Long): Long {
        return SystemClock.elapsedRealtime() - startedAt
    }

    private fun formatLoadResult(result: LibraryLoadResult): String {
        val status = if (result.loaded) "Loaded" else "Failed to load"
        return buildString {
            append(status)
            append(' ')
            append(result.fileName)
            append(" in ")
            append(result.durationMs)
            append("ms from ")
            append(result.source)
            result.detail?.let {
                append(" - ")
                append(it)
            }
        }
    }

    fun getAssets(): AssetManager = assetManager

    fun getGameStringResource(name: String): String? {
        return try {
            val resources = android.content.res.Resources(
                assetManager,
                context.resources.displayMetrics,
                context.resources.configuration
            )
            val packageName = applicationInfo.packageName ?: MinecraftLauncher.MC_PACKAGE_NAME
            val identifier = resources.getIdentifier(name, "string", packageName)
            if (identifier == 0) null else resources.getString(identifier).takeIf { it.isNotBlank() }
        } catch (error: Exception) {
            null
        }
    }

    fun getPackageContext(): Context = packageContext

    fun getApplicationInfo(): ApplicationInfo = applicationInfo

    fun getVersionName(): String? {
        if (version != null && !version.isInstalled) return version.versionCode
        return try {
            context.packageManager.getPackageInfo(packageContext.packageName, 0).versionName
        } catch (e: Exception) {
            version?.versionCode
        }
    }

    companion object {
        private const val TAG = "GamePackageManager"
        private const val EXTRACTOR_VERSION = 4

        @Volatile
        private var instance: GamePackageManager? = null
        private var lastVersionKey: String? = null

        @JvmStatic
        fun getInstance(context: Context, version: GameVersion? = null): GamePackageManager {
            return getInstance(context, version, null, null)
        }

        @JvmStatic
        fun getInstance(
            context: Context,
            version: GameVersion? = null,
            launchTrace: LaunchTrace? = null,
            progressListener: MinecraftRuntimePreparer.ProgressListener? = null
        ): GamePackageManager {
            return synchronized(this) {
                val newVersionKey = buildVersionKey(version)
                if (instance == null || newVersionKey != lastVersionKey) {
                    instance = GamePackageManager(context.applicationContext, version, launchTrace, progressListener)
                    lastVersionKey = newVersionKey
                }
                instance!!
            }
        }

        private fun buildVersionKey(version: GameVersion?): String {
            if (version == null) return "installed-default"
            return listOf(
                version.isInstalled.toString(),
                version.packageName.orEmpty(),
                version.versionCode.orEmpty(),
                version.directoryName.orEmpty(),
                version.versionDir?.absolutePath.orEmpty()
            ).joinToString("|")
        }

        fun isInitialized() = instance != null
    }
}
