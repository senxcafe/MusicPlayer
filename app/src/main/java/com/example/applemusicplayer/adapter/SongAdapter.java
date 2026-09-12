package com.example.applemusicplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.applemusicplayer.R;
import com.example.applemusicplayer.model.Song;
import com.example.applemusicplayer.util.FavoritesStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    public interface Listener {
        void onSongClicked(Song song, int position);
        void onFavoriteToggled(Song song, boolean favorite);
    }

    private final List<Song> songs = new ArrayList<>();
    private final Listener listener;
    private final FavoritesStore favoritesStore;
    private long playingSongId = -1;

    public SongAdapter(Listener listener, FavoritesStore favoritesStore) {
        this.listener = listener;
        this.favoritesStore = favoritesStore;
    }

    public void submitList(List<Song> newSongs) {
        songs.clear();
        songs.addAll(newSongs);
        notifyDataSetChanged();
    }

    public void setPlayingSongId(long songId) {
        this.playingSongId = songId;
        notifyDataSetChanged();
    }

    public List<Song> getCurrentList() {
        return songs;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.title.setText(song.title);
        holder.subtitle.setText(song.artist + " • " + song.album);
        holder.duration.setText(formatDuration(song.durationMs));
        holder.favorite.setImageResource(favoritesStore.isFavorite(song.id)
                ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline);
        holder.itemView.setActivated(song.id == playingSongId);

        holder.itemView.setOnClickListener(v -> listener.onSongClicked(song, holder.getBindingAdapterPosition()));
        holder.favorite.setOnClickListener(v -> {
            boolean newState = favoritesStore.toggleFavorite(song.id);
            holder.favorite.setImageResource(newState
                    ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline);
            listener.onFavoriteToggled(song, newState);
        });
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    private static String formatDuration(long durationMs) {
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView duration;
        final ImageButton favorite;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.songTitle);
            subtitle = itemView.findViewById(R.id.songSubtitle);
            duration = itemView.findViewById(R.id.songDuration);
            favorite = itemView.findViewById(R.id.favoriteButton);
        }
    }
}
