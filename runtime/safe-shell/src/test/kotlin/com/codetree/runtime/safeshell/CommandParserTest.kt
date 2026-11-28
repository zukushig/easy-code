package com.codetree.runtime.safeshell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandParserTest {

    private fun success(input: String): ParseResult.Success {
        val result = CommandParser.parse(input)
        assertTrue(result is ParseResult.Success, "expected success for '$input', got $result")
        return result as ParseResult.Success
    }

    private fun failure(input: String): ParseResult.Failure {
        val result = CommandParser.parse(input)
        assertTrue(result is ParseResult.Failure, "expected failure for '$input', got $result")
        return result as ParseResult.Failure
    }

    @Test
    fun `simple command and arguments`() {
        val parsed = success("echo hello world")
        assertEquals("echo", parsed.command)
        assertEquals(listOf("hello", "world"), parsed.args)
    }

    @Test
    fun `double quotes keep spaces in one argument`() {
        val parsed = success("""cat "file with space.txt"""")
        assertEquals("cat", parsed.command)
        assertEquals(listOf("file with space.txt"), parsed.args)
    }

    @Test
    fun `single quotes keep spaces in one argument`() {
        val parsed = success("echo 'hello   world'")
        assertEquals(listOf("hello   world"), parsed.args)
    }

    @Test
    fun `backslash escapes separator character`() {
        val parsed = success("""echo hello\ world""")
        assertEquals(listOf("hello world"), parsed.args)
    }

    @Test
    fun `wildcard characters are passed through literally`() {
        val parsed = success("ls *.txt")
        assertEquals(listOf("*.txt"), parsed.args)
    }

    @Test
    fun `pipe inside quotes is literal`() {
        val parsed = success("""echo "a|b"""")
        assertEquals(listOf("a|b"), parsed.args)
    }

    @Test
    fun `empty input is rejected`() {
        assertEquals("EMPTY_COMMAND", failure("").code)
        assertEquals("EMPTY_COMMAND", failure("    ").code)
    }

    @Test
    fun `unclosed quote is rejected`() {
        assertEquals("UNCLOSED_QUOTE", failure("""echo "unterminated""").code)
        assertEquals("UNCLOSED_QUOTE", failure("echo 'unterminated").code)
    }

    @Test
    fun `dangling escape is rejected`() {
        assertEquals("UNSUPPORTED_SYNTAX", failure("""echo hello\""").code)
    }

    @Test
    fun `pipes are rejected with a readable error`() {
        val error = failure("ls | cat")
        assertEquals("UNSUPPORTED_SYNTAX", error.code)
        assertTrue(error.message.contains("pipe"))
    }

    @Test
    fun `redirection is rejected`() {
        assertEquals("UNSUPPORTED_SYNTAX", failure("echo hi > out.txt").code)
        assertEquals("UNSUPPORTED_SYNTAX", failure("cat < file.txt").code)
        assertEquals("UNSUPPORTED_SYNTAX", failure("echo hi >> out.txt").code)
    }

    @Test
    fun `command chaining background and substitution are rejected`() {
        assertEquals("UNSUPPORTED_SYNTAX", failure("pwd; ls").code)
        assertEquals("UNSUPPORTED_SYNTAX", failure("pwd && ls").code)
        assertEquals("UNSUPPORTED_SYNTAX", failure("pwd || ls").code)
        assertEquals("UNSUPPORTED_SYNTAX", failure("pwd &").code)
        assertEquals("UNSUPPORTED_SYNTAX", failure("echo `whoami`").code)
        assertEquals("UNSUPPORTED_SYNTAX", failure("echo $(whoami)").code)
    }

    @Test
    fun `variable expansion is not interpreted but kept literal`() {
        val parsed = success("echo \$HOME")
        assertEquals(listOf("\$HOME"), parsed.args)
    }

    @Test
    fun `too many arguments are rejected`() {
        val tooMany = "echo " + (1..CommandParser.MAX_ARGUMENTS + 1).joinToString(" ")
        assertEquals("INVALID_ARGUMENTS", failure(tooMany).code)
        val allowed = "echo " + (1..CommandParser.MAX_ARGUMENTS).joinToString(" ")
        assertEquals(CommandParser.MAX_ARGUMENTS, success(allowed).args.size)
    }

    @Test
    fun `overlong input is rejected`() {
        val longInput = "echo " + "a".repeat(CommandParser.MAX_INPUT_LENGTH)
        assertEquals("INVALID_ARGUMENTS", failure(longInput).code)
    }

    @Test
    fun `command only input has no args`() {
        val parsed = success("pwd")
        assertEquals("pwd", parsed.command)
        assertTrue(parsed.args.isEmpty())
    }

    @Test
    fun `multiple quotes and args combine`() {
        val parsed = success("""echo "a b" 'c d' e""")
        assertEquals(listOf("a b", "c d", "e"), parsed.args)
    }

    @Test
    fun `failure results are data classes with stable codes`() {
        val first = failure("ls | cat")
        val second = failure("ls | cat")
        assertEquals(first, second)
        assertInstanceOf(ParseResult.Failure::class.java, first)
    }
}
