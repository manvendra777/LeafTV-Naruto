package com.leaftv.home

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.Locale

/** Public GitHub-hosted wallpaper browser. Images are only downloaded when chosen. */
class WallpaperLibraryActivity : Activity() {
    private data class Wallpaper(val path: String, val category: String, val title: String) {
        val url: String get() = "https://raw.githubusercontent.com/manvendra777/LeafTV-Naruto/main/" +
            path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val filename: String get() = path.substringAfterLast('/')
    }

    private val pool = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())
    private val items = ArrayList<Wallpaper>()
    private var filter = "All"
    private lateinit var status: TextView
    private lateinit var grid: android.widget.GridLayout
    private lateinit var scroll: ScrollView
    private lateinit var tabs: LinearLayout
    private var generation = 0
    private val base = Color.rgb(10, 14, 22)

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun shape(fill: Int, radius: Int, line: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            setStroke(dp(1).coerceAtLeast(1), line)
        }
    private fun label(s: String, size: Float = 15f, color: Int = Color.WHITE) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
    }
    private fun action(name: String, onClick: () -> Unit) = TextView(this).apply {
        text = name; textSize = 14f; setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(9), dp(16), dp(9))
        isClickable = true; isFocusable = true
        background = shape(Color.rgb(34, 42, 57), 12, Color.rgb(76, 91, 113))
        setOnFocusChangeListener { v, focused ->
            v.background = shape(if (focused) Color.rgb(51, 75, 108) else Color.rgb(34, 42, 57), 12,
                if (focused) Color.WHITE else Color.rgb(76, 91, 113))
        }
        setOnClickListener { onClick() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(base)
            setPadding(dp(34), dp(23), dp(34), dp(18))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("Online Wallpaper Library", 25f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(action("Refresh") { loadCatalog(true) })
        header.addView(action("Back") { finish() }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) })
        root.addView(header)
        root.addView(label("Your GitHub repository · Preview, download, or set wallpaper. Downloaded images work offline.", 12f,
            Color.rgb(182, 195, 211)).apply { setPadding(0, dp(5), 0, dp(12)) })
        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tabs)
        status = label("Connecting to GitHub…", 13f, Color.rgb(180, 194, 211)).apply {
            setPadding(0, dp(12), 0, dp(10))
        }
        root.addView(status)
        scroll = ScrollView(this).apply {
            isFillViewport = true; isVerticalScrollBarEnabled = false
            clipToPadding = true
        }
        grid = android.widget.GridLayout(this).apply {
            columnCount = 4
            alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        scroll.addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        renderTabs()
        loadCatalog(false)
    }
    private fun renderTabs() {
        tabs.removeAllViews()
        for (name in listOf("All", "Anime", "Games", "TV Shows")) {
            tabs.addView(action(name) {
                filter = name; renderTabs(); renderGrid()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply {
                marginEnd = dp(10)
            })
        }
    }
    private fun fetch(url: String, limit: Int): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9000; readTimeout = 14000
            setRequestProperty("User-Agent", "LeafTV-Wallpaper-Library/1.0")
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            val output = ByteArrayOutputStream()
            conn.inputStream.use { stream ->
                val buffer = ByteArray(32768)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    if (output.size() + n > limit) throw IllegalStateException("Image exceeds ${limit / 1_000_000} MB limit")
                    output.write(buffer, 0, n)
                }
            }
            return output.toByteArray()
        } finally { conn.disconnect() }
    }
    private fun loadCatalog(force: Boolean) {
        status.text = "Checking GitHub wallpaper folders…"
        val cache = File(cacheDir, "leaftv-github-wallpaper-tree.json")
        pool.execute {
            try {
                val bytes = if (!force && cache.isFile() && System.currentTimeMillis() - cache.lastModified() < 15 * 60_000L)
                    cache.readBytes() else fetch(
                    "https://api.github.com/repos/manvendra777/LeafTV-Naruto/git/trees/main?recursive=1", 6_000_000
                ).also { cache.writeBytes(it) }
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                if (json.optBoolean("truncated")) throw IllegalStateException("GitHub catalog is truncated")
                val tree = json.getJSONArray("tree")
                val result = ArrayList<Wallpaper>()
                for (i in 0 until tree.length()) {
                    val obj = tree.getJSONObject(i)
                    if (obj.optString("type") != "blob") continue
                    val path = obj.optString("path")
                    if (!path.startsWith("wallpapers/") && !path.startsWith("app/src/main/assets/wallpapers/")) continue
                    if (!Regex("(?i)\\.(jpg|jpeg|png|webp)$").containsMatchIn(path)) continue
                    // Prefer explicit online library categories, but support older asset locations too.
                    val tail = path.substringAfter("wallpapers/")
                    val top = tail.substringBefore('/').lowercase(Locale.ROOT)
                    val category = when {
                        top == "anime" || top.startsWith("anime__") -> "Anime"
                        top == "games" || top.startsWith("games__") -> "Games"
                        top == "tv shows" || top == "tv_shows" || top == "shows" || top.startsWith("tv__") -> "TV Shows"
                        else -> "Other"
                    }
                    val display = tail.substringBeforeLast('/', tail.substringAfterLast('/'))
                    val title = if (display == tail) tail.substringAfterLast('/') else display
                    result.add(Wallpaper(path, category, title.replace('_', ' ')))
                }
                result.sortWith(compareBy<Wallpaper> { it.category }.thenBy { it.title }.thenBy { it.filename })
                ui.post {
                    if (isFinishing || isDestroyed) return@post
                    items.clear(); items.addAll(result)
                    status.text = if (items.isEmpty())
                        "No images are published on GitHub yet. Add JPG/PNG files to wallpapers/Anime, wallpapers/Games or wallpapers/TV Shows on your repo."
                        else "${items.size} online wallpapers · Select a thumbnail to preview, download, or set as Home wallpaper."
                    renderGrid()
                }
            } catch (error: Exception) {
                ui.post {
                    if (!isFinishing && !isDestroyed) {
                        status.text = "Cannot load GitHub library: ${error.message}. Check Wi-Fi and repository visibility, then Refresh."
                    }
                }
            }
        }
    }
    private fun renderGrid() {
        generation++
        val g = generation
        grid.removeAllViews()
        scroll.scrollTo(0, 0)
        val subset = items.filter { filter == "All" || it.category == filter }
        if (subset.isEmpty()) return
        val width = resources.displayMetrics.widthPixels
        val cellWidth = ((width - dp(68 + 3 * 12)) / 4).coerceAtLeast(dp(150))
        for ((index, item) in subset.withIndex()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true; isClickable = true
                background = shape(Color.rgb(24, 31, 44), 15, Color.rgb(53, 67, 84))
                setPadding(dp(5), dp(5), dp(5), dp(7))
                setOnClickListener { openDetails(item) }
                setOnFocusChangeListener { v, focused ->
                    v.background = shape(if (focused) Color.rgb(38, 57, 80) else Color.rgb(24, 31, 44), 15,
                        if (focused) Color.WHITE else Color.rgb(53, 67, 84))
                }
            }
            val poster = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = shape(Color.rgb(39, 47, 61), 12)
                clipToOutline = true
            }
            card.addView(poster, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(122)))
            card.addView(label(item.title, 12f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(6), dp(5), dp(6), 0) })
            val params = android.widget.GridLayout.LayoutParams().apply {
                this.width = cellWidth - dp(12); this.height = dp(163)
                rowSpec = android.widget.GridLayout.spec(index / 4)
                columnSpec = android.widget.GridLayout.spec(index % 4)
                setMargins(0, 0, dp(12), dp(12))
            }
            grid.addView(card, params)
            pool.execute {
                try {
                    val bmp = scaled(fetch(item.url, 15_000_000), 512)
                    ui.post { if (g == generation && card.isAttachedToWindow && bmp != null) poster.setImageBitmap(bmp) }
                } catch (_: Exception) { }
            }
        }
    }
    private fun scaled(bytes: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    private fun openDetails(item: Wallpaper) {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(10))
        }
        val preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(20, 25, 36))
        }
        wrap.addView(preview, LinearLayout.LayoutParams(dp(680), dp(325)))
        val info = label("Checking resolution…", 13f)
        wrap.addView(info)
        val dialog = AlertDialog.Builder(this).setTitle(item.title)
            .setView(wrap)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Download") { _, _ -> download(item, false) }
            .setPositiveButton("Set wallpaper") { _, _ -> download(item, true) }.create()
        dialog.show()
        pool.execute {
            try {
                val bytes = fetch(item.url, 20_000_000)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val ok = bounds.outWidth >= 1920 && bounds.outHeight >= 1080 &&
                    bounds.outWidth.toDouble() / bounds.outHeight.toDouble() in 1.6..2.1
                val bmp = scaled(bytes, 1280)
                ui.post {
                    if (dialog.isShowing) {
                        if (bmp != null) preview.setImageBitmap(bmp)
                        info.text = "${bounds.outWidth} × ${bounds.outHeight} · " +
                            (if (ok) "Full HD or better · Ready" else "Low resolution or not widescreen · Not recommended")
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = ok
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = ok
                    }
                }
            } catch (e: Exception) {
                ui.post { if (dialog.isShowing) {
                    info.text = "Preview failed: ${e.message}"
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
                } }
            }
        }
    }
    private fun download(item: Wallpaper, setNow: Boolean) {
        Toast.makeText(this, "Downloading ${item.filename}…", Toast.LENGTH_SHORT).show()
        pool.execute {
            try {
                val bytes = fetch(item.url, 20_000_000)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth < 1920 || bounds.outHeight < 1080 ||
                    bounds.outWidth.toDouble() / bounds.outHeight.toDouble() !in 1.6..2.1)
                    throw IllegalArgumentException("Only 16:9 Full HD or larger images are accepted")
                val root = getExternalFilesDir("wallpapers") ?: throw IllegalStateException("Storage unavailable")
                val category = if (item.category == "Other") "Other" else item.category
                val dir = File(root, category)
                if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Cannot create wallpaper folder")
                val safeName = item.path.substringAfter("wallpapers/")
                    .replace('/', '_').replace(Regex("[^a-zA-Z0-9_.-]"), "_")
                val target = File(dir, safeName)
                val temp = File(dir, ".$safeName.part")
                FileOutputStream(temp).use { it.write(bytes) }
                if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
                if (setNow) {
                    val selected = File(root, "Online Selected")
                    selected.mkdirs()
                    selected.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
                    File(selected, "current.${item.filename.substringAfterLast('.', "jpg").lowercase(Locale.ROOT)}")
                        .writeBytes(bytes)
                    getSharedPreferences("leaftv", MODE_PRIVATE).edit()
                        .putString("pack", "Online Selected (device)")
                        .putInt("wpIndex", 0).apply()
                }
                ui.post {
                    Toast.makeText(this, if (setNow) "Wallpaper set!" else "Saved offline in $category", Toast.LENGTH_LONG).show()
                    if (setNow) {
                        startActivity(Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                }
            } catch (e: Exception) {
                ui.post { Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
    override fun onDestroy() {
        generation++
        pool.shutdownNow()
        super.onDestroy()
    }
}
