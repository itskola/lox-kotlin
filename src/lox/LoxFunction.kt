package lox

import lox.grammar.Stmt

class LoxFunction(private val declaration: Stmt.Companion.Function, private val closure: Environment,
                  private val isInitializer: Boolean) : LoxCallable {
    override fun arity(): Int = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)

        for (i in declaration.params.indices) {
            environment.define(declaration.params[i].lexeme, arguments[i])
        }

        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (returnValue: Return) {
            return if (isInitializer) closure.getAt(0, "this")
                   else               returnValue.value
        }
        return if (isInitializer) closure.getAt(0, "this") else null
    }

    fun bind(instance: LoxInstance): LoxFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return LoxFunction(declaration, environment, isInitializer)
    }

    override fun toString(): String {
        return "<fn ${declaration.name.lexeme} >"
    }

}