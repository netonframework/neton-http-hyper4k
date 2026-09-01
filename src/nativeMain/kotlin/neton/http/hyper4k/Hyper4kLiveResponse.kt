package neton.http.hyper4k

import hyper4k.Hyper4kResponseChannel
import neton.core.http.Cookie
import neton.core.http.HttpBodyWriter
import neton.core.http.HttpException
import neton.core.http.HttpResponse
import neton.core.http.HttpStatus
import neton.core.http.MutableHeaders
import neton.core.http.NetonErrorCode

/**
 * 真流式 [HttpResponse]：handler 直接写 hyper4k 的下行通道（SSE / relay 依赖）。
 *
 * 与 KtorLiveResponse 一一对应，语义也保持一致：所有「提交」入口
 * （write / stream / redirect）统一置 [isCommitted]，提交后禁止二次写。
 * 适配器据此判断「handler 已经自己应答了」，不再二次写出。
 *
 * 客户端中途断开不是错误：[Hyper4kResponseChannel.write] 返回 false 时停止写入、
 * 收尾返回，SSE 客户端关页面走的就是这条路。
 */
internal class Hyper4kLiveResponse(
    private val channel: Hyper4kResponseChannel,
    private val corsHeaders: Map<String, List<String>>,
) : HttpResponse {

    override val headers: MutableHeaders = SimpleMutableHeaders()

    override var status: HttpStatus = HttpStatus.OK

    /** hyper4k 的头块是扁平文本，Set-Cookie 直接作为一行头写出。 */
    override fun cookie(cookie: Cookie) {
        headers.add("Set-Cookie", encodeCookie(cookie))
    }

    private var committed = false
    override val isCommitted: Boolean get() = committed

    private var writtenBytes: Long = 0L
    override val bytesOut: Long get() = writtenBytes

    /** 客户端是否已经断开（write 报过 CLIENT_GONE）。 */
    var clientGone: Boolean = false
        private set

    private fun ensureNotCommitted() {
        if (committed) throw HttpException(
            NetonErrorCode.INTERNAL_ERROR,
            "Response already committed (ResponseAlreadyCommitted)",
        )
    }

    /**
     * 汇总要发出的响应头。
     *
     * Content-Length 一律剔除：流式响应的长度由引擎按协议表达
     * （HTTP/1.1 chunked 或 HTTP/2 DATA 帧），自己写一个只会和真实长度打架。
     */
    private fun outgoingHeaders(): Map<String, List<String>> = buildMap<String, MutableList<String>> {
        for (name in headers.names()) {
            if (name.equals("Content-Length", ignoreCase = true)) continue
            getOrPut(name) { mutableListOf() }.addAll(headers.getAll(name))
        }
        // CORS 头必须在提交**前**注入：一旦开始写 body 就补不上头了。
        for ((name, values) in corsHeaders) {
            getOrPut(name) { mutableListOf() }.addAll(values)
        }
        if (none { it.key.equals("Content-Type", ignoreCase = true) }) {
            contentType?.let { put("Content-Type", mutableListOf(it)) }
        }
    }

    override suspend fun write(data: ByteArray) {
        ensureNotCommitted()
        committed = true
        channel.begin(status.code, outgoingHeaders())
        if (data.isNotEmpty() && !channel.write(data)) {
            clientGone = true
        }
        writtenBytes = channel.bytesWritten
        channel.finish()
    }

    /**
     * 真流式写出：每块 writeChunk 直接落到引擎的 body 通道并即时发出。
     *
     * 背压由通道本身提供——客户端读得慢时 writeChunk 会等，
     * 这正是「不要把整个响应先在内存里堆起来」的意义。
     */
    override suspend fun stream(block: suspend HttpBodyWriter.() -> Unit) {
        ensureNotCommitted()
        committed = true
        channel.begin(status.code, outgoingHeaders())
        val writer = object : HttpBodyWriter {
            override suspend fun writeChunk(chunk: ByteArray) {
                if (clientGone) return
                if (!channel.write(chunk)) clientGone = true
            }
        }
        try {
            writer.block()
        } finally {
            writtenBytes = channel.bytesWritten
            channel.finish()
        }
    }

    override suspend fun redirect(url: String, status: HttpStatus) {
        ensureNotCommitted()
        this.status = status
        header("Location", url)
        committed = true
        channel.begin(status.code, outgoingHeaders())
        channel.finish()
    }
}

private fun encodeCookie(cookie: Cookie): String = buildString {
    append(cookie.name).append('=').append(cookie.value)
    cookie.path?.let { append("; Path=").append(it) }
    cookie.domain?.let { append("; Domain=").append(it) }
    cookie.maxAge?.let { append("; Max-Age=").append(it) }
    if (cookie.secure) append("; Secure")
    if (cookie.httpOnly) append("; HttpOnly")
    cookie.sameSite?.let {
        append("; SameSite=").append(it.name.lowercase().replaceFirstChar(Char::uppercase))
    }
}

/** 简化的 MutableHeaders 实现：保留原始大小写，查找大小写不敏感。 */
private class SimpleMutableHeaders : MutableHeaders {
    private val map = LinkedHashMap<String, MutableList<String>>()

    private fun actualName(name: String): String? = map.keys.firstOrNull { it.equals(name, ignoreCase = true) }

    override fun get(name: String): String? = actualName(name)?.let { map[it]?.firstOrNull() }
    override fun getAll(name: String): List<String> = actualName(name)?.let { map[it] } ?: emptyList()
    override fun contains(name: String): Boolean = actualName(name) != null
    override fun names(): Set<String> = map.keys
    override fun toMap(): Map<String, List<String>> = map.mapValues { it.value.toList() }

    override fun set(name: String, value: String) {
        actualName(name)?.let(map::remove)
        map[name] = mutableListOf(value)
    }

    override fun add(name: String, value: String) {
        map.getOrPut(actualName(name) ?: name) { mutableListOf() }.add(value)
    }

    override fun remove(name: String) {
        actualName(name)?.let(map::remove)
    }

    override fun clear() = map.clear()
}
