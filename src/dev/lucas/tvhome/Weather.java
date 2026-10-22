package dev.lucas.tvhome;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Current weather for the header. The only network use in the launcher.
 *
 *   Location   "auto": approximate, from the internet connection (ipapi.co, falling back
 *              to get.geojs.io), refreshed weekly. "city": a name you typed, looked up once
 *              with Open-Meteo's geocoding. "off": no requests at all.
 *   Weather    Open-Meteo, no key, coordinates rounded to two decimals (about 1 km) before
 *              they leave the TV. Refreshed at most every 30 minutes.
 *
 * Everything is cached in prefs, so the header shows the last reading instantly.
 */
final class Weather {

    interface Listener { void onWeather(Reading r); }

    static final class Reading {
        final int code;          // WMO weather code
        final double temp;       // in the display unit
        final boolean day, fahrenheit;
        final String place;
        Reading(int code, double temp, boolean day, boolean fahrenheit, String place) {
            this.code = code; this.temp = temp; this.day = day; this.fahrenheit = fahrenheit; this.place = place;
        }
        String temperature() { return Math.round(temp) + "°"; }
    }

    static final String MODE = "wx_mode", CITY = "wx_city";
    private static final String LAT = "wx_lat", LON = "wx_lon", PLACE = "wx_place", LOC_AT = "wx_loc_at", LOC_FOR = "wx_loc_for";
    private static final String CODE = "wx_code", TEMP = "wx_temp", DAY = "wx_day", AT = "wx_at", UNIT = "wx_unit";
    private static final long FRESH = 30 * 60 * 1000L, LOC_FRESH = 7 * 24 * 3600 * 1000L;

    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean busy;

    Weather(SharedPreferences prefs) { this.prefs = prefs; }

    String mode() { return prefs.getString(MODE, "auto"); }

    /** Last reading, or null. */
    Reading cached() {
        if (mode().equals("off") || !prefs.contains(AT)) return null;
        return new Reading(prefs.getInt(CODE, 0), Double.longBitsToDouble(prefs.getLong(TEMP, 0)),
                prefs.getBoolean(DAY, true), prefs.getBoolean(UNIT, false), prefs.getString(PLACE, ""));
    }

    boolean stale() { return System.currentTimeMillis() - prefs.getLong(AT, 0) > FRESH; }

    void setAuto() { prefs.edit().putString(MODE, "auto").remove(LOC_AT).remove(AT).apply(); }
    void setOff() { prefs.edit().putString(MODE, "off").apply(); }
    void setCity(String city) { prefs.edit().putString(MODE, "city").putString(CITY, city.trim()).remove(LOC_AT).remove(AT).apply(); }

    void refresh(final boolean force, final Listener l) {
        if (mode().equals("off") || busy || (!force && !stale())) return;
        android.util.Log.i("tvhome", "weather: refreshing (" + mode() + ")");
        busy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Reading r = fetch();
                    android.util.Log.i("tvhome", "weather: " + (r == null ? "no result" : "code " + r.code + ", " + r.temperature()));
                    if (r != null) main.post(new Runnable() { @Override public void run() { l.onWeather(r); } });
                } catch (Throwable t) {
                    // Offline or a service is down: keep showing the last reading.
                    android.util.Log.w("tvhome", "weather: " + t);
                } finally {
                    busy = false;
                }
            }
        }, "weather").start();
    }

    private Reading fetch() throws Exception {
        String mode = mode();
        String key = mode.equals("city") ? "city:" + prefs.getString(CITY, "") : "auto";
        boolean locOk = key.equals(prefs.getString(LOC_FOR, ""))
                && System.currentTimeMillis() - prefs.getLong(LOC_AT, 0) < LOC_FRESH
                && prefs.contains(LAT);
        if (!locOk) {
            double[] ll; String place;
            if (mode.equals("city")) {
                String q = URLEncoder.encode(prefs.getString(CITY, ""), "UTF-8");
                JSONObject j = get("https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&language="
                        + Locale.getDefault().getLanguage() + "&name=" + q);
                JSONArray res = j.optJSONArray("results");
                if (res == null || res.length() == 0) return null;
                JSONObject f = res.getJSONObject(0);
                ll = new double[] { f.getDouble("latitude"), f.getDouble("longitude") };
                place = f.optString("name", "");
            } else {
                JSONObject j;
                try {
                    j = get("https://ipapi.co/json/");
                    ll = new double[] { j.getDouble("latitude"), j.getDouble("longitude") };
                    place = j.optString("city", "");
                } catch (Throwable first) {
                    android.util.Log.w("tvhome", "weather: ipapi.co failed, trying geojs: " + first);
                    j = get("https://get.geojs.io/v1/ip/geo.json");
                    ll = new double[] { Double.parseDouble(j.getString("latitude")), Double.parseDouble(j.getString("longitude")) };
                    place = j.optString("city", "");
                }
            }
            prefs.edit().putLong(LAT, Double.doubleToLongBits(ll[0])).putLong(LON, Double.doubleToLongBits(ll[1]))
                    .putString(PLACE, place).putString(LOC_FOR, key).putLong(LOC_AT, System.currentTimeMillis()).apply();
        }
        double lat = Double.longBitsToDouble(prefs.getLong(LAT, 0)), lon = Double.longBitsToDouble(prefs.getLong(LON, 0));
        String country = Locale.getDefault().getCountry();
        boolean f = country.equals("US") || country.equals("LR") || country.equals("MM");
        JSONObject j = get(String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day&timezone=auto%s",
                lat, lon, f ? "&temperature_unit=fahrenheit" : ""));
        JSONObject cur = j.getJSONObject("current");
        int code = cur.getInt("weather_code");
        double temp = cur.getDouble("temperature_2m");
        boolean day = cur.optInt("is_day", 1) == 1;
        prefs.edit().putInt(CODE, code).putLong(TEMP, Double.doubleToLongBits(temp)).putBoolean(DAY, day)
                .putBoolean(UNIT, f).putLong(AT, System.currentTimeMillis()).apply();
        return new Reading(code, temp, day, f, prefs.getString(PLACE, ""));
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "tvhome (Android TV launcher)");
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return new JSONObject(sb.toString());
        } finally {
            c.disconnect();
        }
    }

    /** WMO code to a glyph kind. */
    static int glyphFor(int code, boolean day) {
        if (code == 0) return day ? Glyph.WX_SUN : Glyph.WX_MOON;
        if (code == 1 || code == 2) return day ? Glyph.WX_SUN_CLOUD : Glyph.WX_MOON_CLOUD;
        if (code == 3) return Glyph.WX_CLOUD;
        if (code == 45 || code == 48) return Glyph.WX_FOG;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Glyph.WX_SNOW;
        if (code >= 95) return Glyph.WX_THUNDER;
        if (code >= 51) return Glyph.WX_RAIN;
        return Glyph.WX_CLOUD;
    }
}
