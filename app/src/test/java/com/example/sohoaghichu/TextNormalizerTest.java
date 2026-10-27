package com.example.sohoaghichu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.sohoaghichu.util.TextNormalizer;

import org.junit.Test;

public class TextNormalizerTest {

    @Test
    public void removesVietnameseDiacriticsAndCase() {
        assertEquals("so hoa ghi chu", TextNormalizer.normalize("Số Hóa Ghi Chú"));
    }

    @Test
    public void convertsLetterD() {
        assertEquals("duong di", TextNormalizer.normalize("Đường đi"));
    }

    @Test
    public void unaccentedQueryMatchesAccentedText() {
        String haystack = TextNormalizer.normalize("Bài giảng Lập trình di động");
        assertTrue(haystack.contains(TextNormalizer.normalize("lap trinh")));
    }

    @Test
    public void nullBecomesEmpty() {
        assertEquals("", TextNormalizer.normalize(null));
    }
}
