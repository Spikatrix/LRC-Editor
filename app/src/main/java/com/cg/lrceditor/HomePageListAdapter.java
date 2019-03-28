package com.cg.lrceditor;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class HomePageListAdapter extends RecyclerView.Adapter<HomePageListAdapter.item> {
    public LinkedList<File> mFileList;
    private LayoutInflater mInflator;

    public boolean isDarkTheme = false;

    private LyricFileSelectListener mClickListener;

    private SparseBooleanArray expandedItems;

    private SparseBooleanArray selectedItems;

    public HomePageListAdapter(Context context, LinkedList<File> fileList) {
        mInflator = LayoutInflater.from(context);
        this.mFileList = fileList;
        selectedItems = new SparseBooleanArray();
        expandedItems = new SparseBooleanArray();
    }

    @NonNull
    @Override
    public HomePageListAdapter.item onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View mItemView = mInflator.inflate(R.layout.lyricfile_item, parent, false);
        return new item(mItemView, this);
    }

    @Override
    public void onBindViewHolder(@NonNull HomePageListAdapter.item holder, int position) {
        String mCurrent = mFileList.get(position).getName();
        holder.fileName.setText(mCurrent);

        if (expandedItems.get(position)) {
            holder.subView.setVisibility(View.VISIBLE);
            holder.expandableButton.setRotation(180);
        } else {
            holder.subView.setVisibility(View.GONE);
            holder.expandableButton.setRotation(0);
        }

        holder.itemView.setActivated(selectedItems.get(position, false));

        applyClickEvents(holder, position);
    }

    private void applyClickEvents(final HomePageListAdapter.item holder, final int position) {
        holder.linearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (getSelectionCount() == 0) {
                    //mClickListener.fileSelected(mFileList.get(holder.getLayoutPosition()).getName());

                    if (expandedItems.get(holder.getAdapterPosition())) {
                        expandedItems.delete(holder.getAdapterPosition());
                        holder.expandableButton.animate().rotation(0).setDuration(300).start();
                    } else {
                        expandedItems.put(holder.getAdapterPosition(), true);
                        holder.expandableButton.animate().rotation(180).setDuration(300).start();

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final LyricReader r = mClickListener.scanFile(holder.fileName.getText().toString());
                                if (r.getErrorMsg() != null || !r.readLyrics()) {
                                    view.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            String errorMsg = "<font color=\"#c61b1b\">" + r.getErrorMsg() + "</font>";
                                            holder.lyricsTextview.setText(Html.fromHtml(errorMsg));
                                        }
                                    });
                                    return;
                                }

                                String[] lyrics = r.getLyrics();
                                String[] timestamp = r.getTimestamps();

                                final String[] lyricsToDisplay;
                                if (lyrics.length > 8) {
                                    lyricsToDisplay = new String[9];
                                    for (int i = 0; i < 4; i++)
                                        lyricsToDisplay[i] = "<font color=\"#2bb1e2\">[" + timestamp[i] + "]</font> <font color=\"#dd9911\">" + lyrics[i] + "</font>";
                                    lyricsToDisplay[4] = "......\n";
                                    for (int i = lyrics.length - 4, j = 5; i < lyrics.length; i++, j++) {
                                        lyricsToDisplay[j] = "<font color=\"#2bb1e2\">[" + timestamp[i] + "]</font> <font color=\"#dd9911\">" + lyrics[i] + "</font>";
                                    }
                                } else {
                                    lyricsToDisplay = new String[lyrics.length];
                                    for (int i = 0; i < lyrics.length; i++) {
                                        lyricsToDisplay[i] = "<font color=\"#2bb1e2\">[" + timestamp[i] + "]</font> <font color=\"#dd9911\">" + lyrics[i] + "</font>";
                                    }
                                }

                                final SongMetaData songMetaData = r.getSongMetaData();
                                view.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!songMetaData.getSongName().trim().isEmpty()) {
                                            holder.songName.setText(
                                                    String.format(Locale.getDefault(), "%s %s",
                                                            view.getContext().getString(R.string.song_name_prompt),
                                                            songMetaData.getSongName()));
                                        } else {
                                            holder.songName.setText(
                                                    String.format(Locale.getDefault(), "%s N/A",
                                                            view.getContext().getString(R.string.song_name_prompt)));
                                        }

                                        if (!songMetaData.getArtistName().trim().isEmpty()) {
                                            holder.artistName.setText(
                                                    String.format(Locale.getDefault(), "%s %s",
                                                            view.getContext().getString(R.string.artist_name_prompt),
                                                            songMetaData.getArtistName()));
                                        } else {
                                            holder.artistName.setText(
                                                    String.format(Locale.getDefault(), "%s N/A",
                                                            view.getContext().getString(R.string.artist_name_prompt)));
                                        }

                                        if (!songMetaData.getAlbumName().trim().isEmpty()) {
                                            holder.albumName.setText(
                                                    String.format(Locale.getDefault(), "%s %s",
                                                            view.getContext().getString(R.string.album_name_prompt),
                                                            songMetaData.getAlbumName()));
                                        } else {
                                            holder.albumName.setText(
                                                    String.format(Locale.getDefault(), "%s N/A",
                                                            view.getContext().getString(R.string.album_name_prompt)));
                                        }

                                        if (!songMetaData.getComposerName().trim().isEmpty()) {
                                            holder.composerName.setText(
                                                    String.format(Locale.getDefault(), "%s %s",
                                                            view.getContext().getString(R.string.composer_prompt),
                                                            songMetaData.getComposerName()));
                                        } else {
                                            holder.composerName.setText(
                                                    String.format(Locale.getDefault(), "%s N/A",
                                                            view.getContext().getString(R.string.composer_prompt)));
                                        }

                                        holder.lyricsTextview.setText("");
                                        for (String line : lyricsToDisplay) {
                                            holder.lyricsTextview.append(Html.fromHtml(line));
                                            holder.lyricsTextview.append("\n");
                                        }
                                    }
                                });
                            }
                        }).start();
                    }

                    notifyItemChanged(holder.getAdapterPosition());
                } else {
                    mClickListener.onLyricItemClicked(position);
                }
            }
        });

        holder.linearLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                mClickListener.onLyricItemSelected(position);
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                return true;
            }
        });
    }

    @Override
    public int getItemCount() {
        return mFileList.size();
    }

    void setClickListener(LyricFileSelectListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    public void toggleSelection(int pos) {
        if (selectedItems.get(pos, false)) {
            selectedItems.delete(pos);
        } else {
            selectedItems.put(pos, true);
        }

        notifyItemChanged(pos);
    }

    public void selectAll() {
        for (int i = 0; i < getItemCount(); i++)
            selectedItems.put(i, true);
        notifyDataSetChanged();
    }

    public void clearExpandedItems() {
        expandedItems.clear();
    }

    public void clearSelections() {
        selectedItems.clear();
        notifyDataSetChanged();
    }

    public List<Integer> getSelectedItems() {
        List<Integer> items = new ArrayList<>(selectedItems.size());
        for (int i = 0; i < selectedItems.size(); i++) {
            items.add(selectedItems.keyAt(i));
        }
        return items;
    }

    public int getSelectionCount() {
        return selectedItems.size();
    }

    public void animate(TextView t, int start, int end) {
        Drawable[] myTextViewCompoundDrawables = t.getCompoundDrawables();
        for (Drawable drawable : myTextViewCompoundDrawables) {
            if (drawable == null)
                continue;
            ObjectAnimator anim = ObjectAnimator.ofInt(drawable, "level", start, end);
            anim.start();
        }
    }

    public interface LyricFileSelectListener {
        void fileSelected(String fileName);

        void onLyricItemSelected(int position);

        void onLyricItemClicked(int position);

        LyricReader scanFile(String fileName);
    }

    class item extends RecyclerView.ViewHolder {
        final HomePageListAdapter mAdapter;

        private final LinearLayout linearLayout;

        private final TextView fileName;
        private Button editButton;
        private ImageView expandableButton;

        private LinearLayout subView;

        private TextView songName;
        private TextView albumName;
        private TextView artistName;
        private TextView composerName;

        private TextView lyricsTextview;

        public item(View itemView, HomePageListAdapter adapter) {
            super(itemView);

            linearLayout = itemView.findViewById(R.id.lyricfileParentLinearlayout);
            fileName = itemView.findViewById(R.id.fileNameTextview);
            fileName.setSelected(true);
            this.mAdapter = adapter;

            subView = itemView.findViewById(R.id.lrcDetails);
            editButton = itemView.findViewById(R.id.editButton);
            expandableButton = itemView.findViewById(R.id.expandableButton);

            if (isDarkTheme) {
                expandableButton.setImageDrawable(itemView.getContext().getDrawable(R.drawable.ic_arrow_drop_down_light));
            }

            songName = itemView.findViewById(R.id.songNameTextview);
            artistName = itemView.findViewById(R.id.artistNameTextview);
            albumName = itemView.findViewById(R.id.albumNameTextview);
            composerName = itemView.findViewById(R.id.composerNameTextview);

            lyricsTextview = itemView.findViewById(R.id.lyricsTextview);

            editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String fileName = mFileList.get(getLayoutPosition()).getName();
                    if (mClickListener != null) mClickListener.fileSelected(fileName);
                }
            });
        }
    }
}

