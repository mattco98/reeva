package me.mattco.reeva.parsing.lexer

import me.mattco.reeva.mfbt.StringToFP
import me.mattco.reeva.utils.expect

// A basic unit of a script. This class is also used to communicate
// Lexer errors: if type == TokenType.Invalid, then literals will
// contain an error message
data class Token(
    val type: TokenType,
    val start: TokenLocation,
    val end: TokenLocation,
    val afterNewline: Boolean,
    val literals: String,
    val rawLiterals: String,
) {
    fun doubleValue(): Double {
        expect(type == TokenType.NumericLiteral)
        return StringToFP(literals.filter { it != '_' }).parse() ?: TODO()
    }

    fun error() = if (type == TokenType.Invalid) literals else null

    companion object {
        val INVALID = Token(TokenType.Invalid, TokenLocation.EMPTY, TokenLocation.EMPTY, false, "", "")
        val EOF = Token(TokenType.Eof, TokenLocation.EMPTY, TokenLocation.EMPTY, false, "", "")
    }
}

data class TokenLocation(val index: Int, val line: Int, val column: Int) {
    fun shiftColumn(n: Int) = TokenLocation(index, line, column + n)

    override fun toString() = "${line + 1}:${column + 1}"

    companion object {
        val EMPTY = TokenLocation(-1, -1, -1)
    }
}
