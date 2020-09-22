package lox

import lox.parser.Parser
import lox.scanner.Scanner
import lox.scanner.Token
import lox.scanner.TokenType
import util.helper.*

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

object Lox {
    private val interpreter = Interpreter()

    private var hadError = false
    private var hadRuntimeError = false

    fun runFile(path: String) {
        val bytes = Files.readAllBytes(Paths.get(path))
        run(String(bytes, Charset.defaultCharset()))

        // Indicate an error in the exit code.
        if (hadError) exitProcess(65)
        if (hadRuntimeError) exitProcess(65)
    }

    fun runPrompt() {
        while (true) {
            print("> "); run(readLine()!!)
            hadError = false
        }
    }

    private fun run(source: String) {
        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()

        val parser = Parser(tokens)
        val statements = parser.parse()

        if (hadError) return

        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        if (hadError) return

        interpreter.interpret(statements)
    }

    fun error(line: Int, message: String) {
        report(line, "", message)
    }

    fun error(token: Token, message: String) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message)
        } else {
            report(token.line, " at '" + token.lexeme + "'", message)
        }
    }

    fun runtimeError(error: RuntimeError) {
        println("${error.message}\n[line ${error.token.line}]")
        hadRuntimeError = true
    }

    private fun report(line: Int, where: String, message: String) {
        printErrln("[line $line] Error$where: $message")
        hadError = true
    }
}

fun main(args: Array<String>) {
    when {
        args.size > 1 -> {
            println("Usage: klox [script]")
            exitProcess(64)
        }
        args.size == 1 -> Lox.runFile(args[0])
        else           -> Lox.runPrompt()
    }
}