package com.leaftv.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Collections
import java.util.Random
import java.util.concurrent.Executors

class Skin(val name: String, val colors: IntArray, val accent: Int)

val SKINS = listOf(
    Skin("Hidden Leaf", intArrayOf(Color.parseColor("#1B1209"), Color.parseColor("#B3460C"), Color.parseColor("#FFA94D")), Color.parseColor("#FF922B")),
    Skin("Akatsuki", intArrayOf(Color.parseColor("#0B0B0F"), Color.parseColor("#4A0A12"), Color.parseColor("#C0182A")), Color.parseColor("#E03131")),
    Skin("Survey Corps", intArrayOf(Color.parseColor("#0F1A14"), Color.parseColor("#2F5D45"), Color.parseColor("#9DB8A5")), Color.parseColor("#51CF66")),
    Skin("Demon Slayer", intArrayOf(Color.parseColor("#07211F"), Color.parseColor("#0E6B5C"), Color.parseColor("#7FD6B6")), Color.parseColor("#20C997")),
    Skin("Night Sky", intArrayOf(Color.parseColor("#05060F"), Color.parseColor("#1B2A6B"), Color.parseColor("#6C8CFF")), Color.parseColor("#4C6EF5"))
)

class AppEntry(val label: String, val pkg: String, val cls: String, val icon: Bitmap)
class Pack(val name: String, val items: List<String>)
class MenuItem(val label: String, val value: String? = null, val selected: Boolean = false, val action: () -> Unit)
class MenuScreen(val title: String, val build: () -> List<MenuItem>)

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var bgA: ImageView
    private lateinit var bgB: ImageView
    private lateinit var scrim: View
    private lateinit var ui: FrameLayout
    private lateinit var row: LinearLayout
    private lateinit var tray: HorizontalScrollView
    private lateinit var overlay: FrameLayout
    private lateinit var panel: LinearLayout

    private var frontIsA = true
    private var skinIdx = 0
    private var showAll = false
    private var wpIndex = 0
    private var hasWallpaper = false
    private var currentPack = ""
    private var intervalSec = 30
    private var uiVisible = true
    private var lastFocusPkg: String? = null

    private var appCache: List<AppEntry>? = null
    private val iconCache = LruCache<String, Bitmap>(256)
    private val menuStack = ArrayList<MenuScreen>()

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val rotate = object : Runnable {
        override fun run() {
            nextWallpaper()
            scheduleRotation()
        }
    }
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            appCache = null
            if (uiVisible && menuStack.isEmpty()) rebuildTray()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private val skin: Skin get() = SKINS[skinIdx]

    // ================= Lifecycle =================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("leaftv", MODE_PRIVATE)
        skinIdx = prefs.getInt("skin", 0).coerceIn(0, SKINS.size - 1)
        showAll = prefs.getBoolean("showAll", false)
        intervalSec = prefs.getInt("interval", 30)
        wpIndex = prefs.getInt("wpIndex", 0)
        currentPack = prefs.getString("pack", "") ?: ""
        buildUi()

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(pkgReceiver, f)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        rebuildTray()
        if (!hasWallpaper) nextWallpaper()
        scheduleRotation()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(rotate)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)          // HOME pressed: bring controls back
        menuStack.clear()
        overlay.visibility = View.GONE
        setUi(true)
        tray.smoothScrollTo(0, 0)
        lastFocusPkg = null
        focusTray()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(pkgReceiver) } catch (e: Exception) { }
        io.shutdownNow()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    // ================= UI =================

    private fun buildUi() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        bgA = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        bgB = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
        root.addView(bgA, FrameLayout.LayoutParams(match, match))
        root.addView(bgB, FrameLayout.LayoutParams(match, match))

        // Light scrim so the wallpaper colours stay visible
        scrim = View(this)
        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(60, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(130, 0, 0, 0))
        )
        root.addView(scrim, FrameLayout.LayoutParams(match, match))

        ui = FrameLayout(this)
        ui.clipChildren = false
        root.addView(ui, FrameLayout.LayoutParams(match, match))

        val clockBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clockBox.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(8f, 0f, 2f, Color.argb(160, 0, 0, 0))
        })
        clockBox.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setShadowLayer(8f, 0f, 2f, Color.argb(160, 0, 0, 0))
        })
        ui.addView(clockBox, FrameLayout.LayoutParams(wrap, wrap, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(48); topMargin = dp(26)
        })

        // Compact translucent app tray
        row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            setPadding(dp(14), dp(14), dp(2), dp(14))
        }
        tray = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat(); setColor(Color.argb(70, 0, 0, 0))
            }
            addView(row, FrameLayout.LayoutParams(wrap, wrap))
        }
        ui.addView(tray, FrameLayout.LayoutParams(match, wrap, Gravity.BOTTOM).apply {
            leftMargin = dp(36); rightMargin = dp(36); bottomMargin = dp(22)
        })

        // Menu overlay (right-hand panel)
        overlay = FrameLayout(this)
        overlay.setBackgroundColor(Color.argb(100, 0, 0, 0))
        overlay.visibility = View.GONE
        overlay.isClickable = true
        overlay.setOnClickListener { closeMenuTop() }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        val panelScroll = ScrollView(this).apply {
            setBackgroundColor(Color.argb(238, 14, 14, 18))
            isClickable = true
            addView(panel, FrameLayout.LayoutParams(match, wrap))
        }
        overlay.addView(panelScroll, FrameLayout.LayoutParams(dp(400), match, Gravity.END))
        root.addView(overlay, FrameLayout.LayoutParams(match, match))

        setContentView(root)
        hideSystemBars()
    }

    private fun setUi(visible: Boolean) {
        uiVisible = visible
        val vis = if (visible) View.VISIBLE else View.INVISIBLE
        ui.visibility = vis
        scrim.visibility = vis
        if (visible) focusTray()
    }

    private fun focusTray() {
        row.post {
            if (!uiVisible || menuStack.isNotEmpty() || row.childCount == 0) return@post
            var target: View? = null
            for (i in 0 until row.childCount) {
                if (row.getChildAt(i).tag == lastFocusPkg) target = row.getChildAt(i)
            }
            if (target == null) target = row.getChildAt(if (row.childCount > 1) 1 else 0)
            target?.requestFocus()
        }
    }

    // ================= Apps =================

    private fun toBitmap(d: Drawable, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(b))
        return b
    }

    private fun apps(): List<AppEntry> {
        appCache?.let { return it }
        val pm = packageManager
        fun query(cat: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
        var res = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        if (showAll || res.isEmpty()) res = res + query(Intent.CATEGORY_LAUNCHER)
        val list = res
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                val stamp = try { pm.getPackageInfo(pkg, 0).lastUpdateTime } catch (e: Exception) { 0L }
                val key = "$pkg@$stamp"
                val icon = iconCache.get(key) ?: toBitmap(ri.loadIcon(pm), dp(44)).also { iconCache.put(key, it) }
                AppEntry(ri.loadLabel(pm).toString(), pkg, ri.activityInfo.name, icon)
            }
        appCache = list
        return list
    }

    private fun pins(): MutableList<String> =
        (prefs.getString("pins", "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()

    private fun savePins(l: List<String>) = prefs.edit().putString("pins", l.joinToString(",")).apply()

    private fun orderedApps(): List<AppEntry> {
        val all = apps()
        val pinned = pins().mapNotNull { p -> all.firstOrNull { it.pkg == p } }
        val rest = all.filter { a -> pinned.none { it.pkg == a.pkg } }.sortedBy { it.label.lowercase() }
        return pinned + rest
    }

    private fun rebuildTray() {
        row.removeAllViews()
        val menuBadge = badge("\u2261")
        row.addView(tile("Menu", menuBadge, "__menu__", { openMenu(rootMenu()) }, null))
        val pinnedSet = pins().toSet()
        for (app in orderedApps()) {
            val iv = ImageView(this).apply {
                setImageBitmap(app.icon)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            }
            row.addView(tile(app.label, iv, app.pkg, { launch(app) }, { openMenu(appMenu(app)) }, app.pkg in pinnedSet))
        }
        focusTray()
    }

    private fun badge(letter: String): TextView = TextView(this).apply {
        text = letter
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(skin.accent) }
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }

    private fun rounded(color: Int, radius: Int = 14): GradientDrawable =
        GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(color) }

    private fun accentA(alpha: Int): Int {
        val a = skin.accent
        return Color.argb(alpha, Color.red(a), Color.green(a), Color.blue(a))
    }

    // 102 x 96 dp card, 44 dp icon, individual focus zoom
    private fun tile(
        label: String, icon: View, tag: String,
        onClick: () -> Unit, onLong: (() -> Unit)?, pinned: Boolean = false
    ): View {
        val idle = Color.argb(70, 0, 0, 0)
        val focused = accentA(215)
        val v = LinearLayout(this)
        v.tag = tag
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.CENTER
        v.setPadding(dp(6), dp(8), dp(6), dp(6))
        v.isFocusable = true
        v.isClickable = true
        v.background = rounded(idle)
        v.layoutParams = LinearLayout.LayoutParams(dp(102), dp(96)).apply { marginEnd = dp(10) }
        v.addView(icon)
        v.addView(TextView(this).apply {
            text = if (pinned) "\u2022 $label" else label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) }
        })
        v.setOnClickListener { onClick() }
        if (onLong != null) {
            v.isLongClickable = true
            v.setOnLongClickListener { onLong(); true }
        }
        v.setOnFocusChangeListener { view, has ->
            val s = if (has) 1.14f else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(110).start()
            view.background = rounded(if (has) focused else idle)
            if (has) lastFocusPkg = view.tag as? String
        }
        return v
    }

    private fun launch(app: AppEntry) {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).setClassName(app.pkg, app.cls)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) { }
    }

    // ================= Menu overlay =================

    private fun openMenu(s: MenuScreen) { menuStack.add(s); renderMenu() }

    private fun renderMenu() {
        if (menuStack.isEmpty()) return
        val screen = menuStack.last()
        overlay.visibility = View.VISIBLE
        panel.removeAllViews()
        panel.addView(TextView(this).apply {
            text = screen.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, 0, dp(14))
        })
        var first: View? = null
        var sel: View? = null
        for (item in screen.build()) {
            val r = menuRow(item)
            panel.addView(r)
            if (first == null) first = r
            if (item.selected) sel = r
        }
        (sel ?: first)?.let { t -> t.post { t.requestFocus() } }
    }

    private fun menuRow(item: MenuItem): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(14), 0, dp(14), 0)
        r.isFocusable = true
        r.isClickable = true
        r.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(4) }
        r.background = rounded(Color.TRANSPARENT, 10)
        r.addView(TextView(this).apply {
            text = (if (item.selected) "\u25CF  " else "") + item.label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (item.value != null) {
            r.addView(TextView(this).apply {
                text = item.value
                setTextColor(Color.argb(190, 255, 255, 255))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            })
        }
        r.setOnClickListener { item.action() }
        r.setOnFocusChangeListener { view, has ->
            view.background = rounded(if (has) accentA(200) else Color.TRANSPARENT, 10)
        }
        return r
    }

    private fun closeMenuTop(): Boolean {
        if (menuStack.isEmpty()) return false
        menuStack.removeAt(menuStack.size - 1)
        if (menuStack.isEmpty()) { overlay.visibility = View.GONE; focusTray() } else renderMenu()
        return true
    }

    private fun closeAllMenus() {
        menuStack.clear()
        overlay.visibility = View.GONE
        focusTray()
    }

    private fun rootMenu() = MenuScreen("Leaf TV") {
        listOf(
            MenuItem("Wallpaper bundle", currentPackName()) { openMenu(packMenu()) },
            MenuItem("Rotation", intervalLabel()) { openMenu(intervalMenu()) },
            MenuItem("Theme", skin.name) { openMenu(skinMenu()) },
            MenuItem("Next wallpaper") { nextWallpaper(); scheduleRotation() },
            MenuItem("Show all apps", if (showAll) "On" else "Off") {
                showAll = !showAll
                prefs.edit().putBoolean("showAll", showAll).apply()
                appCache = null
                rebuildTray(); renderMenu()
            },
            MenuItem("Clear pinned apps") { savePins(emptyList()); rebuildTray(); renderMenu() },
            MenuItem("Android settings") {
                closeAllMenus()
                try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: ActivityNotFoundException) { }
            }
        )
    }

    private fun packMenu() = MenuScreen("Wallpaper bundle") {
        val name = currentPackName()
        packs().map { p ->
            MenuItem(p.name, "${p.items.size}", p.name == name) {
                currentPack = p.name
                prefs.edit().putString("pack", p.name).apply()
                wpIndex = 0
                nextWallpaper(); scheduleRotation()
                closeMenuTop()
            }
        }
    }

    private fun intervalLabel(): String = when (intervalSec) {
        0 -> "Off"; 15 -> "15 s"; 30 -> "30 s"; 60 -> "1 min"; 300 -> "5 min"; else -> "$intervalSec s"
    }

    private fun intervalMenu() = MenuScreen("Rotation") {
        listOf("Off" to 0, "15 seconds" to 15, "30 seconds" to 30, "1 minute" to 60, "5 minutes" to 300).map { (l, s) ->
            MenuItem(l, null, intervalSec == s) {
                intervalSec = s
                prefs.edit().putInt("interval", s).apply()
                scheduleRotation()
                closeMenuTop()
            }
        }
    }

    private fun skinMenu() = MenuScreen("Theme") {
        SKINS.mapIndexed { i, s ->
            MenuItem(s.name, null, i == skinIdx) {
                skinIdx = i
                prefs.edit().putInt("skin", i).apply()
                appCache = appCache
                rebuildTray()
                if (currentPackName() == "Generated") nextWallpaper()
                closeMenuTop()
            }
        }
    }

    private fun appMenu(app: AppEntry) = MenuScreen(app.label) {
        val p = pins()
        val idx = p.indexOf(app.pkg)
        val l = ArrayList<MenuItem>()
        l.add(MenuItem("Open") { closeAllMenus(); launch(app) })
        if (idx < 0) {
            l.add(MenuItem("Pin to front") { p.add(app.pkg); afterPinChange(p, app.pkg) })
        } else {
            l.add(MenuItem("Unpin") { p.removeAt(idx); afterPinChange(p, app.pkg) })
            if (idx > 0) l.add(MenuItem("Move left") { Collections.swap(p, idx, idx - 1); afterPinChange(p, app.pkg) })
            if (idx < p.size - 1) l.add(MenuItem("Move right") { Collections.swap(p, idx, idx + 1); afterPinChange(p, app.pkg) })
        }
        l.add(MenuItem("App info") {
            closeAllMenus()
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) { }
        })
        l
    }

    private fun afterPinChange(p: List<String>, focusPkg: String) {
        savePins(p)
        lastFocusPkg = focusPkg
        rebuildTray()
        closeAllMenus()
    }

    // ================= Wallpapers =================

    private fun isImg(n: String) = n.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")

    /** Bundles = sub-folders of assets/wallpapers (built into the APK) and of the device folder. */
    private fun packs(): List<Pack> {
        val out = ArrayList<Pack>()
        try {
            val loose = ArrayList<String>()
            for (n in assets.list("wallpapers") ?: emptyArray()) {
                if (isImg(n)) loose.add("asset:wallpapers/$n")
                else {
                    val files = (assets.list("wallpapers/$n") ?: emptyArray())
                        .filter { isImg(it) }.sorted().map { "asset:wallpapers/$n/$it" }
                    if (files.isNotEmpty()) out.add(Pack(n, files))
                }
            }
            if (loose.isNotEmpty()) out.add(Pack("Bundled", loose.sorted()))
        } catch (e: Exception) { }
        try {
            val dir = getExternalFilesDir("wallpapers")
            if (dir != null) {
                dir.mkdirs()
                val loose = ArrayList<String>()
                for (f in (dir.listFiles() ?: emptyArray()).sortedBy { it.name }) {
                    if (f.isFile && isImg(f.name)) loose.add("file:" + f.path)
                    else if (f.isDirectory) {
                        val files = (f.listFiles() ?: emptyArray()).filter { it.isFile && isImg(it.name) }
                            .sortedBy { it.name }.map { "file:" + it.path }
                        if (files.isNotEmpty()) out.add(Pack(f.name + " (device)", files))
                    }
                }
                if (loose.isNotEmpty()) out.add(Pack("My wallpapers", loose))
            }
        } catch (e: Exception) { }
        if (out.isNotEmpty()) out.add(0, Pack("All images", out.flatMap { it.items }))
        out.add(Pack("Generated", (0 until 8).map { "gen:$it" }))
        return out
    }

    private fun currentPackName(): String {
        val names = packs().map { it.name }
        if (currentPack in names) return currentPack
        return if ("All images" in names) "All images" else "Generated"
    }

    private fun scheduleRotation() {
        handler.removeCallbacks(rotate)
        if (intervalSec > 0) handler.postDelayed(rotate, intervalSec * 1000L)
    }

    private fun nextWallpaper() {
        val idx = wpIndex++
        prefs.edit().putInt("wpIndex", wpIndex).apply()
        val s = skin
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        io.execute {
            val bm = try {
                val ps = packs()
                val name = currentPack
                val pack = ps.firstOrNull { it.name == name }
                    ?: ps.firstOrNull { it.name == "All images" } ?: ps.last()
                val src = pack.items[idx % pack.items.size]
                when {
                    src.startsWith("gen:") -> gradient(s, src.substring(4).toInt(), w, h)
                    src.startsWith("asset:") -> decode(w, h) { assets.open(src.substring(6)) }
                    else -> decode(w, h) { FileInputStream(File(src.substring(5))) }
                }
            } catch (t: Throwable) { null }
            if (bm != null) runOnUiThread { if (!isDestroyed) showBitmap(bm) }
        }
    }

    private fun decode(w: Int, h: Int, open: () -> InputStream): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2
        return open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    }

    private fun gradient(s: Skin, variant: Int, w: Int, h: Int): Bitmap {
        val bw = (w / 2).coerceAtLeast(2)
        val bh = (h / 2).coerceAtLeast(2)
        val bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val flip = variant % 2 == 1
        p.shader = LinearGradient(
            if (flip) bw.toFloat() else 0f, 0f, if (flip) 0f else bw.toFloat(), bh.toFloat(),
            s.colors, null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
        p.shader = null
        val rnd = Random((variant * 31 + s.name.hashCode()).toLong())
        repeat(7) {
            p.color = Color.argb(34, Color.red(s.accent), Color.green(s.accent), Color.blue(s.accent))
            c.drawCircle(rnd.nextFloat() * bw, rnd.nextFloat() * bh, bh * (0.1f + rnd.nextFloat() * 0.4f), p)
        }
        return bm
    }

    private fun showBitmap(bm: Bitmap) {
        val front = if (frontIsA) bgA else bgB
        val back = if (frontIsA) bgB else bgA
        back.setImageBitmap(bm)
        back.animate().alpha(1f).setDuration(1200).start()
        front.animate().alpha(0f).setDuration(1200).start()
        frontIsA = !frontIsA
        hasWallpaper = true
    }

    // ================= Keys =================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Wallpaper-only mode: any key (Back = "Back again") restores Home, no wallpaper reload
        if (!uiVisible) {
            if (event.action == KeyEvent.ACTION_DOWN) setUi(true)
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MENU && menuStack.isEmpty()) {
            openMenu(rootMenu())
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        if (closeMenuTop()) return        // normal back through overlays
        setUi(false)                       // Home: first Back = wallpaper only
    }
}
