package com.example.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodescanner.data.database.AppDatabase
import com.example.barcodescanner.data.database.entities.HistoryEntity
import com.example.barcodescanner.utils.CsvExporter
import com.example.barcodescanner.utils.Util
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var adapter: HistoryAdapter
    private val selectedItems = mutableSetOf<Long>()
    private var isMultiSelectMode = false
    private var searchJob: Job? = null
    private lateinit var etSearch: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        database = (application as App).database

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.history_title)

        toolbar.setNavigationOnClickListener {
            if (isMultiSelectMode) {
                exitMultiSelectMode()
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                loadHistory(s?.toString())
            }
        })

        setupRecyclerView()
        loadHistory(null)

        // 导出按钮
        findViewById<android.widget.Button>(R.id.btnCancelExport).setOnClickListener {
            exitMultiSelectMode()
        }

        findViewById<android.widget.Button>(R.id.btnExportCSV).setOnClickListener {
            exportSelectedToCSV()
        }

        findViewById<android.widget.ImageButton>(R.id.ibMultiShare).setOnClickListener {
            shareSelectedItems()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_all -> {
                deleteAllHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.rvHistory)
        adapter = HistoryAdapter(
            onItemClick = { entity ->
                if (isMultiSelectMode) {
                    toggleSelection(entity.id)
                } else {
                    copyToClipboard(entity.resultText)
                    openImagePreview(entity)
                }
            },
            onItemLongClick = { entity ->
                if (!isMultiSelectMode) {
                    enterMultiSelectMode()
                    toggleSelection(entity.id)
                }
                true
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadHistory(query: String?) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val flow = if (query.isNullOrBlank()) {
                database.historyDao().getAll()
            } else {
                database.historyDao().search("%${query}%")
            }
            flow.collectLatest { historyList ->
                adapter.submitList(historyList)
                val tvNoHistory = findViewById<TextView>(R.id.tvNoHistory)
                if (historyList.isEmpty()) {
                    tvNoHistory.visibility = android.view.View.VISIBLE
                    findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvHistory).visibility = android.view.View.GONE
                } else {
                    tvNoHistory.visibility = android.view.View.GONE
                    findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvHistory).visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun enterMultiSelectMode() {
        isMultiSelectMode = true
        selectedItems.clear()
        findViewById<android.widget.LinearLayout>(R.id.llExportBar).visibility = android.view.View.VISIBLE
        adapter.notifyDataSetChanged()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedItems.clear()
        findViewById<android.widget.LinearLayout>(R.id.llExportBar).visibility = android.view.View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun toggleSelection(id: Long) {
        if (selectedItems.contains(id)) {
            selectedItems.remove(id)
        } else {
            selectedItems.add(id)
        }
        adapter.notifyDataSetChanged()
    }

    private fun exportSelectedToCSV() {
        if (selectedItems.isEmpty()) return
        lifecycleScope.launch {
            val selectedHistory = database.historyDao().getAll().first { list ->
                list.any { it.id in selectedItems }
            }.filter { it.id in selectedItems }
            CsvExporter.export(this@HistoryActivity, selectedHistory)
            exitMultiSelectMode()
        }
    }

    private fun shareSelectedItems() {
        if (selectedItems.isEmpty()) return
        lifecycleScope.launch {
            val selectedHistory = database.historyDao().getAll().first { list ->
                list.any { it.id in selectedItems }
            }.filter { it.id in selectedItems }
            val shareText = selectedHistory.joinToString("\n\n") { "${it.barcodeType}: ${it.resultText}" }
            Util.shareText(this@HistoryActivity, shareText)
            exitMultiSelectMode()
        }
    }

    private fun deleteAllHistory() {
        lifecycleScope.launch {
            database.historyDao().deleteAll()
        }
    }

    private fun copyToClipboard(text: String) {
        Util.copyToClipboard(this, text)
    }

    private fun openImagePreview(entity: HistoryEntity) {
        if (entity.imagePath.isNullOrEmpty()) {
            Toast.makeText(this, "无图片记录", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ImagePreviewActivity::class.java).apply {
            putExtra(ImagePreviewActivity.EXTRA_IMAGE_PATH, entity.imagePath)
        }
        startActivity(intent)
    }
}

class HistoryAdapter(
    private val onItemClick: (HistoryEntity) -> Unit,
    private val onItemLongClick: (HistoryEntity) -> Boolean
) : androidx.recyclerview.widget.RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var items = listOf<HistoryEntity>()

    fun submitList(newItems: List<HistoryEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        fun bind(entity: HistoryEntity) {
            val text1 = itemView.findViewById<TextView>(android.R.id.text1)
            val text2 = itemView.findViewById<TextView>(android.R.id.text2)
            text1.text = "${entity.barcodeType}: ${entity.resultText}"
            text2.text = Util.formatTimestamp(entity.timestamp)
            itemView.setOnClickListener { onItemClick(entity) }
            itemView.setOnLongClickListener { onItemLongClick(entity) }
        }
    }
}
