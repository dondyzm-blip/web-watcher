package com.webwatcher.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "WebUtils"

// ─── HTTP Fetcher ─────────────────────────────────────────────────────────────

object WebFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class FetchResult(
        val html: String,
        val statusCode: Int,
        val error: String? = null
    )

    fun fetch(url: String): FetchResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            FetchResult(body, response.code)
        } catch (e: Exception) {
            Log.e(TAG, "Fetch error for $url", e)
            FetchResult("", 0, e.message)
        }
    }
}

// ─── Hash Utility ─────────────────────────────────────────────────────────────

object HashUtil {

    /**
     * HTMLから意味のあるテキストのみ抽出してハッシュ化
     * （スクリプト・スタイル・タイムスタンプ等の動的部分は除外）
     */
    fun computeContentHash(html: String, cssSelector: String? = null): String {
        return try {
            val doc = Jsoup.parse(html)
            // スクリプト・スタイル・メタ除去
            doc.select("script, style, meta, noscript, iframe").remove()
            val content = if (cssSelector != null) {
                doc.select(cssSelector).text()
            } else {
                doc.body()?.text() ?: doc.text()
            }
            sha256(content.trim())
        } catch (e: Exception) {
            sha256(html)
        }
    }

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

// ─── HTML Diff Engine ─────────────────────────────────────────────────────────

object HtmlDiffEngine {

    /**
     * 旧HTML・新HTMLを比較し、変更箇所をハイライトしたHTMLを生成する
     */
    fun generateDiffHtml(oldHtml: String, newHtml: String, cssSelector: String? = null): String {
        val oldDoc = Jsoup.parse(oldHtml)
        val newDoc = Jsoup.parse(newHtml)

        // スクリプト・スタイル除去
        listOf(oldDoc, newDoc).forEach { doc ->
            doc.select("script, noscript, iframe").remove()
        }

        val oldTexts = extractTextNodes(oldDoc, cssSelector)
        val newTexts = extractTextNodes(newDoc, cssSelector)

        // 差分計算（Myers diff algorithm 簡易版）
        val diffs = computeDiff(oldTexts, newTexts)

        return buildDiffHtml(newDoc, diffs, oldTexts, newTexts)
    }

    private fun extractTextNodes(doc: Document, cssSelector: String?): List<String> {
        val root = if (cssSelector != null) doc.select(cssSelector).firstOrNull() ?: doc.body()
                   else doc.body() ?: doc
        return root?.select("*")
            ?.filter { it.childrenSize() == 0 && it.text().isNotBlank() }
            ?.map { it.text().trim() }
            ?: emptyList()
    }

    data class DiffItem(val type: DiffType, val text: String)
    enum class DiffType { ADDED, REMOVED, UNCHANGED }

    private fun computeDiff(old: List<String>, new: List<String>): List<DiffItem> {
        // LCS-based diff
        val m = old.size
        val n = new.size
        val lcs = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) for (j in 1..n) {
            lcs[i][j] = if (old[i-1] == new[j-1]) lcs[i-1][j-1] + 1
                        else maxOf(lcs[i-1][j], lcs[i][j-1])
        }
        val result = mutableListOf<DiffItem>()
        var i = m; var j = n
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && old[i-1] == new[j-1] -> {
                    result.add(0, DiffItem(DiffType.UNCHANGED, new[j-1])); i--; j--
                }
                j > 0 && (i == 0 || lcs[i][j-1] >= lcs[i-1][j]) -> {
                    result.add(0, DiffItem(DiffType.ADDED, new[j-1])); j--
                }
                else -> {
                    result.add(0, DiffItem(DiffType.REMOVED, old[i-1])); i--
                }
            }
        }
        return result
    }

    private fun buildDiffHtml(
        baseDoc: Document,
        diffs: List<DiffItem>,
        oldTexts: List<String>,
        newTexts: List<String>
    ): String {
        val addedSet = diffs.filter { it.type == DiffType.ADDED }.map { it.text }.toSet()
        val removedSet = diffs.filter { it.type == DiffType.REMOVED }.map { it.text }.toSet()

        // ページ内の要素をハイライト
        baseDoc.select("*").filter { it.childrenSize() == 0 && it.text().isNotBlank() }.forEach { el ->
            val text = el.text().trim()
            when {
                addedSet.contains(text) -> {
                    el.attr("style", "${el.attr("style")} background-color:#c8f7c5 !important; outline: 2px solid #27ae60 !important; border-radius:2px;")
                    el.attr("data-diff", "added")
                }
                removedSet.contains(text) -> {
                    // 削除要素は打ち消し線で表示
                    el.attr("style", "${el.attr("style")} background-color:#fadadd !important; outline: 2px solid #e74c3c !important; text-decoration:line-through; opacity:0.7;")
                    el.attr("data-diff", "removed")
                }
            }
        }

        // 削除されたテキストを追加表示
        val removedItems = diffs.filter { it.type == DiffType.REMOVED }
        if (removedItems.isNotEmpty()) {
            val removedDiv = baseDoc.createElement("div").apply {
                attr("style", "position:fixed;bottom:0;left:0;right:0;background:#fff3cd;border-top:2px solid #e74c3c;padding:8px;font-size:12px;z-index:99999;max-height:120px;overflow-y:auto;")
                html("<b>🗑 削除されたテキスト:</b><br>" + removedItems.joinToString("<br>") {
                    "<span style='color:#c0392b'>${it.text}</span>"
                })
            }
            baseDoc.body()?.appendChild(removedDiv)
        }

        // 凡例を追加
        val legend = baseDoc.createElement("div").apply {
            attr("style", "position:fixed;top:8px;right:8px;background:rgba(255,255,255,0.95);border:1px solid #ccc;border-radius:8px;padding:8px 12px;font-size:12px;z-index:99999;box-shadow:0 2px 8px rgba(0,0,0,0.2);")
            html("""
                <b>📊 変更ハイライト</b><br>
                <span style='background:#c8f7c5;padding:2px 6px;border-radius:3px;'>追加</span>
                <span style='background:#fadadd;padding:2px 6px;border-radius:3px;margin-left:4px;text-decoration:line-through;'>削除</span>
            """.trimIndent())
        }
        baseDoc.body()?.appendChild(legend)

        // ビューポート設定
        if (baseDoc.head().select("meta[name=viewport]").isEmpty) {
            baseDoc.head().append("""<meta name="viewport" content="width=device-width, initial-scale=1">""")
        }

        return baseDoc.outerHtml()
    }
}

// ─── File Storage ─────────────────────────────────────────────────────────────

object SnapshotStorage {

    fun saveHtml(context: Context, targetId: Long, html: String, suffix: String = ""): String {
        val dir = File(context.filesDir, "snapshots/$targetId").also { it.mkdirs() }
        val name = "${System.currentTimeMillis()}$suffix.html"
        val file = File(dir, name)
        file.writeText(html, Charsets.UTF_8)
        return file.absolutePath
    }

    fun saveBitmap(context: Context, targetId: Long, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "screenshots/$targetId").also { it.mkdirs() }
        val name = "${System.currentTimeMillis()}.png"
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        return file.absolutePath
    }

    fun readHtml(path: String): String? = try { File(path).readText() } catch (e: Exception) { null }

    fun deleteOldFiles(context: Context, targetId: Long, keepCount: Int = 50) {
        listOf("snapshots", "screenshots").forEach { type ->
            val dir = File(context.filesDir, "$type/$targetId")
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return@forEach
            files.drop(keepCount).forEach { it.delete() }
        }
    }
}
