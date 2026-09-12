package com.example.applemusicplayer.util;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.example.applemusicplayer.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads every playable audio track from the device's MediaStore.
 * Runs synchronously - callers should invoke this off the main thread.
 */
public final class MusicLibraryLoader {

    private MusicLibraryLoader() {
    }

    public static List<Song> loadAllSongs(Context context) {
        List<Song> songs = new ArrayList<>();

        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
                MediaStore.Audio.Media.DURATION + " > 0";

        String sortOrder = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";

        try (Cursor cursor = context.getContentResolver().query(
                collection, projection, selection, null, sortOrder)) {

            if (cursor == null) {
                return Collections.emptyList();
            }

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String title = cursor.getString(titleCol);
                String artist = cursor.getString(artistCol);
                String album = cursor.getString(albumCol);
                long albumId = cursor.getLong(albumIdCol);
                long duration = cursor.getLong(durationCol);

                Uri contentUri = ContentUris.withAppendedId(collection, id);
                Uri artUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId);

                songs.add(new Song(
                        id,
                        title == null ? "Unknown title" : title,
                        artist == null ? "Unknown artist" : artist,
                        album == null ? "Unknown album" : album,
                        duration,
                        contentUri,
                        artUri));
            }
        }

        return songs;
    }
}
