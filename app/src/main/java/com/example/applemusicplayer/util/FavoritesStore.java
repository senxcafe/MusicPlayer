package com.example.applemusicplayer.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Small SharedPreferences-backed store that remembers which track ids
 * the user has marked as a favorite.
 */
public class FavoritesStore {

    private static final String PREFS_NAME = "favorites_prefs";
    private static final String KEY_FAVORITE_IDS = "favorite_song_ids";

    private final SharedPreferences prefs;

    public FavoritesStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFavorite(long songId) {
        return getFavoriteIds().contains(String.valueOf(songId));
    }

    public void setFavorite(long songId, boolean favorite) {
        Set<String> ids = new HashSet<>(getFavoriteIds());
        if (favorite) {
            ids.add(String.valueOf(songId));
        } else {
            ids.remove(String.valueOf(songId));
        }
        prefs.edit().putStringSet(KEY_FAVORITE_IDS, ids).apply();
    }

    public boolean toggleFavorite(long songId) {
        boolean newState = !isFavorite(songId);
        setFavorite(songId, newState);
        return newState;
    }

    public Set<String> getFavoriteIds() {
        return prefs.getStringSet(KEY_FAVORITE_IDS, new HashSet<>());
    }
}
