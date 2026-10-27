package com.example.sohoaghichu.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    @JvmStatic
    fun format(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

    /** Dùng để đặt tên tệp xuất, ví dụ 20261022_143005. */
    @JvmStatic
    fun fileTimestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
