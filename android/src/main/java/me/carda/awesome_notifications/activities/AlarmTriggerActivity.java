package me.carda.awesome_notifications.alarm;

import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.ncorti.slidetoact.SlideToActView;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

import me.carda.awesome_notifications.AwesomeNotificationsPlugin;
import me.carda.awesome_notifications.Utils.Constants.Constants;
import me.carda.awesome_notifications.databinding.ActivityAlarmTriggerBinding;
import me.carda.awesome_notifications.helper.NotificationHelper;
import me.carda.awesome_notifications.utils.JsonUtils;
import me.carda.awesome_notifications.notifications.managers.CreatedManager;
import me.carda.awesome_notifications.notifications.enumeratos.NotificationLifeCycle;
import me.carda.awesome_notifications.Definitions;

import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.app.PendingIntent;
import android.media.MediaPlayer;
import android.media.AudioAttributes;
import android.content.res.AssetFileDescriptor;

public class AlarmTriggerActivity extends AppCompatActivity {

    // UI Components
    private ActivityAlarmTriggerBinding binding;
    private TextView tvAlarmTime, tvAlarmDate, tvAlarmTitle;
    private ImageView ivAlarm;
    private SlideToActView btnSnoozeAlarm;
    private SlideToActView btnOpenAlarm;
    private MediaPlayer mediaPlayer;

    // vars
    private static final String TAG = "AlarmTriggerActivity";
    private boolean isSnoozed = false;
    private Handler handler;
    private Runnable silenceRunnable;
    private SharedPreferences sharedPref;
    private String actionBtnPref;
    private PowerManager.WakeLock wakeLock;
    private String notificationJson;
    private int alarmId = -1;


    //----------------------------- Lifecycle methods --------------------------------------------//

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            binding = ActivityAlarmTriggerBinding.inflate(getLayoutInflater());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        setContentView(binding.getRoot());

        getSupportActionBar().hide();
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getWindow().getDecorView().setBackgroundColor(0xff222222);


        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP),
                "Alarmzy::AlarmTriggerWakeLock");

        /* Acquire wakelock with 15 minutes timeout in case its not released from stopAlarmService()
         * Where 15 minutes is the max silence timeout that can be selected by user
         */
        wakeLock.acquire(900000);

        // Wakeup screen
        turnOnScreen();

        // Register Power button (screen off) intent receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(PowerBtnReceiver, filter);

        // Get Settings shared preferences
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        // Get views
        tvAlarmTime = binding.triggerAlarmTime;
        tvAlarmDate = binding.triggerAlarmDate;
        tvAlarmTitle = binding.triggerAlarmTitle;
        btnSnoozeAlarm = binding.btnSnoozeAlarm;
        btnOpenAlarm = binding.btnOpenAlarm;
        ivAlarm = binding.ivAlarm;
        
        Intent intent = getIntent();

        /* This can produce npe
         * Check if key exists then fetch value
         */
        if (intent.hasExtra(Definitions.NOTIFICATION_ID))
            alarmId = intent.getIntExtra(Definitions.NOTIFICATION_ID, -1);

        notificationJson = intent.getStringExtra(Definitions.NOTIFICATION_JSON);
        Map<String, Object> notificationData = JsonUtils.fromJson(notificationJson);

        String title = (String) ((Map) notificationData.get("content")).get("body");
        if (title != null)
            tvAlarmTitle.setText(title);

        String slideToSnooze = (String) (((Map) ((Map) notificationData.get("content")).get("payload")).get("slideToSnooze"));
        if (slideToSnooze != null)
            btnSnoozeAlarm.setText(slideToSnooze);
        String repeatTimes = (String) (((Map) ((Map) notificationData.get("content")).get("payload")).get("repeatTimes"));
        if (repeatTimes == null || repeatTimes == "" || Integer.parseInt(repeatTimes) == 1) {
            btnSnoozeAlarm.setVisibility(View.GONE);
        }

        String slideToOpen = (String) (((Map) ((Map) notificationData.get("content")).get("payload")).get("slideToOpen"));
        if (slideToOpen != null)
            btnOpenAlarm.setText(slideToOpen);


        mediaPlayer = new MediaPlayer();
        AssetFileDescriptor afd;

        String bigPicture = (String) ((Map) notificationData.get("content")).get("bigPicture");
        if (bigPicture == null)
            ivAlarm.setVisibility(View.GONE);
        else {
            try {
                InputStream ims = getAssets().open(bigPicture.replace("asset://", "flutter_assets/"));
                // load image as Drawable
                Drawable d = Drawable.createFromStream(ims, null);
                // set image to ImageView
                ivAlarm.setImageDrawable(d);
                ims.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String customSound = (String) ((Map) notificationData.get("content")).get("customSound");
        try {
            if (customSound != null) {
                afd = getAssets().openFd(customSound.replace("asset://", "flutter_assets/"));

                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            }else{
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }
                mediaPlayer.setDataSource(getApplicationContext(), alarmUri);
            }

            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build());
            mediaPlayer.setLooping(true);

            mediaPlayer.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mediaPlayer.start();

        formatDate();

        Log.i(TAG, "onCreate: Got alarmIdKey: " + alarmId);

        // Snooze Alarm
        btnSnoozeAlarm.setOnSlideCompleteListener(new SlideToActView.OnSlideCompleteListener() {
            @Override
            public void onSlideComplete(@NonNull SlideToActView slideToActView) {
                snoozeAlarm();
            }
        });

        // Open Alarm
        btnOpenAlarm.setOnSlideCompleteListener(new SlideToActView.OnSlideCompleteListener() {
            @Override
            public void onSlideComplete(@NonNull SlideToActView slideToActView) {
                stopAlarmService(0);
            }
        });

        // Check silenceTimeout
        silenceTimeout(alarmId);
    }

    private void formatDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm",
                Locale.getDefault());
        String formattedTime = sdf.format(System.currentTimeMillis());
        tvAlarmTime.setText(formattedTime);


        sdf = new SimpleDateFormat("EEE, d MMM",
                Locale.getDefault());
        formattedTime = sdf.format(System.currentTimeMillis());
        tvAlarmDate.setText(formattedTime);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler = null;

        unregisterReceiver(PowerBtnReceiver);
    }



    //------------------------------- Get Silence Timeout ----------------------------------------//

    /* Check if silence timeout is greater than 0
     * and deliver missed alarm notification if timeout is exceeded
     */
    public void silenceTimeout(final int alarmId) {
        final String KEY_SILENCE_TIMEOUT = "silenceTimeout";

        // Get silence timeout
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        final String silenceTimeStr = sharedPref.getString(KEY_SILENCE_TIMEOUT, "0");

        /* Set default and add null check
         * to avoid warning and npe later
         */
        int silenceTimeoutInt = 0;
        if (silenceTimeStr != null)
            silenceTimeoutInt = Integer.parseInt(silenceTimeStr);

        /* If silenceTimeout is set to Never(0)
         * Return from here
         */
        if (silenceTimeoutInt == 0)
            return;

        /* If not dismissed under x minutes
         * Stop alarm
         * Post missed alarm notification
         */
        handler = new Handler(getMainLooper());
        silenceRunnable = new Runnable() {
            @Override
            public void run() {
                // Deliver notification using id
                NotificationHelper nh = new NotificationHelper(getApplicationContext(), alarmId);

                /* AlarmEntity is null for snoozed alarm
                 * Get actual alarm time by: CurrentTime - silenceTimeout
                 */
                nh.deliverMissedNotification(
                        System.currentTimeMillis() - (Long.parseLong(silenceTimeStr) * 60000));

                stopAlarmService(0);
            }
        };
        handler.postDelayed(silenceRunnable, silenceTimeoutInt * 60000); // x Minutes * millis
    }


    //--------------------------------- Misc Methods ---------------------------------------------//

    // Stop service and finish activity
    public void stopAlarmService(int action) {
        mediaPlayer.stop();
        wakeLock.release();

        /* Runnable has not yet executed
         * and alarm has been dismissed by user
         * no need to post work now
         */
        if (handler != null && silenceRunnable != null)
            handler.removeCallbacks(silenceRunnable);

        Context context = getApplicationContext();

        NotificationManager notifManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        launchAction(notifManager, action);
        finish();
    }

    private void launchAction(NotificationManager notifManager, int action) {
        StatusBarNotification[] sbns = notifManager.getActiveNotifications();

        for (StatusBarNotification sbn : sbns) {
            try {
                if (sbn == null) {
                    Log.i(TAG, "sbn is null");
                    continue;
                }
                Notification n = sbn.getNotification();
                if (n.actions.length > 0) {
                    PendingIntent pi = n.actions[action].actionIntent;
                    if (pi != null) {
                        pi.send();
                    }
                }
            } catch (Exception e) {
            }
        }
    }

    public void snoozeAlarm() {
        stopAlarmService(1);
    }

    private void turnOnScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true);
            setShowWhenLocked(true);
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
        } else {
            final Window win = getWindow();
            win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
        }
    }

    //-------------------------------- ActionBtn Methods -----------------------------------------//

    /* This method:
     * Receives Volume button press
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {

            // Get volume key pref
            actionBtnPref = sharedPref.getString("volume_btn_action", Constants.ACTION_DO_NOTHING);
            if (actionBtnPref != null)
                actionBtnHandler(actionBtnPref);
        }
        return super.onKeyDown(keyCode, event);
    }

    /* This method:
     * Receives Power button press (Screen off event)
     */
    private final BroadcastReceiver PowerBtnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {

                    // Get power key pref
                    actionBtnPref = sharedPref.getString("power_btn_action", Constants.ACTION_DISMISS);
                    if (actionBtnPref != null)
                        actionBtnHandler(actionBtnPref);
                }
            }
        }
    };

    private void actionBtnHandler(String action) {
        switch (action) {
            case Constants.ACTION_MUTE:
                // Mute is handled by MuteActionReceiver in AlarmService
                sendBroadcast(new Intent().setAction(Constants.ACTION_MUTE));
                break;
            case Constants.ACTION_DISMISS:
                stopAlarmService(0);
                break;
            case Constants.ACTION_SNOOZE:
                snoozeAlarm();
                break;
        }
    }
}
