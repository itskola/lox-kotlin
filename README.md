# lox-kotlin (klox)

<h3>Implementation of Lox Language in Kotlin from <a href="https://craftinginterpreters.com/">Crafting Interpreters</a> book</h3>

### Details
In this repo you will find implementation of Lox, interpreted programming language, in Kotlin.<br/>
In the <a href="https://craftinginterpreters.com/">book</a>, author originally implemented Lox in <b>Java (jlox)</b>.

### Code example:
```
// recursive Fibonacci function
fun fib(n) {
  if (n < 2) return n;
  return fib(n - 1) + fib(n - 2);
}

var before = clock();
print fib(20);
var after = clock();

print after - before;
```

### Build/Run
```
import folder into IntelliJ IDEA and run Lox.kt
  => Lox.kt <path_to_source>
  => Lox.kt // starts REPL
```
