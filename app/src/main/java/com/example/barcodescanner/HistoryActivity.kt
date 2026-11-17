package com.example.barcodescanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodescanner.data.PreferencesManager
import com.example.barcodescanner.data.database.AppDatabase
import com.example.barcodescanner.data.database.entities.HistoryEntity
import com.example.barcodescanner.utils.Util
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class HistoryActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HistoryActivity"
        private val RETENTION_DAYS = intArrayOf(15, 30, 60, 90, 180)
        private const val REMOTE_QUERY_DEBOUNCE_MS = 3000L
        private const val RESULT_PLACEHOLDER = "{result}"
        private const val DEFAULT_REMOTE_QUERY_URL = "https://example.com/api?cxid="
    }

    private lateinit var database: AppDatabase
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var adapter: HistoryAdapter
    private var searchJob: Job? = null
    private var remoteQueryJob: Job? = null
    private var remoteRequestGeneration = 0
    private lateinit var etSearch: EditText

    private var localItems: List<HistoryEntity> = emptyList()
    private var remoteItems: List<HistoryListItem.Remote> = emptyList()
    private var currentRemoteQuery = ""

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        database = (application as App).database
        preferencesManager = (application as App).preferencesManager

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.history_title)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupRecyclerView()

        etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                loadHistory(s?.toString())
            }
        })

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
        val remoteQuery = query?.trim().orEmpty()
        currentRemoteQuery = remoteQuery

        searchJob?.cancel()
        cancelRemoteQuery()

        searchJob = lifecycleScope.launch {
            val flow = if (query.isNullOrBlank()) {
                database.historyDao().getAll()
            } else {
                database.historyDao().search("%${query}%")
            }
            var remoteScheduled = false
            flow.collectLatest { historyList ->
                localItems = historyList
                renderItems()

                if (!remoteScheduled) {
                    remoteScheduled = true
                    if (remoteQuery.isNotEmpty()) {
                        scheduleRemoteQuery(remoteQuery)
                    }
                }
            }
        }
    }

    /** 取消上一次远程查询；已经返回的旧结果不会覆盖当前搜索。 */
    private fun cancelRemoteQuery() {
        remoteQueryJob?.cancel()
        remoteQueryJob = null
        remoteRequestGeneration++
        if (remoteItems.isNotEmpty()) {
            remoteItems = emptyList()
            renderItems()
        }
    }

    /**
     * 输入停止 3 秒后再发起远程查询；连续输入时只保留最后一次查询结果。
     */
    private fun scheduleRemoteQuery(query: String) {
        if (query.isBlank()) return
        if (!preferencesManager.getBoolean(PreferencesManager.KEY_REMOTE_QUERY_ENABLED, false)) {
            return
        }

        val generation = ++remoteRequestGeneration
        val failureText = getString(R.string.remote_query_failed)
        val noMatchText = getString(R.string.remote_query_no_match)
        val missingUrlText = getString(R.string.remote_query_url_missing)

        remoteQueryJob = lifecycleScope.launch {
            delay(REMOTE_QUERY_DEBOUNCE_MS)
            if (!isActive || generation != remoteRequestGeneration || currentRemoteQuery != query) {
                return@launch
            }

            val url = buildRemoteQueryUrl(query)
            if (url.isBlank()) {
                remoteItems = listOf(HistoryListItem.Remote(missingUrlText, null))
                renderItems()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                fetchRemoteQuery(url, failureText, noMatchText)
            }
            if (!isActive || generation != remoteRequestGeneration || currentRemoteQuery != query) {
                return@launch
            }
            remoteItems = result
            renderItems()
        }
    }

    private fun buildRemoteQueryUrl(query: String): String {
        val baseUrl = preferencesManager.getString(
            PreferencesManager.KEY_REMOTE_QUERY_URL,
            DEFAULT_REMOTE_QUERY_URL
        ).trim()
        if (baseUrl.isEmpty()) return ""

        val encodedQuery = Uri.encode(query)
        return if (baseUrl.contains(RESULT_PLACEHOLDER)) {
            baseUrl.replace(RESULT_PLACEHOLDER, encodedQuery)
        } else {
            baseUrl + encodedQuery
        }
    }

    private fun fetchRemoteQuery(
        url: String,
        failureText: String,
        noMatchText: String
    ): List<HistoryListItem.Remote> {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    listOf(HistoryListItem.Remote(failureText, null))
                } else {
                    parseRemoteResponse(body, failureText, noMatchText)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "远程查询失败", e)
            listOf(HistoryListItem.Remote(failureText, null))
        }
    }

    /**
     * 只使用 msg 和 data，忽略 code。
     * data 为空时只显示 msg；有数据时每条显示 "msg + id" 和 timestamp。
     */
    private fun parseRemoteResponse(
        body: String,
        failureText: String,
        noMatchText: String
    ): List<HistoryListItem.Remote> {
        return try {
            val json = JSONObject(body)
            val msg = json.optString("msg").trim()
            val data = json.optJSONArray("data")

            if (data == null || data.length() == 0) {
                return listOf(HistoryListItem.Remote(msg.ifBlank { noMatchText }, null))
            }

            val rows = ArrayList<HistoryListItem.Remote>(data.length())
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val timestamp = item.optString("timestamp").trim()
                if (id.isBlank() && timestamp.isBlank()) continue

                val title = if (msg.isEmpty()) id else msg + id
                rows.add(HistoryListItem.Remote(title, timestamp.ifBlank { null }))
            }

            if (rows.isEmpty()) {
                listOf(HistoryListItem.Remote(msg.ifBlank { noMatchText }, null))
            } else {
                rows
            }
        } catch (e: Exception) {
            Log.w(TAG, "远程查询结果解析失败", e)
            listOf(HistoryListItem.Remote(failureText, null))
        }
    }

    private fun renderItems() {
        val items = ArrayList<HistoryListItem>(localItems.size + remoteItems.size)
        localItems.forEach { items.add(HistoryListItem.Local(it)) }
        items.addAll(remoteItems)

        adapter.submitList(items)

        val recyclerView = findViewById<RecyclerView>(R.id.rvHistory)
        val tvNoHistory = findViewById<TextView>(R.id.tvNoHistory)
        if (items.isEmpty()) {
            tvNoHistory.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvNoHistory.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
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

sealed class HistoryListItem {
    data class Local(val entity: HistoryEntity) : HistoryListItem()
    data class Remote(val title: String, val timestamp: String?) : HistoryListItem()
}

class HistoryAdapter(
    private val onItemClick: (HistoryEntity) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var items = listOf<HistoryListItem>()

    fun submitList(newItems: List<HistoryListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text1 = itemView.findViewById<TextView>(android.R.id.text1)
        private val text2 = itemView.findViewById<TextView>(android.R.id.text2)
        private val localPrimaryColor = text1.currentTextColor
        private val localSecondaryColor = text2.currentTextColor
        private val remoteColor = ContextCompat.getColor(itemView.context, R.color.remote_result_text)

        fun bind(item: HistoryListItem) {
            val context = itemView.context
            when (item) {
                is HistoryListItem.Local -> {
                    val entity = item.entity
                    text1.setTextColor(localPrimaryColor)
                    text2.setTextColor(localSecondaryColor)
                    text1.text = context.getString(
                        R.string.history_item_title,
                        entity.barcodeType,
                        entity.resultText
                    )
                    text2.text = Util.formatTimestamp(entity.timestamp)
                    text2.visibility = View.VISIBLE
                    itemView.isClickable = true
                    itemView.isFocusable = true
                    itemView.setOnClickListener { onItemClick(entity) }
                }

                is HistoryListItem.Remote -> {
                    text1.setTextColor(remoteColor)
                    text2.setTextColor(remoteColor)
                    text1.text = item.title
                    if (item.timestamp.isNullOrBlank()) {
                        text2.text = null
                        text2.visibility = View.GONE
                    } else {
                        text2.text = item.timestamp
                        text2.visibility = View.VISIBLE
                    }
                    itemView.setOnClickListener(null)
                    itemView.isClickable = false
                    itemView.isFocusable = false
                }
            }
        }
    }
}
