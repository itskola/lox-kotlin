package lox

import lox.scanner.Token

class Environment(val enclosing: Environment? = null) {
    private val values = mutableMapOf<String, Any?>()

    private fun ancestor(distance: Int): Environment {
        var environment = this
        for (i in 0 until distance) environment = environment.enclosing!!
        return environment
    }

    fun get(name: Token): Any? {
        if (values.containsKey(name.lexeme)) return values[name.lexeme]
        if (enclosing != null) return enclosing.get(name)
        throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
    }

    fun getAt(distance: Int, name: String): Any? {
        return ancestor(distance).values[name]
    }

    fun define(name: String, value: Any?) = run { values[name] = value }

    fun assign(name: Token, value: Any?) {
        if (values.containsKey(name.lexeme))
            values[name.lexeme] = value
        else if (enclosing != null) {
            enclosing.assign(name, value)
        }
        else throw RuntimeError(name, "Undefined variable ${name.lexeme}.")
    }

    fun assignAt(distance: Int, name: Token, value: Any?) {
        ancestor(distance).values[name.lexeme] = value
    }

}