package id.my.sinanonym.stickerwa.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Handles image scaling, aspect-ratio preservation, transparent padding,
 * and WebP compression adhering to WhatsApp sticker specification:
 * - Sticker: exactly 512x512 pixels, WebP format, <= 100 KB.
 * - Tray icon: exactly 96x96 pixels, WebP format, <= 50 KB.
 */
public final class StickerImageConverter {

    private static final int STICKER_CANVAS_SIZE = 512;
    private static final int STICKER_SAFE_CONTENT_SIZE = 480; // 16px transparent margin on all sides
    private static final int STICKER_MAX_BYTES = 100 * 1024; // 100 KB

    private static final int TRAY_CANVAS_SIZE = 96;
    private static final int TRAY_SAFE_CONTENT_SIZE = 88;
    private static final int TRAY_MAX_BYTES = 50 * 1024; // 50 KB

    private StickerImageConverter() {}

    @NonNull
    public static byte[] convertToStickerWebp(@NonNull Context context, @NonNull Uri uri) throws IOException {
        Bitmap rawBitmap = loadBitmapFromUri(context, uri, STICKER_CANVAS_SIZE);
        if (rawBitmap == null) {
            throw new IOException("Unable to decode image from uri: " + uri);
        }

        Bitmap rotatedBitmap = applyExifRotation(context, uri, rawBitmap);
        Bitmap scaledToCanvas = renderOntoSquareCanvas(rotatedBitmap, STICKER_CANVAS_SIZE, STICKER_SAFE_CONTENT_SIZE);
        if (rotatedBitmap != scaledToCanvas && !rotatedBitmap.isRecycled()) {
            rotatedBitmap.recycle();
        }

        byte[] compressed = compressToWebpWithLimit(scaledToCanvas, STICKER_MAX_BYTES);
        scaledToCanvas.recycle();
        return compressed;
    }

    @NonNull
    public static byte[] convertToTrayWebp(@NonNull Context context, @NonNull Uri uri) throws IOException {
        Bitmap rawBitmap = loadBitmapFromUri(context, uri, TRAY_CANVAS_SIZE);
        if (rawBitmap == null) {
            throw new IOException("Unable to decode tray icon from uri: " + uri);
        }

        Bitmap rotatedBitmap = applyExifRotation(context, uri, rawBitmap);
        Bitmap scaledToCanvas = renderOntoSquareCanvas(rotatedBitmap, TRAY_CANVAS_SIZE, TRAY_SAFE_CONTENT_SIZE);
        if (rotatedBitmap != scaledToCanvas && !rotatedBitmap.isRecycled()) {
            rotatedBitmap.recycle();
        }

        byte[] compressed = compressToWebpWithLimit(scaledToCanvas, TRAY_MAX_BYTES);
        scaledToCanvas.recycle();
        return compressed;
    }

    private static Bitmap loadBitmapFromUri(Context context, Uri uri, int targetSize) throws IOException {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, boundsOptions);
        }

        int width = boundsOptions.outWidth;
        int height = boundsOptions.outHeight;
        if (width <= 0 || height <= 0) {
            return null;
        }

        int inSampleSize = 1;
        while ((width / 2) >= targetSize && (height / 2) >= targetSize) {
            width /= 2;
            height /= 2;
            inSampleSize *= 2;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = inSampleSize;
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, decodeOptions);
        }
    }

    private static Bitmap applyExifRotation(Context context, Uri uri, Bitmap bitmap) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return bitmap;
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.preScale(-1.0f, 1.0f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.preScale(1.0f, -1.0f);
                    break;
                default:
                    return bitmap;
            }
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (Exception e) {
            return bitmap;
        }
    }

    private static Bitmap renderOntoSquareCanvas(Bitmap source, int canvasSize, int maxContentSize) {
        Bitmap canvasBitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBitmap);

        int srcWidth = source.getWidth();
        int srcHeight = source.getHeight();

        float scale = Math.min((float) maxContentSize / srcWidth, (float) maxContentSize / srcHeight);
        int destWidth = Math.round(srcWidth * scale);
        int destHeight = Math.round(srcHeight * scale);

        int left = (canvasSize - destWidth) / 2;
        int top = (canvasSize - destHeight) / 2;

        Bitmap scaledSource = Bitmap.createScaledBitmap(source, destWidth, destHeight, true);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(scaledSource, left, top, paint);

        if (scaledSource != source) {
            scaledSource.recycle();
        }
        return canvasBitmap;
    }

    private static byte[] compressToWebpWithLimit(Bitmap bitmap, int maxBytes) throws IOException {
        Bitmap.CompressFormat format;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            format = Bitmap.CompressFormat.WEBP_LOSSY;
        } else {
            format = Bitmap.CompressFormat.WEBP;
        }

        int quality = 88;
        byte[] result = null;

        while (quality >= 20) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(format, quality, baos);
            byte[] bytes = baos.toByteArray();
            if (bytes.length <= maxBytes) {
                result = bytes;
                break;
            }
            quality -= 12;
            result = bytes;
        }

        if (result == null || result.length > maxBytes) {
            // One last attempt at quality 15
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(format, 15, baos);
            result = baos.toByteArray();
        }

        return result;
    }
}
