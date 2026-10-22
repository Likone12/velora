package ro.masinamea.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Daily 09:00 check of the deadlines the web app hands over via setReminders(),
 * posting a notification on the warning days before each one expires.
 */
public class Reminders extends BroadcastReceiver {
    private static final String ACTION_CHECK = "ro.masinamea.app.CHECK";
    private static final String PREFS = "reminders";
    private static final String CHANNEL = "termene";
    private static final long DAY = 86400000L;
    // PendingIntent.FLAG_IMMUTABLE (API 23), required when targeting API 31+.
    private static final int FLAG_IMMUTABLE = 0x04000000;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_CHECK.equals(intent.getAction())) check(ctx);
        else scheduleDaily(ctx);
    }

    static void store(Context ctx, String json) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("items", json).apply();
    }

    static void scheduleDaily(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, Reminders.class).setAction(ACTION_CHECK);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 9);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    private static long startOfDay(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Warn on the configured lead day and on 14/7/3/1/0 days before; once a week after expiry. */
    private static boolean shouldNotify(int days, int remind) {
        if (days < 0) return (-days) % 7 == 1;
        if (days > remind) return false;
        return days == remind || days == 14 || days == 7 || days == 3 || days == 1 || days == 0;
    }

    private static String describe(int days) {
        if (days < 0) return "A expirat de " + (-days) + (days == -1 ? " zi" : " zile") + "!";
        if (days == 0) return "Expiră astăzi!";
        if (days == 1) return "Expiră mâine.";
        return "Mai sunt " + days + " zile.";
    }

    static void check(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString("items", null);
        if (json == null) return;
        long today = startOfDay(System.currentTimeMillis());
        SharedPreferences.Editor ed = prefs.edit();
        try {
            JSONArray items = new JSONArray(json);
            for (int n = 0; n < items.length(); n++) {
                JSONObject it = items.getJSONObject(n);
                long deadline = startOfDay(it.getLong("deadline"));
                int days = (int) Math.round((deadline - today) / (double) DAY);
                int remind = it.optInt("remind", 30);
                if (!shouldNotify(days, remind)) continue;
                String sentKey = "sent:" + it.getString("key") + ":" + today;
                if (prefs.getBoolean(sentKey, false)) continue;
                ed.putBoolean(sentKey, true);
                notify(ctx, it.getString("key").hashCode(), it.getString("title"), describe(days) + " " + it.optString("sub", ""));
            }
        } catch (Exception ignored) {
        }
        ed.apply();
    }

    private static void notify(Context ctx, int id, String title, String body) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b = null;
        if (Build.VERSION.SDK_INT >= 26) {
            // NotificationChannel is newer than the API level this is compiled against, so it is reached by reflection.
            try {
                Class<?> chClass = Class.forName("android.app.NotificationChannel");
                Object ch = chClass.getConstructor(String.class, CharSequence.class, int.class)
                        .newInstance(CHANNEL, "Termene mașină", 4);
                nm.getClass().getMethod("createNotificationChannel", chClass).invoke(nm, ch);
                b = Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(ctx, CHANNEL);
            } catch (Exception ignored) {
            }
        }
        if (b == null) b = new Notification.Builder(ctx);
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
        b.setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setColor(0xFF2563EB)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(Notification.PRIORITY_HIGH);
        nm.notify(id, b.build());
    }
}
