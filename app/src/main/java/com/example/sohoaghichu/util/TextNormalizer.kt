package com.example.sohoaghichu.util

import java.text.Normalizer
import java.util.Locale

/**
 * Chuẩn hóa chuỗi để tìm kiếm không phân biệt hoa/thường và dấu tiếng Việt
 * ("ghi chu" khớp với "Ghi Chú").
 */
object TextNormalizer {

    private val MARKS = Regex("\\p{M}+")

    @JvmStatic
    fun normalize(input: String?): String {
        if (input == null) return ""
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(MARKS, "")
            // "đ" không tách dấu khi NFD nên phải thay thủ công
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase(Locale.ROOT)
            .trim()
    }
}
