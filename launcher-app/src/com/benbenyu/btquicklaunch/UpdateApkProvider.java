package com.benbenyu.btquicklaunch;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Serves the downloaded update APK over content:// without AndroidX FileProvider. */
public class UpdateApkProvider extends ContentProvider {
  static final String AUTHORITY = "com.benbenyu.btquicklaunch.update";
  static final String APK_NAME = "BT-QuickLaunch-update.apk";

  static File apkFile(android.content.Context context) {
    return new File(new File(context.getCacheDir(), "updates"), APK_NAME);
  }

  static Uri contentUri() {
    return Uri.parse("content://" + AUTHORITY + "/" + APK_NAME);
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public String getType(Uri uri) {
    return "application/vnd.android.package-archive";
  }

  @Override
  public Cursor query(
      Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    if (!isAllowed(uri)) return null;
    File file = apkFile(getContext());
    String[] cols =
        projection != null && projection.length > 0
            ? projection
            : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
    MatrixCursor cursor = new MatrixCursor(cols, 1);
    Object[] row = new Object[cols.length];
    for (int i = 0; i < cols.length; i++) {
      if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = APK_NAME;
      else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = file.exists() ? file.length() : 0L;
    }
    cursor.addRow(row);
    return cursor;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!isAllowed(uri) || (mode != null && !mode.contains("r"))) {
      throw new FileNotFoundException("Invalid update URI");
    }
    File file = apkFile(getContext());
    if (!file.isFile()) throw new FileNotFoundException("Update APK not found");
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
  }

  private boolean isAllowed(Uri uri) {
    return uri != null && getContext() != null && APK_NAME.equals(uri.getLastPathSegment());
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }
}
