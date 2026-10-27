package com.example.sohoaghichu.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.sohoaghichu.R
import com.example.sohoaghichu.data.Note
import com.example.sohoaghichu.util.DateTimeUtils
import java.io.File

class NoteAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onNoteLongClick: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    private val notes = ArrayList<Note>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(data: List<Note>) {
        notes.clear()
        notes.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(v)
    }

    override fun onBindViewHolder(h: NoteViewHolder, position: Int) {
        val note = notes[position]
        h.tvTitle.text = note.title

        val snippet = note.content.take(SNIPPET_LENGTH).replace(Regex("\\s+"), " ").trim()
        h.tvSnippet.text = snippet.ifEmpty { h.itemView.context.getString(R.string.no_content) }
        h.tvDate.text = DateTimeUtils.format(note.updatedAt)

        if (note.hasImage()) {
            h.ivThumb.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(h.ivThumb).load(File(note.imagePath!!)).into(h.ivThumb)
        } else {
            Glide.with(h.ivThumb).clear(h.ivThumb)
            h.ivThumb.scaleType = ImageView.ScaleType.CENTER
            h.ivThumb.setImageResource(R.drawable.ic_image)
        }

        h.itemView.setOnClickListener { onNoteClick(note) }
        h.itemView.setOnLongClickListener {
            onNoteLongClick(note)
            true
        }
    }

    override fun getItemCount(): Int = notes.size

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumb: ImageView = itemView.findViewById(R.id.ivThumb)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvSnippet: TextView = itemView.findViewById(R.id.tvSnippet)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
    }

    private companion object {
        const val SNIPPET_LENGTH = 200
    }
}
