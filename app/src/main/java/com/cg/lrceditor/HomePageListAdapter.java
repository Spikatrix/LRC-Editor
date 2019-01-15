package com.cg.lrceditor;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class HomePageListAdapter extends RecyclerView.Adapter<HomePageListAdapter.item> {
    public LinkedList<File> mFileList;
    private LayoutInflater mInflator;

    private LyricFileSelectListener mClickListener;

    private SparseBooleanArray selectedItems;

    public HomePageListAdapter(Context context, LinkedList<File> fileList) {
        mInflator = LayoutInflater.from(context);
        this.mFileList = fileList;
        selectedItems = new SparseBooleanArray();
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
        holder.songName.setText(mCurrent);

        holder.itemView.setActivated(selectedItems.get(position, false));

        applyClickEvents(holder, position);
    }

    private void applyClickEvents(final HomePageListAdapter.item holder, final int position) {
        holder.linearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getSelectionCount() == 0)
                    mClickListener.fileSelected(mFileList.get(holder.getLayoutPosition()).getName());
                else
                    mClickListener.onLyricItemClicked(position);
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

    public interface LyricFileSelectListener {
        void fileSelected(String fileName);

        void onLyricItemSelected(int position);

        void onLyricItemClicked(int position);
    }

    class item extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        final HomePageListAdapter mAdapter;
        private final LinearLayout linearLayout;
        private final TextView songName;

        public item(View itemView, HomePageListAdapter adapter) {
            super(itemView);
            linearLayout = itemView.findViewById(R.id.lyric_file_parent_linearlayout);
            songName = itemView.findViewById(R.id.song_textview);
            this.mAdapter = adapter;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (getSelectionCount() == 0) {
                String song_name = mFileList.get(getLayoutPosition()).getName();
                if (mClickListener != null) mClickListener.fileSelected(song_name);
            } else {
                mClickListener.onLyricItemClicked(getAdapterPosition());
            }
        }

        @Override
        public boolean onLongClick(View v) {
            mClickListener.onLyricItemSelected(getAdapterPosition());
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return false;
        }

    }
}

