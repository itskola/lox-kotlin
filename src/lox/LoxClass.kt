package lox

class LoxClass(val name: String, private val superclass: LoxClass?, private val methods: Map<String, LoxFunction>) : LoxCallable {
    override fun arity(): Int {
        val initializer = findMethod("init")
        return initializer?.arity() ?: 0
    }

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val instance = LoxInstance(this)

        val initializer = findMethod("init")
        initializer?.bind(instance)?.call(interpreter, arguments)

        return instance
    }

    fun findMethod(name: String): LoxFunction? {
        if (methods.containsKey(name)) return methods[name]
        return superclass?.findMethod(name)
    }

    override fun toString(): String {
        return name
    }

}