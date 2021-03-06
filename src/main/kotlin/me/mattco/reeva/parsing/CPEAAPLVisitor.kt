package me.mattco.reeva.parsing

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.AssignmentExpressionNode
import me.mattco.reeva.ast.expressions.CommaExpressionNode
import me.mattco.reeva.ast.expressions.MemberExpressionNode
import me.mattco.reeva.ast.expressions.ParenthesizedExpressionNode
import me.mattco.reeva.parsing.lexer.TokenType
import me.mattco.reeva.utils.expect

class CPEAAPLVisitor(
    private val parser: Parser,
    private val node: CPEAAPLNode,
) {
    private val reporter = parser.reporter

    fun parseAsParenthesizedExpression() = CPEAAPLToParenthesizedExpression().convert()

    fun parseAsParameterList() = CPEAAPLToParameterList().convert()

    private inner class CPEAAPLToParenthesizedExpression : ASTVisitor {
        fun convert(): ParenthesizedExpressionNode {
            if (node.parts.isEmpty())
                reporter.at(node).emptyParenthesizedExpression()

            if (node.endsWithComma)
                reporter.at(node).unexpectedToken(TokenType.Comma)

            val expressions = mutableListOf<ExpressionNode>()

            for (part in node.parts) {
                visit(part.node)
                if (part.isSpread)
                    reporter.at(part.node).unexpectedToken(TokenType.TriplePeriod)
                expressions.add(part.node)
            }

            val inner = if (expressions.size > 1) {
                CommaExpressionNode(expressions).withPosition(
                    expressions.first().sourceStart,
                    expressions.last().sourceEnd
                )
            } else expressions.first()

            return ParenthesizedExpressionNode(inner).withPosition(node)
        }

        override fun visitMemberExpression(node: MemberExpressionNode) {
            visit(node.lhs)
            if (node.rhs !is IdentifierNode)
                visit(node.rhs)
        }

        override fun visitIdentifier(node: IdentifierNode) {
            val index = node.parent.children.indexOf(node)
            expect(index >= 0, node.sourceStart.toString())
            node.parent.children[index] = IdentifierReferenceNode(node.name)
                .withPosition(node)
                .also { it.parent = node.parent }
        }
    }

    private inner class CPEAAPLToParameterList : ASTVisitor {
        fun convert(): ParameterList {
            for (part in node.parts) {
                // TODO: Relax this to ExpressionNode when we add destructuring support
                if (part.node !is IdentifierReferenceNode)
                    reporter.at(part.node).expected("identifier")
            }

            val indexOfSpread = node.parts.indexOfFirst { it.isSpread }
            if (indexOfSpread != -1 && indexOfSpread != node.parts.size - 1)
                reporter.at(node.parts[indexOfSpread].node).paramAfterRest()

            // TODO: Validate object cover grammar when that becomes necessary
            return ParameterList(node.parts.map { (node, isSpread) ->
                val (identifier, initializer) = if (node is AssignmentExpressionNode) {
                    if (isSpread)
                        reporter.at(node).restParamInitializer()

                    if (node.op != null)
                        reporter.at(node).expected("equals sign in initializer", node.op.symbol)

                    node.lhs to node.rhs
                } else node to null

                // TODO: Relax when we support destructuring
                if (identifier !is IdentifierReferenceNode)
                    reporter.at(node).expected("identifier")

                if (initializer != null)
                    visit(initializer)

                val index = identifier.parent.children.indexOf(identifier)
                expect(index >= 0)
                identifier.parent.children[index] = IdentifierNode(identifier.identifierName)

                Parameter(IdentifierNode(identifier.identifierName), initializer, isSpread)
            })
        }
    }
}
