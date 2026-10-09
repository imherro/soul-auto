package com.soulbot.app

import android.app.Activity
import android.app.Instrumentation
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import java.io.File
import java.security.MessageDigest

/** Uses a disposable synthetic database, never the user's conversation database. */
class MemoryDatabaseInstrumentation : Instrumentation() {
    private var probeModel = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        probeModel = arguments?.getString("model_probe") == "true"
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val file = File(targetContext.cacheDir, "memory-regression-${System.nanoTime()}.db")
        var helper: ConversationMemoryDatabase? = null
        try {
            val actualFile = targetContext.getDatabasePath("conversation_memory.db")
            if (actualFile.exists()) {
                fun fingerprint(): Pair<Int, String> = SQLiteDatabase.openDatabase(
                    actualFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
                ).use { db ->
                    var count = 0
                    val digest = MessageDigest.getInstance("SHA-256")
                    db.rawQuery("SELECT id,event_key,contact,role,content,observed_at,source FROM conversation_messages ORDER BY id", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            count++
                            for (column in 0 until cursor.columnCount) {
                                val value = cursor.getString(column).toByteArray(Charsets.UTF_8)
                                digest.update(value.size.toString().toByteArray())
                                digest.update(0.toByte())
                                digest.update(value)
                            }
                        }
                    }
                    count to digest.digest().joinToString("") { "%02x".format(it) }
                }
                val before = fingerprint()
                ConversationMemoryDatabase(targetContext).use { db -> check(db.readableDatabase.version == 2) }
                check(fingerprint() == before) { "Existing conversation rows changed during migration" }
                result.putString("existing_database_migration", "passed; schema=2; preserved_messages=${before.first}")
            }
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                old.execSQL("CREATE TABLE conversation_messages (id INTEGER PRIMARY KEY AUTOINCREMENT, event_key TEXT NOT NULL UNIQUE, contact TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, observed_at INTEGER NOT NULL, source TEXT NOT NULL)")
                old.execSQL("CREATE INDEX idx_conversation_contact_time ON conversation_messages(contact, observed_at, id)")
                old.execSQL("INSERT INTO conversation_messages(event_key,contact,role,content,observed_at,source) VALUES('legacy','样例甲','in','旧版保留消息',1,'chat')")
                old.version = 1
            }
            helper = ConversationMemoryDatabase(targetContext, file.absolutePath)
            check(helper.readableDatabase.version == 2)
            check(helper.messageCount() == 1)
            check(helper.recentContext("样例甲", emptyList()).single().second == "旧版保留消息")
            val thread = (1..80).map { (if (it % 2 == 0) "out" else "in") to "测试消息$it" }
            helper.syncVisibleThread("样例甲", thread)
            check(helper.syncVisibleThread("样例甲", thread.takeLast(10)) == 0)
            check(helper.syncVisibleThread("样例甲", thread.subList(10, 20)) == 0)
            check(helper.recentContext("样例甲", thread.takeLast(10)) == thread.takeLast(60))
            check(helper.recentContext("另一个人", emptyList()).isEmpty())
            val now = System.currentTimeMillis()
            val (state, batch) = checkNotNull(helper.summaryUpdate("样例甲", now))
            check(helper.markSummaryAttempt("样例甲", state, now))
            check(helper.summaryUpdate("样例甲", now + 1) == null)
            val summary = "对方资料：暂无\n近期话题：测试对话\n约定与待办：暂无"
            check(!helper.saveSummary("样例甲", "无效摘要", state, batch, now))
            check(helper.saveSummary("样例甲", summary, state, batch, now))
            check(helper.summaryCount() == 1)
            helper.close()
            helper = ConversationMemoryDatabase(targetContext, file.absolutePath)
            check(helper.summaryState("样例甲").text == summary)
            check(helper.summaryState("样例甲").throughMessageId == batch.throughMessageId)
            check(helper.summaryState("另一个人").text.isEmpty())
            (1..12).forEach { helper.recordMessage("样例甲", "in", "新增$it") }
            val nextTime = maxOf(System.currentTimeMillis(), now + 2)
            val (nextState, nextBatch) = checkNotNull(helper.summaryUpdate("样例甲", nextTime))
            check(nextBatch.messages.size == 12)
            check(helper.markSummaryAttempt("样例甲", nextState, nextTime))
            helper.clearAll()
            check(!helper.saveSummary("样例甲", summary, nextState, nextBatch, nextTime))
            check(helper.messageCount() == 0 && helper.summaryCount() == 0)
            result.putString("database_checks", "migration, preservation, 60-message context, overlap, isolation, incremental cursor, cooldown, persistence, clear-in-flight passed")
            if (probeModel) {
                val endpoint = checkNotNull(ModelEndpointPrefs.getEndpoints(targetContext).firstOrNull(ModelEndpointRules::isRunnable))
                val messages = listOf(
                    "我养了一只猫，叫团团", "最近在准备去杭州旅行", "我想周六去", "你觉得坐高铁好吗",
                    "我决定坐高铁", "明天晚上有空吗", "原来的视频通话先取消吧", "改成明天晚上八点电话聊，可以吗",
                    "那就确认明天晚上八点电话聊", "酒店我还没订", "想住西湖附近", "你知道那边交通方便吗",
                ).mapIndexed { i, text -> SummaryMessage(i + 1L, "in", text, 1_791_532_800_000L + i * 1000) }
                val call = ModelClient.chatOnce(endpoint.apiKey, endpoint.baseUrl, endpoint.model,
                    ConversationSummaryPolicy.systemPrompt(), listOf("user" to ConversationSummaryPolicy.input(
                        ConversationSummaryState(), ConversationSummaryBatch(messages),
                    )), ConversationSummaryPolicy.CALL_BUDGET_MS)
                val actual = checkNotNull(ConversationSummaryPolicy.validatedSummary(call.content)) { "Model summary not valid; http=${call.httpStatus}" }
                check(actual.contains("猫") && actual.contains("杭州") && actual.contains("电话"))
                result.putString("model_summary_check", "passed; http=${call.httpStatus}; chars=${actual.length}; model=${endpoint.model}")
            }
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("failure", "${error.javaClass.simpleName}: ${error.message}")
            finish(Activity.RESULT_CANCELED, result)
        } finally {
            helper?.close()
            SQLiteDatabase.deleteDatabase(file)
        }
    }
}
