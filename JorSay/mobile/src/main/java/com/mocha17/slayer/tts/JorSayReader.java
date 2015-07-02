package com.mocha17.slayer.tts;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;

import com.mocha17.slayer.R;
import com.mocha17.slayer.notification.db.NotificationDBContract.NotificationData;
import com.mocha17.slayer.notification.db.NotificationDBOps;
import com.mocha17.slayer.utils.Constants;
import com.mocha17.slayer.utils.Logger;
import com.mocha17.slayer.utils.Utils;

import java.util.HashMap;
import java.util.Locale;

/**
 * Created by Chaitanya on 5/21/15.
 */
public class JorSayReader extends IntentService implements TextToSpeech.OnInitListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private TextToSpeech tts;
    int originalVolume;

    private SharedPreferences defaultSharedPreferences;
    private boolean prefMaxVolume;
    private AudioManager audioManager;

    public JorSayReader() {
        super("JorSayReader");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        prefMaxVolume = defaultSharedPreferences.getBoolean(
                getString(R.string.pref_key_max_volume), false);
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
    }

    public static void startReadAloud(Context context) {
        Intent intent = new Intent(context, JorSayReader.class);
        intent.setAction(Constants.ACTION_MSG_START_READ_ALOUD);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (Constants.ACTION_MSG_START_READ_ALOUD.equals(intent.getAction())) {
            Logger.d(this, "onHandleIntent TTS init");
            tts = new TextToSpeech(getApplicationContext(), this);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            //TODO Change this to match user locale
            tts.setLanguage(Locale.US);
            Logger.d(this, "TTS ready");
            /*TODO
            Volume settings check needs to be done for each notification.
            If the user turns the volume off, we should abort immediately.*/
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (prefMaxVolume) {
                Logger.d(this, "setting volume to max");
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            } else {
                if (originalVolume == 0) {
                    Logger.d(this, "Max volume isn't selected and device volume is at 0," +
                            "not reading aloud");
                    return;
                }
            }
            Cursor notificationCursor = NotificationDBOps.get(this).getMostRecentNotification();
            if (notificationCursor != null && notificationCursor.moveToFirst()) {
                Logger.d(this, "TTS reading now");
                //Utterance ID should be unique per notification.
                String utteranceID = Long.toString(System.currentTimeMillis());
                tts.setOnUtteranceProgressListener(new JorSayReaderUtteranceProgressListener());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts.speak(getStringToRead(notificationCursor), TextToSpeech.QUEUE_ADD, null,
                            utteranceID);
                } else {
                    HashMap<String, String> params = new HashMap<>();
                    params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceID);
                    //noinspection deprecation - turned off as we have a version check around this
                    tts.speak(getStringToRead(notificationCursor), TextToSpeech.QUEUE_ADD, params);
                }
            }
            notificationCursor.close();
        }
    }

    private String getStringToRead(Cursor cursor) {
        /*Why are we using Strings directly rather than defining them as static finals? Because...
        defining BY makes little sense, definition and string are not different here. The name for
        the variable isn't supposed to mean anything - it isn't a key, it isn't a path, it isn't an
        intent action etc. For the Strings used in this method, we would instead rely on GC to claim
        the memory back, rather than keeping something permanently occupied.
         */
        StringBuilder sb = new StringBuilder("By ");

        String titleBig = cursor.getString(
                cursor.getColumnIndex(NotificationData.COLUMN_NAME_TITLE_BIG));
        String title = cursor.getString(cursor.getColumnIndex(NotificationData.COLUMN_NAME_TITLE));

        //Use TITLE_BIG if available, else TITLE
        title = (!TextUtils.isEmpty(titleBig))?titleBig:title;

        String appName = Utils.getAppName(cursor.getString(
                cursor.getColumnIndex(NotificationData.COLUMN_NAME_PACKAGE_NAME)));

        if (TextUtils.isEmpty(title) || appName.equals(title)) {
            sb.append(appName).append(".\n");
            String summary = cursor.getString(
                    cursor.getColumnIndex(NotificationData.COLUMN_NAME_SUMMARY));
            if (!TextUtils.isEmpty(summary)) {
                sb.append(summary).append(".\n");
            }
        } else {
            sb.append(appName).append(".\n").append(title).append(".\n");
        }

        String details = "Details: ";
        String textLines = cursor.getString(
                cursor.getColumnIndex(NotificationData.COLUMN_NAME_TEXT_LINES));
        if (!TextUtils.isEmpty(textLines)) {
            sb.append(details).append(textLines).append(".");
        } else {
            //These checks are nested because we want to avoid getting data we are not going to use,
            //and we want to do isEmpty() checks too.
            String bigText = cursor.getString(
                    cursor.getColumnIndex(NotificationData.COLUMN_NAME_BIG_TEXT));
            if (!TextUtils.isEmpty(bigText)) {
                sb.append(details).append(bigText).append(".");
            } else {
                String text = cursor.getString(
                        cursor.getColumnIndex(NotificationData.COLUMN_NAME_TEXT));
                if (!TextUtils.isEmpty(text)) {
                    sb.append(details).append(text).append(".");
                } else {
                    String tickerText = cursor.getString(
                            cursor.getColumnIndex(NotificationData.COLUMN_NAME_TICKER_TEXT));
                    if (!TextUtils.isEmpty(tickerText)) {
                        sb.append(details).append(tickerText).append(".");
                    }
                }
            }
        }
        return sb.toString();
    }

    private void ttsDone() {
        Logger.d(this, "ttsDone shutting down");
        tts.shutdown();
        //After TTS is done, set volume back to original level
        Logger.d(this, "ttsDone restoring original volume");
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
    }

    private class JorSayReaderUtteranceProgressListener extends UtteranceProgressListener {
        @Override
        public void onStart(String utteranceId) {
            Logger.d(this, "onStart utteranceId: " + utteranceId);
        }

        @Override
        public void onDone(String utteranceId) {
            ttsDone();
        }

        @Override
        public void onError(String utteranceId) {
            ttsDone();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getString(R.string.pref_key_max_volume).equals(key)) {
            prefMaxVolume = defaultSharedPreferences.getBoolean(
                    getString(R.string.pref_key_max_volume), false);
        }
    }
}