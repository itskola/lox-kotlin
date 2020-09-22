package lox

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

import lox.scanner.TokenType.*
import lox.scanner.Token

class Interpreter : Expr.Visitor<Any?>, Stmt.Visitor<Unit> {
    private val globals = Environment()
    private var environment = globals
    private var locals = HashMap<Expr, Int>()

    init {
        globals.define("clock", object : LoxCallable {
            override fun arity(): Int = 0

            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                return System.currentTimeMillis().toDouble() / 1000.0
            }

            override fun toString(): String = "<native fn>"
        })
    }

    fun interpret(statements: List<Stmt?>) {
        try {
            for (statement in statements)
                execute(statement!!)
        } catch (error: RuntimeError) {
            Lox.runtimeError(error)
        }
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    /* ========================================================================================================== */

    private fun execute(stmt: Stmt) {
        stmt.accept(this)
    }

    fun executeBlock(statements: List<Stmt?>, environment: Environment) {
        val previous = this.environment
        try {
            this.environment = environment
            for (statement in statements)
                execute(statement!!)
        } finally {
            this.environment = previous
        }
    }

    override fun visit(stmt: Block) {
        executeBlock(stmt.statements, Environment(environment))
    }

    override fun visit(stmt: Stmt.Companion.Class) {
        var superclass: Any? = null
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass)
            if (superclass !is LoxClass) {
                throw RuntimeError(stmt.superclass.name, "Superclass must be a class.")
            }
        }

        environment.define(stmt.name.lexeme, null)

        if (superclass != null) {
            environment = Environment(environment)
            environment.define("super", superclass)
        }

        val methods = HashMap<String, LoxFunction>()
        for (method in stmt.methods) {
            val function = LoxFunction(method, environment, method.name.lexeme == "init")
            methods[method.name.lexeme] = function
        }

        val klass = LoxClass(stmt.name.lexeme, superclass as LoxClass?, methods)

        if (superclass != null) {
            environment = environment.enclosing!!
        }

        environment.assign(stmt.name, klass)
    }

    override fun visit(stmt: Expression) {
        evaluate(stmt.expression)
    }

    override fun visit(stmt: Stmt.Companion.Function) {
        val function = LoxFunction(stmt, environment, false)
        environment.define(stmt.name.lexeme, function)
    }

    override fun visit(stmt: If) {
        if      (isTruthy(evaluate(stmt.condition))) execute(stmt.thenBranch)
        else if (stmt.elseBranch != null           ) execute(stmt.elseBranch)
    }

    override fun visit(stmt: Print) {
        val value = evaluate(stmt.expression)
        println(stringify(value))
    }

    override fun visit(stmt: Stmt.Companion.Return) {
        val value = if (stmt.value != null) evaluate(stmt.value) else null
        throw Return(value)
    }

    override fun visit(stmt: Var) {
        val value = if (stmt.initializer != null) evaluate(stmt.initializer) else null
        environment.define(stmt.name.lexeme, value)
    }

    override fun visit(stmt: While) {
        while (isTruthy(evaluate(stmt.condition)))
            execute(stmt.body)
    }

    /* ========================================================================================================== */

    private fun evaluate(expr: Expr): Any? {
        return expr.accept(this)
    }

    override fun visit(expr: Assign): Any? {
        val value = evaluate(expr.value)

        val distance = locals[expr]
        if (distance != null) environment.assignAt(distance, expr.name, value)
        else                  globals.assign(expr.name, value)

        return value
    }

    override fun visit(expr: Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            PLUS  -> {
                if (left is Double && right is Double) left + right
                else if (left is String && right is String) left + right
                else throw RuntimeError(expr.operator, "Operands must be two numbers or two strings.")
            }
            MINUS -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) - (right as Double)
            }
            SLASH -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) / (right as Double)
            }
            STAR  -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) * (right as Double)
            }

            GREATER       -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) >  (right as Double)
            }
            GREATER_EQUAL -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) >= (right as Double)
            }
            LESS          -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) <  (right as Double)
            }
            LESS_EQUAL    -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) <= (right as Double)
            }

            EQUAL_EQUAL -> return isEqual(left, right)
            BANG_EQUAL  -> return !isEqual(left, right)

            else -> null
        }
    }

    override fun visit(expr: Call): Any? {
        val callee = evaluate(expr.callee)

        val arguments = ArrayList<Any?>()
        for (argument in expr.arguments) arguments.add(evaluate(argument))

        if (callee !is LoxCallable) {
            throw RuntimeError(expr.paren, "Can only call functions and classes.")
        }

        // val function = callee as LoxCallable
        if (arguments.size != callee.arity()) {
            throw RuntimeError(expr.paren, "Expected ${callee.arity()} arguments but got ${arguments.size}.")
        }

        return callee.call(this, arguments)
    }

    override fun visit(expr: Get): Any? {
        val obj = evaluate(expr.obj)
        if (obj is LoxInstance) return obj.get(expr.name)
        throw RuntimeError(expr.name, "Only instances have properties.")
    }

    override fun visit(expr: Grouping): Any? {
        return evaluate(expr.expression)
    }

    override fun visit(expr: Literal): Any? {
        return expr.value
    }

    override fun visit(expr: Logical): Any? {
        val left = evaluate(expr.left)

        if (expr.operator.type == OR) {
            if (isTruthy(left)) return left
        } else {
            if (!isTruthy(left)) return left
        }
        return evaluate(expr.right)
    }

    override fun visit(expr: Set): Any? {
        val obj = evaluate(expr.obj)

        if (obj !is LoxInstance) {
            throw RuntimeError(expr.name, "Only instances have fields.")
        }

        val value = if (expr.value != null) evaluate(expr.value) else null
        obj.set(expr.name, value)

        return value
    }

    override fun visit(expr: Expr.Companion.Super): Any? {
        val distance = locals[expr]!!

        val superclass = environment.getAt(distance, "super") as LoxClass
        val obj = environment.getAt(distance - 1, "this") as LoxInstance

        val method = superclass.findMethod(expr.method.lexeme)
            ?: throw RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.")
        return method.bind(obj)
    }

    override fun visit(expr: Expr.Companion.This): Any? {
        return lookUpVariable(expr.keyword, expr)
    }

    override fun visit(expr: Unary): Any? {
        val right = evaluate(expr.right)
        return when (expr.operator.type) {
            MINUS -> {
                checkNumberOperand(expr.operator, right)
                -(right as Double)
            }
            BANG -> !isTruthy(right)
            else -> null
        }
    }

    override fun visit(expr: Variable): Any? {
        return lookUpVariable(expr.name, expr)
    }

    private fun lookUpVariable(name: Token, expr: Expr): Any? {
        val distance = locals[expr]
        return  if (distance != null) environment.getAt(distance, name.lexeme)
                else                  globals.get(name)
    }

    /* ========================================================================================================== */

    private fun stringify(any: Any?): String? {
        if (any == null) return "nil"

        // Hack. Work around Java adding ".0" to integer-valued doubles.
        if (any is Double) {
            var text = any.toString()
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length - 2)
            }
            return text
        }
        return any.toString()
    }

    private fun isEqual(a: Any?, b: Any?): Boolean {
        // nil is only equal to nil.
        if (a == null && b == null) return true
        return a?.equals(b) ?: false
    }

    private fun isTruthy(any: Any?): Boolean {
        return when (any) {
            null -> false
            is Boolean -> any // automatic cast by Kotlin
            else -> true
        }
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (left is Double && right is Double) return
        throw RuntimeError(operator, "Operands must be a numbers")
    }

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand is Double) return
        throw RuntimeError(operator, "Operand must be a number")
    }

}