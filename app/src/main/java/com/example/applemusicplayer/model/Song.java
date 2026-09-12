package com.example.applemusicplayer.model;

import android.net.Uri;

/**
 * Immutable representation of a single track read from MediaStore.
 */
public class Song {

    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final Uri contentUri;
    public final Uri albumArtUri;

    public Song(long id, String title, String artist, String album, long durationMs,
                Uri contentUri, Uri albumArtUri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.durationMs = durationMs;
        this.contentUri = contentUri;
        this.albumArtUri = albumArtUri;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Song)) return false;
        return id == ((Song) obj).id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
