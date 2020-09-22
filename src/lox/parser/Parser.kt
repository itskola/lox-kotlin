package lox.parser

import lox.Lox

import lox.grammar.Expr
import lox.grammar.Expr.Companion.Assign
import lox.grammar.Expr.Companion.Binary
import lox.grammar.Expr.Companion.Call
import lox.grammar.Expr.Companion.Get
import lox.grammar.Expr.Companion.Grouping
import lox.grammar.Expr.Companion.Literal
import lox.grammar.Expr.Companion.Logical
import lox.grammar.Expr.Companion.Set
import lox.grammar.Expr.Companion.Unary
import lox.grammar.Expr.Companion.Variable

import lox.grammar.Stmt
import lox.grammar.Stmt.Companion.Block
import lox.grammar.Stmt.Companion.Expression
import lox.grammar.Stmt.Companion.If
import lox.grammar.Stmt.Companion.Print
import lox.grammar.Stmt.Companion.Var
import lox.grammar.Stmt.Companion.While

import lox.scanner.Token
import lox.scanner.TokenType
import lox.scanner.TokenType.*

class Parser(private val tokens: List<Token>) {
    private class ParseError : RuntimeException()

    private var current = 0

    fun parse(): List<Stmt?> {
        val statements = ArrayList<Stmt?>()
        while (!isAtEnd()) {
            statements.add(declaration())
        }

        return statements
    }

    /* ========================================================================================================== */

    private fun declaration(): Stmt? {
        try {
            if (match(CLASS)) return classDeclaration()
            if (match(FUN)  ) return function("function")
            if (match(VAR)  ) return varDeclaration()
            return statement()
        } catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    private fun classDeclaration(): Stmt.Companion.Class {
        val name = consume(IDENTIFIER, "Expect class name.")

        var superclass: Variable? = null
        if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name.")
            superclass = Variable(previous())
        }

        consume(LEFT_BRACE, "Expect '{' before class body.")
        val methods = ArrayList<Stmt.Companion.Function>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function("method"))
        }
        consume(RIGHT_BRACE, "Expect '}' after class body.")

        return Stmt.Companion.Class(name, superclass, methods)
    }

    private fun function(kind: String): Stmt.Companion.Function {
        val name = consume(IDENTIFIER, "Expect $kind name.")

        consume(LEFT_PAREN, "Expect '(' after $kind name.")
        val parameters = ArrayList<Token>()
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255) error(peek(), "Cannot have more than 255 parameters.")
                parameters.add(consume(IDENTIFIER, "Expect parameter name."))
            } while (match(COMMA))
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.")

        consume(LEFT_BRACE, "Expect '{' before $kind body.")
        val body = block()

        return Stmt.Companion.Function(name, parameters, body)
    }

    private fun varDeclaration(): Stmt {
        val name = consume(IDENTIFIER, "Expect variable name.")
        val initializer = if (match(EQUAL)) expression() else null

        consume(SEMICOLON, "Expect ';' after variable declaration.")

        return Var(name, initializer)
    }

    private fun statement(): Stmt {
        return when {
            match(FOR)        -> forStatement()
            match(IF)         -> ifStatement()
            match(PRINT)      -> printStatement()
            match(RETURN)     -> returnStatement()
            match(LEFT_BRACE) -> Block(block())
            match(WHILE)      -> whileStatement()
            else              -> expressionStatement()
        }
    }

    private fun block(): List<Stmt?> {
        val statements = ArrayList<Stmt?>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration())
        }
        consume(RIGHT_BRACE, "Expect '}' after block.")

        return statements
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(SEMICOLON, "Expect ';' after expression.")

        return Expression(expr)
    }

    private fun forStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'if'.")
        val initializer = when {
            match(SEMICOLON) -> null
            match(VAR)       -> varDeclaration()
            else             -> expressionStatement()
        }

        var condition = if (!check(SEMICOLON)) expression() else null
        consume(SEMICOLON, "Expect ';' after loop condition.")

        val increment = if (!check(RIGHT_PAREN)) expression() else null
        consume(RIGHT_PAREN, "Expect ')' after for clauses.")

        var body = statement()
        if (increment != null) body = Block(listOf(body, Expression(increment)))

        if (condition == null) condition = Literal(true)
        body = While(condition, body)

        if (initializer != null) body = Block(listOf(initializer, body))

        return body
    }

    private fun ifStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after if condition.")

        val thenBranch = statement()
        val elseBranch = if (match(ELSE)) statement() else null

        return If(condition, thenBranch, elseBranch)
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(SEMICOLON, "Expect ';' after value.")

        return Print(value)
    }

    private fun returnStatement(): Stmt {
        val keyword = previous()
        val value = if (!check(SEMICOLON)) expression() else null
        consume(SEMICOLON, "Expect ';' after return value.")

        return Stmt.Companion.Return(keyword, value)
    }

    private fun whileStatement(): Stmt {
        consume(LEFT_PAREN, "")
        val condition = expression()
        consume(RIGHT_PAREN, "")

        val body = statement()

        return While(condition, body)
    }

    /* ========================================================================================================== */

    private fun expression(): Expr {
        return assignment()
    }

    private fun assignment(): Expr {
        val expr = or()
        if (match(EQUAL)) {
            val equals = previous()
            val value = assignment()

            if      (expr is Variable) return Assign(expr.name, value)
            else if (expr is Get     ) return Set(expr.obj, expr.name, value)

            error(equals, "Invalid assignment target.")
        }
        return expr
    }

    private fun or(): Expr {
        var expr = and()
        while (match(OR)) {
            val operator = previous()
            val right = and()
            expr = Logical(expr, operator, right)
        }
        return expr
    }

    private fun and(): Expr {
        var expr = equality()
        while (match(AND)) {
            val operator = previous()
            val right = equality()
            expr = Logical(expr, operator, right)
        }
        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            val operator = previous()
            val right = comparison()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun comparison(): Expr {
        var expr = addition()
        while(match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            val operator = previous()
            val right = addition()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun addition(): Expr {
        var expr = multiplication()
        while (match(MINUS, PLUS)) {
            val operator = previous()
            val right = multiplication()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun multiplication(): Expr {
        var expr = unary()
        while (match(SLASH, STAR)) {
            val operator = previous()
            val right: Expr = unary()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun unary(): Expr {
        if (match(BANG, MINUS)) {
            val operator = previous()
            val right = unary()
            return Unary(operator, right)
        }
        return call()
    }

    private fun call(): Expr {
        var expr = primary()
        loop@ while (true) {
            expr = when {
                match(LEFT_PAREN) -> finishCall(expr)
                match(DOT) -> {
                    val name = consume(IDENTIFIER, "Expect property name after '.'")
                    Get(expr, name)
                }
                else -> break@loop
            }
        }

        return expr
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments = mutableListOf<Expr>()
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size >= 255) error(peek(), "Cannot have more than 255 arguments.")
                arguments.add(expression())
            } while (match(COMMA))
        }
        val paren = consume(RIGHT_PAREN, "Expect ')' after arguments.")

        return Call(callee, paren, arguments)
    }

    private fun primary(): Expr {
        if (match(FALSE)) return Literal(FALSE)
        if (match(TRUE) ) return Literal(TRUE)
        if (match(NIL)  ) return Literal(NIL)

        if (match(NUMBER, STRING)) return Literal(previous().literal!!)

        if (match(SUPER)) {
            val keyword = previous()
            consume(DOT, "Expect '.' after 'super'.")
            val method = consume(IDENTIFIER, "Expect superclass method name.")
            return Expr.Companion.Super(keyword, method)
        }

        if (match(THIS)) return Expr.Companion.This(previous())

        if (match(IDENTIFIER)) return Variable(previous())

        if (match(LEFT_PAREN)) {
            val expr = expression()
            consume(RIGHT_PAREN, "Expect ')' after expression.")
            return Grouping(expr)
        }

        throw error(peek(), "Expect expression.")
    }

    /* ========================================================================================================== */

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    private fun check(type: TokenType): Boolean {
        return if (isAtEnd()) false else peek().type == type
    }

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]

    private fun isAtEnd(): Boolean = peek().type == EOF

    private fun error(token: Token, message: String): ParseError {
        Lox.error(token, message)
        return ParseError()
    }

    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if (previous().type === SEMICOLON) return
            when (peek().type) {
                CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RETURN -> return
                else                                           -> advance()
            }
        }
    }

}