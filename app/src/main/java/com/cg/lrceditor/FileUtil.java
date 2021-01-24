package com.cg.lrceditor;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.List;

public final class FileUtil {
	private static final String PRIMARY_VOLUME_NAME = "primary";

	// From: https://stackoverflow.com/a/36162691 (Thanks @Anonymous)
	@Nullable
	static String getFullPathFromTreeUri(@Nullable final Uri treeUri, Context con) {
		if (treeUri == null) return null;
		else if (treeUri.toString().equals("content://com.android.providers.downloads.documents/tree/downloads")) {
			// The root of the Downloads folder was selected from the Downloads option in the sidebar
			return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
		}
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

	// Thanks @Jonathan Martinez
	@Nullable
	static DocumentFile getDocumentFileFromPath(@Nullable final Uri treeUri, String path, Context ctx) {
		if (treeUri == null || !treeUri.getScheme().contains("content") || treeUri.getAuthority() == null) {
			// Invalid treeUri, attempt to create a DocumentFile from the provided path via a File
			return DocumentFile.fromFile(new File(path));
		}

		String treePath = getFullPathFromTreeUri(treeUri, ctx);
		if (treePath == null || !path.contains(treePath)) {
			// Invalid path for the provided treeUri, attempt to create a DocumentFile from the provided path via a File
			return DocumentFile.fromFile(new File(path));
		}

		Uri fileUri = Uri.parse(path.substring(treePath.length()));
		DocumentFile file = DocumentFile.fromTreeUri(ctx, treeUri);

		List<String> pathSegments = fileUri.getPathSegments();
		for (String pathSegment : pathSegments) {
			file = file.findFile(pathSegment);
		}

		return file;
	}
}
