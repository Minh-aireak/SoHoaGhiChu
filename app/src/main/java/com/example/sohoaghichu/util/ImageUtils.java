package com.example.sohoaghichu.util;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Xử lý ảnh: tạo file tạm cho camera, chuẩn hóa ảnh (xoay theo EXIF, thu nhỏ) và lưu vào bộ nhớ app.
 */
public final class ImageUtils {

    /** Cạnh dài tối đa của ảnh lưu lại, đủ nét cho OCR mà không tốn bộ nhớ. */
    private static final int MAX_DIMENSION = 2560;
    private static final int JPEG_QUALITY = 90;
    private static final String IMAGE_DIR = "images";
    private static final String CAMERA_DIR = "camera";

    private ImageUtils() {
    }

    public static String getAuthority(Context context) {
        return context.getPackageName() + ".fileprovider";
    }

    public static File createCameraTempFile(Context context) throws IOException {
        File dir = ensureDir(new File(context.getCacheDir(), CAMERA_DIR));
        return File.createTempFile("CAM_", ".jpg", dir);
    }

    /**
     * Đọc ảnh từ Uri (camera hoặc thư viện), xoay đúng chiều, thu nhỏ rồi lưu thành JPEG
     * trong thư mục riêng của app. Chạy trên background thread.
     */
    public static File importImage(Context context, Uri source) throws IOException {
        ContentResolver resolver = context.getContentResolver();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(resolver, source)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Định dạng ảnh không được hỗ trợ");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION);
        Bitmap decoded;
        try (InputStream in = open(resolver, source)) {
            decoded = BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null) {
            throw new IOException("Không giải mã được ảnh");
        }

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = open(resolver, source)) {
            orientation = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException ignored) {
            // Ảnh không có EXIF (PNG, ảnh chụp màn hình...) thì giữ nguyên chiều
        }

        Bitmap normalized = normalize(decoded, orientation, MAX_DIMENSION);
        File output = File.createTempFile("NOTE_", ".jpg", ensureDir(new File(context.getFilesDir(), IMAGE_DIR)));
        try (OutputStream out = new FileOutputStream(output)) {
            if (!normalized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                throw new IOException("Không lưu được ảnh");
            }
        } catch (IOException e) {
            output.delete();
            throw e;
        } finally {
            normalized.recycle();
        }
        return output;
    }

    /** Giải mã ảnh với kích thước xấp xỉ maxDimension (dùng khi xuất PDF). */
    @Nullable
    public static Bitmap decodeSampled(File file, int maxDimension) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension);
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    public static void deleteImage(@Nullable String path) {
        if (path != null && !path.isEmpty()) {
            new File(path).delete();
        }
    }

    private static int calculateInSampleSize(int width, int height, int maxDimension) {
        int sampleSize = 1;
        while (Math.max(width, height) / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private static Bitmap normalize(Bitmap source, int orientation, int maxDimension) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                break;
        }
        float scale = Math.min(1f, (float) maxDimension / Math.max(source.getWidth(), source.getHeight()));
        if (scale < 1f) {
            matrix.postScale(scale, scale);
        }

        Bitmap result = source;
        if (!matrix.isIdentity()) {
            result = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            if (result != source) source.recycle();
        }

        // JPEG không có kênh alpha: nền trong suốt sẽ thành đen, làm hỏng OCR -> phủ nền trắng
        if (result.hasAlpha()) {
            Bitmap opaque = Bitmap.createBitmap(result.getWidth(), result.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(opaque);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(result, 0, 0, null);
            result.recycle();
            result = opaque;
        }
        return result;
    }

    private static InputStream open(ContentResolver resolver, Uri uri) throws IOException {
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new FileNotFoundException(uri.toString());
        return in;
    }

    private static File ensureDir(File dir) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Không tạo được thư mục " + dir);
        }
        return dir;
    }
}
