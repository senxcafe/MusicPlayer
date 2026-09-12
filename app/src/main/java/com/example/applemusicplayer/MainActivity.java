package com.example.applemusicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.applemusicplayer.adapter.SongAdapter;
import com.example.applemusicplayer.model.Song;
import com.example.applemusicplayer.playback.MusicService;
import com.example.applemusicplayer.util.FavoritesStore;
import com.example.applemusicplayer.util.MusicLibraryLoader;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SongAdapter.Listener {

    private static final int REQUEST_CODE_PERMISSIONS = 100;

    private RecyclerView libraryList;
    private SongAdapter adapter;
    private TabLayout tabLayout;
    private View miniPlayerBar;
    private TextView miniPlayerTitle;
    private TextView miniPlayerSubtitle;
    private ImageButton miniPlayerPlayPause;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final List<Song> allSongs = new ArrayList<>();
    private FavoritesStore favoritesStore;

    private MusicService musicService;
    private boolean isBound = false;
    private boolean showingFavoritesOnly = false;

    private final MusicService.PlaybackListener playbackListener = new MusicService.PlaybackListener() {
        @Override
        public void onQueueChanged(List<Song> queue, int currentIndex) {
            Song current = (currentIndex >= 0 && currentIndex < queue.size()) ? queue.get(currentIndex) : null;
            adapter.setPlayingSongId(current == null ? -1 : current.id);
        }

        @Override
        public void onPlaybackStateChanged(boolean isPlaying, Song current) {
            updateMiniPlayer(isPlaying, current);
        }

        @Override
        public void onProgress(int positionMs, int durationMs) {
            // Mini player does not show a progress bar; Now Playing screen does.
        }

        @Override
        public void onShuffleRepeatChanged(boolean shuffle, com.example.applemusicplayer.util.PlaybackQueue.RepeatMode repeatMode) {
            // no-op on this screen
        }

        @Override
        public void onFavoriteChanged(long songId, boolean favorite) {
            adapter.notifyDataSetChanged();
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;
            musicService.addListener(playbackListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));

        favoritesStore = ((PlayerApplication) getApplication()).getFavoritesStore();

        libraryList = findViewById(R.id.libraryList);
        libraryList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SongAdapter(this, favoritesStore);
        libraryList.setAdapter(adapter);

        tabLayout = findViewById(R.id.libraryTabs);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showingFavoritesOnly = tab.getPosition() == 1;
                refreshVisibleList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        miniPlayerBar = findViewById(R.id.miniPlayerBar);
        miniPlayerTitle = findViewById(R.id.miniPlayerTitle);
        miniPlayerSubtitle = findViewById(R.id.miniPlayerSubtitle);
        miniPlayerPlayPause = findViewById(R.id.miniPlayerPlayPause);

        miniPlayerBar.setOnClickListener(v -> openNowPlaying());
        miniPlayerPlayPause.setOnClickListener(v -> {
            if (isBound) musicService.togglePlayPause();
        });

        ensurePermissionsThenLoadLibrary();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterAndDisplay(newText);
                return true;
            }
        });
        return true;
    }

    // ---------------------------------------------------------------
    // Library loading
    // ---------------------------------------------------------------

    private void ensurePermissionsThenLoadLibrary() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadLibrary();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_CODE_PERMISSIONS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_PERMISSIONS + 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLibrary();
            } else {
                Toast.makeText(this, R.string.permission_denied_message, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadLibrary() {
        ioExecutor.execute(() -> {
            List<Song> songs = MusicLibraryLoader.loadAllSongs(this);
            runOnUiThread(() -> {
                allSongs.clear();
                allSongs.addAll(songs);
                refreshVisibleList();
            });
        });
    }

    private void refreshVisibleList() {
        List<Song> source = showingFavoritesOnly ? filterFavorites(allSongs) : allSongs;
        adapter.submitList(source);
    }

    private void filterAndDisplay(String query) {
        List<Song> source = showingFavoritesOnly ? filterFavorites(allSongs) : allSongs;
        if (query == null || query.trim().isEmpty()) {
            adapter.submitList(source);
            return;
        }
        String lower = query.toLowerCase(Locale.getDefault());
        List<Song> filtered = new ArrayList<>();
        for (Song s : source) {
            if (s.title.toLowerCase(Locale.getDefault()).contains(lower)
                    || s.artist.toLowerCase(Locale.getDefault()).contains(lower)
                    || s.album.toLowerCase(Locale.getDefault()).contains(lower)) {
                filtered.add(s);
            }
        }
        adapter.submitList(filtered);
    }

    private List<Song> filterFavorites(List<Song> source) {
        List<Song> favorites = new ArrayList<>();
        for (Song s : source) {
            if (favoritesStore.isFavorite(s.id)) {
                favorites.add(s);
            }
        }
        return favorites;
    }

    // ---------------------------------------------------------------
    // SongAdapter.Listener
    // ---------------------------------------------------------------

    @Override
    public void onSongClicked(Song song, int position) {
        List<Song> currentList = adapter.getCurrentList();
        startService(new Intent(this, MusicService.class));

        if (isBound) {
            musicService.playQueue(currentList, position);
            openNowPlaying();
        } else {
            // Service connection from onStart() hasn't come back yet (rare, very
            // fast tap right after launch) - retry briefly rather than crash.
            pendingSongToPlay = currentList;
            pendingSongIndex = position;
            new android.os.Handler(getMainLooper()).postDelayed(this::playPendingSongIfReady, 150);
        }
    }

    private List<Song> pendingSongToPlay;
    private int pendingSongIndex = -1;

    private void playPendingSongIfReady() {
        if (pendingSongToPlay == null) return;
        if (isBound) {
            musicService.playQueue(pendingSongToPlay, pendingSongIndex);
            openNowPlaying();
            pendingSongToPlay = null;
        } else {
            new android.os.Handler(getMainLooper()).postDelayed(this::playPendingSongIfReady, 150);
        }
    }

    @Override
    public void onFavoriteToggled(Song song, boolean favorite) {
        if (showingFavoritesOnly) {
            refreshVisibleList();
        }
    }

    private void openNowPlaying() {
        startActivity(new Intent(this, NowPlayingActivity.class));
    }

    private void updateMiniPlayer(boolean isPlaying, Song current) {
        if (current == null) {
            miniPlayerBar.setVisibility(View.GONE);
            return;
        }
        miniPlayerBar.setVisibility(View.VISIBLE);
        miniPlayerTitle.setText(current.title);
        miniPlayerSubtitle.setText(current.artist);
        miniPlayerPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
    }
}
