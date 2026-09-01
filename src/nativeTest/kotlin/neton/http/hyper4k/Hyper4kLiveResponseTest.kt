package neton.http.hyper4k

import hyper4k.Hyper4kResponseChannel
import kotlinx.coroutines.runBlocking
import neton.core.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Hyper4kLiveResponseTest {

    @Test
    fun flushesEachChunkInsteadOfBufferingTheWholeBody() = runBlocking {
        val channel = RecordingChannel()
        val response = Hyper4kLiveResponse(channel, corsHeaders = emptyMap())
        response.contentType = "text/event-stream"

        response.stream {
            writeChunk("data: 1\n\n")
            // 第 1 块必须在第 2 块产生**之前**就已经交给引擎——
            // 缓冲实现在这里会是空的，那正是 SSE 静默失效的样子。
            assertEquals(listOf("data: 1\n\n"), channel.chunks)
            writeChunk("data: 2\n\n")
        }

        assertEquals(listOf("data: 1\n\n", "data: 2\n\n"), channel.chunks)
        assertEquals(200, channel.status)
        assertEquals(listOf("text/event-stream"), channel.headers["Content-Type"])
        assertTrue(channel.isFinished)
        assertTrue(response.isCommitted)
        assertEquals(18L, response.bytesOut)
    }

    @Test
    fun dropsContentLengthAndInjectsCorsBeforeCommitting() = runBlocking {
        val channel = RecordingChannel()
        val response = Hyper4kLiveResponse(
            channel,
            corsHeaders = mapOf("Access-Control-Allow-Origin" to listOf("https://example.com")),
        )
        // 流式响应的长度由引擎按协议表达，handler 自己写的 Content-Length 只会和真实长度打架。
        response.header("Content-Length", "999")
        response.header("X-Trace", "abc")

        response.stream { writeChunk("chunk") }

        assertFalse(channel.headers.keys.any { it.equals("Content-Length", ignoreCase = true) })
        assertEquals(listOf("abc"), channel.headers["X-Trace"])
        assertEquals(listOf("https://example.com"), channel.headers["Access-Control-Allow-Origin"])
    }

    @Test
    fun stopsWritingOnceTheClientIsGone() = runBlocking {
        val channel = RecordingChannel(acceptWrites = 1)
        val response = Hyper4kLiveResponse(channel, corsHeaders = emptyMap())

        response.stream {
            writeChunk("first")
            writeChunk("second")
            writeChunk("third")
        }

        // 客户端关页面是正常路径：停止产生数据，照常收尾，不抛异常。
        assertContentEquals(listOf("first"), channel.chunks)
        assertTrue(response.clientGone)
        assertTrue(channel.isFinished)
    }

    @Test
    fun rejectsASecondCommit() = runBlocking {
        val channel = RecordingChannel()
        val response = Hyper4kLiveResponse(channel, corsHeaders = emptyMap())

        response.write("body".encodeToByteArray())

        assertTrue(response.isCommitted)
        assertFailsWith<neton.core.http.HttpException> {
            response.stream { writeChunk("again") }
        }
        Unit
    }

    @Test
    fun redirectCommitsWithoutABody() = runBlocking {
        val channel = RecordingChannel()
        val response = Hyper4kLiveResponse(channel, corsHeaders = emptyMap())

        response.redirect("https://example.com/next", HttpStatus.FOUND)

        assertEquals(HttpStatus.FOUND.code, channel.status)
        assertEquals(listOf("https://example.com/next"), channel.headers["Location"])
        assertTrue(channel.chunks.isEmpty())
        assertTrue(channel.isFinished)
    }

    /** 记录调用的假通道。[acceptWrites] 之后的写入模拟客户端已断开。 */
    private class RecordingChannel(private val acceptWrites: Int = Int.MAX_VALUE) : Hyper4kResponseChannel {
        var status: Int? = null
        var headers: Map<String, List<String>> = emptyMap()
        val chunks = mutableListOf<String>()
        var isFinished = false
        private var begun = false

        override val isStreaming: Boolean get() = begun && !isFinished
        override var bytesWritten: Long = 0L
            private set

        override suspend fun begin(status: Int, headers: Map<String, List<String>>) {
            this.status = status
            this.headers = headers
            begun = true
        }

        override suspend fun write(chunk: ByteArray): Boolean {
            if (chunks.size >= acceptWrites) return false
            chunks.add(chunk.decodeToString())
            bytesWritten += chunk.size
            return true
        }

        override suspend fun finish() {
            isFinished = true
        }
    }
}
