package com.nova.notes;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

public class ReminderScheduler {
    static final String PREFS = "nova_reminders";
    static final String KEY = "items";
    static final String CHANNEL = "nova_notes_reminders";
    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "NOVA Notes hatırlatıcıları", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Zamanı gelen not hatırlatıcıları");
            ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }
    static void sync(Context c, String json) throws Exception {
        SharedPreferences prefs=c.getSharedPreferences(PREFS,0);
        JSONArray previous=new JSONArray(prefs.getString(KEY,"[]"));
        JSONArray incoming=new JSONArray(json), merged=new JSONArray();
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        java.util.HashMap<String,JSONObject> oldMap=new java.util.HashMap<>();
        for(int i=0;i<previous.length();i++){
            JSONObject old=previous.getJSONObject(i);
            String id=old.optString("id");
            oldMap.put(id,old);
            Intent intent=new Intent(c,ReminderReceiver.class).setAction("com.nova.notes.REMINDER");
            PendingIntent pi=PendingIntent.getBroadcast(c,id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        }
        for(int i=0;i<incoming.length();i++){
            JSONObject item=incoming.getJSONObject(i);
            String id=item.optString("id");
            JSONObject old=oldMap.get(id);
            if(old!=null && "daily".equals(item.optString("repeat")) && "daily".equals(old.optString("repeat")) && old.optLong("time")>item.optLong("time")) item.put("time",old.optLong("time"));
            merged.put(item);
            long when=item.optLong("time",0);
            if(when>System.currentTimeMillis()) setAlarm(c,am,id,item.optString("message"),when,item.optString("repeat","once"));
        }
        prefs.edit().putString(KEY,merged.toString()).apply();
    }
    static void setAlarm(Context c, AlarmManager am, String id, String message, long when, String repeat) {
        Intent i=new Intent(c, ReminderReceiver.class).setAction("com.nova.notes.REMINDER").putExtra("id",id).putExtra("message",message).putExtra("time",when).putExtra("repeat",repeat);
        PendingIntent pi=PendingIntent.getBroadcast(c,id.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
    }
    static void restore(Context c) {
        try {
            JSONArray items=new JSONArray(c.getSharedPreferences(PREFS,0).getString(KEY,"[]"));
            AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            for(int i=0;i<items.length();i++){
                JSONObject r=items.getJSONObject(i);
                long t=r.optLong("time",0);
                if(t>System.currentTimeMillis()) setAlarm(c,am,r.optString("id"),r.optString("message"),t,r.optString("repeat","once"));
            }
        } catch(Exception ignored) {}
    }
    static void onAlarm(Context c, Intent intent) {
        String id=intent.getStringExtra("id"), message=intent.getStringExtra("message"), repeat=intent.getStringExtra("repeat");
        long oldTime=intent.getLongExtra("time",0);
        createChannel(c);
        Intent open=new Intent(c,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        Notification n=b.setSmallIcon(c.getApplicationInfo().icon).setContentTitle("NOVA Notes").setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setAutoCancel(true).setContentIntent(content).build();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id.hashCode(),n);
        try {
            SharedPreferences p=c.getSharedPreferences(PREFS,0);
            JSONArray old=new JSONArray(p.getString(KEY,"[]")), keep=new JSONArray();
            JSONObject found=null;
            for(int x=0;x<old.length();x++){JSONObject r=old.getJSONObject(x);if(r.optString("id").equals(id))found=r;else keep.put(r);}
            if(found!=null && "daily".equals(repeat)){
                Calendar cal=Calendar.getInstance();cal.setTimeInMillis(oldTime);cal.add(Calendar.DAY_OF_YEAR,1);
                found.put("time",cal.getTimeInMillis());keep.put(found);
                setAlarm(c,(AlarmManager)c.getSystemService(Context.ALARM_SERVICE),id,message,cal.getTimeInMillis(),"daily");
            }
            p.edit().putString(KEY,keep.toString()).apply();
        } catch(Exception ignored) {}
    }
    public static class ReminderReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) { onAlarm(c,i); }
    }
    public static class BootReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) { if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(i.getAction())) restore(c); }
    }
}