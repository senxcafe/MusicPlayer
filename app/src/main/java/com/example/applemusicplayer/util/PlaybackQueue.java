package com.example.applemusicplayer.util;

import com.example.applemusicplayer.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Holds the currently playing list of songs plus shuffle/repeat state.
 * Kept deliberately dependency-free so it can be unit tested and reused
 * by both the service and the UI.
 */
public class PlaybackQueue {

    public enum RepeatMode {
        OFF, ALL, ONE
    }

    private final List<Song> originalOrder = new ArrayList<>();
    private List<Song> playOrder = new ArrayList<>();
    private int currentIndex = -1;
    private boolean shuffleEnabled = false;
    private RepeatMode repeatMode = RepeatMode.OFF;

    public void setQueue(List<Song> songs, int startIndex) {
        originalOrder.clear();
        originalOrder.addAll(songs);
        rebuildPlayOrder(songs.isEmpty() ? -1 : startIndex);
    }

    private void rebuildPlayOrder(int keepSongIndexInOriginal) {
        Song keepSong = (keepSongIndexInOriginal >= 0 && keepSongIndexInOriginal < originalOrder.size())
                ? originalOrder.get(keepSongIndexInOriginal) : null;

        playOrder = new ArrayList<>(originalOrder);
        if (shuffleEnabled) {
            Collections.shuffle(playOrder, new Random());
        }

        currentIndex = keepSong == null ? (playOrder.isEmpty() ? -1 : 0) : playOrder.indexOf(keepSong);
        if (currentIndex < 0 && !playOrder.isEmpty()) {
            currentIndex = 0;
        }
    }

    public List<Song> getPlayOrder() {
        return Collections.unmodifiableList(playOrder);
    }

    public Song getCurrent() {
        if (currentIndex < 0 || currentIndex >= playOrder.size()) return null;
        return playOrder.get(currentIndex);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public boolean moveTo(int index) {
        if (index < 0 || index >= playOrder.size()) return false;
        currentIndex = index;
        return true;
    }

    /** Advances to the next track. Returns false if there is nothing left to play. */
    public boolean next() {
        if (playOrder.isEmpty()) return false;
        if (repeatMode == RepeatMode.ONE) {
            return true; // caller just restarts the same track
        }
        if (currentIndex < playOrder.size() - 1) {
            currentIndex++;
            return true;
        }
        if (repeatMode == RepeatMode.ALL) {
            currentIndex = 0;
            return true;
        }
        return false;
    }

    public boolean previous() {
        if (playOrder.isEmpty()) return false;
        if (currentIndex > 0) {
            currentIndex--;
            return true;
        }
        if (repeatMode == RepeatMode.ALL) {
            currentIndex = playOrder.size() - 1;
            return true;
        }
        return false;
    }

    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    public void setShuffleEnabled(boolean enabled) {
        if (shuffleEnabled == enabled) return;
        shuffleEnabled = enabled;
        int originalIdx = getCurrent() == null ? -1 : originalOrder.indexOf(getCurrent());
        rebuildPlayOrder(originalIdx);
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void cycleRepeatMode() {
        switch (repeatMode) {
            case OFF:
                repeatMode = RepeatMode.ALL;
                break;
            case ALL:
                repeatMode = RepeatMode.ONE;
                break;
            case ONE:
            default:
                repeatMode = RepeatMode.OFF;
                break;
        }
    }

    public boolean isEmpty() {
        return playOrder.isEmpty();
    }
}
