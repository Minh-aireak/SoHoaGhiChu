package com.example.sohoaghichu;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.sohoaghichu.data.Note;
import com.example.sohoaghichu.data.NoteDbHelper;
import com.example.sohoaghichu.ocr.OcrHelper;
import com.example.sohoaghichu.util.DateTimeUtils;
import com.example.sohoaghichu.util.ExportUtils;
import com.example.sohoaghichu.util.ImageUtils;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tạo ghi chú mới từ ảnh (tự chạy OCR) hoặc xem/sửa ghi chú đã lưu.
 * Từ đây có thể nhận dạng lại, sao chép, xuất TXT/PDF, chia sẻ và xóa.
 * Lớp Java này dùng các lớp Kotlin Note, NoteDbHelper, DateTimeUtils.
 */
public class NoteEditActivity extends AppCompatActivity {

    public static final String EXTRA_NOTE_ID = "extra_note_id";
    public static final String EXTRA_IMAGE_PATH = "extra_image_path";
    private static final String STATE_OCR_DONE = "state_ocr_done";
    private static final int MAX_TITLE_LENGTH = 60;

    private MaterialToolbar toolbar;
    private LinearProgressIndicator progressOcr;
    private MaterialCardView cardImage;
    private ImageView ivImage;
    private TextView tvOcrStatus, tvStats;
    private TextInputEditText edtTitle, edtContent;

    private NoteDbHelper db;
    private OcrHelper ocrHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Ghi chú đang mở; id = -1 nghĩa là ghi chú mới chưa lưu. */
    private Note note;
    private String imagePath;
    private String originalTitle = "";
    private String originalContent = "";
    private boolean ocrDone;
    private boolean ocrRunning;

    private final ActivityResultLauncher<String> exportTxtLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
                if (uri == null) return;
                Note snapshot = buildSnapshot();
                ExportUtils.runAsync(this, executor,
                        () -> ExportUtils.writeText(this, uri, ExportUtils.buildNoteText(this, snapshot)));
            });

    private final ActivityResultLauncher<String> exportPdfLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
                if (uri == null) return;
                Note snapshot = buildSnapshot();
                ExportUtils.runAsync(this, executor, () -> ExportUtils.writePdf(this, uri, snapshot));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_note_edit);

        toolbar = findViewById(R.id.toolbar);
        progressOcr = findViewById(R.id.progressOcr);
        cardImage = findViewById(R.id.cardImage);
        ivImage = findViewById(R.id.ivImage);
        tvOcrStatus = findViewById(R.id.tvOcrStatus);
        tvStats = findViewById(R.id.tvStats);
        edtTitle = findViewById(R.id.edtTitle);
        edtContent = findViewById(R.id.edtContent);
        AppBarLayout appBar = findViewById(R.id.appBar);

        db = NoteDbHelper.getInstance(this);
        ocrHelper = new OcrHelper();

        long noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1);
        if (noteId != -1) {
            note = db.getNoteById(noteId);
            if (note == null) {
                Toast.makeText(this, R.string.note_not_found, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            imagePath = note.getImagePath();
            originalTitle = note.getTitle();
            originalContent = note.getContent();
            edtTitle.setText(originalTitle);
            edtContent.setText(originalContent);
            ocrDone = true;
        } else {
            note = new Note();
            imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
            ocrDone = savedInstanceState != null && savedInstanceState.getBoolean(STATE_OCR_DONE);
        }

        setupToolbar();
        showImage();

        edtContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateStats();
            }
        });
        updateStats();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            appBar.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        // Ghi chú mới: tự nhận dạng ngay (chạy lại nếu bị xoay màn hình giữa chừng)
        if (!ocrDone && imagePath != null) startOcr(false);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_OCR_DONE, ocrDone);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ocrHelper != null) ocrHelper.close();
        executor.shutdown();
    }

    private boolean isNewNote() {
        return note.getId() == -1;
    }

    private void setupToolbar() {
        toolbar.setTitle(isNewNote() ? R.string.title_new_note : R.string.title_edit_note);
        toolbar.setNavigationOnClickListener(v -> handleBack());
        toolbar.inflateMenu(R.menu.menu_note_edit);
        Menu menu = toolbar.getMenu();
        menu.findItem(R.id.action_ocr_again).setVisible(imagePath != null);
        menu.findItem(R.id.action_delete).setVisible(!isNewNote());
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            saveNote();
        } else if (id == R.id.action_copy) {
            copyContent();
        } else if (id == R.id.action_ocr_again) {
            confirmOcrAgain();
        } else if (id == R.id.action_export_txt) {
            exportTxtLauncher.launch(ExportUtils.suggestFileName(buildSnapshot().getTitle(), "txt"));
        } else if (id == R.id.action_export_pdf) {
            exportPdfLauncher.launch(ExportUtils.suggestFileName(buildSnapshot().getTitle(), "pdf"));
        } else if (id == R.id.action_share) {
            ExportUtils.shareText(this, buildSnapshot());
        } else if (id == R.id.action_delete) {
            confirmDelete();
        } else {
            return false;
        }
        return true;
    }

    private void showImage() {
        if (imagePath == null) {
            cardImage.setVisibility(View.GONE);
            return;
        }
        Glide.with(this).load(new File(imagePath)).into(ivImage);
        cardImage.setOnClickListener(v -> openImageViewer());
    }

    /** Mở ảnh gốc bằng ứng dụng xem ảnh để phóng to. */
    private void openImageViewer() {
        Uri uri = FileProvider.getUriForFile(this, ImageUtils.getAuthority(this), new File(imagePath));
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "image/jpeg")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_no_viewer, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- OCR

    private void confirmOcrAgain() {
        if (ocrRunning) return;
        if (textOf(edtContent).trim().isEmpty()) {
            startOcr(false);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_ocr_title)
                .setMessage(R.string.dialog_ocr_message)
                .setPositiveButton(R.string.btn_replace, (d, w) -> startOcr(false))
                .setNeutralButton(R.string.btn_append, (d, w) -> startOcr(true))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void startOcr(boolean append) {
        setOcrRunning(true);
        ocrHelper.recognize(this, new File(imagePath), new OcrHelper.Callback() {
            @Override
            public void onSuccess(@NonNull String text, int lineCount) {
                if (isFinishing() || isDestroyed()) return;
                setOcrRunning(false);
                ocrDone = true;
                if (text.isEmpty()) {
                    showStatus(getString(R.string.ocr_empty));
                    return;
                }
                String current = textOf(edtContent).trim();
                edtContent.setText(append && !current.isEmpty() ? current + "\n\n" + text : text);
                if (textOf(edtTitle).trim().isEmpty()) edtTitle.setText(suggestTitle(text));
                showStatus(getString(R.string.ocr_done, lineCount));
            }

            @Override
            public void onFailure(@NonNull Exception e) {
                if (isFinishing() || isDestroyed()) return;
                setOcrRunning(false);
                ocrDone = true;
                showStatus(getString(R.string.ocr_failed, e.getLocalizedMessage()));
            }
        });
    }

    private void setOcrRunning(boolean running) {
        ocrRunning = running;
        progressOcr.setVisibility(running ? View.VISIBLE : View.GONE);
        toolbar.getMenu().findItem(R.id.action_ocr_again).setEnabled(!running);
        if (running) showStatus(getString(R.string.ocr_running));
    }

    private void showStatus(String message) {
        tvOcrStatus.setVisibility(View.VISIBLE);
        tvOcrStatus.setText(message);
    }

    // ---------------------------------------------------------------- Lưu / xóa

    private void saveNote() {
        String title = textOf(edtTitle).trim();
        String content = textOf(edtContent).trim();
        if (title.isEmpty() && content.isEmpty() && imagePath == null) {
            Toast.makeText(this, R.string.note_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        long now = System.currentTimeMillis();
        note.setTitle(resolveTitle(title, content));
        note.setContent(content);
        note.setImagePath(imagePath);
        note.setUpdatedAt(now);
        if (isNewNote()) {
            note.setCreatedAt(now);
            db.insertNote(note);
        } else {
            db.updateNote(note);
        }
        Toast.makeText(this, R.string.note_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, note.getTitle()))
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_delete, (d, w) -> {
                    db.deleteNote(note.getId());
                    ImageUtils.deleteImage(imagePath);
                    Toast.makeText(this, R.string.note_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    private boolean hasUnsavedChanges() {
        return isNewNote()
                || !textOf(edtTitle).trim().equals(originalTitle.trim())
                || !textOf(edtContent).trim().equals(originalContent.trim());
    }

    private void handleBack() {
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_unsaved_title)
                .setMessage(R.string.dialog_unsaved_message)
                .setPositiveButton(R.string.btn_save, (d, w) -> saveNote())
                .setNegativeButton(R.string.btn_discard, (d, w) -> {
                    // Ghi chú mới bị bỏ thì xóa luôn ảnh đã chụp/chọn để không chiếm bộ nhớ
                    if (isNewNote()) ImageUtils.deleteImage(imagePath);
                    finish();
                })
                .setNeutralButton(R.string.btn_cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- Tiện ích

    private void copyContent() {
        String content = textOf(edtContent);
        if (content.trim().isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_copy, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), content));
        // Android 13+ đã tự hiện thông báo khi sao chép
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    /** Ghi chú với nội dung đang hiển thị (kể cả chưa lưu) để xuất tệp / chia sẻ. */
    private Note buildSnapshot() {
        long now = System.currentTimeMillis();
        String content = textOf(edtContent).trim();
        Note snapshot = new Note();
        snapshot.setId(note.getId());
        snapshot.setTitle(resolveTitle(textOf(edtTitle).trim(), content));
        snapshot.setContent(content);
        snapshot.setImagePath(imagePath);
        snapshot.setCreatedAt(note.getCreatedAt() > 0 ? note.getCreatedAt() : now);
        snapshot.setUpdatedAt(note.getUpdatedAt() > 0 ? note.getUpdatedAt() : now);
        return snapshot;
    }

    /** Tiêu đề trống thì lấy dòng đầu của nội dung, nếu vẫn trống thì dùng ngày giờ. */
    private String resolveTitle(String title, String content) {
        if (!title.isEmpty()) return title;
        String suggested = suggestTitle(content);
        if (!suggested.isEmpty()) return suggested;
        return getString(R.string.default_title, DateTimeUtils.format(System.currentTimeMillis()));
    }

    private static String suggestTitle(String text) {
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > MAX_TITLE_LENGTH
                        ? trimmed.substring(0, MAX_TITLE_LENGTH).trim() + "…"
                        : trimmed;
            }
        }
        return "";
    }

    private void updateStats() {
        String content = textOf(edtContent);
        String trimmed = content.trim();
        int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        tvStats.setText(getString(R.string.stats_format, content.length(), words));
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }
}
