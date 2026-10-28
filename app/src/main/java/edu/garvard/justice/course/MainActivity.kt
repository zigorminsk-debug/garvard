package edu.garvard.justice.course

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Единственная Activity приложения: показывает офлайн-курс (assets/app.html + course_data.json)
 * и даёт JavaScript-мост к Android для проверки обновлений и установки новой версии APK
 * поверх предыдущей (тот же applicationId + постоянный ключ подписи).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val executor = Executors.newSingleThreadExecutor()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.setBackgroundColor(getColor(R.color.brand_background))
        webView.addJavascriptInterface(Bridge(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val target = request?.url?.toString() ?: return false
                if (target.startsWith("https://appassets") || target.startsWith("file://")) return false
                if (target.startsWith("mailto:") || target.startsWith("tel:") ||
                    target.startsWith("https://") || target.startsWith("http://")
                ) {
                    openExternally(target)
                    return true
                }
                return false
            }
        }

        loadCourse()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function(){ if (window.viewStack && viewStack.length > 1) { back(); return 'inner'; } return 'root'; })()"
                ) { result ->
                    if (result == null || !result.contains("inner")) finish()
                }
            }
        })
    }

    /** Загружаем HTML и встраиваем JSON курса — приложение работает полностью офлайн. */
    private fun loadCourse() {
        val html = assets.open("app.html").bufferedReader().use { it.readText() }
        val json = assets.open("course_data.json").bufferedReader().use { it.readText() }
        val page = html.replace("__COURSE_DATA__", json)
        webView.loadDataWithBaseURL(
            "https://appassets.androidplatform.net/",
            page,
            "text/html",
            "UTF-8",
            null
        )
    }

    /** Открываем ссылки, почту и телефон во внешних приложениях. */
    private fun openExternally(target: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            val hint = when {
                target.startsWith("mailto:") -> "Не найдено почтовое приложение"
                target.startsWith("tel:") -> "Не найдено приложение для звонков"
                else -> "Не найдено приложение для открытия ссылки"
            }
            toast(hint)
        }
    }

    private fun toast(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun callJs(fn: String, payload: String) {
        val quoted = JSONObject.quote(payload)
        runOnUiThread { webView.evaluateJavascript("window.$fn($quoted);", null) }
    }

    /** Открываем системный установщик APK из приватной папки приложения (через FileProvider). */
    private fun installApk(file: File) {
        if (!file.exists() || file.length() < 10_000) {
            toast("Файл обновления повреждён")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            toast("Разрешите установку из этого источника и повторите")
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("Не удалось открыть установщик: ${e.message}")
        }
    }

    private fun updatesDir(): File {
        val base = getExternalFilesDir(null) ?: filesDir
        return File(base, "updates").apply { mkdirs() }
    }

    /** Скачивание APK с поддержкой редиректов GitHub Releases. */
    @Throws(IOException::class)
    private fun downloadApk(url: String, target: File) {
        var current = URL(url)
        var hops = 0
        while (hops < 8) {
            hops++
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "GarvardJusticeApp")
            }
            when (val code = connection.responseCode) {
                in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrEmpty()) throw IOException("редирект без адреса")
                    current = URL(current, location)
                }
                200 -> {
                    connection.inputStream.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    connection.disconnect()
                    if (target.length() < 10_000) throw IOException("получен пустой файл")
                    return
                }
                else -> {
                    connection.disconnect()
                    throw IOException("HTTP $code")
                }
            }
        }
        throw IOException("слишком много перенаправлений")
    }

    /** Запрос последнего релиза в GitHub (в фоне, результат уходит в window.__onUpdateResult). */
    private fun fetchLatestRelease(): String {
        val api = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"
        val connection = (URL(api).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GarvardJusticeApp")
        }
        try {
            val code = connection.responseCode
            if (code != 200) {
                return JSONObject().put("error", "GitHub API HTTP $code").toString()
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(body)
            val tag = release.optString("tag_name", "")
            val latestCode = parseVersionCode(tag)
            val currentCode = BuildConfig.VERSION_CODE
            val assets = release.optJSONArray("assets")
            var apkUrl = ""
            var apkName = APK_FILE_NAME
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkName = name
                        break
                    }
                }
            }
            val available = apkUrl.isNotEmpty() && (
                if (latestCode > 0) latestCode > currentCode
                else tag.isNotEmpty() && tag != "v${BuildConfig.VERSION_NAME}"
                )
            return JSONObject().apply {
                put("available", available)
                put("currentVersionCode", currentCode)
                put("currentVersionName", BuildConfig.VERSION_NAME)
                put("latestVersionCode", latestCode)
                put("latestVersionName", tag.ifEmpty { "unknown" })
                put("publishedAt", release.optString("published_at", ""))
                put("notes", release.optString("body", ""))
                put("apkUrl", apkUrl)
                put("apkName", apkName)
            }.toString()
        } finally {
            connection.disconnect()
        }
    }

    /** Тег релиза вида v1.0.42 -> 42 (совпадает с versionCode сборки). */
    private fun parseVersionCode(tag: String): Int {
        val digits = tag.takeLastWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    override fun onPause() {
        // Сворачивание / уход в фон: просим интерфейс зафиксировать авто-закладку
        webView.evaluateJavascript(
            "(function(){ if (window.onAppPause) { window.onAppPause(); return 'saved'; } return 'none'; })()",
            null
        )
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }

    /** Мост JavaScript -> Android. */
    inner class Bridge {

        @JavascriptInterface
        fun versionInfo(): String = JSONObject().apply {
            put("versionName", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("applicationId", BuildConfig.APPLICATION_ID)
            put("signature", BuildConfig.SIGNING_KEY_SHA256)
            put("repo", BuildConfig.GITHUB_REPO)
        }.toString()

        @JavascriptInterface
        fun signature(): String = BuildConfig.SIGNING_KEY_SHA256

        /** Резервное хранилище прогресса и закладок (на случай очистки localStorage в WebView). */
        @JavascriptInterface
        fun saveState(json: String) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STATE, json).apply()
        }

        @JavascriptInterface
        fun loadState(): String =
            getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_STATE, "") ?: ""

        /** Синхронизирует системные панели со светлой/тёмной темой интерфейса. */
        @JavascriptInterface
        fun setDark(enabled: Boolean) {
            runOnUiThread {
                window.statusBarColor = getColor(
                    if (enabled) R.color.brand_status_dark else R.color.brand_crimson_dark
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    window.decorView.systemUiVisibility = if (enabled) {
                        window.decorView.systemUiVisibility and
                            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    } else {
                        window.decorView.systemUiVisibility or
                            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    }
                }
            }
        }

        @JavascriptInterface
        fun checkUpdate() {
            executor.execute {
                val result = try {
                    fetchLatestRelease()
                } catch (e: Exception) {
                    JSONObject().put("error", e.message ?: "ошибка сети").toString()
                }
                callJs("__onUpdateResult", result)
            }
        }

        @JavascriptInterface
        fun downloadAndInstall(url: String, fileName: String) {
            toast("Скачиваем обновление…")
            executor.execute {
                try {
                    val target = File(updatesDir(), "garvard-update.apk")
                    if (target.exists()) target.delete()
                    downloadApk(url, target)
                    runOnUiThread { installApk(target) }
                } catch (e: Exception) {
                    toast("Не удалось скачать обновление: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val APK_FILE_NAME = "garvard-justice-course.apk"
        private const val PREFS = "garvard_state"
        private const val KEY_STATE = "state"
    }
}
