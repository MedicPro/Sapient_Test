package com.medicpro.myassistant;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "my_assistant_reminders";
    @Override public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "My Assistant Reminders", NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("काम और पेमेंट reminders"); nm.createNotificationChannel(c);
        }
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(context, CHANNEL_ID) : new android.app.Notification.Builder(context);
        b.setSmallIcon(com.medicpro.myassistant.R.drawable.ic_launcher).setContentTitle("My Assistant").setContentText(title == null ? "आपका काम याद है।" : title)
                .setAutoCancel(true).setContentIntent(pi);
        nm.notify((int)(System.currentTimeMillis()%Integer.MAX_VALUE), b.build());
    }
}
