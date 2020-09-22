package lox.grammar

import lox.scanner.Token

abstract class Stmt {
	interface Visitor<R> {
		fun visit(stmt: Block): R

		fun visit(stmt: Class): R

		fun visit(stmt: Expression): R

		fun visit(stmt: Function): R

		fun visit(stmt: If): R

		fun visit(stmt: Print): R

		fun visit(stmt: Return): R

		fun visit(stmt: Var): R

		fun visit(stmt: While): R

	}

	companion object {
		data class Block(val statements: List<Stmt?>) : Stmt() {
			override fun <R> accept(visitor: Visitor<R>): R = visitor.visit(this)
		}

		data class Class(val name: Token, val superclass: Expr.Companion.Variable?, val methods: List<Function>) : Stmt() {
			override fun <R> accept(visitor: Visitor<R>): R = visitor.visit(this)
		}

		data class Expression(val expression: Expr) : Stmt() {
			override fun <R> accept(visitor: Visitor<R>): R = visitor.visit(this)
		}

		data class Function(val name: Token, val params: List<Token>, val body: List<Stmt?>) : Stmt() {
			override fun <R> accept(visitor: Visitor<R>): R = visitor.visit(this)
		}

		data class If(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt() {
			override fun <R> accept(visitor: Visitor<R>): R = visitor.visit(this)
		}

		data class Print(val expression: Expr) : Stmt() {
			override fun <R> accept(visitor: Visitor<R>): R = visitor.visit(this)
		}

		data class Return(val keyword: Token, val value: Expr?) : Stmt() {
			override fun <R> accept(visitor: Visitor<R>): R = visitor.visit(this)
		}

		data class Var(val name: Token, val initializer: Expr?) : Stmt() {
			override fun <R> accept(visitor: Visitor<R>): R = visitor.visit(this)
		}

		data class While(val condition: Expr, val body: Stmt) : Stmt() {
			override fun <R> accept(visitor: Visitor<R>): R = visitor.visit(this)
		}

	}

	abstract fun <R> accept(visitor: Visitor<R>): R

}
