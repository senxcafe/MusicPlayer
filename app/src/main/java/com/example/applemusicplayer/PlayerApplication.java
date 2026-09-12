package com.example.applemusicplayer;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.example.applemusicplayer.util.FavoritesStore;

public class PlayerApplication extends Application {

    public static final String NOTIFICATION_CHANNEL_ID = "music_playback_channel";

    private FavoritesStore favoritesStore;

    @Override
    public void onCreate() {
        super.onCreate();
        favoritesStore = new FavoritesStore(this);
        createNotificationChannel();
    }

    public FavoritesStore getFavoritesStore() {
        return favoritesStore;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_channel_description));
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
