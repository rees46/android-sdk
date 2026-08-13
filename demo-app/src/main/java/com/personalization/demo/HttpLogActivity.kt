package com.personalization.demo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.personalization.demo.httplogger.HttpLogEntry
import com.personalization.demo.httplogger.HttpLogStore

/**
 * Debug screen that lists the SDK's HTTP traffic captured by [HttpLogStore]. Tap a row to expand
 * the full request/response, long-press to copy that call, or use "Copy all" / "Clear" in the bar.
 */
class HttpLogActivity : AppCompatActivity() {

    private val adapter = LogAdapter()
    private var unsubscribe: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_http_log)

        val recycler = findViewById<RecyclerView>(R.id.rvHttpLog)
        val empty = findViewById<TextView>(R.id.tvHttpLogEmpty)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnClearLogs).setOnClickListener { HttpLogStore.clear() }
        findViewById<Button>(R.id.btnCopyAllLogs).setOnClickListener {
            val all = HttpLogStore.snapshot().joinToString("\n\n──────────\n\n") { it.fullText() }
            if (all.isBlank()) {
                toast(getString(R.string.http_log_empty))
            } else {
                copy(all)
                toast(getString(R.string.http_log_copied_all))
            }
        }

        fun render() {
            val items = HttpLogStore.snapshot()
            adapter.submit(items)
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }

        unsubscribe = HttpLogStore.subscribe { runOnUiThread { render() } }
        render()
    }

    override fun onDestroy() {
        unsubscribe?.invoke()
        super.onDestroy()
    }

    private fun copy(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("http_log", text))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private inner class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

        private var items: List<HttpLogEntry> = emptyList()
        private val expanded = HashSet<Long>()

        fun submit(newItems: List<HttpLogEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_http_log, parent, false)
            return VH(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = items[position]
            holder.header.text = entry.headerLine()
            holder.header.setTextColor(statusColor(entry))

            val isOpen = expanded.contains(entry.id)
            holder.body.visibility = if (isOpen) View.VISIBLE else View.GONE
            if (isOpen) holder.body.text = entry.fullText()

            holder.itemView.setOnClickListener {
                if (expanded.contains(entry.id)) expanded.remove(entry.id) else expanded.add(entry.id)
                notifyItemChanged(holder.bindingAdapterPosition)
            }
            holder.itemView.setOnLongClickListener {
                copy(entry.fullText())
                toast(getString(R.string.http_log_copied_one))
                true
            }
        }

        private fun statusColor(entry: HttpLogEntry): Int = ContextCompat.getColor(
            this@HttpLogActivity,
            when {
                entry.isError -> R.color.log_status_error
                entry.isSuccess -> R.color.log_status_success
                else -> R.color.log_status_neutral
            }
        )

        private inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val header: TextView = view.findViewById(R.id.tvLogHeader)
            val body: TextView = view.findViewById(R.id.tvLogBody)
        }
    }
}
