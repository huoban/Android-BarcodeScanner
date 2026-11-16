package com.example.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodescanner.data.database.AppDatabase
import com.example.barcodescanner.data.database.entities.HistoryEntity
import com.example.barcodescanner.utils.Util
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HistoryActivity"
        private val RETENTION_DAYS = intArrayOf(15, 30, 60, 90, 180)
    }

    private lateinit var database: AppDatabase
    private lateinit var adapter: HistoryAdapter
    private var searchJob: kotlinx.coroutines.Job? = null
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
            onBackPressedDispatcher.onBackPressed()
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
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_all -> {
                showDeleteDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteDialog() {
        val options = RETENTION_DAYS.map {
            getString(R.string.history_delete_older_than_days, it)
        } + getString(R.string.history_delete_all)

        AlertDialog.Builder(this)
            .setTitle(R.string.history_delete_title)
            .setItems(options.toTypedArray()) { _, which ->
                if (which < RETENTION_DAYS.size) {
                    confirmDeleteOlderThan(RETENTION_DAYS[which])
                } else {
                    confirmDeleteAll()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun confirmDeleteOlderThan(days: Int) {
        val keepDays = days - 1
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.history_delete_confirm_title_days, days))
            .setMessage(getString(R.string.history_delete_older_than_message, days, keepDays))
            .setPositiveButton(R.string.history_delete_confirm) { _, _ ->
                deleteOlderThanDays(days)
            }
            .setNegativeButton(R.string.history_delete_cancel, null)
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_delete_all_confirm_title)
            .setMessage(R.string.history_delete_all_message)
            .setPositiveButton(R.string.history_delete_confirm) { _, _ ->
                deleteAllHistory()
            }
            .setNegativeButton(R.string.history_delete_cancel, null)
            .show()
    }

    /**
     * 保留今天及之前 (days - 1) 天，共 days 天的记录。
     * 返回 cutoff 时间戳：早于该时间的记录将被删除。
     */
    private fun retentionCutoff(days: Int): Long {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(days - 1))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun deleteOlderThanDays(days: Int) {
        val cutoff = retentionCutoff(days)
        lifecycleScope.launch {
            try {
                val deletedCount = withContext(Dispatchers.IO) {
                    val dao = database.historyDao()
                    val records = dao.getOlderThan(cutoff)
                    dao.deleteOlderThan(cutoff)
                    deleteImageFiles(records)
                    records.size
                }
                Toast.makeText(
                    this@HistoryActivity,
                    getString(R.string.history_deleted_days, days, deletedCount),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "删除历史记录失败", e)
                Toast.makeText(this@HistoryActivity, R.string.history_delete_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteAllHistory() {
        lifecycleScope.launch {
            try {
                val deletedCount = withContext(Dispatchers.IO) {
                    val dao = database.historyDao()
                    val records = dao.getAllOnce()
                    dao.deleteAll()
                    deleteImageFiles(records)
                    records.size
                }
                Toast.makeText(
                    this@HistoryActivity,
                    getString(R.string.history_deleted_all, deletedCount),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "清空历史记录失败", e)
                Toast.makeText(this@HistoryActivity, R.string.history_delete_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 删除历史记录对应的截图，避免图片文件长期残留。 */
    private fun deleteImageFiles(records: List<HistoryEntity>) {
        val imagesDir = File(filesDir, "images").canonicalFile
        records.asSequence()
            .mapNotNull { it.imagePath }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { path ->
                try {
                    val file = File(path).canonicalFile
                    if (file.parentFile == imagesDir && file.exists() && !file.delete()) {
                        Log.w(TAG, "截图删除失败: $path")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "截图删除异常: $path", e)
                }
            }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.rvHistory)
        adapter = HistoryAdapter(
            onItemClick = { entity ->
                copyToClipboard(entity.resultText)
                openImagePreview(entity)
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

    private fun copyToClipboard(text: String) {
        Util.copyToClipboard(this, text)
    }

    private fun openImagePreview(entity: HistoryEntity) {
        if (entity.imagePath.isNullOrEmpty()) {
            Toast.makeText(this, R.string.no_image_record, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ImagePreviewActivity::class.java).apply {
            putExtra(ImagePreviewActivity.EXTRA_IMAGE_PATH, entity.imagePath)
        }
        startActivity(intent)
    }
}

class HistoryAdapter(
    private val onItemClick: (HistoryEntity) -> Unit
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
            text1.text = itemView.context.getString(
                R.string.history_item_title,
                entity.barcodeType,
                entity.resultText
            )
            text2.text = Util.formatTimestamp(entity.timestamp)
            itemView.setOnClickListener { onItemClick(entity) }
        }
    }
}
