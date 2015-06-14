package com.mocha17.slayer.communication;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;
import com.mocha17.slayer.R;
import com.mocha17.slayer.WearMainActivity;
import com.mocha17.slayer.trigger.ShakeDetector;
import com.mocha17.slayer.utils.Constants;
import com.mocha17.slayer.utils.Logger;

/**
 * Receives 'start_trigger_detection' message and starts trigger detection.
 * <br>Created by Chaitanya on 5/13/15.
 */

public class MobileDataReceiver extends WearableListenerService {
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        //We do not need dataEvents outside this scope, freeze() isn't needed
        for (DataEvent event : dataEvents) {
            Uri uri = event.getDataItem().getUri();
            Logger.v(this, "onDataChanged URI lastPath: " + uri.getLastPathSegment());
            if (Constants.PATH_MSG_START_SHAKE_DETECTION.contains(uri.getLastPathSegment())) {
                Logger.d(this, "onDataChanged starting shake detection");
                ShakeDetector.startShakeDetection(this);
                break; //we do not consider multiple nodes yet (Issue #36)
            }
        }

        Logger.d(this, "releasing data buffer");
        dataEvents.release();
    }

    //for debugging - short-circuits shake detection
    //TODO explore and possibly utilize the debug vs release configuration in Android Studio
    private void notifyUserAction() {
        Intent intent = new Intent(this, WearMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        Notification.Builder notificationBuilder = new Notification.Builder(this)
                .setContentTitle("Send reply")
                .setVibrate(new long[]{0, 200})  // Vibrate for 200 milliseconds.
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(Notification.PRIORITY_MAX)
                .addAction(R.mipmap.ic_launcher, getString(R.string.read_aloud), pendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            notificationBuilder.setLocalOnly(true);
        }
        Notification card = notificationBuilder.build();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(4321, card);
    }
}