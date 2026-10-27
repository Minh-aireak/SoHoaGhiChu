# Hướng dẫn chạy, kiểm thử và giải thích dự án "Số hóa ghi chú"

App Android viết bằng **Kotlin + Java**: chụp hoặc chọn ảnh ghi chú, **nhận dạng chữ trong ảnh (OCR)** thành văn bản, cho sửa, **lưu**, **tìm kiếm** và **xuất tệp** TXT/PDF. Toàn bộ chạy trên điện thoại, **không cần Internet, không cần server**.

```
 [Chụp ảnh]   [Chọn ảnh]          ← MainActivity (Kotlin)
       \         /
  ImageUtils (Java): xoay đúng chiều, thu nhỏ, lưu JPEG vào bộ nhớ app
             |
  NoteEditActivity (Java) ──► OcrHelper (Java, ML Kit) ──► văn bản
             |   người dùng sửa tiêu đề / nội dung
          [Lưu] ──► NoteDbHelper (Kotlin, SQLite)
             |
  MainActivity (Kotlin): danh sách + tìm kiếm không dấu (TextNormalizer, Kotlin)
             |
  ExportUtils (Java): xuất TXT / PDF / chia sẻ
```

> **Lưu ý:** code đã được viết và kiểm tra tĩnh (tham chiếu tài nguyên, cú pháp XML) nhưng **chưa build và chưa chạy thử lần nào**. Nếu build lỗi, copy nguyên văn lỗi trong tab **Build** của Android Studio để sửa.

---

## 1. Chuẩn bị

| Thành phần | Yêu cầu |
|---|---|
| Android Studio | Bản mới (hỗ trợ AGP 9.3.3, giống các bài TX1 trước) |
| Android SDK | Platform 37 (Android Studio sẽ nhắc cài nếu thiếu) |
| Kotlin | Không cần cài riêng: AGP 9 tự biên dịch Kotlin (giống bài TX1 viết bằng Kotlin) |
| Internet | Chỉ cần lần đầu, để Gradle tải thư viện |
| Thiết bị chạy | Điện thoại Android **7.0 (API 24)** trở lên, hoặc máy ảo (emulator) |

Thư viện Gradle tự tải:

| Thư viện | Phiên bản | Vai trò |
|---|---|---|
| ML Kit Text Recognition | 16.0.1 | OCR, model nằm sẵn trong APK, chạy offline |
| Glide | 4.16.0 | Hiển thị ảnh thumbnail và ảnh xem trước |
| RecyclerView | 1.4.0 | Danh sách ghi chú |
| AppCompat, Material, Activity | 1.8.0, 1.14.0, 1.13.0 | Giao diện Material 3, Activity Result API |

---

## 2. Chạy app từng bước

### Bước 2.1. Mở project

1. Mở Android Studio, chọn **File → Open**, rồi chọn thư mục **`D:\LearningMaterials\TBDD\SoHoaGhiChu`**.
2. Chờ **Gradle Sync** chạy xong (thanh tiến trình góc dưới). Lần đầu mất khoảng 3–10 phút.
3. Nếu được nhắc cài **SDK Platform 37**, bấm **Install**.
4. Nếu báo *SDK location not found*: mở **File → Project Structure → SDK Location** và chọn thư mục SDK (thường là `C:\Users\<tên>\AppData\Local\Android\Sdk`).

### Bước 2.2a. Chạy trên điện thoại thật (khuyến nghị, vì camera thật OCR tốt hơn)

1. Bật **Tùy chọn nhà phát triển**: vào **Cài đặt → Thông tin điện thoại**, bấm 7 lần vào **Số hiệu bản tạo** (Xiaomi: bấm vào *Phiên bản HyperOS/MIUI*; Samsung: *Thông tin phần mềm → Số hiệu bản tạo*).
2. Vào **Tùy chọn nhà phát triển**, bật **Gỡ lỗi USB**.
3. Cắm cáp USB. Trên điện thoại, chọn **Cho phép** khi được hỏi.
4. Chọn tên điện thoại ở thanh trên cùng Android Studio, rồi bấm **Run ▶** (Shift+F10).

### Bước 2.2b. Chạy trên máy ảo

1. Mở **Device Manager** (thanh bên phải), bấm **+ → Create Virtual Device**, chọn **Pixel 8**, chọn system image **API 35 trở lên**.
2. Muốn chụp ảnh bằng webcam laptop: ở bước cuối bấm **Advanced Settings**, chỉnh **Camera → Back** thành **Webcam0**.
3. Chọn máy ảo ở thanh trên cùng, rồi bấm **Run ▶**.
4. Đưa ảnh mẫu vào máy ảo: **kéo thả file ảnh** từ Windows vào cửa sổ máy ảo. Ảnh được lưu vào thư mục *Download* và sẽ hiện khi bấm **Chọn ảnh**. Nếu chưa thấy, đợi vài giây hoặc mở app *Files* một lần.

### Bước 2.3. Tạo file APK (để nộp bài hoặc gửi qua Zalo)

1. Chọn **Build → Generate App Bundles or APKs → Generate APKs** (bản Android Studio cũ: **Build → Build Bundle(s) / APK(s) → Build APK(s)**).
2. File nằm ở `app\build\outputs\apk\debug\app-debug.apk`.

### Bước 2.4. Chạy unit test

Dự án có sẵn test cho phần tìm kiếm không dấu: `app/src/test/java/com/example/sohoaghichu/TextNormalizerTest.java` (4 test). Test viết bằng Java và kiểm tra lớp Kotlin `TextNormalizer`.

- **Cách 1:** mở file test, bấm nút ▶ xanh cạnh dòng `public class TextNormalizerTest`, rồi chọn **Run**. Cả 4 test phải có dấu ✓ xanh.
- **Cách 2:** mở tab **Terminal** trong Android Studio, chạy:
  ```
  .\gradlew.bat :app:testDebugUnitTest
  ```
  Báo cáo HTML nằm ở `app\build\reports\tests\testDebugUnitTest\index.html`.

---

## 3. Kịch bản kiểm thử chức năng (test thủ công)

Chuẩn bị: **một tờ giấy in chữ rõ ràng** (trang sách, tờ đề cương) và **một ảnh chụp màn hình có chữ**.

| # | Thao tác | Kết quả đúng |
|---|---|---|
| 1 | Mở app lần đầu | Danh sách trống, hiện dòng "Chưa có ghi chú nào…", phụ đề "0 ghi chú" |
| 2 | Bấm **Chụp ảnh**, chụp tờ giấy, bấm ✓ | Thanh tiến trình chạy, mở màn "Ghi chú mới", hiện "Đang nhận dạng chữ trong ảnh…", sau vài giây chữ hiện trong ô Nội dung |
| 3 | Nhìn ô **Tiêu đề** sau bước 2 | Tự điền bằng dòng chữ đầu tiên nhận dạng được (tối đa 60 ký tự) |
| 4 | Sửa vài chữ, bấm **Lưu** (icon đĩa mềm) | Toast "Đã lưu ghi chú", quay về danh sách, ghi chú nằm trên cùng, có ảnh thu nhỏ |
| 5 | Bấm **Chọn ảnh**, chọn ảnh chụp màn hình | Như bước 2–3 |
| 6 | Bấm **Chụp ảnh** rồi hủy (không chụp) | Không có gì xảy ra, không tạo ghi chú |
| 7 | Chọn ảnh không có chữ (ảnh phong cảnh) | Hiện "Không tìm thấy chữ nào trong ảnh." |
| 8 | Bấm kính lúp, gõ **không dấu** (ví dụ `lap trinh`) | Vẫn ra ghi chú chứa "Lập trình", phụ đề "Tìm thấy x / y ghi chú" |
| 9 | Gõ 2 từ cách nhau (ví dụ `android java`) | Chỉ ra ghi chú chứa **cả hai** từ |
| 10 | Gõ từ không có | Hiện "Không tìm thấy ghi chú nào khớp với …" |
| 11 | Mở ghi chú, menu ⋮ → **Xuất tệp TXT**, chọn nơi lưu | Toast "Đã xuất tệp thành công". Mở app *Files* thấy file `.txt` có tiêu đề, ngày tạo, nội dung |
| 12 | Menu ⋮ → **Xuất tệp PDF** | File PDF khổ A4 có tiêu đề, ảnh gốc, nội dung, chân trang "Trang 1" |
| 13 | Ghi chú dài (nối nhiều lần) rồi xuất PDF | PDF tự sang trang 2, 3… |
| 14 | Màn danh sách, menu ⋮ → **Xuất tất cả ra tệp TXT** | Một file chứa toàn bộ ghi chú, đánh số 1, 2, 3… |
| 15 | Trong ghi chú bấm **Nhận dạng lại** (icon mũi tên tròn) | Hỏi **Thay thế / Nối thêm / Hủy**, làm đúng lựa chọn |
| 16 | Bấm **Sao chép**, dán vào Zalo/Ghi chú | Dán ra đúng nội dung |
| 17 | Menu ⋮ → **Chia sẻ** | Hiện danh sách app (Zalo, Gmail…), gửi đi đúng tiêu đề + nội dung |
| 18 | Sửa nội dung rồi bấm **Back** | Hỏi "Chưa lưu ghi chú": Lưu / Không lưu / Hủy |
| 19 | Ghi chú mới, bấm Back, chọn **Không lưu** | Không tạo ghi chú, ảnh vừa chụp bị xóa khỏi bộ nhớ |
| 20 | Ở danh sách, **nhấn giữ** một ghi chú, chọn **Xóa** | Ghi chú biến mất, toast "Đã xóa ghi chú" |
| 21 | Xoay ngang màn hình lúc đang nhận dạng | Không crash, nhận dạng tự chạy lại và vẫn ra kết quả |
| 22 | Tắt Wi-Fi và 4G rồi chụp ảnh mới | OCR vẫn chạy bình thường (chạy offline) |
| 23 | Bật chế độ tối của điện thoại | Giao diện đổi sang màu tối |
| 24 | Đóng hẳn app rồi mở lại | Ghi chú vẫn còn (đã lưu trong SQLite) |

### Xem dữ liệu đã lưu (để chứng minh khi được hỏi)

- **Cơ sở dữ liệu:** khi app đang chạy, mở **View → Tool Windows → App Inspection**, chọn tab **Database Inspector**, mở `so_hoa_ghi_chu.db` rồi bảng `notes`. Cần thiết bị API 26 trở lên.
- **File ảnh:** mở **View → Tool Windows → Device Explorer** và vào `/data/data/com.example.sohoaghichu/files/images/`.

---

## 4. Hướng dẫn sử dụng

### Màn hình danh sách (MainActivity)

- **Chụp ảnh** (nút dưới cùng): mở camera của máy. Chụp xong, app tự nhận dạng chữ.
- **Chọn ảnh**: chọn ảnh có sẵn trong thư viện.
- **Kính lúp**: tìm theo tiêu đề và nội dung, gõ có dấu hay không dấu đều được.
- **Menu ⋮ → Xuất tất cả ra tệp TXT**: gộp mọi ghi chú vào một file.
- **Chạm** vào ghi chú để mở. **Nhấn giữ** để xóa.
- Phụ đề dưới tên app cho biết số ghi chú, hoặc số kết quả khi đang tìm.

### Màn hình ghi chú (NoteEditActivity)

- **Ảnh gốc** ở trên cùng: chạm vào để mở bằng app xem ảnh, phóng to so với văn bản.
- **Tiêu đề / Nội dung**: sửa thoải mái. Bên dưới đếm số ký tự và số từ.
- Thanh công cụ (màn hình hẹp thì icon Sao chép / Nhận dạng lại có thể nằm trong menu ⋮):
  - 💾 **Lưu**. Nếu để trống tiêu đề, app lấy dòng đầu của nội dung, nếu vẫn trống thì đặt "Ghi chú dd/MM/yyyy HH:mm".
  - 📋 **Sao chép** toàn bộ nội dung.
  - 🔄 **Nhận dạng lại từ ảnh**: thay thế hoặc nối thêm vào nội dung cũ.
  - ⋮ **Xuất tệp TXT**, **Xuất tệp PDF**, **Chia sẻ**, **Xóa ghi chú** (mục Xóa chỉ có với ghi chú đã lưu).
- Xuất tệp và chia sẻ dùng **nội dung đang hiển thị**, kể cả khi chưa bấm Lưu.

**Mẹo để OCR chính xác:** chụp thẳng góc, đủ sáng, không rung, chữ chiếm phần lớn khung hình. Chữ in nhận dạng tốt hơn chữ viết tay.

---

## 5. Giải thích chức năng (chuẩn bị khi bị hỏi)

### 5.1. Cấu trúc mã nguồn

Tất cả nằm trong `app/src/main/java/com/example/sohoaghichu/`:

| File | Ngôn ngữ | Nhiệm vụ |
|---|---|---|
| `MainActivity.kt` | Kotlin | Danh sách, tìm kiếm, nút chụp/chọn ảnh, xóa, xuất tất cả |
| `adapter/NoteAdapter.kt` | Kotlin | Đổ dữ liệu ghi chú vào RecyclerView |
| `data/Note.kt` | Kotlin | `data class` mô hình: id, tiêu đề, nội dung, đường dẫn ảnh, ngày tạo, ngày cập nhật |
| `data/NoteDbHelper.kt` | Kotlin | SQLite: tạo bảng, thêm/sửa/xóa/truy vấn |
| `util/TextNormalizer.kt` | Kotlin | Bỏ dấu tiếng Việt để tìm kiếm |
| `util/DateTimeUtils.kt` | Kotlin | Định dạng ngày giờ |
| `NoteEditActivity.java` | Java | Gọi OCR, sửa, lưu, sao chép, xuất, chia sẻ, xóa một ghi chú |
| `ocr/OcrHelper.java` | Java | Bọc ML Kit Text Recognition |
| `util/ImageUtils.java` | Java | Tạo file tạm cho camera, chuẩn hóa ảnh, giải mã ảnh |
| `util/ExportUtils.java` | Java | Tạo nội dung TXT, vẽ PDF, chia sẻ, chạy xuất tệp ở luồng nền |
| `res/xml/file_paths.xml` | XML | Khai báo thư mục FileProvider được phép chia sẻ |

Cách chia và cách hai ngôn ngữ gọi nhau: xem mục 5.10.

### 5.2. Chụp ảnh

- Dùng `ActivityResultContracts.TakePicture`: app tạo sẵn một file tạm trong `cache/camera/`, biến nó thành `content://` Uri bằng **FileProvider**, rồi đưa Uri cho app Camera của hệ thống ghi ảnh vào.
- **Không cần quyền CAMERA**, vì app không trực tiếp mở camera mà nhờ app Camera hệ thống chụp. Quyền chỉ bắt buộc khi app tự điều khiển camera (CameraX/Camera2).
- Đường dẫn file tạm được lưu trong `onSaveInstanceState`, nên nếu Android tắt app trong lúc đang ở màn hình camera, khi quay lại vẫn lấy được ảnh.

### 5.3. Chọn ảnh

- Dùng **Photo Picker** (`ActivityResultContracts.PickVisualMedia`, chỉ lấy ảnh). Người dùng chỉ cấp cho app đúng ảnh được chọn nên **không cần quyền đọc bộ nhớ**.
- Máy cũ không có Photo Picker sẽ tự chuyển sang trình chọn file của hệ thống.

### 5.4. Xử lý ảnh trước khi OCR (`ImageUtils.importImage`)

Chạy ở luồng nền (`ExecutorService`) để không đơ giao diện:

1. Đọc kích thước ảnh trước (`inJustDecodeBounds`) rồi giải mã với `inSampleSize` để không tràn bộ nhớ với ảnh 12–50 MP.
2. Đọc **EXIF orientation** và xoay/lật ảnh cho đúng chiều. Camera điện thoại thường lưu ảnh nằm ngang kèm cờ "xoay 90°"; nếu không xử lý, OCR sẽ đọc chữ bị xoay.
3. Thu nhỏ để cạnh dài tối đa **2560 px**: đủ nét cho OCR mà vẫn nhẹ.
4. Ảnh PNG trong suốt được phủ nền trắng, vì JPEG không có kênh alpha (nền sẽ thành đen, chữ đen sẽ biến mất).
5. Lưu JPEG chất lượng 90 vào `files/images/` (bộ nhớ riêng của app).

### 5.5. OCR – nhận dạng chữ (`OcrHelper`)

- Dùng **Google ML Kit Text Recognition** (bản *bundled*): mô hình học sâu đã được Google huấn luyện sẵn, đóng gói trong APK. **Chạy hoàn toàn trên máy, không cần Internet**, ảnh không bị gửi đi đâu.
- Quy trình: `InputImage.fromFilePath(...)` → `recognizer.process(image)` (bất đồng bộ, trả về `Task`) → kết quả `Text`.
- Cấu trúc kết quả: `Text` → `TextBlock` (đoạn) → `Line` (dòng) → `Element` (từ). App lấy `getText()` làm nội dung và đếm số `Line` để báo "Đã nhận dạng N dòng".
- Bên trong ML Kit gồm 2 bước: **phát hiện vùng có chữ** rồi **nhận dạng ký tự** trong từng vùng.
- `TextRecognizer` được `close()` trong `onDestroy` để giải phóng mô hình.

### 5.6. Lưu trữ (`NoteDbHelper`)

- **SQLite** qua `SQLiteOpenHelper`, file `so_hoa_ghi_chu.db`, bảng:

  ```sql
  CREATE TABLE notes (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      title       TEXT NOT NULL,
      content     TEXT NOT NULL DEFAULT '',
      image_path  TEXT,
      created_at  INTEGER NOT NULL,   -- mili giây
      updated_at  INTEGER NOT NULL
  )
  ```
- Danh sách sắp xếp theo `updated_at DESC`: ghi chú vừa sửa nằm trên cùng.
- **Ảnh lưu thành file**, DB chỉ lưu **đường dẫn**: giữ DB nhẹ, truy vấn nhanh, và Glide đọc file trực tiếp được.
- `NoteDbHelper` dùng **Singleton** với `ApplicationContext`: cả app dùng chung một kết nối, không rò rỉ Activity. Trong Kotlin, Singleton viết bằng `companion object` + `@Volatile` + `synchronized` (khóa kiểm tra hai lần, an toàn khi nhiều luồng cùng gọi).
- Mỗi `Cursor` được đóng tự động bằng `use { }` của Kotlin, nên không bị rò rỉ cursor.
- Xóa ghi chú thì xóa luôn file ảnh. Bỏ ghi chú mới (bấm "Không lưu") cũng xóa ảnh, nên không để lại file rác.

### 5.7. Tìm kiếm không dấu

- `TextNormalizer.normalize()` (Kotlin `object`):
  1. Chuẩn hóa Unicode **NFD**, tách chữ và dấu: "ố" thành "o" + dấu mũ + dấu sắc.
  2. Xóa mọi ký tự dấu bằng regex `\p{M}`.
  3. Thay "đ/Đ" bằng "d" (chữ đ không tách dấu được bằng NFD).
  4. Đổi về chữ thường.
- Câu tìm kiếm được tách thành các từ; ghi chú phải chứa **tất cả** các từ.
- **Vì sao không dùng `LIKE` của SQL?** `LIKE` của SQLite chỉ không phân biệt hoa/thường với chữ không dấu (ASCII) và không bỏ qua được dấu tiếng Việt. Lọc trong Java xử lý được cả hai, và vẫn đủ nhanh với vài trăm ghi chú.

### 5.8. Xuất tệp (`ExportUtils`)

- Dùng `ActivityResultContracts.CreateDocument` (**Storage Access Framework**): hệ thống mở hộp thoại để người dùng tự chọn nơi lưu và tên file, app chỉ được ghi vào đúng file đó. Vì vậy **không cần quyền WRITE_EXTERNAL_STORAGE**, và cách này lưu được cả vào Google Drive.
- **TXT:** ghi chuỗi UTF-8 (giữ đúng tiếng Việt), gồm tiêu đề, ngày tạo, ngày cập nhật và nội dung.
- **PDF:** dùng `android.graphics.pdf.PdfDocument` có sẵn trong Android, không cần thư viện ngoài.
  - Trang A4 = 595 × 842 point (1 point = 1/72 inch), lề 40.
  - Chữ được ngắt dòng bằng `StaticLayout`. Khi hết chỗ thì tạo trang mới và vẽ tiếp các dòng còn lại (lớp `PdfPager`).
  - Ảnh được thu nhỏ vừa khổ giấy (cao tối đa 360 pt). Mỗi trang có số trang ở chân.
- Xuất tệp chạy ở **luồng nền** rồi báo kết quả bằng Toast.
- **Chia sẻ:** `Intent.ACTION_SEND` kiểu `text/plain`, gửi tiêu đề và nội dung sang app khác.
- **Sao chép:** `ClipboardManager`. Từ Android 13, hệ thống tự hiện thông báo đã sao chép nên app không hiện Toast nữa.

### 5.9. Các kỹ thuật Android khác

- **Activity Result API** (`registerForActivityResult`) thay cho `startActivityForResult` đã bị *deprecated*.
- **FileProvider:** từ Android 7, đưa đường dẫn `file://` cho app khác sẽ bị lỗi `FileUriExposedException`, nên phải dùng `content://` qua FileProvider. Dự án dùng FileProvider cho ảnh camera và để mở ảnh bằng app xem ảnh.
- **Edge-to-edge:** với `targetSdk` 35 trở lên, app bắt buộc vẽ tràn dưới thanh trạng thái, nên code dùng `WindowInsets` để đẩy toolbar xuống và đẩy nội dung lên trên bàn phím/thanh điều hướng.
- **Xoay màn hình:** cờ `ocrDone` được lưu trong `onSaveInstanceState`. Nếu đang nhận dạng mà xoay máy thì chạy lại OCR; chữ trong ô nhập được Android tự giữ.
- **Hỏi trước khi thoát:** dùng `OnBackPressedCallback` thay cho `onBackPressed()` đã bị deprecated.
- **Material 3** (`Theme.Material3.DayNight`): tự hỗ trợ chế độ tối, màu tối khai báo trong `values-night/colors.xml`.
- **Glide:** tải ảnh ở luồng nền, có cache và tự thu nhỏ theo kích thước ImageView, nên danh sách cuộn mượt.
- **minSdk 24** vì code dùng `ExifInterface(InputStream)` (cần API 24) và `StaticLayout.Builder` (cần API 23).

### 5.10. Kết hợp Kotlin và Java

**Cách chia:**

| Phần | Ngôn ngữ | Lý do |
|---|---|---|
| Màn hình danh sách (`MainActivity`, `NoteAdapter`) | Kotlin | Giao diện nhiều sự kiện click, tìm kiếm: lambda và các hàm `filter`, `all` giúp code ngắn gọn |
| Dữ liệu (`Note`, `NoteDbHelper`) | Kotlin | `data class` thay cho khoảng 90 dòng getter/setter của Java; kiểu `Note?` buộc phải xử lý trường hợp không tìm thấy |
| Tiện ích (`TextNormalizer`, `DateTimeUtils`) | Kotlin | `object` là Singleton có sẵn, không cần viết lớp tĩnh |
| Màn hình ghi chú (`NoteEditActivity`) | Java | Phần giao diện còn lại viết bằng Java |
| OCR, xử lý ảnh, xuất tệp (`OcrHelper`, `ImageUtils`, `ExportUtils`) | Java | Code thao tác trực tiếp API Android (Bitmap, Matrix, Canvas, PdfDocument, ML Kit) |
| Unit test (`TextNormalizerTest`) | Java | Test Java kiểm tra lớp Kotlin |

Hai ngôn ngữ **gọi nhau theo cả hai chiều**: màn hình Kotlin dùng lớp Java, còn màn hình Java dùng lớp Kotlin.

**Build:** từ AGP 9, plugin `com.android.application` có sẵn Kotlin (*built-in Kotlin*), không cần thêm plugin `kotlin-android`. File `.kt` và `.java` nằm chung trong `src/main/java`. Khi build:

1. Trình biên dịch Kotlin dịch các file `.kt` trước. Nó đọc được mã nguồn `.java` nên biết các lớp Java có những hàm gì.
2. `javac` dịch các file `.java`, dùng các lớp Kotlin vừa dịch xong.
3. Cả hai đều ra bytecode JVM, được gộp chung vào APK. Lúc chạy, Android không phân biệt lớp nào viết bằng ngôn ngữ nào.

**Java gọi Kotlin** (trong `NoteEditActivity.java`, `ExportUtils.java`):

```java
Note note = new Note();                              // data class có đủ giá trị mặc định nên có constructor rỗng
note.setTitle("Bài 1");                              // var title trong Kotlin = getTitle()/setTitle() trong Java
NoteDbHelper db = NoteDbHelper.getInstance(this);    // nhờ @JvmStatic trong companion object
String time = DateTimeUtils.format(millis);          // nhờ @JvmStatic trong object
```

Nếu không có `@JvmStatic`, Java phải viết dài: `NoteDbHelper.Companion.getInstance(this)` và `DateTimeUtils.INSTANCE.format(millis)`.

**Kotlin gọi Java** (trong `MainActivity.kt`):

```kotlin
val image = ImageUtils.importImage(this, uri)       // hàm static của Java gọi như hàm bình thường
ExportUtils.runAsync(this, executor) {              // ExportTask là interface Java có 1 hàm,
    ExportUtils.writeText(this, uri, text)          // nên Kotlin truyền lambda (SAM conversion)
}
startActivity(Intent(this, NoteEditActivity::class.java))
```

**Lưu ý ở ranh giới hai ngôn ngữ:**
- **Null-safety:** `imagePath: String?` trong Kotlin cho phép null; `title: String` thì không. Nếu code Java truyền `null` vào `setTitle(...)`, Kotlin báo lỗi `NullPointerException` ngay lập tức, nên phía Java luôn truyền chuỗi khác null (dùng `""` thay cho null).
- **Checked exception:** hàm Java `importImage` khai báo `throws IOException`, nhưng Kotlin không bắt buộc `try/catch`. App vẫn bắt lỗi bằng `runCatching { }` để hiện Toast thay vì crash.

---

## 6. Câu hỏi thường gặp khi bảo vệ

**Hỏi: Vì sao dự án dùng cả Kotlin và Java?**
Đề bài yêu cầu kết hợp hai ngôn ngữ. Thực tế nhiều dự án Android cũng vậy: code cũ viết bằng Java, phần mới viết bằng Kotlin (Google ưu tiên Kotlin cho Android từ 2019 nhưng vẫn hỗ trợ Java đầy đủ). Hai ngôn ngữ cùng dịch ra bytecode JVM nên gọi nhau trực tiếp, không cần cầu nối. Chi tiết ở mục 5.10.

**Hỏi: Java gọi được lớp Kotlin bằng cách nào, và ngược lại?**
Java gọi Kotlin: property `var` thành getter/setter, `data class` có constructor rỗng, hàm trong `object`/`companion object` gắn `@JvmStatic` để gọi như hàm static. Kotlin gọi Java: gọi trực tiếp như lớp Kotlin; interface Java chỉ có 1 hàm thì truyền lambda (SAM conversion). Ví dụ cụ thể ở mục 5.10.

**Hỏi: `data class` khác class Java thường ở chỗ nào?**
Kotlin tự sinh getter/setter, `equals()`, `hashCode()`, `toString()`, `copy()`. File `Note.kt` chỉ khoảng 10 dòng, thay cho khoảng 90 dòng nếu viết bằng Java.

**Hỏi: Trong dự án, ưu điểm của Kotlin thể hiện ở đâu?**
- Null-safety: `getNoteById()` trả về `Note?`; dùng `?.let { }` và `?:` khi đọc `savedInstanceState`.
- Lambda và hàm xử lý danh sách: tìm kiếm viết bằng `filter { }` + `all { }`; adapter nhận lambda thay vì interface.
- `use { }` tự đóng Cursor; `apply { }` khi tạo `ContentValues`; chuỗi mẫu `"$COL_ID = ?"`; `when` thay cho chuỗi if-else; `lateinit` cho các View.

**Hỏi: App có cần Internet không?**
Không. OCR, lưu trữ, tìm kiếm và xuất tệp đều chạy trên máy. Chỉ cần Internet lúc build để Gradle tải thư viện.

**Hỏi: Vì sao chọn ML Kit mà không dùng Tesseract hay Google Cloud Vision?**
ML Kit chạy offline, nhanh, miễn phí, tích hợp chỉ bằng một dòng Gradle và không cần huấn luyện. Cloud Vision chính xác hơn nhưng cần Internet, API key và tính phí theo lượt. Tesseract hỗ trợ tiếng Việt có dấu tốt hơn nhưng phải kèm file dữ liệu ngôn ngữ và tích hợp phức tạp hơn.

**Hỏi: Nhận dạng tiếng Việt có dấu có chính xác không?**
ML Kit nhận dạng tốt chữ Latin in, nhưng với tiếng Việt thì dấu có thể bị mất hoặc sai. Vì vậy app luôn cho người dùng sửa văn bản trước khi lưu và có ảnh gốc ngay phía trên để đối chiếu. Hướng cải tiến: thêm Tesseract với dữ liệu `vie`.

**Hỏi: Có đọc được chữ viết tay không?**
Một phần. Mô hình tối ưu cho chữ in; chữ viết tay rõ ràng thì đọc được, chữ viết ẩu thì sai nhiều.

**Hỏi: Dữ liệu lưu ở đâu? Người khác có xem được không?**
Trong bộ nhớ riêng của app (`/data/data/com.example.sohoaghichu/`): file `databases/so_hoa_ghi_chu.db` và thư mục `files/images/`. App khác không đọc được thư mục này. Gỡ app thì dữ liệu bị xóa theo.

**Hỏi: Vì sao lưu ảnh ra file mà không lưu vào DB dạng BLOB?**
Ảnh vài trăm KB đến vài MB; lưu trong DB làm DB phình to và chậm, còn `CursorWindow` của Android giới hạn khoảng 2 MB mỗi dòng. Lưu file và chỉ ghi đường dẫn là cách làm chuẩn.

**Hỏi: Vì sao dùng SQLiteOpenHelper mà không dùng Room?**
Dữ liệu chỉ có một bảng, SQLiteOpenHelper đơn giản, không cần annotation processor và sát với nội dung môn học. Room phù hợp hơn khi có nhiều bảng hoặc cần LiveData.

**Hỏi: Thao tác DB có chạy ở luồng chính không?**
Có. Dữ liệu nhỏ (một bảng, vài trăm dòng), mỗi truy vấn chỉ mất vài mili giây. Những việc nặng như xử lý ảnh, xuất PDF/TXT, OCR đều chạy nền. Nếu dữ liệu lớn thì sẽ chuyển DB sang luồng nền (ExecutorService hoặc Room).

**Hỏi: App xin những quyền gì?**
Không xin quyền nào lúc chạy. Chụp ảnh nhờ app Camera hệ thống, chọn ảnh qua Photo Picker, xuất tệp qua Storage Access Framework.

**Hỏi: Tìm kiếm không dấu hoạt động thế nào?**
Xem mục 5.7: tách dấu bằng chuẩn hóa Unicode NFD, xóa dấu bằng regex, thay "đ" bằng "d", rồi so sánh chữ thường. Có unit test `TextNormalizerTest` chứng minh.

**Hỏi: PDF được tạo bằng gì?**
Bằng lớp `PdfDocument` có sẵn của Android: mỗi trang là một `Canvas`, app vẽ chữ (`StaticLayout`) và ảnh lên đó, tự sang trang khi hết chỗ.

**Hỏi: Nếu Android tắt app khi đang mở camera thì sao?**
Đường dẫn file ảnh tạm đã được lưu trong `onSaveInstanceState`, nên khi Activity được tạo lại vẫn nhận được ảnh vừa chụp.

**Hỏi: Hạn chế và hướng phát triển?**
- Dấu tiếng Việt chưa chuẩn: thêm Tesseract `vie` hoặc dùng API đám mây làm tùy chọn.
- Thêm **ML Kit Document Scanner** để tự cắt viền và làm phẳng trang giấy trước khi OCR.
- Một ghi chú chứa nhiều ảnh (nhiều trang).
- Gắn nhãn/thư mục, sắp xếp, ghim ghi chú.
- Đồng bộ đám mây (Firebase) và sao lưu.
- Dịch văn bản bằng ML Kit Translation, đọc to bằng Text-to-Speech.

---

## 7. Kịch bản demo nhanh (3–5 phút)

1. Mở app: giới thiệu màn danh sách trống.
2. **Chụp** một trang sách: chỉ thanh tiến trình, chữ hiện ra, tiêu đề tự điền.
3. Sửa một chữ sai, bấm **Lưu**: ghi chú hiện trong danh sách với ảnh thu nhỏ.
4. **Chọn ảnh** chụp màn hình có sẵn để có ghi chú thứ hai.
5. **Tìm kiếm** bằng từ không dấu.
6. Mở ghi chú, **Xuất PDF**, mở file PDF cho xem.
7. Bật **chế độ máy bay**, chụp thêm một ảnh: chứng minh chạy offline.
8. (Nếu được hỏi về lưu trữ) mở **Database Inspector** cho xem bảng `notes`.

---

## 8. Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách sửa |
|---|---|---|
| Gradle Sync lỗi phiên bản AGP | Android Studio quá cũ | Cập nhật Android Studio bản mới nhất |
| "Failed to find target android-37" | Chưa cài SDK 37 | **Tools → SDK Manager**, tích *Android API 37*, rồi bấm Apply |
| "SDK location not found" | `local.properties` sai đường dẫn (khi chép sang máy khác) | Xóa `local.properties`, mở lại project; hoặc chỉnh trong Project Structure |
| Bấm Chụp ảnh báo "Không tìm thấy ứng dụng máy ảnh" | Máy ảo không có camera | Chỉnh Camera Back = Webcam0 (bước 2.2b) hoặc dùng điện thoại thật |
| OCR ra rỗng hoặc sai nhiều | Ảnh mờ, tối, nghiêng, chữ quá nhỏ | Chụp lại gần hơn, đủ sáng, thẳng góc |
| Chữ mất dấu tiếng Việt | Giới hạn của ML Kit (mục 6) | Sửa tay trong ô Nội dung |
| Không mở được file PDF đã xuất | Máy chưa có app đọc PDF | Cài Google Drive hoặc trình đọc PDF |
| Chọn ảnh HEIC báo lỗi trên Android 8 trở xuống | Android dưới 9 không giải mã được HEIC | Chọn ảnh JPG/PNG |
| `jlink executable ...redhat.java...\jre\...\jlink.exe does not exist` | Mở project bằng VS Code / Antigravity: extension Java của Red Hat chạy Gradle bằng JRE thu gọn của nó (không có `jlink`) | Project đã đặt `toolchainVendor=JETBRAINS` trong `gradle/gradle-daemon-jvm.properties`. Trong VS Code / Antigravity, thêm setting `"java.import.gradle.java.home"` trỏ tới một JDK đầy đủ, rồi reload. Nên build/chạy bằng Android Studio |
| Lỗi `Redeclaration` hoặc `Duplicate class` | Cùng một lớp có cả file `.java` và `.kt` (ví dụ chép đè bản cũ vào) | Mỗi lớp chỉ giữ một file, theo đúng bảng ở mục 5.1 |
| Lỗi liên quan plugin `org.jetbrains.kotlin.android` | Có người thêm plugin này vào `build.gradle.kts`; AGP 9 đã tích hợp sẵn Kotlin | Xóa dòng plugin đó, giữ nguyên như hiện tại |

---

## 9. Khi nộp hoặc gửi project

Không cần gửi các thư mục sau vì chúng nặng và sẽ tự tạo lại: `.gradle\`, `.idea\`, `build\`, `app\build\`, `local.properties`.
