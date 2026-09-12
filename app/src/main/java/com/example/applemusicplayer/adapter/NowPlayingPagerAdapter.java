package com.example.applemusicplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.applemusicplayer.R;
import com.example.applemusicplayer.model.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * Feeds the ViewPager2 on the Now Playing screen. Each page shows the
 * artwork/title/artist for one track in the queue; swiping between pages
 * is how the user changes tracks (mirrored by MusicService.skipToIndex).
 */
public class NowPlayingPagerAdapter extends RecyclerView.Adapter<NowPlayingPagerAdapter.PageViewHolder> {

    private final List<Song> songs = new ArrayList<>();

    public void submitList(List<Song> newSongs) {
        songs.clear();
        songs.addAll(newSongs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_now_playing_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.title.setText(song.title);
        holder.subtitle.setText(song.artist);
        holder.art.setImageURI(song.albumArtUri);
        if (holder.art.getDrawable() == null) {
            holder.art.setImageResource(R.drawable.ic_album_placeholder);
        }
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        final ImageView art;
        final TextView title;
        final TextView subtitle;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            art = itemView.findViewById(R.id.nowPlayingArt);
            title = itemView.findViewById(R.id.nowPlayingTitle);
            subtitle = itemView.findViewById(R.id.nowPlayingSubtitle);
        }
    }
}
