package com.example.applemusicplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.applemusicplayer.adapter.NowPlayingPagerAdapter;
import com.example.applemusicplayer.model.Song;
import com.example.applemusicplayer.playback.MusicService;
import com.example.applemusicplayer.util.FavoritesStore;
import com.example.applemusicplayer.util.PlaybackQueue;

import java.util.List;
import java.util.Locale;

public class NowPlayingActivity extends AppCompatActivity {

    private ViewPager2 pager;
    private NowPlayingPagerAdapter pagerAdapter;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private SeekBar seekBar;
    private ImageButton playPauseButton;
    private ImageButton nextButton;
    private ImageButton previousButton;
    private ImageButton shuffleButton;
    private ImageButton repeatButton;
    private ImageButton favoriteButton;

    private MusicService musicService;
    private boolean isBound = false;
    private boolean pagerUpdateIsProgrammatic = false;
    private boolean userIsDraggingSeekBar = false;
    private FavoritesStore favoritesStore;

    private final MusicService.PlaybackListener playbackListener = new MusicService.PlaybackListener() {
        @Override
        public void onQueueChanged(List<Song> queue, int currentIndex) {
            pagerAdapter.submitList(queue);
            if (currentIndex >= 0) {
                pagerUpdateIsProgrammatic = true;
                pager.setCurrentItem(currentIndex, false);
                pagerUpdateIsProgrammatic = false;
            }
            updateFavoriteIcon();
        }

        @Override
        public void onPlaybackStateChanged(boolean isPlaying, Song current) {
            playPauseButton.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            updateFavoriteIcon();
        }

        @Override
        public void onProgress(int positionMs, int durationMs) {
            if (userIsDraggingSeekBar) return;
            seekBar.setMax(Math.max(durationMs, 1));
            seekBar.setProgress(positionMs);
            currentTimeText.setText(formatTime(positionMs));
            totalTimeText.setText(formatTime(durationMs));
        }

        @Override
        public void onShuffleRepeatChanged(boolean shuffle, PlaybackQueue.RepeatMode repeatMode) {
            shuffleButton.setAlpha(shuffle ? 1f : 0.4f);
            switch (repeatMode) {
                case OFF:
                    repeatButton.setAlpha(0.4f);
                    repeatButton.setImageResource(R.drawable.ic_repeat);
                    break;
                case ALL:
                    repeatButton.setAlpha(1f);
                    repeatButton.setImageResource(R.drawable.ic_repeat);
                    break;
                case ONE:
                    repeatButton.setAlpha(1f);
                    repeatButton.setImageResource(R.drawable.ic_repeat_one);
                    break;
            }
        }

        @Override
        public void onFavoriteChanged(long songId, boolean favorite) {
            updateFavoriteIcon();
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.LocalBinder) service).getService();
            isBound = true;
            musicService.addListener(playbackListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_now_playing);

        favoritesStore = ((PlayerApplication) getApplication()).getFavoritesStore();

        pager = findViewById(R.id.nowPlayingPager);
        pagerAdapter = new NowPlayingPagerAdapter();
        pager.setAdapter(pagerAdapter);

        currentTimeText = findViewById(R.id.currentTime);
        totalTimeText = findViewById(R.id.totalTime);
        seekBar = findViewById(R.id.seekBar);
        playPauseButton = findViewById(R.id.playPauseButton);
        nextButton = findViewById(R.id.nextButton);
        previousButton = findViewById(R.id.previousButton);
        shuffleButton = findViewById(R.id.shuffleButton);
        repeatButton = findViewById(R.id.repeatButton);
        favoriteButton = findViewById(R.id.favoriteButton);

        findViewById(R.id.collapseButton).setOnClickListener(v -> finish());

        playPauseButton.setOnClickListener(v -> {
            if (isBound) musicService.togglePlayPause();
        });
        nextButton.setOnClickListener(v -> {
            if (isBound) musicService.skipToNext();
        });
        previousButton.setOnClickListener(v -> {
            if (isBound) musicService.skipToPrevious();
        });
        shuffleButton.setOnClickListener(v -> {
            if (isBound) musicService.setShuffleEnabled(!musicService.getQueue().isShuffleEnabled());
        });
        repeatButton.setOnClickListener(v -> {
            if (isBound) musicService.cycleRepeatMode();
        });
        favoriteButton.setOnClickListener(v -> {
            if (isBound) musicService.toggleFavoriteForCurrent();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) currentTimeText.setText(formatTime(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                userIsDraggingSeekBar = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                userIsDraggingSeekBar = false;
                if (isBound) musicService.seekTo(sb.getProgress());
            }
        });

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (pagerUpdateIsProgrammatic) return;
                if (isBound) musicService.skipToIndex(position);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, MusicService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            musicService.removeListener(playbackListener);
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    private void updateFavoriteIcon() {
        if (!isBound) return;
        Song current = musicService.getCurrentSong();
        if (current == null) return;
        boolean fav = favoritesStore.isFavorite(current.id);
        favoriteButton.setImageResource(fav ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline);
    }

    private static String formatTime(int millis) {
        int totalSeconds = millis / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
}
