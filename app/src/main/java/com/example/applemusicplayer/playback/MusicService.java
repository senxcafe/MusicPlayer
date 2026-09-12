package com.example.applemusicplayer.playback;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import com.example.applemusicplayer.NowPlayingActivity;
import com.example.applemusicplayer.PlayerApplication;
import com.example.applemusicplayer.R;
import com.example.applemusicplayer.model.Song;
import com.example.applemusicplayer.util.FavoritesStore;
import com.example.applemusicplayer.util.PlaybackQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Foreground service that owns the MediaPlayer, MediaSession and the
 * persistent playback notification. UI components bind to this service
 * and register a {@link PlaybackListener} to receive state updates.
 */
public class MusicService extends android.app.Service implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener,
        AudioManager.OnAudioFocusChangeListener {

    private static final int NOTIFICATION_ID = 1001;

    public interface PlaybackListener {
        void onQueueChanged(List<Song> queue, int currentIndex);
        void onPlaybackStateChanged(boolean isPlaying, Song current);
        void onProgress(int positionMs, int durationMs);
        void onShuffleRepeatChanged(boolean shuffle, PlaybackQueue.RepeatMode repeatMode);
        void onFavoriteChanged(long songId, boolean favorite);
    }

    private final IBinder binder = new LocalBinder();
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();
    private final Handler progressHandler = new Handler(Looper.getMainLooper());

    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private PlaybackQueue queue;
    private FavoritesStore favoritesStore;
    private AudioManager audioManager;
    private android.media.AudioFocusRequest lastFocusRequest;

    private boolean isPrepared = false;
    private boolean playWhenReady = false;
    private boolean resumeOnFocusGain = false;

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPrepared) {
                int pos = safeGetCurrentPosition();
                int dur = safeGetDuration();
                for (PlaybackListener l : listeners) {
                    l.onProgress(pos, dur);
                }
            }
            progressHandler.postDelayed(this, 500);
        }
    };

    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        queue = new PlaybackQueue();
        favoritesStore = ((PlayerApplication) getApplication()).getFavoritesStore();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mediaSession = new MediaSessionCompat(this, "AppleMusicLocalPlayerSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new SessionCallback());
        mediaSession.setActive(true);

        progressHandler.post(progressTicker);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        progressHandler.removeCallbacks(progressTicker);
        abandonAudioFocus();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaSession.release();
        super.onDestroy();
    }

    // ---------------------------------------------------------------
    // Public API used by activities
    // ---------------------------------------------------------------

    public void addListener(PlaybackListener listener) {
        listeners.add(listener);
        listener.onQueueChanged(queue.getPlayOrder(), queue.getCurrentIndex());
        listener.onShuffleRepeatChanged(queue.isShuffleEnabled(), queue.getRepeatMode());
        Song current = queue.getCurrent();
        if (current != null) {
            listener.onPlaybackStateChanged(playWhenReady, current);
        }
    }

    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    public void playQueue(List<Song> songs, int startIndex) {
        queue.setQueue(songs, startIndex);
        notifyQueueChanged();
        Song current = queue.getCurrent();
        if (current != null) {
            prepareAndPlay(current);
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer == null || queue.getCurrent() == null) return;
        if (mediaPlayer.isPlaying()) {
            pause();
        } else {
            resume();
        }
    }

    public void resume() {
        if (!requestAudioFocus()) return;
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.start();
            playWhenReady = true;
            startForegroundWithNotification();
            notifyPlaybackState();
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        playWhenReady = false;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH);
        updateNotification();
        notifyPlaybackState();
    }

    public void skipToNext() {
        if (queue.getRepeatMode() == PlaybackQueue.RepeatMode.ONE) {
            seekTo(0);
            resume();
            return;
        }
        if (queue.next()) {
            Song song = queue.getCurrent();
            if (song != null) prepareAndPlay(song);
            notifyQueueChanged();
        }
    }

    public void skipToPrevious() {
        // Standard behaviour: restart current track if more than 3s in.
        if (safeGetCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }
        if (queue.previous()) {
            Song song = queue.getCurrent();
            if (song != null) prepareAndPlay(song);
            notifyQueueChanged();
        }
    }

    public void skipToIndex(int index) {
        if (queue.moveTo(index)) {
            Song song = queue.getCurrent();
            if (song != null) prepareAndPlay(song);
            notifyQueueChanged();
        }
    }

    public void seekTo(int positionMs) {
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.seekTo(positionMs);
        }
    }

    public void setShuffleEnabled(boolean enabled) {
        queue.setShuffleEnabled(enabled);
        notifyQueueChanged();
        notifyShuffleRepeat();
    }

    public void cycleRepeatMode() {
        queue.cycleRepeatMode();
        notifyShuffleRepeat();
    }

    public boolean toggleFavoriteForCurrent() {
        Song current = queue.getCurrent();
        if (current == null) return false;
        boolean fav = favoritesStore.toggleFavorite(current.id);
        for (PlaybackListener l : listeners) {
            l.onFavoriteChanged(current.id, fav);
        }
        updateNotification();
        return fav;
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public Song getCurrentSong() {
        return queue.getCurrent();
    }

    public PlaybackQueue getQueue() {
        return queue;
    }

    public int getCurrentPositionMs() {
        return safeGetCurrentPosition();
    }

    public int getDurationMs() {
        return safeGetDuration();
    }

    // ---------------------------------------------------------------
    // Internal playback machinery
    // ---------------------------------------------------------------

    private void prepareAndPlay(Song song) {
        isPrepared = false;
        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                mediaPlayer.setOnPreparedListener(this);
                mediaPlayer.setOnCompletionListener(this);
                mediaPlayer.setOnErrorListener(this);
            } else {
                mediaPlayer.reset();
            }
            mediaPlayer.setDataSource(this, song.contentUri);
            mediaPlayer.prepareAsync();
            playWhenReady = true;
            updateSessionMetadata(song);
            notifyPlaybackState();
        } catch (Exception e) {
            playWhenReady = false;
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        if (playWhenReady && requestAudioFocus()) {
            mp.start();
            startForegroundWithNotification();
        }
        notifyPlaybackState();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        skipToNext();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        isPrepared = false;
        return true;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                resumeOnFocusGain = false;
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                resumeOnFocusGain = isPlaying();
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mediaPlayer != null) mediaPlayer.setVolume(0.3f, 0.3f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mediaPlayer != null) mediaPlayer.setVolume(1f, 1f);
                if (resumeOnFocusGain) {
                    resume();
                    resumeOnFocusGain = false;
                }
                break;
            default:
                break;
        }
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) return true;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        android.media.AudioFocusRequest request = new android.media.AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(this)
                .build();
        lastFocusRequest = request;
        int result = audioManager.requestAudioFocus(request);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager != null && lastFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(lastFocusRequest);
        }
    }

    private int safeGetCurrentPosition() {
        try {
            return (mediaPlayer != null && isPrepared) ? mediaPlayer.getCurrentPosition() : 0;
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    private int safeGetDuration() {
        try {
            return (mediaPlayer != null && isPrepared) ? mediaPlayer.getDuration() : 0;
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------
    // MediaSession + notification
    // ---------------------------------------------------------------

    private void updateSessionMetadata(Song song) {
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.durationMs)
                .build();
        mediaSession.setMetadata(metadata);
    }

    private void updatePlaybackState() {
        int state = isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO;
        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, safeGetCurrentPosition(), 1f)
                .build();
        mediaSession.setPlaybackState(playbackState);
    }

    private void startForegroundWithNotification() {
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void updateNotification() {
        Notification notification = buildNotification();
        androidx.core.app.NotificationManagerCompat manager =
                androidx.core.app.NotificationManagerCompat.from(this);
        try {
            manager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted; media session still works.
        }
    }

    /**
     * Builds the persistent playback notification. Uses the standard
     * MediaStyle "big art + compact transport controls" layout, which on
     * modern Android surfaces as a live, glanceable media card (the closest
     * platform-native equivalent of a Dynamic-Island style presentation).
     */
    private Notification buildNotification() {
        Song song = queue.getCurrent();

        Intent contentIntent = new Intent(this, NowPlayingActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                this, PlaybackStateCompat.ACTION_PLAY_PAUSE);
        PendingIntent nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
        PendingIntent prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);

        boolean playing = isPlaying();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, PlayerApplication.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(song != null ? song.title : getString(R.string.app_name))
                .setContentText(song != null ? song.artist : "")
                .setSubText(song != null ? song.album : null)
                .setContentIntent(contentPendingIntent)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.ic_skip_previous, getString(R.string.action_previous), prevIntent)
                .addAction(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        getString(playing ? R.string.action_pause : R.string.action_play),
                        playPauseIntent)
                .addAction(R.drawable.ic_skip_next, getString(R.string.action_next), nextIntent)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this, PlaybackStateCompat.ACTION_STOP));

        return builder.build();
    }

    // ---------------------------------------------------------------
    // Listener dispatch
    // ---------------------------------------------------------------

    private void notifyQueueChanged() {
        List<Song> snapshot = new ArrayList<>(queue.getPlayOrder());
        for (PlaybackListener l : listeners) {
            l.onQueueChanged(snapshot, queue.getCurrentIndex());
        }
    }

    private void notifyPlaybackState() {
        updatePlaybackState();
        updateNotification();
        Song current = queue.getCurrent();
        for (PlaybackListener l : listeners) {
            l.onPlaybackStateChanged(isPlaying(), current);
        }
    }

    private void notifyShuffleRepeat() {
        for (PlaybackListener l : listeners) {
            l.onShuffleRepeatChanged(queue.isShuffleEnabled(), queue.getRepeatMode());
        }
    }

    private class SessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            resume();
        }

        @Override
        public void onPause() {
            pause();
        }

        @Override
        public void onSkipToNext() {
            skipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            skipToPrevious();
        }

        @Override
        public void onSeekTo(long pos) {
            seekTo((int) pos);
        }

        @Override
        public void onStop() {
            pause();
            stopSelf();
        }
    }
}
