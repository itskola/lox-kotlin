package lox.scanner

import lox.Lox
import lox.scanner.TokenType.*

class Scanner(private val source: String) {
    private val tokens = arrayListOf<Token>()
    private val keywords = mapOf<String, TokenType>(
        "var"    to    VAR,
        "true"   to   TRUE,
        "false"  to  FALSE,
        "nil"    to    NIL,
        "if"     to     IF,
        "else"   to   ELSE,
        "for"    to    FOR,
        "while"  to  WHILE,
        "and"    to    AND,
        "or"     to     OR,
        "fun"    to    FUN,
        "return" to RETURN,
        "print"  to  PRINT,
        "class"  to  CLASS,
        "super"  to  SUPER,
        "this"   to   THIS
    )

    private var start: Int = 0
    private var current: Int = 0
    private var line: Int = 1

    fun scanTokens(): ArrayList<Token> {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current
            scanToken()
        }

        tokens.add(Token(EOF, "", null, line))
        return tokens
    }

    private fun scanToken() {
        when (val c = advance()) {
            '(' -> addToken(LEFT_PAREN)
            ')' -> addToken(RIGHT_PAREN)
            '{' -> addToken(LEFT_BRACE)
            '}' -> addToken(RIGHT_BRACE)
            ',' -> addToken(COMMA)
            '.' -> addToken(DOT)
            '-' -> addToken(MINUS)
            '+' -> addToken(PLUS)
            ';' -> addToken(SEMICOLON)
            '*' -> addToken(STAR)
            '/' -> {
                if (match('/')) while(peek() != '\n' && !isAtEnd()) advance()
                else if (match('*')) {
                    while (!isAtEnd()) {
                        if (peek() == '*' && peekNext() == '/') {
                            advance(); advance()
                            break
                        } else if (advance() == '\n') ++line
                    }
                    if (isAtEnd()) Lox.error(line, "Unterminated block comment.")
                }
                else addToken(SLASH)
            }

            '!' -> addToken(if (match('=')) BANG_EQUAL    else BANG)
            '=' -> addToken(if (match('=')) EQUAL_EQUAL   else EQUAL)
            '<' -> addToken(if (match('=')) LESS_EQUAL    else LESS)
            '>' -> addToken(if (match('=')) GREATER_EQUAL else GREATER)

            '"'         -> string()
            in '0'..'9' -> number()

            '\n'            -> ++line
            ' ', '\r', '\t' -> {}

            else -> {
                if (c.isLetter()) identifier()
                else Lox.error(line, "Unexpected character.")
            }
        }
    }

    private fun addToken(type: TokenType) {
        addToken(type, null)
    }

    private fun addToken(type: TokenType, literal: Any?) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, line))
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') ++line
            advance()
        }

        // Unterminated string.
        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.")
            return
        }

        // The closing ".
        advance()

        // Trim the surrounding quotes.
        val value = source.substring(start + 1, current - 1)
        addToken(STRING, value)
    }

    private fun number() {
        while (peek().isDigit()) advance()

        // Look for a fractional part.
        if (peek() == '.' && peekNext().isDigit()) {
            advance() // Consume the "."
            while(peek().isDigit()) advance()
        }

        addToken(NUMBER, source.substring(start, current).toDouble())
    }

    private fun identifier() {
        while (peek().isLetterOrDigit()) advance()

        val text = source.substring(start, current)
        val type = keywords[text] ?: IDENTIFIER

        addToken(type)
    }

    private fun advance(): Char {
        ++current
        return source[current - 1]
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false

        ++current
        return true
    }

    private fun peek(): Char {
        return if (isAtEnd()) 0.toChar() else source[current]
    }
    private fun peekNext(): Char {
        return if (current + 1 >= source.length) 0.toChar() else source[current + 1]
    }

    private fun isAtEnd() : Boolean {
        return current >= source.length
    }

}