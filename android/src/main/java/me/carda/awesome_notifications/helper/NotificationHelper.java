package me.carda.awesome_notifications.helper;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Locale;
import me.carda.awesome_notifications.activities.AlarmTriggerActivity;

public class NotificationHelper {

    private static final String PRIMARY_CHANNEL_ID = "primary_channel_id";
    private static final String TAG = "NotificationHelper";
    private final Context mContext;
    public int mAlarmId;
    private NotificationManager mNotifyManager;

    public NotificationHelper(Context context, int alarmId) {
        this.mContext = context;
        this.mAlarmId = alarmId;
    }

    //----------------------------- Deliver Notification -----------------------------------------//

    public Notification deliverNotification() {


        Intent fullScreenIntent = new Intent(mContext, AlarmTriggerActivity.class);
        Log.i(TAG, "deliverNotification: Putting alarmIdKey: " + mAlarmId);
        fullScreenIntent.putExtra("alarmIdKey", mAlarmId);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(mContext,
                0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Display Alarm Time in notification
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm aa",
                Locale.getDefault());
        String formattedTime = sdf.format(System.currentTimeMillis());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                mContext, PRIMARY_CHANNEL_ID)
                .setContentTitle("Hola")
                .setContentText(formattedTime)
                .setSmallIcon(android.R.drawable.arrow_up_float)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM);
        /* Since on A10 activity cannot be started from service(Including foreground service)
         * Use a High priority notification with FullScreenPendingIntent()
         * Also requires USE_FULL_SCREEN_INTENT permission in manifest
         *
         * This full-screen intent will be launched immediately if device's screen is off
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            builder.setFullScreenIntent(fullScreenPendingIntent, true);
        else
            // Set on notification click intent for pre oreo
            builder.setContentIntent(fullScreenPendingIntent);

        // Return a Notification object to be used by startForeground()
        return builder.build();
    }
}