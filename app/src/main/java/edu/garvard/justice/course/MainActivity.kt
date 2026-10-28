package edu.garvard.justice.course

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Единственная Activity приложения: отображает офлайн-курс (assets/app.html + course_data.json)
 * и предоставляет мост в Android для проверки обновлений и установки новой версии APK.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val executor = Executors.newSingleThreadExecutor()

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != lastDownloadId) return
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                apkTargetName
            )
            installApk(file)
        }
    }
    private var lastDownloadId = -1L

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
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
        webView.setBackgroundColor(getColor(R.color.brand_background))
        webView.addJavascriptInterface(Bridge(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: android.webkit.WebResourceRequest?
            ): Boolean {
                val target = url?.url?.toString() ?: return false
                if (target.startsWith("https://appassets") || target.startsWith("file://")) return false
                // Внешние ссылки открываем в браузере
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        loadCourse()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("(function(){ if(window.viewStack && viewStack.length>1){ back(); return 'in'; } return 'out'; })()") { result ->
                    if (result == null || !result.contains("in")) {
                        finish()
                    }
                }
            }
        })

        registerDownloadReceiver()
    }

    /** Загружаем HTML и встраиваем JSON курса, чтобы приложение работало полностью офлайн. */
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

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadReceiver, filter)
        }
    }

    private fun installApk(file: File) {
        if (!file.exists()) {
            toast("Файл обновления не найден")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            toast("Разрешите установку приложений из этого источника")
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

    private fun toast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    private fun callJs(fn: String, payload: String) {
        val escaped = JSONObject.quote(payload)
        runOnUiThread {
            webView.evaluateJavascript("window.$fn($escaped);", null)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {
        }
        executor.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }

    /** Мост JavaScript -> Android. */
    inner class Bridge {

        @JavascriptInterface
        fun versionInfo(): String {
            return JSONObject().apply {
                put("versionName", BuildConfig.VERSION_NAME)
                put("versionCode", BuildConfig.VERSION_CODE)
                put("applicationId", BuildConfig.APPLICATION_ID)
                put("signature", BuildConfig.SIGNING_KEY_SHA256)
                put("repo", BuildConfig.GITHUB_REPO)
            }.toString()
        }

        @JavascriptInterface
        fun signature(): String = BuildConfig.SIGNING_KEY_SHA256

        /** Проверка последнего релиза на GitHub (в фоновом потоке, результат — в window.__onUpdateResult). */
        @JavascriptInterface
        fun checkUpdate() {
            executor.execute {
                val result = try {
                    fetchLatestRelease()
                } catch (e: Exception) {
                    JSONObject().put("error", e.message ?: "network error").toString()
                }
                callJs("__onUpdateResult", result)
            }
        }

        /** Скачивание APK через DownloadManager; после завершения предлагается установка. */
        @JavascriptInterface
        fun downloadAndInstall(url: String, fileName: String) {
            try {
                val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle("Гарвард: Справедливость")
                    setDescription("Загрузка обновления приложения")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                    setMimeType("application/vnd.android.package-archive")
                }
                lastDownloadId = manager.enqueue(request)
                apkTargetName = safeName
                toast("Загрузка началась")
            } catch (e: Exception) {
                toast("Ошибка загрузки: ${e.message}")
            }
        }
    }

    private var apkTargetName = APK_FILE_NAME

    private fun fetchLatestRelease(): String {
        val api = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"
        val connection = (URL(api).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
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

    /**
     * versionCode берётся из имени тега релиза вида v1.0.42 -> 142,
     * либо из тега с чистым номером сборки.
     */
    private fun parseVersionCode(tag: String): Int {
        val digits = tag.takeLastWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    companion object {
        private const val APK_FILE_NAME = "garvard-justice-course.apk"
    }
}
