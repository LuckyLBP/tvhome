package dev.lucas.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * A quiet home screen for an Android TV. One activity, no layouts, no libraries.
 *
 * Screen (designed at 1920x1080, scaled to the panel):
 *   one of your photographs as the whole background
 *   the time, top left
 *   three glyphs top right: wallpaper, all apps, settings
 *   a glass dock along the bottom with the apps you chose, white logos
 *
 * Remote:
 *   left/right     move along the dock
 *   up             the glyphs top right
 *   OK             open
 *   long OK, MENU  edit a dock item: move left/right, remove, app info
 *   BACK           close a panel; on the home screen itself, nothing
 *   HOME           back here, first dock item
 */
public class MainActivity extends Activity {

    static final String PREFS = "config";
    static final String KEY_APPS = "apps";
    static final String KEY_STAMP = "stamp";
    static final String KEY_WALL = "wallpaper";
    static final String KEY_LAST = "last";
    static final String KEY_MONO = "mono";
    static final String KEY_EVERY = "every";
    static final String SHUFFLE = "shuffle";

    static final String LIVE_TV = "org.droidtv.playtv";        // Philips tuner / sources app
    static final String TV_SETTINGS = "com.android.tv.settings";
    static final String PLAY_STORE = "com.android.vending";

    /** Dock keys that are not packages. */
    static final String K_LIVETV = "livetv", K_STORE = "store", K_INPUT = "input:", K_TVSET = "tvsettings";

    /** Never offered as dock apps; some have their own entry. */
    static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.google.android.apps.tv.launcherx",
            "com.android.tv.settings",
            "org.droidtv.settings",
            PLAY_STORE,
            LIVE_TV));

    // Design metrics at 1920x1080.
    // Header, top left: date and weather on one line, the time large beneath.
    static final int HEAD_LEFT = 96, HEAD_TOP = 58, TIME_SIZE = 136, DATE_SIZE = 32, WX_ICON = 40;
    // Status capsule, top right.
    static final int CAP_RIGHT = 80, CAP_TOP = 66, CAP_BTN = 72, CAP_GLYPH = 32, CAP_PAD = 8;
    // Dock: icons are sized so every app fits, between ICON_MIN and ICON_MAX; the rest of
    // the dock is proportional to the icon size, the way an iPad dock scales.
    static final int DOCK_BOTTOM = 56, DOCK_MAX_W = 1760, ICON_MAX = 128, ICON_MIN = 96, DOCK_FADE = 120;
    static final float GAP_R = 0.25f, PADX_R = 0.22f, PADV_R = 0.20f, POP_R = 0.36f, RADIUS_R = 0.36f, LIFT_R = 0.12f;
    /** Focus growth. Every container leaves at least this much room around a focused item. */
    static final float GROW_DOCK = 1.18f, GROW_GLYPH = 1.08f, GROW_GRID = 1.08f, GROW_CHIP = 1.06f, GROW_PHOTO = 1.06f;
    static final int GRID_PAD = 14, ROW_PAD = 8, STRIP_PAD = 16, BODY_PAD = 16, CHIP_PAD = 10;
    static final int SHEET_LEFT = 160, SHEET_TOP = 100, SHEET_W = 1600, SHEET_H = 880, SHEET_RADIUS = 40;
    static final int GRID_COLS = 7, GRID_W = 206, GRID_H = 196, GRID_ICON = 104;
    static final int BLUR = 8;

    static final int WHITE = 0xFFFFFFFF;
    static final int WHITE_DIM = 0xD9FFFFFF;
    static final int WHITE_FAINT = 0x8CFFFFFF;
    static final int FOCUS_BG = 0x52FFFFFF;
    static final int CHIP_BG = 0x24FFFFFF;
    static final int CHIP_FOCUS = 0x5CFFFFFF;
    static final int GLASS_LIGHT = 0x38FFFFFF;      // dock and capsule: light frosted glass
    static final int SHEET_TINT = 0x8C0B1018;

    private SharedPreferences prefs;
    private PackageManager pm;
    private Wallpapers wallpapers;
    private float scale;
    private int screenW, screenH;

    private FrameLayout root;
    private ImageView wallA, wallB;
    private boolean frontIsA = true;
    private Bitmap bitmapA, bitmapB, blurSmall;
    private TextClock clock;
    private ImageView wxIcon, wifiIcon;
    private TextView wxText;
    private LinearLayout statusBar;
    private Glass statusGlass;
    private Weather weather;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback netCallback;
    private View dockGlassView;
    private int iconPx;
    private final Map<String, Bitmap> iconCache = new HashMap<>();
    private FrameLayout dockBox;
    private HorizontalScrollView dockScroll;
    private LinearLayout dock;
    private Glass dockGlass;
    private FrameLayout overlay;
    private final List<Glass> glasses = new ArrayList<>();
    private final Map<String, Bitmap> monoCache = new HashMap<>();

    private long wallSetAt;
    private String focusedKey;
    private boolean dirty, firstBuild = true;
    private final Random rnd = new Random();
    private int idWifiBtn, idGridBtn;

    private final BroadcastReceiver packageWatcher = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            monoCache.clear();
            iconCache.clear();
            dirty = true;
            if (hasWindowFocus()) rebuildDock();
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        pm = getPackageManager();
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;
        scale = screenW / 1920f;
        wallpapers = new Wallpapers(this, screenW, screenH);
        weather = new Weather(prefs);
        applyExtras(getIntent());

        buildChrome();
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageWatcher, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageWatcher, f);
        }

        loadWallpaper(false);
        rebuildDock();
        showWeather(weather.cached());
        watchNetwork();
        ticker.post(tick);
    }

    /** Every 30 s: Wi-Fi level. Weather refreshes itself at most every 30 minutes. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateNetwork();
            refreshWeather(false);
            ticker.postDelayed(this, 30_000L);
        }
    };

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (overlay.getVisibility() == View.VISIBLE) hideOverlay(false);
        if (applyExtras(intent) || dirty) { rebuildDock(); refreshWeather(true); }
        if (SHUFFLE.equals(prefs.getString(KEY_WALL, SHUFFLE)) && shuffleDue()) loadWallpaper(true);
        dockScroll.smoothScrollTo(0, 0);
        if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
    }

    private boolean shuffleDue() {
        String every = prefs.getString(KEY_EVERY, "10m");
        long age = System.currentTimeMillis() - wallSetAt;
        if (every.equals("visit")) return true;
        if (every.equals("1h")) return age > 3600_000L;
        if (every.equals("never")) return false;
        return age > 600_000L;
    }

    @Override public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has && dirty) rebuildDock();
    }

    @Override protected void onDestroy() {
        ticker.removeCallbacksAndMessages(null);
        try { getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback); } catch (Throwable ignored) { }
        unregisterReceiver(packageWatcher);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) hideOverlay(true);
    }

    // ---------------------------------------------------------------- chrome

    private void buildChrome() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05080F);

        wallA = new ImageView(this);
        wallB = new ImageView(this);
        for (ImageView w : new ImageView[] { wallA, wallB }) {
            w.setScaleType(ImageView.ScaleType.CENTER_CROP);
            root.addView(w, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        wallB.setAlpha(0f);

        // Gentle darkening at the top and bottom, so white text and the dock read on any photo.
        View top = new View(this);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0x73000000, 0x00000000 }));
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(380)));
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] { 0x59000000, 0x00000000 }));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(420));
        bp.gravity = Gravity.BOTTOM;
        root.addView(bottom, bp);

        // Header: "torsdag 22 oktober  [cloud] 12°" above a large time, like a lock screen.
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE, MMMM d");
        style(date, DATE_SIZE, 0xEBFFFFFF);
        date.setSingleLine(true);
        date.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        line.addView(date);
        wxIcon = new ImageView(this);
        LinearLayout.LayoutParams wip = new LinearLayout.LayoutParams(px(WX_ICON), px(WX_ICON));
        wip.leftMargin = px(26);
        line.addView(wxIcon, wip);
        wxText = text("", DATE_SIZE, 0xEBFFFFFF);
        wxText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        wxText.setPadding(px(10), 0, 0, 0);
        line.addView(wxText);
        wxIcon.setVisibility(View.GONE);
        wxText.setVisibility(View.GONE);
        // The clocks set their text only once attached, so a wrap-content header would size
        // itself to the time and squeeze the date and weather; give the line its own width.
        head.addView(line, new LinearLayout.LayoutParams(px(1100), ViewGroup.LayoutParams.WRAP_CONTENT));

        clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        style(clock, TIME_SIZE, WHITE);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setLetterSpacing(-0.02f);
        clock.setIncludeFontPadding(false);
        clock.setShadowLayer(px(18), 0, px(2), 0x40000000);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = px(2);
        clp.leftMargin = -px(6);         // optical alignment: big digits carry side bearing
        head.addView(clock, clp);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(px(1100), ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.leftMargin = px(HEAD_LEFT);
        hp.topMargin = px(HEAD_TOP);
        root.addView(head, hp);

        // Status capsule: Wi-Fi and the app drawer on a small piece of glass.
        statusBar = new LinearLayout(this);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setPadding(px(CAP_PAD), px(CAP_PAD), px(CAP_PAD), px(CAP_PAD));
        statusBar.setClipChildren(false);
        statusBar.setClipToPadding(false);
        statusGlass = new Glass(null, BLUR, 0, 0, px(CAP_BTN / 2f + CAP_PAD), GLASS_LIGHT, px(1.5f));
        statusGlass.setBorderColor(0x66FFFFFF);
        statusGlass.setAdaptive(0x59000000);
        glasses.add(statusGlass);
        statusBar.setBackground(statusGlass);
        idWifiBtn = View.generateViewId();
        idGridBtn = View.generateViewId();
        FrameLayout wifiBtn = glyphButton(idWifiBtn, Glyph.wifi(3), new Runnable() {
            @Override public void run() { openNetwork(); }
        }, null);
        wifiIcon = (ImageView) wifiBtn.getChildAt(0);
        statusBar.addView(wifiBtn);
        statusBar.addView(glyphButton(idGridBtn, new Glyph(Glyph.GRID), new Runnable() {
            @Override public void run() { showOverlay(drawer(false)); }
        }, new Runnable() {
            @Override public void run() { showOverlay(settingsPanel()); }
        }));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.TOP | Gravity.END;
        sp.rightMargin = px(CAP_RIGHT);
        sp.topMargin = px(CAP_TOP);
        root.addView(statusBar, sp);
        trackGlass(statusBar, statusGlass);

        // Dock: floating and centred, sized to its icons. The glass is only the lower part
        // of the dock's box; the space above it is room for a focused icon to rise into.
        dockBox = new FrameLayout(this);
        dockBox.setClipChildren(false);
        dockGlassView = new View(this);
        dockGlass = new Glass(null, BLUR, 0, 0, px(40), GLASS_LIGHT, px(2f));
        dockGlass.setBorderColor(0x8CFFFFFF);
        dockGlass.setAdaptive(0x40000000);
        glasses.add(dockGlass);
        dockGlassView.setBackground(dockGlass);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(170));
        gp.gravity = Gravity.BOTTOM;
        dockBox.addView(dockGlassView, gp);
        trackGlass(dockGlassView, dockGlass);
        dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setClipToPadding(false);
        dockScroll.setHorizontalFadingEdgeEnabled(true);
        dockScroll.setFadingEdgeLength(px(DOCK_FADE));
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        dockScroll.addView(dock, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dockBox.addView(dockScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(px(800), px(220));
        dp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dp.bottomMargin = px(DOCK_BOTTOM);
        root.addView(dockBox, dp);

        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Keeps a glass panel's blur lined up with the photo behind it, wherever it moves. */
    private void trackGlass(final View v, final Glass g) {
        v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int[] at = new int[2];
                view.getLocationOnScreen(at);
                g.setScreenPosition(at[0], at[1]);
            }
        });
    }

    /** A round button for the status capsule. Focused: white disc, dark glyph. */
    private FrameLayout glyphButton(int id, Drawable glyph, final Runnable action, final Runnable longAction) {
        final FrameLayout b = new FrameLayout(this);
        b.setId(id);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setLayoutParams(new LinearLayout.LayoutParams(px(CAP_BTN), px(CAP_BTN)));
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x00FFFFFF);
        b.setBackground(bg);

        final ImageView g = new ImageView(this);
        g.setImageDrawable(glyph);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(px(CAP_GLYPH), px(CAP_GLYPH));
        gp.gravity = Gravity.CENTER;
        b.addView(g, gp);

        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) focusedKey = "top:" + v.getId();
                animateColor(bg, f ? 0xF2FFFFFF : 0x00FFFFFF);
                if (f) g.setColorFilter(0xFF1C1C1E, PorterDuff.Mode.SRC_IN); else g.clearColorFilter();
                scaleTo(v, f ? GROW_GLYPH : 1f, 0);
            }
        });
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { press(v); action.run(); }
        });
        if (longAction != null) {
            b.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { longAction.run(); return true; }
            });
        }
        return b;
    }

    // ---------------------------------------------------------------- network and weather

    private void watchNetwork() {
        try {
            netCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { post(); }
                @Override public void onLost(Network n) { post(); }
                @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { post(); }
                private void post() {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { updateNetwork(); refreshWeather(false); }
                    });
                }
            };
            getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(netCallback);
        } catch (Throwable ignored) { }
        updateNetwork();
    }

    /** Wi-Fi with its signal level, the network symbol for Ethernet, or crossed out offline. */
    private void updateNetwork() {
        Glyph g = previewNetwork();
        if (g != null) { setNetworkGlyph(g); return; }
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            if (c == null) {
                g = new Glyph(Glyph.OFFLINE);
            } else if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                g = new Glyph(Glyph.ETHERNET);
            } else {
                int level = 3;
                try {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    level = WifiManager.calculateSignalLevel(wm.getConnectionInfo().getRssi(), 4);
                } catch (Throwable ignored) { }
                g = Glyph.wifi(Math.max(1, Math.min(3, level)));
            }
        } catch (Throwable t) {
            g = Glyph.wifi(3);
        }
        setNetworkGlyph(g);
    }

    private void setNetworkGlyph(Glyph g) {
        boolean focused = wifiIcon.getParent() != null && ((View) wifiIcon.getParent()).isFocused();
        wifiIcon.setImageDrawable(g);
        if (focused) wifiIcon.setColorFilter(0xFF1C1C1E, PorterDuff.Mode.SRC_IN);
    }

    /**
     * Preview only: the emulator is wired, so scripts/preview.sh can put "wifi:2", "ethernet"
     * or "offline" in files/preview-network.txt to show each state. Never exists on the TV.
     */
    private Glyph previewNetwork() {
        try {
            java.io.File f = new java.io.File(getExternalFilesDir(null), "preview-network.txt");
            if (!f.exists()) return null;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            String v;
            try { v = r.readLine(); } finally { r.close(); }
            if (v == null) return null;
            v = v.trim();
            if (v.startsWith("wifi")) {
                int level = 3;
                int colon = v.indexOf(':');
                if (colon > 0) level = Integer.parseInt(v.substring(colon + 1).trim());
                return Glyph.wifi(Math.max(1, Math.min(3, level)));
            }
            if (v.equals("ethernet")) return new Glyph(Glyph.ETHERNET);
            if (v.equals("offline")) return new Glyph(Glyph.OFFLINE);
        } catch (Throwable ignored) { }
        return null;
    }

    private void openNetwork() {
        Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
        if (pm.resolveActivity(i, 0) == null) i = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        if (pm.resolveActivity(i, 0) == null) { openSettings(); return; }
        start(i);
    }

    private void refreshWeather(boolean force) {
        weather.refresh(force, new Weather.Listener() {
            @Override public void onWeather(Weather.Reading r) { showWeather(r); }
        });
    }

    private void showWeather(Weather.Reading r) {
        if (r == null || weather.mode().equals("off")) {
            wxIcon.setVisibility(View.GONE);
            wxText.setVisibility(View.GONE);
            return;
        }
        boolean appearing = wxText.getVisibility() != View.VISIBLE;
        wxIcon.setImageDrawable(new Glyph(Weather.glyphFor(r.code, r.day)));
        wxText.setText(r.temperature());
        wxIcon.setVisibility(View.VISIBLE);
        wxText.setVisibility(View.VISIBLE);
        if (appearing) {
            for (View v : new View[] { wxIcon, wxText }) {
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(400).start();
            }
        }
    }

    // ---------------------------------------------------------------- config

    /**
     * install.sh can pass "--es apps pkg1,pkg2", "--es city Uppsala" (or auto / off) and
     * "--es stamp 123". The stamp stops a relaunch of the same intent from reapplying it.
     */
    private boolean applyExtras(Intent intent) {
        if (intent == null) return false;
        String stamp = intent.getStringExtra(KEY_STAMP);
        if (stamp != null && stamp.equals(prefs.getString(KEY_STAMP, null))) return false;
        boolean changed = false;
        String apps = intent.getStringExtra(KEY_APPS);
        if (apps != null) {
            if (apps.trim().isEmpty() || apps.trim().equals("auto")) prefs.edit().remove(KEY_APPS).apply();
            else prefs.edit().putString(KEY_APPS, apps.trim()).apply();
            changed = true;
        }
        String city = intent.getStringExtra("city");
        if (city != null) {
            if (city.equals("auto")) weather.setAuto();
            else if (city.equals("off")) weather.setOff();
            else weather.setCity(city);
            changed = true;
        }
        if (changed && stamp != null) prefs.edit().putString(KEY_STAMP, stamp).apply();
        return changed;
    }

    /** Glass icons (white logo on frosted glass) instead of colour icons. */
    private boolean mono() { return prefs.getBoolean(KEY_MONO, false); }

    /**
     * Preview only: the emulator has none of the TV's apps, so scripts/preview.sh can push a
     * list of labels to files/preview-dock.txt. Each becomes a placeholder dock item with
     * its initial as the logo. On the TV the file never exists and this does nothing.
     */
    private List<String> previewLabels() {
        List<String> out = new ArrayList<>();
        try {
            java.io.File f = new java.io.File(getExternalFilesDir(null), "preview-dock.txt");
            if (!f.exists()) return out;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            try {
                String line;
                while ((line = r.readLine()) != null) { line = line.trim(); if (!line.isEmpty()) out.add(line); }
            } finally { r.close(); }
        } catch (Throwable ignored) { }
        return out;
    }

    /** Dock keys in order: package names, or livetv / store / input:<id>. */
    private List<String> dockKeys() {
        List<String> preview = previewLabels();
        if (!preview.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String l : preview) out.add("preview:" + l);
            return out;
        }
        String saved = prefs.getString(KEY_APPS, null);
        String[] wanted = saved != null ? saved.split("[,\\s]+") : Apps.LIST;
        List<String> out = new ArrayList<>();
        for (String k : wanted) {
            if (k.isEmpty() || out.contains(k)) continue;
            if (entryFor(k) != null) out.add(k);
        }
        if (saved == null) {
            for (Map.Entry<String, String> e : installedApps().entrySet()) {
                if (out.size() >= 12) break;
                String p = e.getKey();
                if (out.contains(p)) continue;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                    boolean stock = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                            && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                    if (!stock) out.add(p);
                } catch (PackageManager.NameNotFoundException ignored) { }
            }
        }
        return out;
    }

    private void saveDock(List<String> keys) {
        prefs.edit().putString(KEY_APPS, TextUtils.join(",", keys)).apply();
    }

    /** package -> label for everything launchable on a TV, sorted by label. */
    private Map<String, String> installedApps() {
        TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String cat : new String[] { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER }) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || HIDDEN.contains(p)) continue;
                if (byLabel.containsValue(p)) continue;
                CharSequence l = ri.loadLabel(pm);
                String label = l == null ? p : l.toString();
                while (byLabel.containsKey(label)) label += " ";
                byLabel.put(label, p);
            }
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : byLabel.entrySet()) out.put(e.getValue(), e.getKey().trim());
        return out;
    }

    // ---------------------------------------------------------------- entries

    /** One thing the dock or the drawer can open. */
    private final class Entry {
        final String key, label;
        final Drawable colour;      // the app's own icon, or a Glyph
        final Runnable action;
        Entry(String key, String label, Drawable colour, Runnable action) {
            this.key = key; this.label = label; this.colour = colour; this.action = action;
        }
        boolean isApp() { return !key.equals(K_LIVETV) && !key.equals(K_STORE) && !key.startsWith(K_INPUT) && !key.equals(K_TVSET); }
    }

    private Entry entryFor(final String key) {
        if (key.startsWith("preview:")) {
            // "Label" or "Label|icon": icon names real icon files pushed by preview.sh into
            // files/preview-icons/ (icon.png, or icon_bg.png + icon_fg.png for a two-layer icon).
            String spec = key.substring(8);
            int bar = spec.indexOf('|');
            final String label = bar < 0 ? spec : spec.substring(0, bar);
            Drawable icon = bar < 0 ? null : previewIcon(spec.substring(bar + 1));
            if (icon == null) icon = new AppIcon.Placeholder(label);
            return new Entry(key, label, icon, new Runnable() {
                @Override public void run() { Toast.makeText(MainActivity.this, "Preview: " + label, Toast.LENGTH_SHORT).show(); }
            });
        }
        if (key.equals(K_LIVETV)) {
            if (launchIntent(LIVE_TV) == null) return null;
            return new Entry(key, "Live TV", new Glyph(Glyph.TV), new Runnable() {
                @Override public void run() { launch(LIVE_TV); }
            });
        }
        if (key.equals(K_STORE)) {
            if (launchIntent(PLAY_STORE) == null) return null;
            return new Entry(key, "Play Store", new Glyph(Glyph.STORE), new Runnable() {
                @Override public void run() { launch(PLAY_STORE); }
            });
        }
        if (key.equals(K_TVSET)) {
            return new Entry(key, "TV settings", new Glyph(Glyph.GEAR), new Runnable() {
                @Override public void run() { openSettings(); }
            });
        }
        if (key.startsWith(K_INPUT)) {
            final TvInputInfo in = inputById(key.substring(K_INPUT.length()));
            if (in == null) return null;
            return new Entry(key, inputLabel(in), new Glyph(Glyph.INPUT), new Runnable() {
                @Override public void run() { openInput(in); }
            });
        }
        final String pkg = key;
        if (launchIntent(pkg) == null) return null;
        String label = pkg;
        try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Throwable ignored) { }
        return new Entry(key, label, appIcon(pkg), new Runnable() {
            @Override public void run() { launch(pkg); }
        });
    }

    /** Preview only: a real app icon from files/preview-icons/, legacy or two-layer. */
    private Drawable previewIcon(String name) {
        try {
            java.io.File dir = new java.io.File(getExternalFilesDir(null), "preview-icons");
            java.io.File one = new java.io.File(dir, name + ".png");
            if (one.exists()) {
                return new BitmapDrawable(getResources(), android.graphics.BitmapFactory.decodeFile(one.getAbsolutePath()));
            }
            java.io.File bg = new java.io.File(dir, name + "_bg.png"), fg = new java.io.File(dir, name + "_fg.png");
            if (bg.exists() && fg.exists()) {
                return new android.graphics.drawable.AdaptiveIconDrawable(
                        new BitmapDrawable(getResources(), android.graphics.BitmapFactory.decodeFile(bg.getAbsolutePath())),
                        new BitmapDrawable(getResources(), android.graphics.BitmapFactory.decodeFile(fg.getAbsolutePath())));
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /** Everything on the TV: apps, inputs, Live TV, Play Store, TV settings. */
    private List<Entry> allEntries() {
        List<Entry> out = new ArrayList<>();
        for (String p : installedApps().keySet()) { Entry e = entryFor(p); if (e != null) out.add(e); }
        for (TvInputInfo in : hdmiInputs()) { Entry e = entryFor(K_INPUT + in.getId()); if (e != null) out.add(e); }
        for (String k : new String[] { K_LIVETV, K_STORE, K_TVSET }) { Entry e = entryFor(k); if (e != null) out.add(e); }
        return out;
    }

    /**
     * The dock and drawer icon: the app's own icon in colour, or its logo in white on frosted
     * glass, both in the squircle shape. Built once per app, mode and size.
     */
    private Drawable iconFor(Entry e, int sizePx) {
        boolean glass = mono();
        String ck = e.key + "|" + glass + "|" + sizePx;
        Bitmap b = iconCache.get(ck);
        if (b == null) {
            if (e.colour instanceof Glyph) {
                Glyph g = ((Glyph) e.colour).copy();
                if (glass) b = AppIcon.white(null, g, sizePx);
                else if (e.key.equals(K_TVSET)) b = AppIcon.system(g, 0xFF98989D, 0xFF636366, sizePx);
                else if (e.key.equals(K_STORE)) b = AppIcon.system(g, 0xFF34C759, 0xFF248A3D, sizePx);
                else if (e.key.equals(K_LIVETV)) b = AppIcon.system(g, 0xFF0A84FF, 0xFF0060DF, sizePx);
                else b = AppIcon.system(g, 0xFF48484A, 0xFF2C2C2E, sizePx);
            } else if (glass) {
                Bitmap m = monoCache.get(e.key);
                if (m == null && !monoCache.containsKey(e.key)) {
                    m = Mono.glyph(e.colour, 96);
                    monoCache.put(e.key, m);
                }
                b = AppIcon.white(m, e.colour, sizePx);
            } else {
                b = AppIcon.colour(e.colour, sizePx);
            }
            iconCache.put(ck, b);
        }
        BitmapDrawable d = new BitmapDrawable(getResources(), b);
        d.setFilterBitmap(true);
        return d;
    }

    // ---------------------------------------------------------------- dock

    private void rebuildDock() {
        dirty = false;
        dock.removeAllViews();
        List<Entry> entries = new ArrayList<>();
        for (String k : dockKeys()) { Entry e = entryFor(k); if (e != null) entries.add(e); }
        int n = Math.max(1, entries.size());

        // Icon size so the whole dock fits on screen; the dock scrolls only past ICON_MIN.
        float units = n + GAP_R * (n - 1) + 2 * PADX_R;
        float s = Math.max(ICON_MIN, Math.min(ICON_MAX, DOCK_MAX_W / units));
        iconPx = px(s);
        int gap = px(s * GAP_R), padX = px(s * PADX_R), padV = px(s * PADV_R), pop = px(s * POP_R);
        int glassH = iconPx + 2 * padV;
        int contentW = n * iconPx + (n - 1) * gap + 2 * padX;
        ViewGroup.LayoutParams bl = dockBox.getLayoutParams();
        bl.width = Math.min(contentW, px(DOCK_MAX_W));
        bl.height = glassH + pop;
        dockBox.setLayoutParams(bl);
        ViewGroup.LayoutParams gl = dockGlassView.getLayoutParams();
        gl.height = glassH;
        dockGlassView.setLayoutParams(gl);
        dockGlass.setRadius(Math.min(glassH / 2f, px(s * RADIUS_R + PADV_R * s)));
        dock.setPadding(padX, pop + padV, padX, padV);

        int firstId = View.NO_ID;
        for (int i = 0; i < entries.size(); i++) {
            View v = dockItem(entries.get(i), i, entries.size(), gap);
            if (firstId == View.NO_ID) firstId = v.getId();
            dock.addView(v);
            if (firstBuild) {
                v.setAlpha(0f);
                v.setTranslationY(px(24));
                v.animate().alpha(1f).translationY(0).setStartDelay(80 + i * 30L).setDuration(380)
                        .setInterpolator(new DecelerateInterpolator(1.8f)).start();
            }
        }
        if (firstBuild) {
            dockBox.setAlpha(0f);
            dockBox.setTranslationY(px(40));
            dockBox.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(new DecelerateInterpolator(1.8f)).start();
        }
        firstBuild = false;
        if (entries.isEmpty()) {
            TextView hint = text("Nothing in the dock yet. Press up, open the grid, then Settings.", 28, WHITE_DIM);
            hint.setGravity(Gravity.CENTER);
            bl.width = px(1000);
            dockBox.setLayoutParams(bl);
            dock.addView(hint, new LinearLayout.LayoutParams(px(1000) - 2 * padX, iconPx));
        }
        int downTo = firstId != View.NO_ID ? firstId : idGridBtn;
        for (int j = 0; j < statusBar.getChildCount(); j++) statusBar.getChildAt(j).setNextFocusDownId(downTo);

        if (overlay.getVisibility() == View.VISIBLE) return;
        View v = focusedKey == null ? null : dock.findViewWithTag(focusedKey);
        if (v == null && focusedKey != null && focusedKey.startsWith("top:")) {
            v = root.findViewById(Integer.parseInt(focusedKey.substring(4)));
        }
        if (v == null && dock.getChildCount() > 0) v = dock.getChildAt(0);
        if (v == null) v = statusBar.getChildAt(1);
        if (v != null) v.requestFocus();
    }

    /** One dock icon: no label. Focused, it grows from its base, rises and casts a shadow. */
    private View dockItem(final Entry e, final int index, final int count, int gap) {
        final ImageView t = new ImageView(this);
        t.setId(View.generateViewId());
        t.setTag(e.key);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setContentDescription(e.label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconPx, iconPx);
        if (index > 0) lp.leftMargin = gap;
        t.setLayoutParams(lp);
        t.setScaleType(ImageView.ScaleType.FIT_XY);
        t.setImageDrawable(iconFor(e, iconPx));
        t.setNextFocusUpId(idGridBtn);
        // Colour icons cast a soft shadow that deepens as they rise. Glass icons are see-through,
        // so a shadow would show through them as a dark square: they lift without one.
        final boolean shadow = !mono();
        final float corner = iconPx * 0.225f;
        if (shadow) {
            t.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner); }
            });
            t.setElevation(px(3));
        } else {
            t.setOutlineProvider(null);
        }
        t.setPivotX(iconPx / 2f);
        t.setPivotY(iconPx);

        final float lift = iconPx * LIFT_R;
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) focusedKey = (String) v.getTag();
                v.animate().scaleX(f ? GROW_DOCK : 1f).scaleY(f ? GROW_DOCK : 1f)
                        .translationY(f ? -lift : 0).translationZ(f && shadow ? px(22) : 0)
                        .setDuration(f ? 260 : 200)
                        .setInterpolator(f ? new OvershootInterpolator(1.4f) : new DecelerateInterpolator()).start();
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { press(v); e.action.run(); }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showOverlay(editSheet(e, index, count)); return true; }
        });
        t.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent ev) {
                if (code == KeyEvent.KEYCODE_MENU && ev.getAction() == KeyEvent.ACTION_UP) {
                    showOverlay(editSheet(e, index, count));
                    return true;
                }
                return false;
            }
        });
        return t;
    }

    // ---------------------------------------------------------------- overlay panels

    private void showOverlay(View content) {
        overlay.removeAllViews();
        overlay.addView(content);
        overlay.setBackgroundColor(0x00000000);
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        content.setTranslationY(px(36));
        content.setScaleX(0.98f);
        content.setScaleY(0.98f);
        overlay.animate().alpha(1f).setDuration(200).start();
        content.animate().translationY(0).scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        animateDim(0x00000000, 0x59000000);
        dockBox.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        statusBar.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        View first = content.findViewWithTag("first");
        if (first != null) first.requestFocus(); else content.requestFocus();
    }

    private void hideOverlay(boolean animate) {
        final View content = overlay.getChildCount() > 0 ? overlay.getChildAt(0) : null;
        // Remember where focus came from now: once the panel goes, Android hands focus to the
        // first free button (Wi-Fi), and that button would overwrite focusedKey.
        final String back = focusedKey;
        dockBox.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        statusBar.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        for (int i = glasses.size() - 1; i >= 0; i--) {
            Glass g = glasses.get(i);
            if (g != dockGlass && g != statusGlass) glasses.remove(i);   // drop only the panel's glass
        }
        Runnable done = new Runnable() {
            @Override public void run() {
                View v = back == null ? null : dock.findViewWithTag(back);
                if (v == null && back != null && back.startsWith("top:")) {
                    v = root.findViewById(Integer.parseInt(back.substring(4)));
                }
                if (v == null && dock.getChildCount() > 0) v = dock.getChildAt(0);
                if (v != null) v.requestFocus();      // before the panel goes, so focus never jumps
                focusedKey = back;
                overlay.setVisibility(View.GONE);
                overlay.removeAllViews();
            }
        };
        if (!animate || content == null) { done.run(); return; }
        animateDim(0x59000000, 0x00000000);
        overlay.animate().alpha(0f).setDuration(160).withEndAction(done).start();
        content.animate().translationY(px(24)).scaleX(0.98f).scaleY(0.98f).setDuration(160).start();
    }

    private void animateDim(int from, int to) {
        ValueAnimator a = ValueAnimator.ofArgb(from, to);
        a.setDuration(220);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) { overlay.setBackgroundColor((Integer) an.getAnimatedValue()); }
        });
        a.start();
    }

    /** A glass sheet at a design rect. */
    private FrameLayout sheet(int left, int top, int w, int h) {
        FrameLayout s = new FrameLayout(this);
        Glass g = new Glass(blurSmall, BLUR, px(left), px(top), px(SHEET_RADIUS), SHEET_TINT, px(1.5f));
        glasses.add(g);
        s.setBackground(g);
        s.setClipToOutline(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), px(h));
        lp.leftMargin = px(left);
        lp.topMargin = px(top);
        s.setLayoutParams(lp);
        return s;
    }

    private TextView chip(String label, final boolean selected, final Runnable action) {
        final TextView c = text(label, 28, selected ? 0xFF0B1018 : WHITE);
        c.setShadowLayer(0, 0, 0, 0);
        c.setFocusable(true);
        c.setFocusableInTouchMode(true);
        c.setPadding(px(34), px(16), px(34), px(16));
        c.setSingleLine(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(999));
        bg.setColor(selected ? 0xE6FFFFFF : CHIP_BG);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = px(14);
        lp.bottomMargin = px(14);
        c.setLayoutParams(lp);
        c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                animateColor(bg, selected ? 0xFFFFFFFF : (f ? CHIP_FOCUS : CHIP_BG));
                scaleTo(v, f ? GROW_CHIP : 1f, 0);
            }
        });
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { press(v); action.run(); }
        });
        return c;
    }

    private TextView heading(String s) {
        TextView t = text(s, 26, WHITE_FAINT);
        t.setShadowLayer(0, 0, 0, 0);
        t.setAllCaps(true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, px(26), 0, px(14));
        return t;
    }

    /** Type a city for the weather. OK on the field opens the TV's keyboard. */
    private View citySheet() {
        FrameLayout s = sheet(560, 300, 800, 380);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(48), px(40), px(48), px(32));
        col.setClipChildren(false);
        TextView title = text("Weather city", 38, WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(0, 0, 0, px(24));
        col.addView(title);
        final EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(prefs.getString(Weather.CITY, ""));
        field.setHint("Stockholm");
        field.setTextColor(WHITE);
        field.setHintTextColor(WHITE_FAINT);
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(32));
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setPadding(px(32), px(18), px(32), px(18));
        final GradientDrawable fbg = new GradientDrawable();
        fbg.setCornerRadius(px(999));
        fbg.setColor(CHIP_BG);
        fbg.setStroke(px(2), 0x00FFFFFF);
        field.setBackground(fbg);
        field.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) { fbg.setStroke(px(2), f ? 0xCCFFFFFF : 0x00FFFFFF); }
        });
        field.setTag("first");
        col.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final Runnable save = new Runnable() {
            @Override public void run() {
                String c = field.getText().toString().trim();
                if (!c.isEmpty()) { weather.setCity(c); refreshWeather(true); }
                showOverlay(settingsPanel());
            }
        };
        field.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent ev) { save.run(); return true; }
        });
        LinearLayout buttons = chipRow();
        buttons.setPadding(px(CHIP_PAD), px(24), px(CHIP_PAD), 0);
        buttons.addView(chip("Save", true, save));
        buttons.addView(chip("Cancel", false, new Runnable() {
            @Override public void run() { showOverlay(settingsPanel()); }
        }));
        col.addView(buttons);
        s.addView(col);
        return s;
    }

    private View editSheet(final Entry e, final int index, final int count) {
        FrameLayout s = sheet(660, 290, 600, 500);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(48), px(40), px(48), px(32));
        col.setClipChildren(false);
        TextView title = text(e.label, 38, WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(0, 0, 0, px(22));
        col.addView(title);
        boolean first = true;
        if (index > 0) { col.addView(row(chip("Move left", false, new Runnable() {
            @Override public void run() { List<String> k = dockKeys(); k.remove(index); k.add(index - 1, e.key); saveDock(k); rebuildDock(); hideOverlay(true); }
        }), first)); first = false; }
        if (index < count - 1) { col.addView(row(chip("Move right", false, new Runnable() {
            @Override public void run() { List<String> k = dockKeys(); k.remove(index); k.add(index + 1, e.key); saveDock(k); rebuildDock(); hideOverlay(true); }
        }), first)); first = false; }
        col.addView(row(chip("Remove from dock", false, new Runnable() {
            @Override public void run() {
                List<String> k = dockKeys(); k.remove(e.key); saveDock(k);
                focusedKey = k.isEmpty() ? null : k.get(Math.min(index, k.size() - 1));
                rebuildDock(); hideOverlay(true);
            }
        }), first)); first = false;
        if (e.isApp()) col.addView(row(chip("App info", false, new Runnable() {
            @Override public void run() { hideOverlay(false); start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.key))); }
        }), false));
        s.addView(col);
        return s;
    }

    private View row(View chip, boolean first) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipToPadding(false);
        r.setPadding(px(CHIP_PAD), 0, px(CHIP_PAD), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(-px(CHIP_PAD), 0, -px(CHIP_PAD), 0);
        r.setLayoutParams(lp);
        r.addView(chip);
        if (first) chip.setTag("first");
        return r;
    }

    /** All apps as a grid, Launchpad style. In add mode, OK toggles dock membership. */
    private View drawer(final boolean addMode) {
        FrameLayout s = sheet(SHEET_LEFT, SHEET_TOP, SHEET_W, SHEET_H);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(56), px(40), px(56), px(24));

        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        headRow.setClipChildren(false);
        TextView title = text(addMode ? "Dock" : "All apps", 40, WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!addMode) {
            TextView set = chip("Settings", false, new Runnable() {
                @Override public void run() { showOverlay(settingsPanel()); }
            });
            ((LinearLayout.LayoutParams) set.getLayoutParams()).rightMargin = 0;
            ((LinearLayout.LayoutParams) set.getLayoutParams()).bottomMargin = 0;
            headRow.addView(set);
        }
        col.addView(headRow);
        TextView hint = text(addMode ? "OK adds to or removes from the dock" : "OK opens  ·  long press adds to the dock  ·  BACK closes", 24, WHITE_FAINT);
        hint.setPadding(0, px(6), 0, px(22));
        col.addView(hint);

        // The grid scrolls, so it must clip; it gets GRID_PAD of room on every side for the
        // focus grow, and the scroll view reaches into the sheet's padding by the same amount
        // so the icons stay lined up with the title.
        col.setClipToPadding(false);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, px(GRID_PAD - ROW_PAD), 0, px(GRID_PAD - ROW_PAD));
        sv.addView(grid);
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svp.setMargins(-px(GRID_PAD), -px(GRID_PAD), -px(GRID_PAD), 0);
        col.addView(sv, svp);

        final List<String> inDock = dockKeys();
        List<Entry> all = allEntries();
        LinearLayout rowV = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % GRID_COLS == 0) {
                rowV = new LinearLayout(this);
                rowV.setOrientation(LinearLayout.HORIZONTAL);
                // Drawing into a row's own padding is reliable; drawing past a row's edge
                // is not, so every row carries the room its focused item grows into.
                rowV.setClipChildren(false);
                rowV.setClipToPadding(false);
                rowV.setPadding(px(GRID_PAD), px(ROW_PAD), px(GRID_PAD), px(ROW_PAD));
                grid.addView(rowV);
            }
            View item = gridItem(all.get(i), addMode, inDock);
            if (i == 0) item.setTag("first");
            rowV.addView(item);
        }
        s.addView(col);
        return s;
    }

    private View gridItem(final Entry e, final boolean addMode, final List<String> inDock) {
        final FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(GRID_W), px(GRID_H));
        lp.setMargins(px(3), 0, px(3), 0);
        t.setLayoutParams(lp);
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x00FFFFFF);
        bg.setCornerRadius(px(22));
        t.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(iconFor(e, px(GRID_ICON)));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(px(GRID_ICON), px(GRID_ICON));
        ip.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        ip.topMargin = px(18);
        t.addView(icon, ip);

        final TextView label = text(e.label, 25, WHITE_DIM);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(px(10), 0, px(10), 0);
        FrameLayout.LayoutParams lbl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lbl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lbl.bottomMargin = px(16);
        t.addView(label, lbl);

        final ImageView badge = new ImageView(this);
        badge.setImageDrawable(new Glyph(Glyph.CHECK));
        final GradientDrawable bbg = new GradientDrawable();
        bbg.setShape(GradientDrawable.OVAL);
        bbg.setColor(0xFF3DDC84);
        badge.setBackground(bbg);
        badge.setPadding(px(5), px(5), px(5), px(5));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(px(30), px(30));
        bp.gravity = Gravity.TOP | Gravity.END;
        bp.topMargin = px(16);
        bp.rightMargin = px(22);
        t.addView(badge, bp);
        badge.setVisibility(inDock.contains(e.key) ? View.VISIBLE : View.INVISIBLE);

        final Runnable toggle = new Runnable() {
            @Override public void run() {
                List<String> k = dockKeys();
                if (k.contains(e.key)) k.remove(e.key); else k.add(e.key);
                saveDock(k);
                inDock.clear(); inDock.addAll(k);
                badge.setVisibility(k.contains(e.key) ? View.VISIBLE : View.INVISIBLE);
                badge.setScaleX(0.6f); badge.setScaleY(0.6f);
                badge.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(2f)).start();
                rebuildDock();
            }
        };
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                animateColor(bg, f ? FOCUS_BG : 0x00FFFFFF);
                label.setTextColor(f ? WHITE : WHITE_DIM);
                scaleTo(v, f ? GROW_GRID : 1f, 0);
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                press(v);
                if (addMode) { toggle.run(); return; }
                hideOverlay(false);
                e.action.run();
            }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { toggle.run(); return true; }
        });
        return t;
    }

    /** Wallpaper thumbnails, logo style, how often the photo changes, dock, TV settings. */
    private View settingsPanel() {
        FrameLayout s = sheet(SHEET_LEFT, SHEET_TOP, SHEET_W, SHEET_H);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(56), px(40), px(56), px(24));
        TextView title = text("Settings", 40, WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(title);

        // Same idea as the drawer: the scrolling body reaches BODY_PAD into the sheet padding
        // and pads itself back, so headings stay aligned and focused items have room.
        col.setClipToPadding(false);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        body.setPadding(px(BODY_PAD), 0, px(BODY_PAD), px(BODY_PAD));
        sv.addView(body);
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svp.setMargins(-px(BODY_PAD), 0, -px(BODY_PAD), 0);
        col.addView(sv, svp);

        // Wallpaper
        body.addView(heading("Photo"));
        final List<String> refs = wallpapers.refs();
        final String current = prefs.getString(KEY_WALL, SHUFFLE);
        // The photo strip scrolls sideways, so it clips at its own edges. It reaches STRIP_PAD
        // past the body padding on both sides and pads itself back: the first tile lines up
        // with the heading, and a focused tile has room to grow and keep its border.
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setHorizontalFadingEdgeEnabled(true);
        hs.setFadingEdgeLength(px(80));
        final LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);       // the padding IS the room to grow into
        strip.setPadding(px(STRIP_PAD), px(12), px(STRIP_PAD), px(12));
        hs.addView(strip);
        LinearLayout.LayoutParams hsp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsp.setMargins(-px(STRIP_PAD), 0, -px(STRIP_PAD), 0);
        body.addView(hs, hsp);
        View shuffleTile = photoTile(null, SHUFFLE.equals(current), new Runnable() {
            @Override public void run() { prefs.edit().putString(KEY_WALL, SHUFFLE).apply(); loadWallpaper(true); showOverlay(settingsPanel()); }
        });
        shuffleTile.setTag("first");
        strip.addView(shuffleTile);
        final List<ImageView> thumbViews = new ArrayList<>();
        for (final String ref : refs) {
            View tile = photoTile(ref, ref.equals(current), new Runnable() {
                @Override public void run() { prefs.edit().putString(KEY_WALL, ref).apply(); loadWallpaper(true); showOverlay(settingsPanel()); }
            });
            strip.addView(tile);
            thumbViews.add((ImageView) ((FrameLayout) tile).getChildAt(0));
        }
        if (refs.isEmpty()) {
            TextView none = text("No photos yet. Push some with scripts/wallpapers.sh, or put them in wallpapers/ and rebuild.", 26, WHITE_FAINT);
            none.setPadding(px(6), px(10), 0, 0);
            body.addView(none);
        }
        loadThumbs(refs, thumbViews);

        body.addView(heading("Change photo"));
        String every = prefs.getString(KEY_EVERY, "10m");
        LinearLayout r1 = chipRow();
        r1.addView(chip("Every visit", every.equals("visit"), setEvery("visit")));
        r1.addView(chip("Every 10 minutes", every.equals("10m"), setEvery("10m")));
        r1.addView(chip("Every hour", every.equals("1h"), setEvery("1h")));
        r1.addView(chip("Only when I pick one", every.equals("never"), setEvery("never")));
        r1.addView(chip("Next photo now", false, new Runnable() {
            @Override public void run() { nextPhoto(); showOverlay(settingsPanel()); }
        }));
        body.addView(r1);

        body.addView(heading("Icons"));
        LinearLayout r2 = chipRow();
        r2.addView(chip("Colour", !mono(), new Runnable() {
            @Override public void run() { prefs.edit().putBoolean(KEY_MONO, false).apply(); rebuildDock(); showOverlay(settingsPanel()); }
        }));
        r2.addView(chip("Glass", mono(), new Runnable() {
            @Override public void run() { prefs.edit().putBoolean(KEY_MONO, true).apply(); rebuildDock(); showOverlay(settingsPanel()); }
        }));
        body.addView(r2);

        String wmode = weather.mode();
        Weather.Reading wr = weather.cached();
        String place = wr == null ? "" : wr.place;
        body.addView(heading(place.isEmpty() || wmode.equals("off") ? "Weather" : "Weather  ·  " + place));
        LinearLayout rw = chipRow();
        rw.addView(chip("Automatic location", wmode.equals("auto"), new Runnable() {
            @Override public void run() { weather.setAuto(); refreshWeather(true); showOverlay(settingsPanel()); }
        }));
        String city = prefs.getString(Weather.CITY, "");
        rw.addView(chip(wmode.equals("city") && !city.isEmpty() ? "City: " + city : "Choose a city", wmode.equals("city"), new Runnable() {
            @Override public void run() { showOverlay(citySheet()); }
        }));
        rw.addView(chip("Off", wmode.equals("off"), new Runnable() {
            @Override public void run() { weather.setOff(); showWeather(null); showOverlay(settingsPanel()); }
        }));
        body.addView(rw);

        body.addView(heading("Dock"));
        LinearLayout r3 = chipRow();
        r3.addView(chip("Add or remove apps", false, new Runnable() {
            @Override public void run() { showOverlay(drawer(true)); }
        }));
        r3.addView(chip("Reset to default", false, new Runnable() {
            @Override public void run() { prefs.edit().remove(KEY_APPS).apply(); rebuildDock(); hideOverlay(true); }
        }));
        body.addView(r3);

        body.addView(heading("TV"));
        LinearLayout r4 = chipRow();
        r4.addView(chip("TV settings", false, new Runnable() {
            @Override public void run() { hideOverlay(false); openSettings(); }
        }));
        r4.addView(chip("Network", false, new Runnable() {
            @Override public void run() { hideOverlay(false); start(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        }));
        String v = "?";
        try { v = pm.getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable ignored) { }
        r4.addView(chip("Home " + v + "  ·  " + refs.size() + " photos", false, new Runnable() {
            @Override public void run() { Toast.makeText(MainActivity.this, "Long press OK on a dock item to move or remove it.", Toast.LENGTH_LONG).show(); }
        }));
        body.addView(r4);

        s.addView(col);
        return s;
    }

    private Runnable setEvery(final String value) {
        return new Runnable() {
            @Override public void run() { prefs.edit().putString(KEY_EVERY, value).apply(); showOverlay(settingsPanel()); }
        };
    }

    private LinearLayout chipRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        r.setPadding(px(CHIP_PAD), px(6), px(CHIP_PAD), px(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(-px(CHIP_PAD), 0, -px(CHIP_PAD), 0);
        r.setLayoutParams(lp);
        return r;
    }

    private View photoTile(final String ref, boolean selected, final Runnable action) {
        final FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        int w = px(280), h = px(158);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.rightMargin = px(16);
        t.setLayoutParams(lp);
        final GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(px(18));
        frame.setColor(CHIP_BG);
        frame.setStroke(px(3), selected ? 0xFFFFFFFF : 0x00FFFFFF);
        t.setBackground(frame);
        t.setClipToOutline(true);

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (ref == null) {
            ImageView g = new ImageView(this);
            g.setImageDrawable(new Glyph(Glyph.SHUFFLE));
            FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(px(44), px(44));
            gp.gravity = Gravity.CENTER;
            gp.topMargin = -px(18);
            t.addView(g, gp);
            TextView l = text("Shuffle", 26, WHITE);
            l.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp2.gravity = Gravity.BOTTOM;
            lp2.bottomMargin = px(22);
            t.addView(l, lp2);
        } else if (!Wallpapers.name(ref).matches("(?i)^(IMG_|DSC|A\\d{6,}|[0-9a-f-]{20,}).*")) {
            // Camera and UUID names say nothing; a name you gave the file is shown.
            TextView l = text(Wallpapers.name(ref), 22, WHITE);
            l.setSingleLine(true);
            l.setEllipsize(TextUtils.TruncateAt.END);
            l.setPadding(px(14), px(6), px(14), px(8));
            l.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] { 0xA6000000, 0x00000000 }));
            FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp2.gravity = Gravity.BOTTOM;
            t.addView(l, lp2);
        }
        final boolean sel = selected;
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                frame.setStroke(px(3), f ? 0xFFFFFFFF : (sel ? 0xB3FFFFFF : 0x00FFFFFF));
                scaleTo(v, f ? GROW_PHOTO : 1f, 0);
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { press(v); action.run(); }
        });
        return t;
    }

    private void loadThumbs(final List<String> refs, final List<ImageView> views) {
        if (refs.isEmpty()) return;
        new Thread(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < refs.size(); i++) {
                    final Bitmap b = wallpapers.thumb(refs.get(i));
                    final ImageView v = views.get(i);
                    if (b == null) continue;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!v.isAttachedToWindow()) { b.recycle(); return; }
                            v.setAlpha(0f);
                            v.setImageBitmap(b);
                            v.animate().alpha(1f).setDuration(240).start();
                        }
                    });
                }
            }
        }, "thumbs").start();
    }

    // ---------------------------------------------------------------- wallpaper

    private String pickRef() {
        List<String> refs = wallpapers.refs();
        if (refs.isEmpty()) return null;
        String pref = prefs.getString(KEY_WALL, SHUFFLE);
        if (!SHUFFLE.equals(pref) && refs.contains(pref)) return pref;
        List<String> pool = new ArrayList<>(refs);
        String last = prefs.getString(KEY_LAST, null);
        if (pool.size() > 1 && last != null) pool.remove(last);
        String pick = pool.get(rnd.nextInt(pool.size()));
        prefs.edit().putString(KEY_LAST, pick).apply();
        return pick;
    }

    private void nextPhoto() {
        prefs.edit().putString(KEY_WALL, SHUFFLE).apply();
        loadWallpaper(true);
    }

    private void loadWallpaper(final boolean animate) {
        final String pick = pickRef();
        wallSetAt = System.currentTimeMillis();
        if (pick == null) {
            Bitmap b = wallpapers.placeholder();
            setWall(b, Wallpapers.blurDown(b, BLUR, 3, 3), animate);
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap b = wallpapers.decodeFitted(pick);
                if (b == null) b = wallpapers.placeholder();
                final Bitmap photo = b;
                final Bitmap blur = Wallpapers.blurDown(b, BLUR, 3, 3);
                runOnUiThread(new Runnable() {
                    @Override public void run() { setWall(photo, blur, animate); }
                });
            }
        }, "wallpaper").start();
    }

    private void setWall(Bitmap b, Bitmap blur, boolean animate) {
        Bitmap oldBlur = blurSmall;
        blurSmall = blur;
        for (Glass g : glasses) g.setBlur(blurSmall, BLUR);
        if (oldBlur != null) oldBlur.recycle();

        final ImageView back = frontIsA ? wallB : wallA;
        final Bitmap oldBack = frontIsA ? bitmapB : bitmapA;
        back.setImageBitmap(b);
        if (frontIsA) bitmapB = b; else bitmapA = b;
        if (oldBack != null && oldBack != b) oldBack.recycle();

        final boolean toB = frontIsA;
        Runnable finish = new Runnable() {
            @Override public void run() {
                ImageView front = toB ? wallA : wallB;
                Bitmap oldFront = toB ? bitmapA : bitmapB;
                front.setImageBitmap(null);
                if (toB) bitmapA = null; else bitmapB = null;
                if (oldFront != null) oldFront.recycle();
                frontIsA = !toB;
            }
        };
        if (!animate) {
            wallB.setAlpha(toB ? 1f : 0f);
            finish.run();
            return;
        }
        // wallB sits above wallA: fade it in to reveal B, fade it out to reveal A.
        wallB.animate().alpha(toB ? 1f : 0f).setDuration(700).withEndAction(finish).start();
    }

    // ---------------------------------------------------------------- actions

    private Intent launchIntent(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    private void launch(String pkg) {
        Intent i = launchIntent(pkg);
        if (i == null) { Toast.makeText(this, "Not installed: " + pkg, Toast.LENGTH_SHORT).show(); return; }
        start(i);
    }

    private void start(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Could not open: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        Intent i = new Intent(Settings.ACTION_SETTINGS).setPackage(TV_SETTINGS);
        if (pm.resolveActivity(i, 0) == null) i = new Intent(Settings.ACTION_SETTINGS);
        start(i);
    }

    private List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
            if (tim == null) return out;
            TreeMap<String, TvInputInfo> sorted = new TreeMap<>();
            for (TvInputInfo in : tim.getTvInputList()) {
                if (in.getType() != TvInputInfo.TYPE_HDMI) continue;
                if (in.getParentId() != null) continue;
                try { if (in.isHidden(this)) continue; } catch (Throwable ignored) { }
                sorted.put(in.getId(), in);
            }
            out.addAll(sorted.values());
        } catch (Throwable ignored) { }
        return out;
    }

    private TvInputInfo inputById(String id) {
        for (TvInputInfo in : hdmiInputs()) if (in.getId().equals(id)) return in;
        return null;
    }

    private String inputLabel(TvInputInfo in) {
        CharSequence l = null;
        try { l = in.loadCustomLabel(this); } catch (Throwable ignored) { }
        if (l == null || l.length() == 0) l = in.loadLabel(this);
        String s = l == null ? "HDMI" : l.toString();
        int amp = s.indexOf('&');                 // Philips appends "&DEVICE_TYPE=..."
        return amp > 0 ? s.substring(0, amp).trim() : s;
    }

    private void openInput(TvInputInfo in) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(in.getId());
        Intent view = new Intent(Intent.ACTION_VIEW, uri);
        if (pm.resolveActivity(view, 0) != null) { start(view); return; }
        Intent live = launchIntent(LIVE_TV);
        if (live != null) { live.setData(uri); start(live); return; }
        Toast.makeText(this, "No app handles " + inputLabel(in), Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- helpers

    private Drawable appIcon(String pkg) {
        try { return pm.getApplicationIcon(pkg); } catch (Throwable t) { return null; }
    }

    private void animateColor(final GradientDrawable bg, int to) {
        Object tag = bg.getConstantState();
        ValueAnimator a = ValueAnimator.ofArgb(currentColor(bg), to);
        a.setDuration(180);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) {
                int c = (Integer) an.getAnimatedValue();
                bg.setColor(c);
                colorMemo.put(bg, c);
            }
        });
        a.start();
    }

    private final Map<GradientDrawable, Integer> colorMemo = new HashMap<>();

    private int currentColor(GradientDrawable bg) {
        Integer c = colorMemo.get(bg);
        return c == null ? 0x00FFFFFF : c;
    }

    private void scaleTo(View v, float s, float ty) {
        v.animate().scaleX(s).scaleY(s).translationY(ty).setDuration(200)
                .setInterpolator(s > 1f ? new OvershootInterpolator(1.3f) : new DecelerateInterpolator()).start();
    }

    /** A quick press-down bounce before an action runs. */
    private void press(final View v) {
        final float back = v.getScaleX();
        v.animate().scaleX(back * 0.96f).scaleY(back * 0.96f).setDuration(70).withEndAction(new Runnable() {
            @Override public void run() { v.animate().scaleX(back).scaleY(back).setDuration(120).start(); }
        }).start();
    }

    private TextView text(String s, int sizeAt1080, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        style(t, sizeAt1080, color);
        return t;
    }

    private void style(TextView t, int sizeAt1080, int color) {
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(sizeAt1080));
        t.setShadowLayer(px(3), 0, px(1), 0x80000000);
    }

    private int px(float at1080) { return Math.round(at1080 * scale); }
}
