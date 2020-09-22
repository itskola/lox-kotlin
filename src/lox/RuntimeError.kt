package lox

import lox.scanner.Token

open class RuntimeError(val token: Token, message: String) : RuntimeException(message)