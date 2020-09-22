package util.helper

fun printErr(message: String) {
    System.err.print(message)
}

fun printErrln(message: String) {
    printErr(message + "\n")
}