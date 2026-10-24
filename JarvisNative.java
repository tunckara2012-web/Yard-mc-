package com.jarvis.asistan;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Address;
import android.location.Geocoder;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.view.KeyEvent;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@CapacitorPlugin(
    name = "JarvisNative",
    permissions = {
        @Permission(alias = "calendar", strings = { Manifest.permission.READ_CALENDAR })
    }
)
public class JarvisNative extends Plugin {

    private void ok(PluginCall call, String msg) {
        JSObject r = new JSObject();
        r.put("message", msg);
        call.resolve(r);
    }

    private double num(PluginCall call, String key, double def) {
        try {
            String s = call.getString(key);
            if (s == null || s.trim().isEmpty()) return def;
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private boolean launch(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @PluginMethod
    public void setAlarm(PluginCall call) {
        int h = (int) num(call, "hour", -1);
        int m = (int) num(call, "minute", 0);
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            call.reject("Saat geçersiz");
            return;
        }
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
        i.putExtra(AlarmClock.EXTRA_HOUR, h);
        i.putExtra(AlarmClock.EXTRA_MINUTES, m);
        i.putExtra(AlarmClock.EXTRA_MESSAGE, call.getString("label", "Jarvis"));
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        if (launch(i)) {
            ok(call, String.format(Locale.US, "Saat uygulamasına alarm kuruldu: %02d:%02d", h, m));
        } else {
            call.reject("Saat uygulaması alarmı kabul etmedi");
        }
    }

    @PluginMethod
    public void setTimer(PluginCall call) {
        int sec = (int) Math.round(num(call, "seconds", 0) + num(call, "minutes", 0) * 60);
        if (sec < 1 || sec > 86400) {
            call.reject("Süre geçersiz");
            return;
        }
        Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
        i.putExtra(AlarmClock.EXTRA_LENGTH, sec);
        i.putExtra(AlarmClock.EXTRA_MESSAGE, call.getString("label", "Jarvis"));
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        if (launch(i)) {
            ok(call, "Saat uygulamasına zamanlayıcı kuruldu: " + sec + " saniye");
        } else {
            call.reject("Saat uygulaması zamanlayıcıyı kabul etmedi");
        }
    }

    @PluginMethod
    public void openApp(PluginCall call) {
        String q = call.getString("name", "").toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            call.reject("Uygulama adı yok");
            return;
        }
        PackageManager pm = getContext().getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        ResolveInfo best = null;
        String bestLabel = "";
        for (ResolveInfo ri : apps) {
            String label = ri.loadLabel(pm).toString();
            String low = label.toLowerCase(Locale.ROOT);
            if (low.isEmpty()) continue;
            if (low.equals(q)) {
                best = ri;
                bestLabel = label;
                break;
            }
            if (best == null && (low.contains(q) || q.contains(low))) {
                best = ri;
                bestLabel = label;
            }
        }
        if (best == null) {
            call.reject("Bu adla bir uygulama bulunamadı");
            return;
        }
        Intent i = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (i != null && launch(i)) {
            ok(call, bestLabel + " açıldı");
        } else {
            call.reject("Uygulama açılamadı");
        }
    }

    @PluginMethod
    public void youtubeSearch(PluginCall call) {
        String q = call.getString("query", "");
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)));
        i.setPackage("com.google.android.youtube");
        if (!launch(i)) {
            Intent j = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)));
            if (!launch(j)) {
                call.reject("YouTube açılamadı");
                return;
            }
        }
        ok(call, "YouTube araması açıldı: " + q + ". Videoyu kullanıcının seçmesi gerekir.");
    }

    @PluginMethod
    public void mapsSearch(PluginCall call) {
        String q = call.getString("query", "");
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)));
        if (launch(i)) {
            ok(call, "Haritada açıldı: " + q);
        } else {
            call.reject("Harita uygulaması açılamadı");
        }
    }

    @PluginMethod
    public void mediaControl(PluginCall call) {
        String a = call.getString("action", "play_pause");
        int code;
        if ("next".equals(a)) code = KeyEvent.KEYCODE_MEDIA_NEXT;
        else if ("previous".equals(a)) code = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        else if ("pause".equals(a)) code = KeyEvent.KEYCODE_MEDIA_PAUSE;
        else if ("play".equals(a)) code = KeyEvent.KEYCODE_MEDIA_PLAY;
        else code = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
            ok(call, "Müzik komutu gönderildi: " + a);
        } catch (Exception e) {
            call.reject("Müzik komutu gönderilemedi");
        }
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        double pct = num(call, "percent", -1);
        if (pct < 0 || pct > 100) {
            call.reject("Yüzde 0 ile 100 arasında olmalı");
            return;
        }
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (int) Math.round(max * pct / 100.0), 0);
            ok(call, "Medya sesi yüzde " + Math.round(pct) + " yapıldı");
        } catch (Exception e) {
            call.reject("Ses ayarlanamadı");
        }
    }

    @PluginMethod
    public void flashlight(PluginCall call) {
        boolean on = !"false".equals(call.getString("on", "true"));
        try {
            CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            String id = null;
            for (String cid : cm.getCameraIdList()) {
                Boolean f = cm.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) {
                    id = cid;
                    break;
                }
            }
            if (id == null) {
                call.reject("Bu telefonda el feneri bulunamadı");
                return;
            }
            cm.setTorchMode(id, on);
            ok(call, on ? "El feneri açıldı" : "El feneri kapatıldı");
        } catch (Exception e) {
            call.reject("El feneri ayarlanamadı");
        }
    }

    @PluginMethod
    public void deviceStatus(PluginCall call) {
        Context c = getContext();
        StringBuilder sb = new StringBuilder();
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            sb.append("Pil yüzde ").append(level * 100 / scale).append(ch ? " (şarj oluyor). " : ". ");
        } catch (Exception e) {
            sb.append("Pil durumu okunamadı. ");
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = (n == null) ? null : cm.getNetworkCapabilities(n);
            boolean wifi = nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean cell = nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            sb.append(wifi ? "Wi-Fi bağlı. " : (cell ? "Mobil veri kullanılıyor. " : "İnternet bağlantısı yok. "));
        } catch (Exception e) {
            sb.append("Bağlantı durumu okunamadı. ");
        }
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba == null) sb.append("Bluetooth yok.");
            else sb.append(ba.isEnabled() ? "Bluetooth açık." : "Bluetooth kapalı.");
        } catch (Exception e) {
            sb.append("Bluetooth durumu okunamadı.");
        }
        ok(call, sb.toString());
    }

    @PluginMethod
    public void addCalendarEvent(PluginCall call) {
        String title = call.getString("title", "Etkinlik");
        String startStr = call.getString("start", "");
        long start;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            Date d = f.parse(startStr);
            if (d == null) throw new Exception("tarih");
            start = d.getTime();
        } catch (Exception e) {
            call.reject("Tarih biçimi YYYY-AA-GGTSS:DD olmalı");
            return;
        }
        long dur = (long) num(call, "duration_minutes", 60) * 60000L;
        Intent i = new Intent(Intent.ACTION_INSERT);
        i.setData(CalendarContract.Events.CONTENT_URI);
        i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start);
        i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + dur);
        i.putExtra(CalendarContract.Events.TITLE, title);
        if (launch(i)) {
            ok(call, "Takvim formu açıldı. Kaydetmek için kullanıcının Kaydet düğmesine basması gerekiyor.");
        } else {
            call.reject("Takvim uygulaması açılamadı");
        }
    }

    @PluginMethod
    public void listCalendar(PluginCall call) {
        if (getPermissionState("calendar") != PermissionState.GRANTED) {
            requestPermissionForAlias("calendar", call, "calendarPerm");
            return;
        }
        doList(call);
    }

    @PermissionCallback
    private void calendarPerm(PluginCall call) {
        if (getPermissionState("calendar") == PermissionState.GRANTED) {
            doList(call);
        } else {
            call.reject("Takvim izni verilmedi");
        }
    }

    private void doList(PluginCall call) {
        int days = (int) Math.max(1, Math.min(30, num(call, "days", 7)));
        long now = System.currentTimeMillis();
        long end = now + days * 86400000L;
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now);
        ContentUris.appendId(b, end);
        String[] proj = {
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN
        };
        SimpleDateFormat f = new SimpleDateFormat("d MMMM EEEE HH:mm", new Locale("tr"));
        StringBuilder sb = new StringBuilder();
        int n = 0;
        Cursor c = null;
        try {
            c = getContext().getContentResolver().query(b.build(), proj, null, null, CalendarContract.Instances.BEGIN + " ASC");
            while (c != null && c.moveToNext() && n < 15) {
                sb.append(f.format(new Date(c.getLong(1)))).append(": ").append(c.getString(0)).append("; ");
                n++;
            }
        } catch (Exception e) {
            call.reject("Takvim okunamadı");
            return;
        } finally {
            if (c != null) c.close();
        }
        ok(call, n == 0 ? "Önümüzdeki " + days + " günde etkinlik yok." : "Etkinlikler: " + sb);
    }

    @PluginMethod
    public void placeName(PluginCall call) {
        try {
            Geocoder g = new Geocoder(getContext(), new Locale("tr"));
            List<Address> l = g.getFromLocation(num(call, "lat", 0), num(call, "lon", 0), 1);
            if (l == null || l.isEmpty()) {
                call.reject("Yer adı bulunamadı");
                return;
            }
            Address a = l.get(0);
            StringBuilder sb = new StringBuilder();
            if (a.getSubLocality() != null) sb.append(a.getSubLocality()).append(", ");
            if (a.getSubAdminArea() != null) sb.append(a.getSubAdminArea()).append(", ");
            if (a.getAdminArea() != null) sb.append(a.getAdminArea());
            ok(call, sb.toString());
        } catch (Exception e) {
            call.reject("Yer adı alınamadı");
        }
    }
}
