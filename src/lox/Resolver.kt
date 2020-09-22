package lox

import lox.grammar.Expr
import lox.grammar.Stmt
import lox.scanner.Token
import java.util.*
import kotlin.collections.HashMap

class Resolver(private val interpreter: Interpreter)
    : Expr.Visitor<Unit>, Stmt.Visitor<Unit> {

    private enum class FunctionType {
        NONE,
        FUNCTION,
        INITIALIZER,
        METHOD
    }

    private enum class ClassType {
        NONE,
        CLASS,
        SUBCLASS
    }

    private val scopes = Stack<MutableMap<String, Boolean>>()

    private var currentFunction = FunctionType.NONE
    private var currentClass = ClassType.NONE

    /* ========================================================================================================== */

    fun resolve(statements: List<Stmt?>) {
        for (statement in statements)
            resolve(statement!!)
    }

    private fun resolve(stmt: Stmt) {
        stmt.accept(this)
    }

    override fun visit(stmt: Stmt.Companion.Block) {
        beginScope()
        resolve(stmt.statements)
        endScope()
    }

    override fun visit(stmt: Stmt.Companion.Class) {
        val enclosingClass = currentClass
        currentClass = ClassType.CLASS

        declare(stmt.name)
        define(stmt.name)

        if (stmt.superclass != null) {
            if (stmt.name.lexeme == stmt.superclass.name.lexeme) {
                Lox.error(stmt.superclass.name, "A class cannot inherit from itself.")
            }

            currentClass = ClassType.SUBCLASS

            resolve(stmt.superclass)

            beginScope()
            scopes.peek()["super"] = true
        }

        beginScope()
        scopes.peek()["this"] = true

        for (method in stmt.methods) {
            var declaration = FunctionType.METHOD
            if (method.name.lexeme == "init") declaration = FunctionType.INITIALIZER
            resolveFunction(method, declaration)
        }

        endScope()

        if (stmt.superclass != null) endScope()

        currentClass = enclosingClass
    }

    override fun visit(stmt: Stmt.Companion.Expression) {
        resolve(stmt.expression)
    }

    override fun visit(stmt: Stmt.Companion.Function) {
        declare(stmt.name)
        define(stmt.name)
        resolveFunction(stmt, FunctionType.FUNCTION)
    }

    private fun resolveFunction(function: Stmt.Companion.Function, type: FunctionType) {
        val enclosingFunction = currentFunction
        currentFunction = type

        beginScope()
        for (param in function.params) {
            declare(param)
            define(param)
        }
        resolve(function.body)
        endScope()

        currentFunction = enclosingFunction
    }

    override fun visit(stmt: Stmt.Companion.If) {
        resolve(stmt.condition)
        resolve(stmt.thenBranch)
        if (stmt.elseBranch != null) resolve(stmt.elseBranch)
    }

    override fun visit(stmt: Stmt.Companion.Print) {
        resolve(stmt.expression)
    }

    override fun visit(stmt: Stmt.Companion.Return) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Cannot return from top-level code.")
        }
        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword, "Cannot return a value from an initializer.")
            }
            resolve(stmt.value)
        }
    }

    override fun visit(stmt: Stmt.Companion.Var) {
        declare(stmt.name)
        if (stmt.initializer != null) resolve(stmt.initializer)
        define(stmt.name)
    }

    override fun visit(stmt: Stmt.Companion.While) {
        resolve(stmt.condition)
        resolve(stmt.body)
    }

    /* ========================================================================================================== */

    private fun resolve(expr: Expr) {
        expr.accept(this)
    }

    private fun resolveLocal(expr: Expr, name: Token) {
        for (i in scopes.indices.reversed()) {
            if (scopes[i].containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size - 1 - i)
                return
            }
        }

        // Not found. Assume it is global.
    }

    override fun visit(expr: Expr.Companion.Assign) {
        resolve(expr.value)
        resolveLocal(expr, expr.name)
    }

    override fun visit(expr: Expr.Companion.Binary) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visit(expr: Expr.Companion.Call) {
        resolve(expr.callee)
        for (argument in expr.arguments) resolve(argument)
    }

    override fun visit(expr: Expr.Companion.Get) {
        resolve(expr.obj)
    }

    override fun visit(expr: Expr.Companion.Grouping) {
        resolve(expr.expression)
    }

    override fun visit(expr: Expr.Companion.Literal) {}

    override fun visit(expr: Expr.Companion.Logical) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visit(expr: Expr.Companion.Set) {
        if (expr.value != null) resolve(expr.value)
        resolve(expr.obj)
    }

    override fun visit(expr: Expr.Companion.Super) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Cannot use 'super' outside of a class.")
        } else if (currentClass != ClassType.SUBCLASS) {
            Lox.error(expr.keyword, "Cannot use 'super' in a class with no superclass.")
        }

        resolveLocal(expr, expr.keyword)
    }

    override fun visit(expr: Expr.Companion.This) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Cannot use 'this' outside of a class.")
            return
        }
        resolveLocal(expr, expr.keyword)
    }

    override fun visit(expr: Expr.Companion.Unary) {
        resolve(expr.right)
    }

    override fun visit(expr: Expr.Companion.Variable) {
        if (!scopes.isEmpty() && scopes.peek()[expr.name.lexeme] == false) {
            Lox.error(expr.name, "Cannot read local variable in its own initializer.")
        }
        resolveLocal(expr, expr.name)
    }

    /* ========================================================================================================== */

    private fun beginScope() {
        scopes.push(HashMap())
    }

    private fun endScope() {
        scopes.pop()
    }

    private fun declare(name: Token) {
        if (scopes.isEmpty()) return

        val scope = scopes.peek()
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Variable with this name already declared in this scope.")
        }

        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) return

        scopes.peek()[name.lexeme] = true
    }

}