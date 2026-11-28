package com.codetree.domain.ai

import com.codetree.domain.common.AppError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiSseParserTest {

    private val parser = OpenAiSseParser()

    private fun delta(content: String): String =
        """data: {"id":"chatcmpl-1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":$content},"finish_reason":null}]}"""

    @Test
    fun `content delta is parsed`() {
        val events = parser.parseLine(delta("\"你好\""))
        assertEquals(listOf(AiEvent.Delta("你好")), events)
    }

    @Test
    fun `escaped quotes and newlines are decoded`() {
        val events = parser.parseLine(delta("\"He said \\\"world\\\"\\nnext line\""))
        assertEquals(1, events.size)
        val event = events.first()
        assertInstanceOf(AiEvent.Delta::class.java, event)
        assertEquals("He said \"world\"\nnext line", (event as AiEvent.Delta).text)
    }

    @Test
    fun `unicode escapes and emoji are preserved`() {
        val events = parser.parseLine(delta("\"\\u4f60\\u597d \\ud83d\\ude80 😀\""))
        assertEquals(1, events.size)
        assertEquals("你好 🚀 😀", (events.first() as AiEvent.Delta).text)
    }

    @Test
    fun `done sentinel completes stream`() {
        assertEquals(listOf(AiEvent.Completed), parser.parseLine("data: [DONE]"))
        assertEquals(listOf(AiEvent.Completed), parser.parseLine("data:[DONE]"))
    }

    @Test
    fun `usage chunk produces usage event`() {
        val events = parser.parseLine(
            """data: {"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":5,"total_tokens":17}}""",
        )
        assertEquals(listOf(AiEvent.Usage(12, 5)), events)
    }

    @Test
    fun `delta and usage in the same chunk keep order`() {
        val events = parser.parseLine(
            """data: {"choices":[{"delta":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":2}}""",
        )
        assertEquals(2, events.size)
        assertEquals(AiEvent.Delta("ok"), events[0])
        assertEquals(AiEvent.Usage(1, 2), events[1])
    }

    @Test
    fun `empty delta content produces no event`() {
        assertTrue(parser.parseLine(delta("\"\"")).isEmpty())
        assertTrue(parser.parseLine(delta("null")).isEmpty())
        assertTrue(parser.parseLine("""data: {"choices":[{"delta":{}}]}""").isEmpty())
    }

    @Test
    fun `finish reason without content produces no event`() {
        assertTrue(
            parser.parseLine("""data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""").isEmpty(),
        )
    }

    @Test
    fun `invalid json is ignored without crashing`() {
        assertTrue(parser.parseLine("data: {not-json").isEmpty())
        assertTrue(parser.parseLine("data: [1,2,3]").isEmpty())
        assertTrue(parser.parseLine("data:").isEmpty())
        assertTrue(parser.parseLine("data: ").isEmpty())
    }

    @Test
    fun `blank lines comments and non data fields are ignored`() {
        assertTrue(parser.parseLine("").isEmpty())
        assertTrue(parser.parseLine("   ").isEmpty())
        assertTrue(parser.parseLine(": keep-alive").isEmpty())
        assertTrue(parser.parseLine("event: message").isEmpty())
        assertTrue(parser.parseLine("id: 42").isEmpty())
        assertTrue(parser.parseLine("retry: 3000").isEmpty())
        assertTrue(parser.parseLine("hello world").isEmpty())
    }

    @Test
    fun `crlf and leading whitespace are tolerated`() {
        val raw = "  data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\r\n"
        assertEquals(listOf(AiEvent.Delta("hi")), parser.parseLine(raw))
    }

    @Test
    fun `stream error field maps to internal failure`() {
        val events = parser.parseLine("""data: {"error":{"message":"quota exceeded","type":"rate_limit"}}""")
        assertEquals(1, events.size)
        val failure = events.first()
        assertInstanceOf(AiEvent.Failed::class.java, failure)
        val error = (failure as AiEvent.Failed).error
        assertInstanceOf(AppError.Internal::class.java, error)
        assertEquals("internal", error.code)
        assertTrue(error.message.contains("quota exceeded"))
    }

    @Test
    fun `stream error snake and camel keys are supported`() {
        assertTrue(parser.parseLine("""data: {"stream_error":"upstream reset"}""").first() is AiEvent.Failed)
        assertTrue(parser.parseLine("""data: {"streamError":"upstream reset"}""").first() is AiEvent.Failed)
    }

    @Test
    fun `full mock stream assembles answer and completes`() {
        val stream = listOf(
            ": keep-alive",
            "",
            delta("\"列表\""),
            delta("\"推导式\""),
            delta("\"可以\""),
            delta("\"一行生成列表。\""),
            """data: {"choices":[],"usage":{"prompt_tokens":20,"completion_tokens":8}}""",
            "data: [DONE]",
        )

        val text = StringBuilder()
        var completed = false
        var usage: AiEvent.Usage? = null
        for (line in stream) {
            for (event in parser.parseLine(line)) {
                when (event) {
                    is AiEvent.Delta -> text.append(event.text)
                    is AiEvent.Usage -> usage = event
                    AiEvent.Completed -> completed = true
                    else -> Unit
                }
            }
        }

        assertEquals("列表推导式可以一行生成列表。", text.toString())
        assertTrue(completed)
        assertEquals(AiEvent.Usage(20, 8), usage)
    }

    @Test
    fun `parser is stateless and reusable`() {
        repeat(3) {
            assertEquals(listOf(AiEvent.Delta("x")), parser.parseLine(delta("\"x\"")))
            assertEquals(listOf(AiEvent.Completed), parser.parseLine("data: [DONE]"))
        }
    }
}
