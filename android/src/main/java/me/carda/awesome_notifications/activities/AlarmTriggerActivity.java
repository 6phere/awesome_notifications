package me.carda.awesome_notifications.alarm;

import android.app.Application;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.ncorti.slidetoact.SlideToActView;
import me.carda.awesome_notifications.R;
import me.carda.awesome_notifications.Utils.Constants.Constants;
import me.carda.awesome_notifications.Utils.Constants.PreferenceKeys;
import me.carda.awesome_notifications.helper.NotificationHelper;
import me.carda.awesome_notifications.services.AlarmService;
import me.carda.awesome_notifications.databinding.ActivityAlarmTriggerBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AlarmTriggerActivity extends AppCompatActivity {

    // UI Components
    private ActivityAlarmTriggerBinding binding;
    private TextView tvAlarmTime, tvAlarmTitle;
    private ImageView ivWeatherIcon;

    // vars
    private static final String TAG = "AlarmTriggerActivity";
    private boolean isSnoozed = false;
    private Handler handler;
    private Runnable silenceRunnable;
    private SharedPreferences sharedPref;
    private String actionBtnPref;
    private PowerManager.WakeLock wakeLock;


    //----------------------------- Lifecycle methods --------------------------------------------//

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try{
            binding = ActivityAlarmTriggerBinding.inflate(getLayoutInflater());
        }catch (Exception ex){
            ex.printStackTrace();
        }

        setContentView(binding.getRoot());

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
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
        tvAlarmTitle = binding.triggerAlarmTitle;
        //SlideToActView btnDismissAlarm = binding.btnDismissAlarm;
        //SlideToActView btnSnoozeAlarm = binding.btnSnoozeAlarm;

        Intent intent = getIntent();

        /* This can produce npe
         * Check if key exists then fetch value
         */
        int alarmId = -1;
        if (intent.hasExtra("alarmIdKey"))
            alarmId = intent.getIntExtra("alarmIdKey", -1);

        Log.i(TAG, "onCreate: Got alarmIdKey: " + alarmId);


        // SlideToActView Listeners

        // Dismiss Alarm
     /*   btnDismissAlarm.setOnSlideCompleteListener(new SlideToActView.OnSlideCompleteListener() {
            @Override
            public void onSlideComplete(@NonNull SlideToActView slideToActView) {
                // Stop service and finish this activity
                stopAlarmService();
            }
        });*/

        // Snooze Alarm
       /* btnSnoozeAlarm.setOnSlideCompleteListener(new SlideToActView.OnSlideCompleteListener() {
            @Override
            public void onSlideComplete(@NonNull SlideToActView slideToActView) {
                snoozeAlarm();
            }
        });*/

        // Check silenceTimeout
        silenceTimeout(alarmId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler = null;

        unregisterReceiver(PowerBtnReceiver);
    }



    // Display alarm title and time of snoozed alarm
    private void displaySnoozedInfo() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Simply show current time and "Snoozed Alarm" as title

                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm aa",
                        Locale.getDefault());
                String formattedTime = sdf.format(System.currentTimeMillis());
                tvAlarmTime.setText(formattedTime);

                tvAlarmTitle.setText("snoozed_alarm");
            }
        });
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

                stopAlarmService();
            }
        };
        handler.postDelayed(silenceRunnable, silenceTimeoutInt * 60000); // x Minutes * millis
    }


    //--------------------------------- Misc Methods ---------------------------------------------//

    // Stop service and finish activity
    public void stopAlarmService() {
        wakeLock.release();
        Intent intent = new Intent(AlarmTriggerActivity.this, AlarmService.class);
        stopService(intent);

        /* Runnable has not yet executed
         * and alarm has been dismissed by user
         * no need to post work now
         */
        if (handler != null && silenceRunnable != null)
            handler.removeCallbacks(silenceRunnable);
        finish();
    }

    public void snoozeAlarm() {

        stopAlarmService();
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
                    actionBtnPref = sharedPref.getString("power_btn_action", Constants.ACTION_DO_NOTHING);
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
                stopAlarmService();
                break;
            case Constants.ACTION_SNOOZE:
                snoozeAlarm();
                break;
        }
    }
}
