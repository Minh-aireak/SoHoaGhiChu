package com.example.sohoaghichu.ocr;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;

/**
 * Nhận dạng chữ trong ảnh (OCR) bằng ML Kit, chạy hoàn toàn trên thiết bị.
 */
public class OcrHelper {

    public interface Callback {
        void onSuccess(@NonNull String text, int lineCount);

        void onFailure(@NonNull Exception e);
    }

    private final TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    /** Kết quả trả về trên main thread. */
    public void recognize(Context context, File imageFile, Callback callback) {
        InputImage image;
        try {
            image = InputImage.fromFilePath(context, Uri.fromFile(imageFile));
        } catch (IOException e) {
            callback.onFailure(e);
            return;
        }
        recognizer.process(image)
                .addOnSuccessListener(result -> callback.onSuccess(result.getText().trim(), countLines(result)))
                .addOnFailureListener(callback::onFailure);
    }

    private static int countLines(Text result) {
        int count = 0;
        for (Text.TextBlock block : result.getTextBlocks()) {
            count += block.getLines().size();
        }
        return count;
    }

    public void close() {
        recognizer.close();
    }
}
