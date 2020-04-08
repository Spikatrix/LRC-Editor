package com.cg.lrceditor;

import android.content.Context;
import android.text.Html;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomePageListAdapter extends RecyclerView.Adapter<HomePageListAdapter.LyricFileListItem> implements Filterable {
	ArrayList<HomePageListItem> listData;
	ArrayList<HomePageListItem> backupListData;

	private LayoutInflater inflater;

	private boolean isDarkTheme;

	private LyricFileSelectListener clickListener;

	HomePageListAdapter(Context context, boolean isDarkTheme) {
		inflater = LayoutInflater.from(context);
		this.listData = new ArrayList<>();
		this.isDarkTheme = isDarkTheme;
		this.backupListData = new ArrayList<>();
	}

	@NonNull
	@Override
	public LyricFileListItem onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View mItemView = inflater.inflate(R.layout.row_lyricfile_item, parent, false);
		return new LyricFileListItem(mItemView);
	}

	@Override
	public void onBindViewHolder(@NonNull LyricFileListItem holder, int position) {
		String name = listData.get(position).file.getName();
		holder.fileName.setText(name);

		String location = listData.get(position).file.getAbsolutePath();
		holder.fileLocation.setText(FileUtil.stripFileNameFromPath(location));

		if (listData.get(position).isExpanded) {
			holder.subView.setVisibility(View.VISIBLE);
			holder.expandableButton.setRotation(180);

			if (listData.get(position).songMetaData != null) {
				displaySongMetaData(holder, listData.get(position).songMetaData);
				displayLyricContents(holder, listData.get(position).lyrics);
			}
		} else {
			holder.subView.setVisibility(View.GONE);
			holder.expandableButton.setRotation(0);
		}

		holder.itemView.setActivated(listData.get(position).isSelected);

		applyClickEvents(holder);
	}

	private void applyClickEvents(final LyricFileListItem holder) {
		holder.linearLayout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View view) {
				if (getSelectionCount() == 0) {
					HomePageListItem item = listData.get(holder.getAdapterPosition());
					if (item.isExpanded) {
						item.isExpanded = false;
						item.songMetaData = null;
						item.lyrics = null;
						holder.expandableButton.animate().rotation(0).setDuration(300).start();
					} else {
						item.isExpanded = true;
						holder.expandableButton.animate().rotation(180).setDuration(300).start();

						clearExpandedData(holder);
						previewLrcFileContents(holder, view);
					}

					notifyItemChanged(holder.getAdapterPosition());
				} else {
					clickListener.onLyricItemClicked(holder.getAdapterPosition());
				}
			}
		});

		holder.linearLayout.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View view) {
				clickListener.onLyricItemSelected(holder.getAdapterPosition());
				view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
				return true;
			}
		});
	}

	private void previewLrcFileContents(final LyricFileListItem holder, final View view) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				final LyricReader r = new LyricReader(holder.fileLocation.getText().toString(), holder.fileName.getText().toString(), holder.linearLayout.getContext());
				if (r.getErrorMsg() != null || !r.readLyrics()) {
					view.post(new Runnable() {
						@Override
						public void run() {
							String errorMsg = "<font color=\"" + ContextCompat.getColor(view.getContext(), R.color.errorColor) + "\">" + r.getErrorMsg() + "</font>";
							holder.lyricsTextview.setText(Html.fromHtml(errorMsg));

							String[] msg = new String[1];
							msg[0] = errorMsg;
							listData.get(holder.getAdapterPosition()).songMetaData = new SongMetaData();
							listData.get(holder.getAdapterPosition()).lyrics = msg;
						}
					});
					return;
				}

				String[] lyrics = r.getLyrics();
				Timestamp[] timestamp = r.getTimestamps();

				final String[] lyricsToDisplay;
				final Context ctx = view.getContext();
				int homePageTimestampColor = ContextCompat.getColor(ctx, R.color.homepageTimestampColor);
				int homePageLyricColor = ContextCompat.getColor(ctx, R.color.homepageLyricColor);
				if (lyrics.length > 8) {
					lyricsToDisplay = new String[9];
					for (int i = 0; i < 4; i++)
						lyricsToDisplay[i] = "<font color=\"" + homePageTimestampColor + "\">[" + timestamp[i] + "]</font> <font color=\"" + homePageLyricColor + "\">" + lyrics[i] + "</font>";
					lyricsToDisplay[4] = "......\n";
					for (int i = lyrics.length - 4, j = 5; i < lyrics.length; i++, j++) {
						lyricsToDisplay[j] = "<font color=\"" + homePageTimestampColor + "\">[" + timestamp[i] + "]</font> <font color=\"" + homePageLyricColor + "\">" + lyrics[i] + "</font>";
					}
				} else {
					lyricsToDisplay = new String[lyrics.length];
					for (int i = 0; i < lyrics.length; i++) {
						lyricsToDisplay[i] = "<font color=\"" + homePageTimestampColor + "\">[" + timestamp[i] + "]</font> <font color=\"" + homePageLyricColor + "\">" + lyrics[i] + "</font>";
					}
				}

				final SongMetaData songMetaData = r.getSongMetaData();

				listData.get(holder.getAdapterPosition()).songMetaData = songMetaData;
				listData.get(holder.getAdapterPosition()).lyrics = lyricsToDisplay;

				view.post(new Runnable() {
					@Override
					public void run() {
						displaySongMetaData(holder, songMetaData);
						displayLyricContents(holder, lyricsToDisplay);
					}
				});
			}
		}).start();
	}

	private void displaySongMetaData(LyricFileListItem holder, SongMetaData songMetaData) {
		Context ctx = holder.linearLayout.getContext();
		String string;

		string = songMetaData.getSongName();
		if (string.trim().isEmpty())
			string = "N/A";
		holder.songName.setText(String.format(Locale.getDefault(), "%s %s", ctx.getString(R.string.song_name_prompt), string));

		string = songMetaData.getArtistName();
		if (string.trim().isEmpty())
			string = "N/A";
		holder.artistName.setText(String.format(Locale.getDefault(), "%s %s", ctx.getString(R.string.artist_name_prompt), string));

		string = songMetaData.getAlbumName();
		if (string.trim().isEmpty())
			string = "N/A";
		holder.albumName.setText(String.format(Locale.getDefault(), "%s %s", ctx.getString(R.string.album_name_prompt), string));

		string = songMetaData.getComposerName();
		if (string.trim().isEmpty())
			string = "N/A";
		holder.composerName.setText(String.format(Locale.getDefault(), "%s %s", ctx.getString(R.string.composer_prompt), string));
	}

	private void displayLyricContents(LyricFileListItem holder, String[] lyricsToDisplay) {
		holder.lyricsTextview.setText("");
		for (String line : lyricsToDisplay) {
			holder.lyricsTextview.append(Html.fromHtml(line));
			holder.lyricsTextview.append("\n");
		}
	}

	private void clearExpandedData(LyricFileListItem holder) {
		Context ctx = holder.linearLayout.getContext();
		holder.songName.setText(ctx.getString(R.string.song_name_prompt));
		holder.artistName.setText(ctx.getString(R.string.artist_name_prompt));
		holder.albumName.setText(ctx.getString(R.string.album_name_prompt));
		holder.composerName.setText(ctx.getString(R.string.composer_prompt));
		holder.lyricsTextview.setText(ctx.getString(R.string.loading_lyrics));
	}

	@Override
	public int getItemCount() {
		return listData.size();
	}

	void setClickListener(LyricFileSelectListener itemClickListener) {
		this.clickListener = itemClickListener;
	}

	void toggleSelection(int pos) {
		listData.get(pos).isSelected = !listData.get(pos).isSelected;
		notifyItemChanged(pos);
	}

	void selectAll() {
		for (int i = 0; i < getItemCount(); i++)
			listData.get(i).isSelected = true;
		notifyDataSetChanged();
	}

	void clearExpandedItems() {
		for (int i = 0, len = getItemCount(); i < len; i++) {
			HomePageListItem item = listData.get(i);
			if (item.isExpanded) {
				item.isExpanded = false;
				item.songMetaData = null;
				item.lyrics = null;
				notifyItemChanged(i);
			}
		}
	}

	void clearSelections() {
		for (int i = 0, len = getItemCount(); i < len; i++) {
			HomePageListItem item = listData.get(i);
			if (item.isSelected) {
				item.isSelected = false;
				notifyItemChanged(i);
			}
		}
	}

	List<Integer> getSelectedItemIndices() {
		List<Integer> items = new ArrayList<>();
		for (int i = 0; i < getItemCount(); i++) {
			if (listData.get(i).isSelected)
				items.add(i);
		}
		return items;
	}

	int getSelectionCount() {
		int noOfSelectedItems = 0;
		for (HomePageListItem item : listData) {
			if (item.isSelected)
				noOfSelectedItems++;
		}

		return noOfSelectedItems;
	}

	@Override
	public Filter getFilter() {
		return new Filter() {
			@Override
			protected void publishResults(CharSequence constraint, FilterResults results) {
				listData = (ArrayList<HomePageListItem>) results.values;
				notifyDataSetChanged();
			}

			@Override
			protected FilterResults performFiltering(CharSequence constraint) {
				ArrayList<HomePageListItem> filteredResults;
				if (constraint.length() == 0) {
					filteredResults = backupListData;
				} else {
					filteredResults = getFilteredResults(constraint.toString().toLowerCase());
				}

				FilterResults results = new FilterResults();
				results.values = filteredResults;

				return results;
			}
		};
	}

	private ArrayList<HomePageListItem> getFilteredResults(String constraint) {
		ArrayList<HomePageListItem> results = new ArrayList<>();

		for (HomePageListItem item : backupListData) {
			if (item.file.getName().toLowerCase().contains(constraint)) {
				results.add(item);
			}
		}
		return results;
	}

	public interface LyricFileSelectListener {
		void fileSelected(String fileLocation, String fileName);

		void onLyricItemSelected(int position);

		void onLyricItemClicked(int position);
	}

	class LyricFileListItem extends RecyclerView.ViewHolder {

		private final LinearLayout linearLayout;

		private final TextView fileName;
		private final TextView fileLocation;
		private ImageView expandableButton;

		private LinearLayout subView;

		private TextView songName;
		private TextView albumName;
		private TextView artistName;
		private TextView composerName;

		private TextView lyricsTextview;

		LyricFileListItem(View itemView) {
			super(itemView);

			linearLayout = itemView.findViewById(R.id.lyricfile_parent_linearlayout);
			fileName = itemView.findViewById(R.id.filename_textview);
			fileLocation = itemView.findViewById(R.id.filelocation_textview);
			fileName.setSelected(true);
			fileLocation.setSelected(true);

			subView = itemView.findViewById(R.id.lrc_details);
			Button editButton = itemView.findViewById(R.id.edit_button);
			expandableButton = itemView.findViewById(R.id.expandable_button);

			if (isDarkTheme) {
				expandableButton.setImageDrawable(itemView.getContext().getDrawable(R.drawable.ic_arrow_drop_down_light));
			}

			songName = itemView.findViewById(R.id.songname_textview);
			artistName = itemView.findViewById(R.id.artistname_textview);
			albumName = itemView.findViewById(R.id.albumname_textview);
			composerName = itemView.findViewById(R.id.composername_textview);

			lyricsTextview = itemView.findViewById(R.id.lyrics_textview);

			editButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					String fileName = listData.get(getLayoutPosition()).file.getName();
					String fileLocation = FileUtil.stripFileNameFromPath(listData.get(getLayoutPosition()).file.getAbsolutePath());
					if (clickListener != null) clickListener.fileSelected(fileLocation, fileName);
				}
			});
		}
	}
}

