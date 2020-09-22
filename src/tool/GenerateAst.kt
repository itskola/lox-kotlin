package tool

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths

object GenerateAst {
    const val DLM = "-" // delimiter

    fun defineAst(outputDir: String, packageName: String, dependencies: List<String>, baseName: String, types: List<String>) {
        val path = "$outputDir/$baseName.kt"
        val writer = PrintWriter(path, "UTF-8")

        writer.println("package $packageName\n")
        for (depend in dependencies) writer.println("$depend\n")
        writer.println("abstract class $baseName {")

        defineVisitor(writer, baseName, types); writer.println()

        writer.println("\tcompanion object {")
        for (type in types)
        {
            val className = type.split(DLM)[0].trim()
            val fields = type.split(DLM)[1].trim()
            defineType(writer, baseName, className, fields)
        }
        writer.println("\t}\n")

        writer.println("\tabstract fun <R> accept(visitor: Visitor<R>): R\n")

        writer.println("}")
        writer.close()
    }

    private fun defineVisitor(writer: PrintWriter, baseName: String, types: List<String>) {
        writer.println("\tinterface Visitor<R> {")
        for (type in types) {
            val typeName = type.split(DLM)[0].trim()
            writer.println("\t\tfun visit(${baseName.toLowerCase()}: $typeName): R\n")
        }
        writer.println("\t}")
    }

    private fun defineType(writer: PrintWriter, baseName: String, className: String, fieldsList: String) {
        writer.println("\t\tdata class $className($fieldsList) : $baseName() {")
        writer.print("\t\t\toverride fun <R> accept(visitor: Visitor<R>): R = ")
        writer.println("visitor.visit(this)")
        writer.println("\t\t}\n")
    }
}

fun main(args: Array<String>) {
    val targetDir = "src/lox/grammar"
    val packageName = "lox.grammar"

    val dependencies = listOf(
        "import lox.scanner.Token"
    )

    Files.createDirectories(Paths.get(targetDir))
    val outputDir = File(targetDir).absolutePath

    val dlm = GenerateAst.DLM

    GenerateAst.defineAst(
        outputDir, packageName, dependencies, "Stmt", listOf(
            "Block      $dlm val statements: List<Stmt?>",
            "Class      $dlm val name: Token, val superclass: Expr.Companion.Variable?, val methods: List<Function>",
            "Expression $dlm val expression: Expr",
            "Function   $dlm val name: Token, val params: List<Token>, val body: List<Stmt?>",
            "If         $dlm val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?",
            "Print      $dlm val expression: Expr",
            "Return     $dlm val keyword: Token, val value: Expr?",
            "Var        $dlm val name: Token, val initializer: Expr?",
            "While      $dlm val condition: Expr, val body: Stmt"
        )
    )

    GenerateAst.defineAst(
        outputDir, packageName, dependencies, "Expr", listOf(
            "Assign   $dlm val name: Token, val value: Expr",
            "Binary   $dlm val left: Expr, val operator: Token, val right: Expr",
            "Call     $dlm val callee: Expr, val paren: Token, val arguments: List<Expr>",
            "Get      $dlm val obj: Expr, val name: Token",
            "Grouping $dlm val expression: Expr",
            "Literal  $dlm val value: Any?",
            "Logical  $dlm val left: Expr, val operator: Token, val right: Expr",
            "Set      $dlm val obj: Expr, val name: Token, val value: Expr?",
            "Super    $dlm val keyword: Token, val method: Token",
            "This     $dlm val keyword: Token",
            "Unary    $dlm val operator: Token, val right: Expr",
            "Variable $dlm val name: Token"
        )
    )
}