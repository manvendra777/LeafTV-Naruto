package com.leaftv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import android.widget.HorizontalScrollView;
import org.json.JSONArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.widget.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lightweight, fully remote-operated, immersive Android TV launcher. */
public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 300;
    // Keep navigation available at all times; app dock is always visible on Home.
    private static final int BACKGROUND = 0xff101820;
    private final String[] titles = {"Naruto", "Death Note", "Demon Slayer", "Attack on Titan", "Steins;Gate", "Avatar: The Last Airbender", "Breaking Bad", "Game of Thrones", "How I Met Your Mother", "GTA V", "GTA VI", "Valorant", "Ghost of Tsushima"};
    private final String[] groups = {"Anime", "Anime", "Anime", "Anime", "Anime", "Anime", "TV Shows", "TV Shows", "TV Shows", "Games", "Games", "Games", "Games"};
    private final int[] colors = {0xffa45a38, 0xff29283d, 0xff286d6a, 0xff426b83, 0xff6a4e73, 0xff517d8e, 0xff847348, 0xff3e5366, 0xffa26662, 0xff876c79, 0xffd67b8b, 0xff304f67, 0xffa54e46};
    private final Random rng = new Random();
    private SharedPreferences prefs;
    private FrameLayout frame;
    private LinearLayout shell, content, nav;
    private ImageView wallpaperLayer;
    private View wallpaperScrim;
    private String displayedWallpaperKey = null;
    private int wallpaperEpoch = 0;
    private String page = "Home";
    private final HomeImmersion homeImmersion = new HomeImmersion();
    private int current = 0;
    private int currentVariant = 1;
    private final int[] wallpapersPerTitle = {6,6,6,6,6,6,1,1,1,1,6,1,8};
    private final ArrayList<View> controls = new ArrayList<>();
    private final LinkedHashMap<String, ResolveInfo> installed = new LinkedHashMap<>();
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private final ExecutorService wallpaperWorker = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> wallpaperCache = new LruCache<String, Bitmap>(14 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bmp) { return bmp.getByteCount(); }
    };
    private final int white = Color.WHITE;
    private static final String HOME_ORDER_KEY = "home_apps_order_v8"; // Preserve previous saved pins
    private static final String ALL_ORDER_KEY = "all_apps_order_v8";
    private static final String CUSTOM_SORT_KEY = "all_apps_custom_sort_v8";
    private long lastAppScan = 0;
    private final HashMap<String, Drawable.ConstantState> iconStates = new HashMap<>();
    private final HashMap<String, String> appNames = new HashMap<>();
    private LinearLayout topAppsRow;
    private View firstDockControl;
    private final ExecutorService usageWorker = Executors.newSingleThreadExecutor();
    private final HashMap<String, Long> tvForegroundMs = new HashMap<>();
    private boolean usageRefreshInFlight = false;
    private long lastUsageRefresh = 0;


    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("leaf_settings", MODE_PRIVATE);
        detectAvailableWallpapers();
        initializeBundleSelection();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        // One Activity and one wallpaper background; the dock and all panels overlay it.
        frame = new FrameLayout(this);
        frame.setBackgroundColor(BACKGROUND);
        wallpaperLayer = new ImageView(this);
        wallpaperLayer.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(wallpaperLayer, new FrameLayout.LayoutParams(-1, -1));
        wallpaperScrim = new View(this);
        frame.addView(wallpaperScrim, new FrameLayout.LayoutParams(-1, -1));
        shell = vertical();
        frame.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        chooseWallpaper(true);
        updateWallpaper();
        render();
    }
    @Override protected void onResume() {
        super.onResume();
        refreshTvUsage();
    }
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        page = "Home";
        homeImmersion.reset();
        shell.setVisibility(View.VISIBLE);
        wallpaperScrim.setVisibility(View.VISIBLE);
        chooseWallpaper(false);
        render();
    }
    @Override protected void onDestroy() {
        wallpaperWorker.shutdownNow();
        usageWorker.shutdownNow();
        super.onDestroy();
    }
    private int dp(float n) { return (int)(getResources().getDisplayMetrics().density * n + 0.5f); }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    /** Local, zero-network artwork treatments for real installed-app icons. */
    private GradientDrawable tileSurface(boolean focused, boolean homeCard) {
        // Keep Home translucent so the artwork remains visible between smaller cards.
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                homeCard ? (focused ? new int[]{0xb53b7063, 0x9f18332f} :
                                      new int[]{0x7323303c, 0x79202b37}) :
                focused ? new int[]{0xf5223e3c, 0xf411232e} :
                          new int[]{0xd823303c, 0xd60b1420});
        d.setCornerRadius(dp(homeCard ? 14 : 18));
        d.setStroke(dp(focused ? 2 : 1), focused ? 0xff91ffd0 : 0x667b95a4);
        return d;
    }
    private GradientDrawable appArtwork(String pkg, boolean focused) {
        // Consistent muted jewel tones, stable across launches and app ordering.
        final int[][] palettes = {
            {0xff345e73, 0xff182a46}, {0xff6f405d, 0xff2a2647},
            {0xff356f5d, 0xff17383e}, {0xff6d533d, 0xff302536},
            {0xff485f91, 0xff202b48}, {0xff75564c, 0xff38263c}
        };
        int index = (pkg.hashCode() & 0x7fffffff) % palettes.length;
        int[] choice = palettes[index];
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                focused ? new int[]{lighten(choice[0], 0.17f), choice[1]} : choice);
        d.setCornerRadius(dp(14));
        d.setStroke(dp(1), focused ? 0x99c2ffe3 : 0x337fa4b1);
        return d;
    }
    private int lighten(int color, float fraction) {
        return Color.rgb(
            Math.min(255, (int)(Color.red(color) + (255-Color.red(color))*fraction)),
            Math.min(255, (int)(Color.green(color) + (255-Color.green(color))*fraction)),
            Math.min(255, (int)(Color.blue(color) + (255-Color.blue(color))*fraction)));
    }
    private TextView txt(String text, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(text); t.setTextColor(white); t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL); if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD); return t;
    }
    private LinearLayout vertical() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private void add(LinearLayout p, View v, int w, int h) {
        p.addView(v, new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)));
    }
    /** Each selection is a concrete asset, not a generated gradient. */
    private String assetId(int title, int variant) {
        return String.format(Locale.US, "%02d_%02d", title, variant);
    }
    private String assetPath(String id) { return "wallpapers/w" + id + ".jpg"; }
    private String thumbPath(String id) { return "wallpaper_thumbs/w" + id + ".jpg"; }
    /** Only shipped assets may be selected or shuffled. Empty slots must never become gradients. */
    private final LinkedHashSet<String> availableWallpapers = new LinkedHashSet<>();
    private void detectAvailableWallpapers() {
        availableWallpapers.clear();
        try {
            for (String name : getAssets().list("wallpapers")) {
                if (name.matches("w[0-9]{2}_[0-9]{2}\\.jpg")) {
                    String id = name.substring(1, 6);
                    String[] n = id.split("_");
                    int title = Integer.parseInt(n[0]), variant = Integer.parseInt(n[1]);
                    if (title >= 0 && title < wallpapersPerTitle.length && variant >= 1 && variant <= wallpapersPerTitle[title])
                        availableWallpapers.add(id);
                }
            }
        } catch (Exception ignored) { }
        if (availableWallpapers.isEmpty()) {
            // Legacy v6 drawable is packaged with this app and is always usable.
            availableWallpapers.add("00_01");
        }
    }
    /** Migrate saved per-wallpaper selections into selected TITLE bundles exactly once.
     * Each selected title automatically activates every bundled image for that title. */
    private void initializeBundleSelection() {
        if (prefs.contains(WALLPAPER_BUNDLES_KEY)) return;
        Set<String> previousPictures = prefs.getStringSet("wallpapers_v7", null);
        Set<String> previousTitles = prefs.getStringSet("wallpapers", null);
        Set<String> selected = WallpaperBundles.migrate(previousPictures, previousTitles,
                availableWallpapers, titles.length);
        prefs.edit().putStringSet(WALLPAPER_BUNDLES_KEY, selected).apply();
    }
    private static final String WALLPAPER_BUNDLES_KEY = "wallpaper_bundles_v13";
    /** Two-digit title IDs, rather than individual image IDs. */
    private Set<String> selectedBundles() {
        Set<String> stored = prefs.getStringSet(WALLPAPER_BUNDLES_KEY, null);
        if (stored == null) {
            initializeBundleSelection();
            stored = prefs.getStringSet(WALLPAPER_BUNDLES_KEY, Collections.emptySet());
        }
        HashSet<String> valid = new HashSet<>();
        for (String asset : availableWallpapers) {
            String title = WallpaperBundles.titleForAsset(asset);
            if (stored.contains(title)) valid.add(title);
        }
        if (valid.isEmpty() && !availableWallpapers.isEmpty())
            valid.add(WallpaperBundles.titleForAsset(availableWallpapers.iterator().next()));
        return valid;
    }
    /** Materialize the active rotation pool from all images inside selected bundles. */
    private Set<String> enabled() {
        return WallpaperBundles.assetsForBundles(selectedBundles(), availableWallpapers);
    }
    private int assetCountForBundle(int titleIndex) {
        int n = 0;
        String prefix = String.format(Locale.US, "%02d_", titleIndex);
        for (String id : availableWallpapers) if (id.startsWith(prefix)) n++;
        return n;
    }
    private String firstAssetForBundle(int titleIndex) {
        String prefix = String.format(Locale.US, "%02d_", titleIndex);
        for (String id : availableWallpapers) if (id.startsWith(prefix)) return id;
        return null;
    }
    private void chooseWallpaper(boolean firstStart) {
        String mode = prefs.getString("rotation", "render");
        if ("fixed".equals(mode)) {
            current = prefs.getInt("fixed_title", prefs.getInt("fixed", 0));
            currentVariant = prefs.getInt("fixed_variant", 1);
            if (!availableWallpapers.contains(assetId(current, currentVariant))) {
                String safe = availableWallpapers.iterator().next();
                String[] parts = safe.split("_");
                current = Integer.parseInt(parts[0]);
                currentVariant = Integer.parseInt(parts[1]);
            }
            return;
        }
        if (!firstStart && "startup".equals(mode)) return;
        // First pick a selected TITLE uniformly; then pick one wallpaper inside it.
        // A title with eight images must not dominate a title with one image.
        Set<String> activeImages = enabled();
        TreeMap<String, ArrayList<String>> byTitle = new TreeMap<>();
        for (String id : activeImages) {
            String title = WallpaperBundles.titleForAsset(id);
            ArrayList<String> wallpapers = byTitle.get(title);
            if (wallpapers == null) { wallpapers = new ArrayList<>(); byTitle.put(title, wallpapers); }
            wallpapers.add(id);
        }
        if (byTitle.isEmpty()) return;
        String last = prefs.getString("last_wallpaper_asset", "");
        String lastBundle = WallpaperBundles.titleForAsset(last);
        ArrayList<String> choices = new ArrayList<>(byTitle.keySet());
        if (choices.size() > 1) choices.remove(lastBundle);
        String bundle = choices.get(rng.nextInt(choices.size()));
        ArrayList<String> options = byTitle.get(bundle);
        if (options.size() > 1) options.remove(last);
        String choice = options.get(rng.nextInt(options.size()));
        String[] parts = choice.split("_");
        current = Integer.parseInt(parts[0]);
        currentVariant = Integer.parseInt(parts[1]);
        prefs.edit().putString("last_wallpaper_asset", choice).apply();
    }
    private GradientDrawable gradientFor(int i) {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{colors[Math.max(0, Math.min(i, colors.length-1))], 0xff14222a, 0xff090f18});
    }
    private String wallpaperKey() {
        String uri = prefs.getString("custom_uri", "");
        if (prefs.getBoolean("use_custom", false) && "fixed".equals(prefs.getString("rotation", "render")) && !uri.isEmpty())
            return "uri:" + uri;
        return "asset:" + assetPath(assetId(current, currentVariant));
    }
    /** Built-ins are already bundled as optimized 1080p JPEGs: no networking,
     *  preview-to-full swap, or panel replacement at runtime. */
    private void updateWallpaper() {
        final String key = wallpaperKey();
        if (key.equals(displayedWallpaperKey)) return;
        displayedWallpaperKey = key;
        final int epoch = ++wallpaperEpoch;
        Bitmap cached = wallpaperCache.get(key);
        if (cached != null && !cached.isRecycled()) { wallpaperLayer.setImageBitmap(cached); return; }
        // Never decode 1080p artwork on the UI thread. Retain the previous bitmap
        // until the new one is ready, avoiding a blank or flickering frame.
        wallpaperWorker.execute(() -> {
            Bitmap loaded = decodeWallpaper(key);
            if (loaded == null && key.startsWith("asset:")) {
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inPreferredConfig = Bitmap.Config.RGB_565;
                loaded = BitmapFactory.decodeResource(getResources(), R.drawable.naruto_wallpaper, opt);
            }
            final Bitmap ready = loaded;
            mainThread.post(() -> {
                if (ready == null) return;
                if (isFinishing() || isDestroyed()) { ready.recycle(); return; }
                wallpaperCache.put(key, ready);
                if (epoch == wallpaperEpoch && key.equals(displayedWallpaperKey))
                    wallpaperLayer.setImageBitmap(ready);
            });
        });
    }
    private Bitmap decodeWallpaper(String key) {
        try {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inPreferredConfig = Bitmap.Config.RGB_565;
            opt.inScaled = false;
            if (key.startsWith("asset:")) {
                try (InputStream input = getAssets().open(key.substring(6))) {
                    return BitmapFactory.decodeStream(input, null, opt);
                }
            }
            if (!key.startsWith("uri:")) return null;
            Uri uri = Uri.parse(key.substring(4));
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                opt.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, opt);
            }
            int w = Math.max(1, getResources().getDisplayMetrics().widthPixels);
            int h = Math.max(1, getResources().getDisplayMetrics().heightPixels);
            int sample = 1;
            while (opt.outWidth/(sample*2) >= w && opt.outHeight/(sample*2) >= h) sample *= 2;
            opt.inJustDecodeBounds = false;
            opt.inSampleSize = sample;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(is, null, opt);
            }
        } catch (Exception e) { return null; }
    }
    private Bitmap thumbnail(String id) {
        try (InputStream is = getAssets().open(thumbPath(id))) {
            BitmapFactory.Options opt = new BitmapFactory.Options(); opt.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(is, null, opt);
        } catch (Exception e) {
            // Naruto preview was packaged as a drawable before the v7 asset migration.
            if (id.startsWith("00_")) return BitmapFactory.decodeResource(getResources(), R.drawable.naruto_preview);
            return null;
        }
    }
    /**
     * A single launcher canvas. Home is a background with a quick-launch dock;
     * Apps, Favorites, Settings and the wallpaper library are translucent overlays.
     * Switching an overlay never replaces or decodes the underlying wallpaper.
     */
    private void render() {
        controls.clear();
        topAppsRow = null;
        firstDockControl = null;
        shell.removeAllViews();
        updateWallpaper();
        wallpaperScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x25050b10, 0x00050b10, 0x24050b10}));
        shell.setPadding(dp(26), dp(11), dp(26), dp(14));

        // No launcher wordmark or title at the top: let the wallpaper breathe.
        // Only an unobtrusive group of navigation chips floats at top right.
        nav = new LinearLayout(this);
        nav.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(Color.TRANSPARENT);
        nav.setPadding(0, dp(2), dp(4), dp(2));
        add(shell, nav, -1, 59);
        View freeSpace = new View(this);
        nav.addView(freeSpace, new LinearLayout.LayoutParams(0, dp(1), 1));
        String[] navigation = {"Home", "Apps", "Favorites", "Settings"};
        String[] labels = {"⌂  Home", "▦  All Apps", "☆  Pinned", "⚙  Settings"};
        for (int i = 0; i < navigation.length; i++) {
            final String destination = navigation[i];
            final boolean selected = page.equals(destination) ||
                ("Favorites".equals(destination) && "Customize".equals(page)) ||
                ("Apps".equals(destination) && "CustomizeAll".equals(page)) ||
                ("Settings".equals(destination) && "Wallpapers".equals(page));
            TextView tab = button(labels[i], () -> {
                if (page.equals(destination)) return;
                page = destination;
                if ("Home".equals(destination)) chooseWallpaper(false);
                render();
            });
            tab.setTextSize(14);
            tab.setPadding(dp(19), dp(8), dp(19), dp(8));
            tab.setTextColor(selected ? 0xffc7ffdf : 0xffeef6f8);
            GradientDrawable navBg = new GradientDrawable();
            navBg.setColor(selected ? 0xe8235146 : 0xc516222d);
            navBg.setCornerRadius(dp(25));
            navBg.setStroke(dp(1), selected ? 0xff8ff6c0 : 0x556c8b92);
            tab.setBackground(navBg);
            tab.setOnFocusChangeListener((view, focused) -> {
                GradientDrawable chip = new GradientDrawable();
                chip.setColor(focused ? 0xf52a6554 : selected ? 0xe8235146 : 0xc516222d);
                chip.setCornerRadius(dp(25));
                chip.setStroke(dp(focused ? 2 : 1),
                        focused ? 0xffb2ffda : selected ? 0xff8ff6c0 : 0x556c8b92);
                tab.setBackground(chip);
                tab.setTextColor(focused || selected ? 0xffd4ffe5 : 0xffeef6f8);
                view.animate().cancel();
                view.animate().scaleX(focused ? 1.045f : 1f)
                    .scaleY(focused ? 1.045f : 1f).setDuration(120).start();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(45));
            lp.setMargins(dp(5), 0, dp(5), 0);
            nav.addView(tab, lp);
        }

        // This body, the dock and any overlay are ALL children of the same screen.
        FrameLayout body = new FrameLayout(this);
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        addHomeDock(body);

        if (!"Home".equals(page)) {
            // A light scrim makes overlay text readable without hiding the wallpaper.
            View shade = new View(this);
            shade.setBackgroundColor(0x44030a10);
            body.addView(shade, new FrameLayout.LayoutParams(-1, -1));
            shade.setOnClickListener(v -> { page = "Home"; render(); });

            LinearLayout panel = vertical();
            panel.setBackground(bg(0xc5132028, 20));
            panel.setPadding(dp(22), dp(14), dp(22), dp(15));
            FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -1);
            panelParams.setMargins(dp(32), dp(8), dp(32), dp(10));
            body.addView(panel, panelParams);

            LinearLayout header = new LinearLayout(this);
            header.setGravity(Gravity.CENTER_VERTICAL);
            add(panel, header, -1, 48);
            TextView panelLabel = txt("Wallpapers".equals(page) ? "Choose your background" :
                "CustomizeAll".equals(page) ? "Reorder All Apps" :
                "Customize".equals(page) ? "Customize Home" : page, 21, true);
            header.addView(panelLabel, new LinearLayout.LayoutParams(0, -1, 1));
            TextView close = button("✕ Close", () -> { page = "Home"; render(); });
            header.addView(close, new LinearLayout.LayoutParams(-2, dp(41)));

            content = vertical();
            panel.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
            int overlayStart = controls.size();
            if ("Apps".equals(page)) apps(false);
            else if ("Favorites".equals(page)) apps(true);
            else if ("Customize".equals(page)) customizeHome();
            else if ("CustomizeAll".equals(page)) customizeAll();
            else if ("Settings".equals(page)) settings();
            else if ("Wallpapers".equals(page)) wallpapers();
            // First focus a relevant control inside the overlay, never the Home tab.
            if (controls.size() > overlayStart) controls.get(overlayStart).requestFocus();
            else close.requestFocus();
        } else {
            // Home immediately allows remote navigation to the app dock.
            View quick = firstDockControl;
            if (quick != null) quick.requestFocus();
            else if (!controls.isEmpty()) controls.get(0).requestFocus();
        }
    }

    /** Home contains ONLY explicitly pinned applications, in their saved order. */
    private void addHomeDock(FrameLayout body) {
        loadApps();
        ArrayList<String> pinned = homeOrder();
        LinearLayout dock = vertical();
        GradientDrawable dockSurface = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x750a1420, 0x920a1019});
        dockSurface.setCornerRadius(dp(18));
        dockSurface.setStroke(dp(1), 0x497c9d9c);
        dock.setBackground(dockSurface);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        dock.setPadding(dp(12), dp(8), dp(12), dp(10));
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(-1, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockLp.leftMargin = dp(26);
        dockLp.rightMargin = dp(26);
        dockLp.bottomMargin = dp(12);
        body.addView(dock, dockLp);

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        add(dock, titleBar, -1, 26);
        TextView caption = txt("HOME", 10, true);
        caption.setTextColor(0xffc9e0da);
        titleBar.addView(caption, new LinearLayout.LayoutParams(0, -1, 1));
        TextView edit = button("✎  Edit", () -> { page = "Customize"; render(); });
        edit.setTextSize(11);
        titleBar.addView(edit, new LinearLayout.LayoutParams(-2, dp(27)));
        edit.setFocusable("Home".equals(page));
        firstDockControl = edit;
        if (pinned.isEmpty()) {
            TextView empty = button("No pinned apps — open All Apps to choose some", () -> {
                page = "Apps"; render();
            });
            add(dock, empty, -1, 60);
            return;
        }
        HorizontalScrollView ribbon = new HorizontalScrollView(this);
        ribbon.setHorizontalScrollBarEnabled(false);
        ribbon.setFillViewport(true);
        ribbon.setClipChildren(false);
        ribbon.setClipToPadding(false);
        add(dock, ribbon, -1, -2);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        ribbon.addView(row, new android.widget.FrameLayout.LayoutParams(-2, -2));
        int shown = 0;
        for (String pkg : pinned) {
            ResolveInfo info = installed.get(pkg);
            if (info == null) continue;
            LinearLayout cell = appTile(info, 44, 102, 96, "home:" + pkg);
            cell.setFocusable("Home".equals(page));
            if (shown++ == 0) firstDockControl = cell;
            cell.setOnClickListener(v -> launch(info));
            attachCustomizeActions(cell, pkg);
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(dp(102), dp(96));
            cellLp.setMargins(dp(3), dp(2), dp(5), dp(2));
            row.addView(cell, cellLp);
            controls.add(cell);
        }
    }

    /** Cohesive TV cards: real app artwork, translucent casing, focus halo, legible titles. */
    private LinearLayout appTile(ResolveInfo info, int iconSize, int cellWidth, int cellHeight, String tag) {
        String pkg = info.activityInfo.packageName;
        final boolean homeCard = tag.startsWith("home:");
        LinearLayout cell = vertical();
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(homeCard ? 5 : 8), dp(homeCard ? 5 : 8),
                dp(homeCard ? 5 : 8), dp(homeCard ? 4 : 7));
        cell.setBackground(tileSurface(false, homeCard));
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setTag(tag);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);

        // Artwork area has a soft brand-like gradient behind each installed app's REAL icon.
        FrameLayout artwork = new FrameLayout(this);
        artwork.setBackground(appArtwork(pkg, false));
        artwork.setClipToOutline(true);
        int artHeight = Math.max(44, cellHeight - (homeCard ? 36 : 46));
        LinearLayout.LayoutParams artworkParams = new LinearLayout.LayoutParams(-1, dp(artHeight));
        artworkParams.bottomMargin = dp(4);
        cell.addView(artwork, artworkParams);

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageDrawable(iconFor(info));
        int size = Math.min(iconSize, Math.max(28, artHeight - 12));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER);
        artwork.addView(icon, iconParams);

        TextView name = txt(nameFor(info), homeCard ? 11 : 13, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setTextColor(0xfff1fafb);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.addView(name, new LinearLayout.LayoutParams(-1, dp(homeCard ? 19 : 23)));

        cell.setOnFocusChangeListener((view, focused) -> {
            cell.setBackground(tileSurface(focused, homeCard));
            artwork.setBackground(appArtwork(pkg, focused));
            name.setTextColor(focused ? 0xffc2ffdc : 0xfff1fafb);
            view.animate().cancel();
            view.animate().scaleX(focused ? 1.065f : 1f)
                .scaleY(focused ? 1.065f : 1f).setDuration(130).start();
            view.setElevation(focused ? dp(15) : dp(1));
        });
        return cell;
    }

    private String nameFor(ResolveInfo info) {
        String pkg = info.activityInfo.packageName;
        String found = appNames.get(pkg);
        if (found != null) return found;
        String name = info.loadLabel(getPackageManager()).toString();
        appNames.put(pkg, name);
        return name;
    }
    private Drawable iconFor(ResolveInfo info) {
        String pkg = info.activityInfo.packageName;
        Drawable.ConstantState cached = iconStates.get(pkg);
        if (cached != null) return cached.newDrawable(getResources()).mutate();
        try {
            Drawable loaded = info.loadIcon(getPackageManager());
            if (loaded != null && loaded.getConstantState() != null)
                iconStates.put(pkg, loaded.getConstantState());
            return loaded;
        } catch (Exception ignored) { return null; }
    }

    private void attachCustomizeActions(View target, String pkg) {
        target.setOnLongClickListener(v -> { showAppActions(pkg); return true; });
        target.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_MENU) {
                showAppActions(pkg); return true;
            }
            return false;
        });
    }

    private TextView button(String label, Runnable action) {
        TextView v = txt(label, 15, true);
        v.setGravity(Gravity.CENTER); v.setPadding(dp(16), dp(8), dp(16), dp(8));
        v.setFocusable(true); v.setClickable(true); v.setBackground(bg(0x8822323c, 12));
        v.setOnClickListener(x -> action.run());
        v.setOnFocusChangeListener((x, has) -> {
            x.setScaleX(has ? 1.04f : 1f); x.setScaleY(has ? 1.04f : 1f);
            v.setTextColor(has ? 0xffbaffcf : Color.WHITE);
            v.setBackground(bg(has ? 0xff335a57 : 0x8822323c, 12));
            x.setElevation(has ? dp(8) : 0);
        });
        controls.add(v); return v;
    }
    private void heading(String title, String sub) {
        TextView t = txt(title, 27, true); add(content, t, -1, 44);
        TextView s = txt(sub, 14, false); s.setTextColor(0xffb6c8c9); add(content, s, -1, 32);
    }
    private void loadApps() {
        // Installed-app scans are relatively expensive on TV. Reuse a recent snapshot
        // when overlays, wallpaper selections and the dock are redrawn.
        if (!installed.isEmpty() && System.currentTimeMillis() - lastAppScan < 30_000) return;
        installed.clear();
        iconStates.clear();
        appNames.clear();
        PackageManager pm = getPackageManager();
        for (String cat : new String[]{Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(cat);
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                if (info.activityInfo != null && !getPackageName().equals(info.activityInfo.packageName))
                    installed.put(info.activityInfo.packageName, info);
            }
        }
        lastAppScan = System.currentTimeMillis();
    }
    private ArrayList<ResolveInfo> alphabeticalApps() {
        loadApps();
        ArrayList<ResolveInfo> apps = new ArrayList<>(installed.values());
        Collections.sort(apps, (a,b) -> {
            int nameOrder = String.CASE_INSENSITIVE_ORDER.compare(nameFor(a), nameFor(b));
            return nameOrder != 0 ? nameOrder : a.activityInfo.packageName.compareTo(b.activityInfo.packageName);
        });
        return apps;
    }
    private ArrayList<String> readOrder(String pref) {
        ArrayList<String> order = new ArrayList<>();
        String json = prefs.getString(pref, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i=0; i<array.length(); i++) {
                String pkg = array.optString(i, "");
                if (!pkg.isEmpty() && !order.contains(pkg)) order.add(pkg);
            }
        } catch (Exception ignored) {}
        return order;
    }
    private void saveOrder(String pref, List<String> pkgs) {
        JSONArray array = new JSONArray();
        for (String pkg : pkgs) array.put(pkg);
        prefs.edit().putString(pref, array.toString()).apply();
    }
    /** On upgrade, existing favorites become the pinned dock; otherwise seed six apps. */
    private ArrayList<String> homeOrder() {
        loadApps();
        if (!prefs.contains(HOME_ORDER_KEY)) {
            Set<String> oldFavorites = prefs.getStringSet("favorites", Collections.emptySet());
            ArrayList<String> initial = new ArrayList<>();
            for (ResolveInfo info : alphabeticalApps()) {
                String pkg = info.activityInfo.packageName;
                if (!oldFavorites.isEmpty() ? oldFavorites.contains(pkg) : initial.size() < 6)
                    initial.add(pkg);
            }
            saveOrder(HOME_ORDER_KEY, AppLayout.seed(initial));
        }
        return AppLayout.filter(readOrder(HOME_ORDER_KEY), installed.keySet());
    }
    private ArrayList<String> orderedAllPackages() {
        ArrayList<String> sorted = new ArrayList<>();
        for (ResolveInfo info : alphabeticalApps()) sorted.add(info.activityInfo.packageName);
        if (!prefs.getBoolean(CUSTOM_SORT_KEY, false)) return sorted;
        return AppLayout.merge(readOrder(ALL_ORDER_KEY), sorted);
    }
    private void editHome(String pkg, int movement) {
        ArrayList<String> pinned = homeOrder();
        if (!installed.containsKey(pkg)) return;
        ArrayList<String> changed = movement == 0
            ? AppLayout.toggle(pinned, pkg) : AppLayout.move(pinned, pkg, movement);
        if (changed.equals(pinned)) return;
        pinned = changed;
        saveOrder(HOME_ORDER_KEY, pinned);
        // Keep legacy Favorites in sync so v7 downgrade does not lose selections.
        prefs.edit().putStringSet("favorites", new HashSet<>(pinned)).apply();
        renderKeepingFocus("pin:" + pkg, "edit:" + pkg, "app:" + pkg, "home:" + pkg);
    }
    private void moveAll(String pkg, int movement) {
        ArrayList<String> order = orderedAllPackages();
        ArrayList<String> changed = AppLayout.move(order, pkg, movement);
        if (changed.equals(order)) return;
        saveOrder(ALL_ORDER_KEY, changed);
        prefs.edit().putBoolean(CUSTOM_SORT_KEY, true).apply();
        renderKeepingFocus("all-edit:" + pkg, "app:" + pkg, "top:" + pkg);
    }
    private void renderKeepingFocus(String... candidates) {
        render();
        for (String tag : candidates) {
            View found = shell.findViewWithTag(tag);
            if (found != null && found.isFocusable()) { found.requestFocus(); return; }
        }
    }
    private void showAppActions(String pkg) {
        ResolveInfo info = installed.get(pkg);
        if (info == null) return;
        ArrayList<String> actions = new ArrayList<>();
        ArrayList<Runnable> callbacks = new ArrayList<>();
        ArrayList<String> pinned = homeOrder();
        int homeIndex = pinned.indexOf(pkg);
        if (homeIndex < 0) {
            actions.add("＋ Add to Home tray");
            callbacks.add(() -> editHome(pkg, 0));
        } else {
            actions.add("Remove from Home (keep in All Apps)");
            callbacks.add(() -> editHome(pkg, 0));
            if (homeIndex > 0) {
                actions.add("← Move left on Home");
                callbacks.add(() -> editHome(pkg, -1));
            }
            if (homeIndex + 1 < pinned.size()) {
                actions.add("Move right on Home →");
                callbacks.add(() -> editHome(pkg, +1));
            }
        }
        if ("Apps".equals(page)) {
            ArrayList<String> all = orderedAllPackages();
            int i = all.indexOf(pkg);
            if (i > 0) {
                actions.add("↑ Move earlier in All Apps");
                callbacks.add(() -> moveAll(pkg, -1));
            }
            if (i >= 0 && i + 1 < all.size()) {
                actions.add("↓ Move later in All Apps");
                callbacks.add(() -> moveAll(pkg, +1));
            }
        }
        new AlertDialog.Builder(this)
            .setTitle("Customize " + nameFor(info))
            .setItems(actions.toArray(new String[0]), (dialog, which) -> callbacks.get(which).run())
            .setNegativeButton("Cancel", null)
            .show();
    }
    private int gridColumns() {
        float widthDp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        return Math.max(4, Math.min(8, (int)((widthDp - 80) / 160)));
    }
    private boolean usageAllowed() {
        try {
            AppOpsManager op = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            return op != null && op.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Exception ignored) { return false; }
    }
    private void refreshTvUsage() {
        if (!usageAllowed() || usageRefreshInFlight ||
                System.currentTimeMillis() - lastUsageRefresh < 120_000) return;
        usageRefreshInFlight = true;
        usageWorker.execute(() -> {
            HashMap<String, Long> times = new HashMap<>();
            try {
                UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                if (manager != null) {
                    long now = System.currentTimeMillis();
                    Map<String, UsageStats> data = manager.queryAndAggregateUsageStats(now - 30L * 86400000L, now);
                    if (data != null) for (Map.Entry<String, UsageStats> record : data.entrySet())
                        times.put(record.getKey(), record.getValue().getTotalTimeInForeground());
                }
            } catch (Exception ignored) {}
            mainThread.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                usageRefreshInFlight = false;
                tvForegroundMs.clear();
                tvForegroundMs.putAll(times);
                lastUsageRefresh = System.currentTimeMillis();
                if ("Apps".equals(page) && topAppsRow != null) fillTopAppsRow(topAppsRow);
            });
        });
    }
    private long scoreFor(String pkg) {
        long opened = prefs.getInt("launches:" + pkg, 0);
        long tvMs = tvForegroundMs.containsKey(pkg) ? tvForegroundMs.get(pkg) : 0;
        return tvMs + opened * 10L * 60L * 1000L;
    }
    private ArrayList<String> topPackages() {
        ArrayList<String> pkgs = orderedAllPackages();
        ArrayList<String> home = homeOrder();
        // Without a launch history, choose the user's pinned apps first.
        Collections.sort(pkgs, (a, b) -> {
            int diff = Long.compare(scoreFor(b), scoreFor(a));
            if (diff != 0) return diff;
            int aHome = home.indexOf(a), bHome = home.indexOf(b);
            if (aHome >= 0 || bHome >= 0) {
                if (aHome < 0) return 1;
                if (bHome < 0) return -1;
                return Integer.compare(aHome, bHome);
            }
            return String.CASE_INSENSITIVE_ORDER.compare(nameFor(installed.get(a)), nameFor(installed.get(b)));
        });
        if (pkgs.size() > 4) pkgs.subList(4, pkgs.size()).clear();
        return pkgs;
    }
    private void fillTopAppsRow(LinearLayout row) {
        View previous = getCurrentFocus();
        Object focusTag = previous == null ? null : previous.getTag();
        row.removeAllViews();
        for (String pkg : topPackages()) {
            ResolveInfo info = installed.get(pkg);
            if (info == null) continue;
            LinearLayout tile = appTile(info, 56, 166, 117, "top:" + pkg);
            tile.setOnClickListener(v -> launch(info));
            attachCustomizeActions(tile, pkg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(166), dp(117));
            lp.setMargins(dp(3), dp(3), dp(11), dp(3));
            row.addView(tile, lp);
            controls.add(tile);
        }
        if (focusTag != null && focusTag.toString().startsWith("top:")) {
            View match = row.findViewWithTag(focusTag);
            if (match != null) match.requestFocus();
        }
    }
    private void apps(boolean favoritesOnly) {
        heading(favoritesOnly ? "Pinned to Home" : "All Apps",
                "OK opens app · Focus its Add/Remove button to customize Home · Menu for reorder");
        loadApps();
        if (!favoritesOnly) {
            TextView usageText = txt(usageAllowed()
                ? "TOP 4 APPS   ·   Based on TV usage"
                : "QUICK PICKS   ·   Recently launched apps", 14, true);
            usageText.setTextColor(0xffc9e0da);
            add(content, usageText, -1, 29);
            topAppsRow = new LinearLayout(this);
            topAppsRow.setGravity(Gravity.CENTER_VERTICAL);
            add(content, topAppsRow, -1, 128);
            fillTopAppsRow(topAppsRow);
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        add(content, actions, -1, 50);
        TextView edit = button("✎ Customize Home tray  (" + homeOrder().size() + " pinned)", () -> {
            page = "Customize"; render();
        });
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-2, dp(45));
        ep.rightMargin = dp(12);
        actions.addView(edit, ep);
        if (!favoritesOnly) {
            TextView sort = button("Sort: " + (prefs.getBoolean(CUSTOM_SORT_KEY, false) ? "Custom" : "A–Z") + "  ↻", () -> {
                boolean custom = prefs.getBoolean(CUSTOM_SORT_KEY, false);
                if (!custom && !prefs.contains(ALL_ORDER_KEY)) saveOrder(ALL_ORDER_KEY, orderedAllPackages());
                prefs.edit().putBoolean(CUSTOM_SORT_KEY, !custom).apply();
                renderKeepingFocus("apps:sort");
            });
            sort.setTag("apps:sort");
            actions.addView(sort, new LinearLayout.LayoutParams(-2, dp(45)));
            TextView reorder = button("↑↓ Reorder All Apps", () -> { page = "CustomizeAll"; render(); });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, dp(45));
            rp.leftMargin = dp(10);
            actions.addView(reorder, rp);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        GridLayout grid = new GridLayout(this);
        int cols = gridColumns();
        grid.setColumnCount(cols);
        grid.setPadding(dp(2), dp(12), dp(2), dp(20));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        scroll.addView(grid);
        int available = (int)(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density) - 70;
        int cellW = Math.max(105, (available - 100) / cols - 9);
        ArrayList<String> ordered = favoritesOnly ? homeOrder() : orderedAllPackages();
        Set<String> pinned = new HashSet<>(homeOrder());
        for (String pkg : ordered) {
            ResolveInfo info = installed.get(pkg);
            if (info == null) continue;
            // Separate focusable siblings are reliable with TV D-pad focus navigation:
            // a focusable button nested within a focusable app tile was ambiguous.
            LinearLayout card = vertical();
            card.setPadding(dp(2), dp(2), dp(2), dp(2));
            LinearLayout tile = appTile(info, 58, cellW, 131, "app:" + pkg);
            tile.setOnClickListener(v -> launch(info));
            attachCustomizeActions(tile, pkg);
            card.addView(tile, new LinearLayout.LayoutParams(-1, dp(131)));
            TextView pinButton = button(pinned.contains(pkg) ? "✓  PINNED  ·  REMOVE" : "+  ADD TO HOME", () -> editHome(pkg, 0));
            pinButton.setTextSize(11);
            pinButton.setBackground(bg(pinned.contains(pkg) ? 0xc4316350 : 0xc4233b49, 12));
            pinButton.setTag("pin:" + pkg);
            LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(-1, dp(37));
            pinLp.topMargin = dp(5);
            card.addView(pinButton, pinLp);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(cellW);
            params.height = dp(185);
            params.setMargins(dp(3), dp(3), dp(6), dp(8));
            grid.addView(card, params);
            controls.add(tile);
        }
        if (grid.getChildCount() == 0) {
            TextView empty = txt(favoritesOnly
                ? "Your Home tray is empty. Open All Apps and hold OK to pin apps."
                : "No launchable apps found.", 17, false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(530);
            params.height = dp(88);
            grid.addView(empty, params);
        }
    }
    /** Large, remote-friendly row editor for the Home dock. */
    private void customizeHome() {
        heading("Customize Home", "Move apps with ← / → buttons, or remove them. Removed apps remain in All Apps.");
        TextView choose = button("＋ Add apps from All Apps", () -> { page = "Apps"; render(); });
        add(content, choose, -1, 49);
        ScrollView scroll = new ScrollView(this);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout rows = vertical();
        rows.setPadding(dp(3), dp(5), dp(3), dp(14));
        scroll.addView(rows);
        ArrayList<String> pinned = homeOrder();
        if (pinned.isEmpty()) {
            TextView empty = txt("No apps pinned yet. Add apps from All Apps to build your Home tray.", 17, false);
            add(rows, empty, -1, 85);
            return;
        }
        for (int i=0; i<pinned.size(); i++) {
            String pkg = pinned.get(i);
            ResolveInfo info = installed.get(pkg);
            if (info == null) continue;
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(10), dp(5), dp(10), dp(5));
            line.setBackground(bg(0x9923333d, 13));
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-1, dp(69));
            lineLp.bottomMargin = dp(8);
            rows.addView(line, lineLp);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(iconFor(info));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            line.addView(icon, new LinearLayout.LayoutParams(dp(43), dp(43)));
            TextView label = txt((i + 1) + ".  " + nameFor(info), 16, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
            lp.leftMargin = dp(16);
            line.addView(label, lp);
            if (i > 0) {
                TextView left = button("← Left", () -> editHome(pkg, -1));
                line.addView(left, new LinearLayout.LayoutParams(dp(90), dp(48)));
                left.setTag("edit:" + pkg);
            }
            if (i+1 < pinned.size()) {
                TextView right = button("Right →", () -> editHome(pkg, +1));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(100), dp(48));
                rp.leftMargin = dp(6);
                line.addView(right, rp);
                if (i == 0) right.setTag("edit:" + pkg);
            }
            TextView remove = button("✕ Remove", () -> editHome(pkg, 0));
            LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(dp(115), dp(48));
            xp.leftMargin = dp(6);
            line.addView(remove, xp);
        }
    }
    /** Explicit D-pad editor for the whole apps grid: no long-press needed. */
    private void customizeAll() {
        heading("Reorder All Apps", "Move apps ↑ / ↓. Custom order is saved automatically; unpinning never removes apps here.");
        TextView done = button("✓ Back to All Apps", () -> { page = "Apps"; render(); });
        add(content, done, -1, 48);
        ScrollView scroll = new ScrollView(this);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout rows = vertical();
        scroll.addView(rows);
        ArrayList<String> order = orderedAllPackages();
        for (int i = 0; i < order.size(); i++) {
            String pkg = order.get(i);
            ResolveInfo info = installed.get(pkg);
            if (info == null) continue;
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setBackground(bg(0x9923333d, 13));
            line.setPadding(dp(12), dp(5), dp(12), dp(5));
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-1, dp(65));
            lineLp.bottomMargin = dp(7);
            rows.addView(line, lineLp);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(iconFor(info));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            line.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
            TextView label = txt((i + 1) + ".  " + nameFor(info), 16, true);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -1, 1);
            labelLp.leftMargin = dp(12);
            line.addView(label, labelLp);
            if (i > 0) {
                TextView up = button("↑ Up", () -> moveAll(pkg, -1));
                up.setTag("all-edit:" + pkg);
                line.addView(up, new LinearLayout.LayoutParams(dp(110), dp(46)));
            }
            if (i + 1 < order.size()) {
                TextView down = button("↓ Down", () -> moveAll(pkg, +1));
                if (i == 0) down.setTag("all-edit:" + pkg);
                LinearLayout.LayoutParams downLp = new LinearLayout.LayoutParams(dp(110), dp(46));
                downLp.leftMargin = dp(7);
                line.addView(down, downLp);
            }
        }
    }
    private void launch(ResolveInfo info) {
        String pkg = info.activityInfo.packageName;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(pkg, info.activityInfo.name);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            recordLaunch(pkg);
        } catch (Exception ex) {
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) { startActivity(intent); recordLaunch(pkg); }
                else Toast.makeText(this, "Unable to open app", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) { Toast.makeText(this, "Unable to open app", Toast.LENGTH_SHORT).show(); }
        }
    }
    private void recordLaunch(String pkg) {
        String key = "launches:" + pkg;
        int count = prefs.getInt(key, 0);
        prefs.edit().putInt(key, count >= 1_000_000 ? count : count + 1)
            .putLong("launch_last:" + pkg, System.currentTimeMillis()).apply();
    }
    /** One remote-focusable selection per TITLE. All images in the title rotate together. */
    private void wallpapers() {
        heading("Wallpaper bundles · " + titles.length + " titles / " + availableWallpapers.size() + " images",
                "Select a title to include ALL its images in the carousel · Hold OK for only that title");
        LinearLayout filters = new LinearLayout(this); add(content, filters, -1, 52);
        for (String category : new String[]{"Anime", "TV Shows", "Games"}) {
            final String focusTag = "wallpaper-category:" + category;
            TextView categoryButton = button(category, () -> {
                if (category.equals(prefs.getString("category", "Anime"))) return;
                prefs.edit().putString("category", category).apply();
                render();
                View same = shell.findViewWithTag(focusTag);
                if (same != null) same.requestFocus();
            });
            categoryButton.setTag(focusTag);
            categoryButton.setBackground(bg(category.equals(prefs.getString("category", "Anime")) ? 0xff335a57 : 0x8822323c, 12));
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-2, dp(44));
            fp.rightMargin = dp(9); filters.addView(categoryButton, fp);
        }
        ScrollView scroll = new ScrollView(this);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        GridLayout grid = new GridLayout(this);
        int cols = Math.max(3, gridColumns() - 1);
        grid.setColumnCount(cols);
        grid.setPadding(0, dp(12), 0, dp(12)); scroll.addView(grid);
        int availableWidth = (int)(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density) - 70;
        int width = Math.max(155, (availableWidth - 100) / cols - 12);
        Set<String> selected = selectedBundles();
        String category = prefs.getString("category", "Anime");
        for (int i = 0; i < titles.length; i++) {
            if (!groups[i].equals(category)) continue;
            final int titleIndex = i;
            final String bundle = String.format(Locale.US, "%02d", i);
            final int imageCount = assetCountForBundle(i);
            final String coverId = firstAssetForBundle(i);
            if (imageCount == 0 || coverId == null) continue;
            boolean checked = selected.contains(bundle);
            LinearLayout tile = vertical();
            tile.setPadding(dp(7), dp(7), dp(7), dp(9));
            tile.setBackground(bg(checked ? 0xff31524a : 0x99213139, 14));
            tile.setFocusable(true); tile.setClickable(true);
            FrameLayout preview = new FrameLayout(this);
            preview.setBackground(gradientFor(i));
            Bitmap picture = thumbnail(coverId);
            if (picture != null) {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageBitmap(picture);
                preview.addView(image, new FrameLayout.LayoutParams(-1, -1));
            }
            TextView marker = txt(checked ? "✓" : "+", 16, true);
            marker.setGravity(Gravity.CENTER);
            marker.setBackground(bg(0xbb142c2c, 10));
            FrameLayout.LayoutParams m = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.RIGHT);
            m.setMargins(0, dp(5), dp(5), 0); preview.addView(marker, m);
            tile.addView(preview, new LinearLayout.LayoutParams(-1, dp(94)));
            TextView label = txt(titles[i], 14, true);
            label.setMaxLines(1); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(label, new LinearLayout.LayoutParams(-1, dp(27)));
            TextView countLabel = txt(imageCount + (imageCount == 1 ? " wallpaper" : " wallpapers") + " · one bundle", 12, false);
            countLabel.setTextColor(0xffc7d7d6);
            tile.addView(countLabel, new LinearLayout.LayoutParams(-1, dp(23)));
            tile.setOnFocusChangeListener((v, focused) -> {
                v.setBackground(bg(focused ? 0xff3c7566 : selectedBundles().contains(bundle) ? 0xff31524a : 0x99213139, 14));
                v.setScaleX(focused ? 1.035f : 1f); v.setScaleY(focused ? 1.035f : 1f);
            });
            tile.setOnClickListener(v -> {
                Set<String> next = new HashSet<>(selectedBundles());
                if (next.contains(bundle)) {
                    if (next.size() <= 1) {
                        Toast.makeText(this, "Keep at least one wallpaper bundle selected", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    next.remove(bundle);
                } else next.add(bundle);
                prefs.edit().putStringSet(WALLPAPER_BUNDLES_KEY, next)
                    .putString("rotation", "render").putBoolean("use_custom", false).apply();
                boolean active = next.contains(bundle);
                marker.setText(active ? "✓" : "+");
                tile.setBackground(bg(tile.isFocused() ? 0xff3c7566 : active ? 0xff31524a : 0x99213139, 14));
                if (!enabled().contains(assetId(current, currentVariant))) {
                    chooseWallpaper(false);
                    updateWallpaper();
                }
                Toast.makeText(this, titles[titleIndex] + (active ? " bundle selected" : " bundle removed"), Toast.LENGTH_SHORT).show();
            });
            tile.setOnLongClickListener(v -> {
                Set<String> only = new HashSet<>(); only.add(bundle);
                prefs.edit().putStringSet(WALLPAPER_BUNDLES_KEY, only)
                    .putString("rotation", "render").putBoolean("use_custom", false).apply();
                chooseWallpaper(false); updateWallpaper();
                // Redraw category markers after changing the other selected bundles.
                render();
                View same = shell.findViewWithTag("wallpaper-bundle:" + bundle);
                if (same != null) same.requestFocus();
                Toast.makeText(this, "Rotating " + imageCount + " " + titles[titleIndex] + " wallpapers", Toast.LENGTH_SHORT).show();
                return true;
            });
            tile.setTag("wallpaper-bundle:" + bundle);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = dp(width); p.height = dp(160);
            p.setMargins(dp(4), dp(4), dp(8), dp(9)); grid.addView(tile, p); controls.add(tile);
        }
    }
    private void settings() {
        heading("Settings", "Wallpaper library and TV options");
        TextView browse = button("Browse wallpaper library   ›", () -> {
            page = "Wallpapers";
            render();
        });
        LinearLayout.LayoutParams browseLp = new LinearLayout.LayoutParams(-1, dp(54));
        browseLp.bottomMargin = dp(10);
        content.addView(browse, browseLp);
        for (String mode : new String[]{"render", "startup", "fixed"}) {
            String description = mode.equals("render") ? "Shuffle on each Home visit" :
                mode.equals("startup") ? "Shuffle when Home starts" : "Keep the chosen wallpaper";
            TextView b = button((prefs.getString("rotation", "render").equals(mode) ? "●  " : "○  ") + description, () -> {
                SharedPreferences.Editor e = prefs.edit().putString("rotation", mode);
                if ("fixed".equals(mode)) e.putInt("fixed_title", current).putInt("fixed_variant", currentVariant);
                else e.putBoolean("use_custom", false);
                e.apply(); render();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(51)); p.bottomMargin = dp(8);
            content.addView(b, p);
        }
        TextView custom = button("Use a custom background image", () -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT); pick.setType("image/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(pick, PICK_IMAGE);
        }); add(content, custom, -1, 55);
        TextView tvSettings = button("Android TV Settings", () -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception e) { Toast.makeText(this, "Settings unavailable", Toast.LENGTH_SHORT).show(); }
        }); add(content, tvSettings, -1, 55);
        TextView usage = button(usageAllowed()
            ? "✓ TV-wide app usage enabled"
            : "Enable TV-wide app usage for accurate Top Apps", () -> {
            try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
            catch (Exception ignored) { Toast.makeText(this,
                "Use ADB: adb shell appops set com.leaftv.launcher GET_USAGE_STATS allow",
                Toast.LENGTH_LONG).show(); }
        });
        add(content, usage, -1, 55);
        TextView note = txt("" + availableWallpapers.size() + " of 55 planned wallpapers inside APK. Choose titles as bundles; Ghost of Tsushima supports up to eight images; only bundled files are shown. App pins and ordering stay on this TV.", 13, false);
        note.setTextColor(0xffb6c8c9); add(content, note, -1, 72);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_IMAGE && result == RESULT_OK && data != null && data.getData() != null) {
            try { getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) {}
            prefs.edit().putString("custom_uri", data.getData().toString()).putBoolean("use_custom", true)
                .putInt("fixed", 0).putString("rotation", "fixed").apply();
            current = 0; currentVariant = 1; page = "Home"; render();
        }
    }
    /** First Back on Home = 100% unobstructed wallpaper; next Back = Home.
     * Neither action selects a new image or reconstructs the launcher.
     * Back in an overlay retains the usual overlay-to-Home behavior. */
    private void setWallpaperOnly(boolean showWallpaperOnly) {
        if (showWallpaperOnly) {
            shell.clearFocus();
            shell.setVisibility(View.GONE);
            wallpaperScrim.setVisibility(View.GONE);
        } else {
            shell.setVisibility(View.VISIBLE);
            wallpaperScrim.setVisibility(View.VISIBLE);
            if (firstDockControl != null) firstDockControl.requestFocus();
        }
    }
    @Override public void onBackPressed() {
        if ("Home".equals(page)) {
            setWallpaperOnly(homeImmersion.toggle());
            return;
        }
        homeImmersion.reset();
        if ("Wallpapers".equals(page)) page = "Settings";
        else if ("Customize".equals(page)) page = "Favorites";
        else if ("CustomizeAll".equals(page)) page = "Apps";
        else { page = "Home"; chooseWallpaper(false); }
        render();
    }
}
