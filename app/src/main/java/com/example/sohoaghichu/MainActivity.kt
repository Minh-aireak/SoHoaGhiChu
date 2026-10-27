package com.example.sohoaghichu

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sohoaghichu.adapter.NoteAdapter
import com.example.sohoaghichu.data.Note
import com.example.sohoaghichu.data.NoteDbHelper
import com.example.sohoaghichu.util.DateTimeUtils
import com.example.sohoaghichu.util.ExportUtils
import com.example.sohoaghichu.util.ImageUtils
import com.example.sohoaghichu.util.TextNormalizer
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Màn hình chính (Kotlin): danh sách ghi chú, tìm kiếm, chụp/chọn ảnh mới và xuất tất cả ghi chú.
 * Gọi sang các lớp Java ImageUtils, ExportUtils và mở NoteEditActivity (Java).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var appBar: AppBarLayout
    private lateinit var progressImport: LinearProgressIndicator
    private lateinit var rvNotes: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var fabCamera: ExtendedFloatingActionButton
    private lateinit var fabPick: ExtendedFloatingActionButton

    private lateinit var db: NoteDbHelper
    private lateinit var adapter: NoteAdapter
    private val allNotes = ArrayList<Note>()
    private var currentQuery = ""
    /** File tạm mà app Camera sẽ ghi ảnh vào. */
    private var cameraFile: File? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success -> onPictureTaken(success) }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) importImage(uri, null)
        }

    private val exportAllLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) exportAll(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        appBar = findViewById(R.id.appBar)
        progressImport = findViewById(R.id.progressImport)
        rvNotes = findViewById(R.id.rvNotes)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        tvEmpty = findViewById(R.id.tvEmpty)
        fabCamera = findViewById(R.id.fabCamera)
        fabPick = findViewById(R.id.fabPick)

        savedInstanceState?.getString(STATE_CAMERA_FILE)?.let { cameraFile = File(it) }

        db = NoteDbHelper.getInstance(this)
        adapter = NoteAdapter(onNoteClick = this::openNote, onNoteLongClick = this::confirmDelete)
        rvNotes.layoutManager = LinearLayoutManager(this)
        rvNotes.adapter = adapter

        setupToolbar()
        fabCamera.setOnClickListener { takePhoto() }
        fabPick.setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            appBar.setPadding(0, bars.top, 0, 0)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        cameraFile?.let { outState.putString(STATE_CAMERA_FILE, it.absolutePath) }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun setupToolbar() {
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_export_all -> {
                    startExportAll()
                    true
                }
                else -> false
            }
        }

        val searchItem = toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText.orEmpty()
                applyFilter()
                return true
            }
        })

        // Nút Back đóng ô tìm kiếm trước khi thoát app
        val collapseSearch = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                searchItem.collapseActionView()
            }
        }
        onBackPressedDispatcher.addCallback(this, collapseSearch)
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                collapseSearch.isEnabled = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                collapseSearch.isEnabled = false
                return true
            }
        })
    }

    // ---------------------------------------------------------------- Danh sách & tìm kiếm

    private fun loadNotes() {
        allNotes.clear()
        allNotes.addAll(db.getAllNotes())
        applyFilter()
    }

    /** Lọc theo tiêu đề + nội dung; mọi từ khóa đều phải xuất hiện, không phân biệt dấu. */
    private fun applyFilter() {
        val keywords = TextNormalizer.normalize(currentQuery).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val result = if (keywords.isEmpty()) {
            allNotes.toList()
        } else {
            allNotes.filter { note ->
                val haystack = TextNormalizer.normalize("${note.title} ${note.content}")
                keywords.all { haystack.contains(it) }
            }
        }

        adapter.submit(result)
        val searching = keywords.isNotEmpty()
        layoutEmpty.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = if (searching) {
            getString(R.string.empty_search, currentQuery.trim())
        } else {
            getString(R.string.empty_notes)
        }
        toolbar.subtitle = if (searching) {
            getString(R.string.subtitle_search_result, result.size, allNotes.size)
        } else {
            getString(R.string.subtitle_count, allNotes.size)
        }
    }

    private fun openNote(note: Note) {
        startActivity(
            Intent(this, NoteEditActivity::class.java)
                .putExtra(NoteEditActivity.EXTRA_NOTE_ID, note.id)
        )
    }

    private fun confirmDelete(note: Note) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, note.title))
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                db.deleteNote(note.id)
                ImageUtils.deleteImage(note.imagePath)
                loadNotes()
                Toast.makeText(this, R.string.note_deleted, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ---------------------------------------------------------------- Chụp / chọn ảnh

    private fun takePhoto() {
        val file = try {
            ImageUtils.createCameraTempFile(this)
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.error_import_image, e.message), Toast.LENGTH_SHORT).show()
            return
        }
        cameraFile = file
        try {
            takePictureLauncher.launch(FileProvider.getUriForFile(this, ImageUtils.getAuthority(this), file))
        } catch (e: ActivityNotFoundException) {
            file.delete()
            cameraFile = null
            Toast.makeText(this, R.string.error_no_camera, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPictureTaken(success: Boolean) {
        val file = cameraFile ?: return
        cameraFile = null
        if (success) importImage(Uri.fromFile(file), file) else file.delete()
    }

    /** Chuẩn hóa ảnh ở background rồi mở màn hình nhận dạng. */
    private fun importImage(source: Uri, tempFile: File?) {
        setBusy(true)
        executor.execute {
            val result = runCatching { ImageUtils.importImage(this, source) }
            tempFile?.delete()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                setBusy(false)
                result
                    .onSuccess { image ->
                        startActivity(
                            Intent(this, NoteEditActivity::class.java)
                                .putExtra(NoteEditActivity.EXTRA_IMAGE_PATH, image.absolutePath)
                        )
                    }
                    .onFailure { e ->
                        Toast.makeText(this, getString(R.string.error_import_image, e.message), Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        progressImport.visibility = if (busy) View.VISIBLE else View.GONE
        fabCamera.isEnabled = !busy
        fabPick.isEnabled = !busy
    }

    // ---------------------------------------------------------------- Xuất tệp

    private fun startExportAll() {
        if (allNotes.isEmpty()) {
            Toast.makeText(this, R.string.export_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        exportAllLauncher.launch("TatCaGhiChu_${DateTimeUtils.fileTimestamp()}.txt")
    }

    private fun exportAll(uri: Uri) {
        val snapshot = ArrayList(allNotes)
        // ExportTask là interface Java -> Kotlin truyền lambda nhờ SAM conversion
        ExportUtils.runAsync(this, executor) {
            ExportUtils.writeText(this, uri, ExportUtils.buildAllNotesText(this, snapshot))
        }
    }

    private companion object {
        const val STATE_CAMERA_FILE = "state_camera_file"
    }
}
