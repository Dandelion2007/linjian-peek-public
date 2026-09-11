package dev.linjian.peek;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DebugState {
    private static final String PREFS = "linjian_peek";
    private static final String KEY_DEBUG = "debug_text";
    private static final String KEY_GATE_DEBUG = "app_gate_debug_v2";
    private static String gateBuffer;
    private static boolean gateFlushPending;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Separate bounded gate trace so unrelated status updates cannot erase the race evidence. */
    public static synchronized void gate(Context ctx, String message) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        if (gateBuffer == null) gateBuffer = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_GATE_DEBUG, "");
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA).format(new Date()) + " " + message;
        android.util.Log.d("AppGate", line);
        gateBuffer = gateBuffer + line + "\n";
        while (gateBuffer.length() > 48000) gateBuffer = gateBuffer.substring(gateBuffer.indexOf('\n') + 1);
        if (!gateFlushPending) {
            gateFlushPending = true;
            MAIN.postDelayed(() -> {
                synchronized (DebugState.class) {
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_GATE_DEBUG, gateBuffer).apply();
                    gateFlushPending = false;
                }
            }, 500);
        }
    }

    public static void set(Context ctx, String message) {
        if (ctx == null) return;
        String line = now() + "  " + message;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_DEBUG, line)
                .apply();
    }

    public static void append(Context ctx, String message) {
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String old = prefs.getString(KEY_DEBUG, "");
        String line = now() + "  " + message;
        String next = old == null || old.isEmpty() ? line : old + "\n" + line;
        String[] lines = next.split("\\n");
        if (lines.length > 12) {
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - 12; i < lines.length; i++) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(lines[i]);
            }
            next = sb.toString();
        }
        prefs.edit().putString(KEY_DEBUG, next).apply();
    }

    public static synchronized String get(Context ctx) {
        if (ctx == null) return "";
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DEBUG, "等待调试信息…") + "\nAppGate trace:\n"
                + (gateBuffer == null ? prefs.getString(KEY_GATE_DEBUG, "") : gateBuffer);
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
    }
}
