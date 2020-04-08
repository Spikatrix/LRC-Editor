package com.cg.lrceditor;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;

public final class FileUtil {
	private static final String PRIMARY_VOLUME_NAME = "primary";

	// From: https://stackoverflow.com/a/36162691 (Thanks @Anonymous)
	@Nullable
	static String getFullPathFromTreeUri(@Nullable final Uri treeUri, Context con) {
		if (treeUri == null) return null;
		String volumePath = getVolumePath(getVolumeIdFromTreeUri(treeUri), con);
		if (volumePath == null) return File.separator;
		if (volumePath.endsWith(File.separator))
			volumePath = volumePath.substring(0, volumePath.length() - 1);

		String documentPath = getDocumentPathFromTreeUri(treeUri);
		if (documentPath.endsWith(File.separator))
			documentPath = documentPath.substring(0, documentPath.length() - 1);

		if (documentPath.length() > 0) {
			if (documentPath.startsWith(File.separator))
				return volumePath + documentPath;
			else
				return volumePath + File.separator + documentPath;
		} else return volumePath;
	}

	// From: https://stackoverflow.com/a/36162691 (Thanks @Anonymous)
	private static String getVolumePath(final String volumeId, Context context) {
		try {
			StorageManager mStorageManager =
				(StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
			Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
			Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
			Method getUuid = storageVolumeClazz.getMethod("getUuid");
			Method getPath = storageVolumeClazz.getMethod("getPath");
			Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
			Object result = getVolumeList.invoke(mStorageManager);

			final int length = Array.getLength(result);
			for (int i = 0; i < length; i++) {
				Object storageVolumeElement = Array.get(result, i);
				String uuid = (String) getUuid.invoke(storageVolumeElement);
				Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);

				// primary volume?
				if (primary && PRIMARY_VOLUME_NAME.equals(volumeId))
					return (String) getPath.invoke(storageVolumeElement);

				// other volumes?
				if (uuid != null && uuid.equals(volumeId))
					return (String) getPath.invoke(storageVolumeElement);
			}
			// not found.
			return null;
		} catch (Exception ex) {
			return null;
		}
	}

	// From: https://stackoverflow.com/a/36162691 (Thanks @Anonymous)
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static String getVolumeIdFromTreeUri(final Uri treeUri) {
		final String docId = DocumentsContract.getTreeDocumentId(treeUri);
		final String[] split = docId.split(":");
		if (split.length > 0) return split[0];
		else return null;
	}

	// From: https://stackoverflow.com/a/36162691 (Thanks @Anonymous)
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static String getDocumentPathFromTreeUri(final Uri treeUri) {
		final String docId = DocumentsContract.getTreeDocumentId(treeUri);
		final String[] split = docId.split(":");
		if ((split.length >= 2) && (split[1] != null)) return split[1];
		else return File.separator;
	}

	// From: https://stackoverflow.com/a/25005243/ (Thanks @Stefan Haustein)
	static String getFileName(Context ctx, Uri uri) {
		String result = null;
		if (uri.getScheme().equals("content")) {
			try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
				if (cursor != null && cursor.moveToFirst()) {
					result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
				}
			}
		}
		if (result == null) {
			result = uri.getPath();
			int cut = result.lastIndexOf('/');
			if (cut != -1) {
				result = result.substring(cut + 1);
			}
		}
		return result;
	}

	static String stripFileNameFromPath(String path) {
		try {
			return path.substring(0, path.lastIndexOf(File.separator));
		} catch (IndexOutOfBoundsException e) { // Should not happen for the most part
			return path;
		}
	}

	static DocumentFile getPersistableDocumentFile(Uri uri, String location, Context ctx) {
		DocumentFile pickedDir;
		try {
			pickedDir = DocumentFile.fromTreeUri(ctx, uri);
			try {
				ctx.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			} catch (SecurityException e) {
				e.printStackTrace();
			}
		} catch (IllegalArgumentException | NullPointerException e) {
			pickedDir = DocumentFile.fromFile(new File(location));
		}

		return pickedDir;
	}

	public static DocumentFile searchForFile(DocumentFile documentFile, String name) {
		/* This is noticably VERY slow. But since we already know the absolute path of the file, we can optimize it (see the optimized method below) */

		DocumentFile f;
		if ((f = documentFile.findFile(name)) != null)
			return f;

		ArrayList<DocumentFile> list = new ArrayList<>();
		do {
			DocumentFile[] allFiles = documentFile.listFiles();
			for (DocumentFile file : allFiles) {
				if (file.isDirectory()) {
					list.add(file);
				}
			}

			documentFile = list.remove(list.size() - 1);
			if ((f = documentFile.findFile(name)) != null) {
				return f;
			}
		} while (!list.isEmpty());

		return null;
	}

	static DocumentFile searchForFileOptimized(DocumentFile pickedDir, String location, String name, File[] storageMedias) {
		/* Speeds up the search using the fact that we already have the absolute path of the file */

		/* First, we need to get the absolute path of mounted storages (Internal storage, SD Card etc) */
		/* So we get the app's public directory and split on "/Android" because it will always(?) contain it */
		/* The first argument of the split is the absolute path of the mounted storage */
		/* Then, we split it on the set read location and the second argument is the location from the mounted storage */
		/* Next, split it on "/" to get a list of folders to search to */
		/* Finally, navigate through them to find the required file */

		String[] folders = null;
		for (File file : storageMedias) {
			String storageMediaPath;
			try {
				storageMediaPath = file.getAbsolutePath().split("/Android")[0];
			} catch (NullPointerException e) { // Got a crash report
				continue;
			}

			if (location.startsWith(storageMediaPath)) {
				String path;
				try {
					path = location.split(storageMediaPath)[1];
				} catch (ArrayIndexOutOfBoundsException e) {
					/* File is in the same directory as the mount point/picked directory */
					return pickedDir.findFile(name);
				}

				folders = path.split("/");
				/* `folders[0]` will be empty as the first character in `path` is a '/' */
			}
		}

		DocumentFile f = null;
		int index = 1;

		try {
			if (index < folders.length) {
				DocumentFile[] allFiles = pickedDir.listFiles();
				do {
					for (DocumentFile file : allFiles) {
						if (file.getName().equals(folders[index])) {
							f = file;
							allFiles = file.listFiles();
							break;
						}
					}

					index++;
				} while (index < folders.length);
			}
		} catch (NullPointerException e) {
			return pickedDir.findFile(name);
		}

		if (f == null) {
			return pickedDir.findFile(name);
		}

		return f.findFile(name);
	}
}
