package id.my.sinanonym.stickerwa.core;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import id.my.sinanonym.stickerwa.R;
import id.my.sinanonym.stickerwa.data.StickerRepository;

/**
 * Helper to save sticker images into public Pictures/StickerWA storage.
 */
public final class StickerStorageHelper {

    private static final String SUBFOLDER_NAME = "StickerWA";

    private StickerStorageHelper() {}

    public static void saveStickerToGallery(
            @NonNull Context context,
            @NonNull String packIdentifier,
            @NonNull String stickerFileName,
            boolean isAnimated) {
        final Context appContext = context.getApplicationContext();
        AppExecutors.execute(
                () -> saveInternal(appContext, packIdentifier, stickerFileName, isAnimated),
                savedPath -> Toast.makeText(
                        appContext,
                        appContext.getString(R.string.sticker_saved_success, savedPath),
                        Toast.LENGTH_SHORT
                ).show(),
                error -> Toast.makeText(
                        appContext,
                        appContext.getString(R.string.sticker_saved_failed, error.getMessage()),
                        Toast.LENGTH_LONG
                ).show()
        );
    }

    @NonNull
    private static String saveInternal(
            @NonNull Context context,
            @NonNull String packIdentifier,
            @NonNull String stickerFileName,
            boolean isAnimated) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        byte[] rawBytes = StickerRepository.fetchStickerAsset(packIdentifier, stickerFileName, resolver);

        String baseName = stickerFileName;
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        String sanitizedId = packIdentifier.replaceAll("[^a-zA-Z0-9_-]", "_");

        // Decode static WebP to PNG for universal gallery compatibility, or keep WebP if animated
        Bitmap bitmap = !isAnimated ? BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.length) : null;
        boolean saveAsPng = (bitmap != null);
        String ext = saveAsPng ? ".png" : ".webp";
        String mimeType = saveAsPng ? "image/png" : "image/webp";
        String outFileName = sanitizedId + "_" + baseName + "_" + System.currentTimeMillis() + ext;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, outFileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + SUBFOLDER_NAME);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri itemUri = resolver.insert(collection, values);
            if (itemUri == null) {
                throw new IOException("Failed to create MediaStore entry");
            }

            try (OutputStream out = resolver.openOutputStream(itemUri)) {
                if (out == null) {
                    throw new IOException("Failed to open output stream");
                }
                if (saveAsPng) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                } else {
                    out.write(rawBytes);
                }
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }

            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(itemUri, values, null, null);
            return "Pictures/" + SUBFOLDER_NAME + "/" + outFileName;
        } else {
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File targetDir = new File(picturesDir, SUBFOLDER_NAME);
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw new IOException("Cannot create directory: " + targetDir.getAbsolutePath());
            }
            File outFile = new File(targetDir, outFileName);
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                if (saveAsPng) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                } else {
                    out.write(rawBytes);
                }
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
            MediaScannerConnection.scanFile(
                    context,
                    new String[]{outFile.getAbsolutePath()},
                    new String[]{mimeType},
                    null
            );
            return outFile.getAbsolutePath();
        }
    }
}
