package dev.linjian.peek;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Locale;

/** 回家模式：看见指定 App 连续停留过久时，发送非强制提醒。 */
public class HomeMode {
    private static final long MIN = 60L * 1000L;
    private static final String KEY_CURRENT_PKG = "home_mode_current_pkg";
    private static final String KEY_CURRENT_START = "home_mode_current_start_ms";
    private static final String KEY_LAST_FIRE = "home_mode_last_fire_ms";

    public static JSONObject config(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            SharedPreferences p = AppPrefs.get(ctx);
            o.put("enabled", p.getBoolean(AppPrefs.KEY_HOME_MODE_ENABLED, false));
            o.put("watch_packages", AppPrefs.homeWatchPackages(ctx));
            o.put("threshold_minutes", p.getInt(AppPrefs.KEY_HOME_THRESHOLD_MIN, 10));
            o.put("cooldown_minutes", p.getInt(AppPrefs.KEY_HOME_COOLDOWN_MIN, 5));
        } catch (Exception ignored) { }
        return o;
    }

    public static String pretty(Context ctx) {
        try {
            SharedPreferences p = AppPrefs.get(ctx);
            if (!p.getBoolean(AppPrefs.KEY_HOME_MODE_ENABLED, false)) return "回家模式：关闭";
            return "回家模式：开启（只提醒）" +
                    "\n盯住：" + AppPrefs.homeWatchPackages(ctx) +
                    "\n超过：" + p.getInt(AppPrefs.KEY_HOME_THRESHOLD_MIN, 10) + " 分钟  冷却：" + p.getInt(AppPrefs.KEY_HOME_COOLDOWN_MIN, 5) + " 分钟" +
                    "\n不会自动切换或拦截应用";
        } catch (Exception e) { return "回家模式读取失败：" + ScreenshotService.shortMsg(e); }
    }

    public static void evaluate(Context ctx, JSONObject state) {
        try {
            SharedPreferences p = AppPrefs.get(ctx);
            if (!p.getBoolean(AppPrefs.KEY_HOME_MODE_ENABLED, false)) { resetTracking(ctx); return; }
            String pkg = state.optString("current_package", "").trim();
            if (pkg.length() == 0 || pkg.equals(ctx.getPackageName())) { resetCurrent(p); return; }
            if (!isWatched(AppPrefs.homeWatchPackages(ctx), pkg)) { resetCurrent(p); return; }

            long now = System.currentTimeMillis();
            String current = p.getString(KEY_CURRENT_PKG, "");
            long start = p.getLong(KEY_CURRENT_START, 0L);
            if (!pkg.equals(current) || start <= 0L) {
                p.edit().putString(KEY_CURRENT_PKG, pkg).putLong(KEY_CURRENT_START, now).apply();
                DebugState.append(ctx, "回家模式开始计时：" + pkg);
                return;
            }

            int thresholdMin = clamp(p.getInt(AppPrefs.KEY_HOME_THRESHOLD_MIN, 10), 1, 240);
            int cooldownMin = clamp(p.getInt(AppPrefs.KEY_HOME_COOLDOWN_MIN, 5), 1, 240);
            long last = p.getLong(KEY_LAST_FIRE, 0L);
            if (now - start < thresholdMin * MIN || now - last < cooldownMin * MIN) return;

            p.edit().putLong(KEY_LAST_FIRE, now).apply();
            String app = state.optString("current_app", pkg);
            boolean popup = CompanionService.showHomeModeNotification(ctx, "掌心窗回家模式", AppPrefs.userName(ctx) + "，你在 " + app + " 停了 " + ((now - start) / MIN) + " 分钟。休息一下，回到" + AppPrefs.companionName(ctx) + "这里吧。");
            DebugState.append(ctx, popup ? "回家模式已发悬浮横幅提醒：" + pkg : "回家模式提醒失败：" + pkg);
        } catch (Exception e) {
            DebugState.append(ctx, "回家模式异常：" + ScreenshotService.shortMsg(e));
        }
    }

    private static boolean isWatched(String csv, String pkg) {
        if (csv == null) return false;
        String target = pkg.trim().toLowerCase(Locale.US);
        for (String part : csv.split("[,，\\n ]+")) {
            String s = part == null ? "" : part.trim().toLowerCase(Locale.US);
            if (s.length() > 0 && s.equals(target)) return true;
        }
        return false;
    }

    public static void resetTracking(Context ctx) {
        try {
            AppPrefs.get(ctx).edit()
                    .putString(KEY_CURRENT_PKG, "")
                    .putLong(KEY_CURRENT_START, 0L)
                    .putLong(KEY_LAST_FIRE, 0L)
                    .apply();
            CompanionService.cancelHomeModeNotification(ctx);
        } catch (Exception ignored) { }
    }

    private static void resetCurrent(SharedPreferences p) {
        p.edit().putString(KEY_CURRENT_PKG, "").putLong(KEY_CURRENT_START, 0L).apply();
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
