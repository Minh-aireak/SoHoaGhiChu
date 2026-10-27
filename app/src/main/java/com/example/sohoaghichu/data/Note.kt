package com.example.sohoaghichu.data

/**
 * Một ghi chú được số hóa: ảnh gốc + văn bản nhận dạng (đã chỉnh sửa).
 *
 * Mọi tham số đều có giá trị mặc định nên Kotlin sinh thêm constructor rỗng,
 * phía Java gọi được `new Note()` và các getter/setter như `getTitle()`, `setTitle(...)`.
 */
data class Note(
    var id: Long = -1,
    var title: String = "",
    var content: String = "",
    var imagePath: String? = null,
    var createdAt: Long = 0,
    var updatedAt: Long = 0
) {
    fun hasImage(): Boolean = !imagePath.isNullOrEmpty()
}
