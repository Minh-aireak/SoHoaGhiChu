package com.example.sohoaghichu.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.widget.Toast;

import com.example.sohoaghichu.R;
import com.example.sohoaghichu.data.Note;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Xuất ghi chú ra tệp TXT / PDF (người dùng chọn nơi lưu qua Storage Access Framework) và chia sẻ.
 */
public final class ExportUtils {

    public interface ExportTask {
        void run() throws IOException;
    }

    private static final String SEPARATOR = "----------------------------------------";
    private static final String NOTE_SEPARATOR = "========================================";

    // Khổ A4 tính theo point (1/72 inch)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;
    private static final int IMAGE_MAX_HEIGHT = 360;
    private static final int IMAGE_DECODE_SIZE = 1600;

    private ExportUtils() {
    }

    /** Chạy tác vụ xuất tệp ở background rồi báo kết quả bằng Toast. */
    public static void runAsync(Activity activity, Executor executor, ExportTask task) {
        Context appContext = activity.getApplicationContext();
        executor.execute(() -> {
            String error = null;
            try {
                task.run();
            } catch (IOException | RuntimeException e) {
                error = e.getLocalizedMessage() != null ? e.getLocalizedMessage() : e.toString();
            }
            String message = error == null
                    ? appContext.getString(R.string.export_success)
                    : appContext.getString(R.string.export_failed, error);
            activity.runOnUiThread(() -> Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show());
        });
    }

    public static String suggestFileName(String title, String extension) {
        String base = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        if (base.length() > 50) base = base.substring(0, 50).trim();
        if (base.isEmpty()) base = "GhiChu";
        return base + "." + extension;
    }

    // ---------------------------------------------------------------- TXT

    public static String buildNoteText(Context context, Note note) {
        return note.getTitle() + '\n'
                + context.getString(R.string.export_created_at, DateTimeUtils.format(note.getCreatedAt())) + '\n'
                + context.getString(R.string.export_updated_at, DateTimeUtils.format(note.getUpdatedAt())) + '\n'
                + SEPARATOR + "\n\n"
                + (note.getContent().isEmpty() ? context.getString(R.string.no_content) : note.getContent())
                + '\n';
    }

    public static String buildAllNotesText(Context context, List<Note> notes) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(R.string.export_all_header, notes.size())).append('\n');
        sb.append(context.getString(R.string.export_time, DateTimeUtils.format(System.currentTimeMillis())))
                .append("\n\n");
        for (int i = 0; i < notes.size(); i++) {
            if (i > 0) sb.append('\n').append(NOTE_SEPARATOR).append("\n\n");
            sb.append(i + 1).append(". ").append(buildNoteText(context, notes.get(i)));
        }
        return sb.toString();
    }

    public static void writeText(Context context, Uri uri, String text) throws IOException {
        try (OutputStream out = openOutput(context, uri);
             Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }

    // ---------------------------------------------------------------- PDF

    /** Tạo PDF gồm tiêu đề, ngày tạo, ảnh gốc và nội dung văn bản (tự sang trang). */
    public static void writePdf(Context context, Uri uri, Note note) throws IOException {
        PdfDocument document = new PdfDocument();
        try {
            PdfPager pager = new PdfPager(context, document);
            int contentWidth = PAGE_WIDTH - 2 * MARGIN;

            pager.drawText(note.getTitle(), newPaint(18, Typeface.DEFAULT_BOLD, Color.BLACK), contentWidth);
            pager.space(4);
            pager.drawText(context.getString(R.string.export_created_at, DateTimeUtils.format(note.getCreatedAt())),
                    newPaint(10, Typeface.DEFAULT, Color.DKGRAY), contentWidth);
            pager.space(12);

            if (note.hasImage()) {
                Bitmap bitmap = ImageUtils.decodeSampled(new File(note.getImagePath()), IMAGE_DECODE_SIZE);
                if (bitmap != null) {
                    pager.drawBitmap(bitmap, contentWidth, IMAGE_MAX_HEIGHT);
                    bitmap.recycle();
                    pager.space(16);
                }
            }

            String body = note.getContent().isEmpty() ? context.getString(R.string.no_content) : note.getContent();
            pager.drawText(body, newPaint(12, Typeface.DEFAULT, Color.BLACK), contentWidth);
            pager.finishPage();

            try (OutputStream out = openOutput(context, uri)) {
                document.writeTo(out);
            }
        } finally {
            document.close();
        }
    }

    // ---------------------------------------------------------------- Chia sẻ

    public static void shareText(Context context, Note note) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, note.getTitle());
        send.putExtra(Intent.EXTRA_TEXT, note.getTitle() + "\n\n" + note.getContent());
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser_title)));
    }

    private static OutputStream openOutput(Context context, Uri uri) throws IOException {
        OutputStream out = context.getContentResolver().openOutputStream(uri);
        if (out == null) throw new FileNotFoundException(uri.toString());
        return out;
    }

    private static TextPaint newPaint(float size, Typeface typeface, int color) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size);
        paint.setTypeface(typeface);
        paint.setColor(color);
        return paint;
    }

    /** Quản lý vị trí vẽ hiện tại và tự tạo trang mới khi hết chỗ. */
    private static final class PdfPager {
        private final Context context;
        private final PdfDocument document;
        private final TextPaint footerPaint = newPaint(9, Typeface.DEFAULT, Color.GRAY);
        private PdfDocument.Page page;
        private Canvas canvas;
        private int pageNumber;
        private float y;

        PdfPager(Context context, PdfDocument document) {
            this.context = context;
            this.document = document;
        }

        void space(float dy) {
            y += dy;
        }

        void drawText(String text, TextPaint paint, int width) {
            ensurePage();
            StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                    .setLineSpacing(0f, 1.2f)
                    .build();
            int line = 0;
            int lineCount = layout.getLineCount();
            while (line < lineCount) {
                if (layout.getLineBottom(line) - layout.getLineTop(line) > remaining()) newPage();

                // Lấy nhiều dòng nhất vừa với phần còn lại của trang
                int first = line;
                int top = layout.getLineTop(first);
                while (line < lineCount && layout.getLineBottom(line) - top <= remaining()) line++;
                if (line == first) line++; // dòng cao hơn cả trang: vẫn vẽ để không lặp vô hạn
                int bottom = layout.getLineBottom(line - 1);

                canvas.save();
                canvas.translate(MARGIN, y - top);
                canvas.clipRect(0, top, width, bottom);
                layout.draw(canvas);
                canvas.restore();
                y += bottom - top;
            }
        }

        void drawBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
            ensurePage();
            float scale = Math.min((float) maxWidth / bitmap.getWidth(), (float) maxHeight / bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            if (height > remaining()) newPage();

            float left = MARGIN + (maxWidth - width) / 2f;
            canvas.drawBitmap(bitmap, null, new RectF(left, y, left + width, y + height),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            y += height;
        }

        void finishPage() {
            if (page == null) return;
            String footer = context.getString(R.string.pdf_page_number, pageNumber);
            canvas.drawText(footer, (PAGE_WIDTH - footerPaint.measureText(footer)) / 2f,
                    PAGE_HEIGHT - MARGIN / 2f, footerPaint);
            document.finishPage(page);
            page = null;
            canvas = null;
        }

        private void ensurePage() {
            if (page == null) newPage();
        }

        private void newPage() {
            finishPage();
            pageNumber++;
            page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
            canvas = page.getCanvas();
            y = MARGIN;
        }

        private float remaining() {
            return PAGE_HEIGHT - MARGIN - y;
        }
    }
}
