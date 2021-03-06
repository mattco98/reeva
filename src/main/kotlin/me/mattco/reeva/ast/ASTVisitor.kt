package me.mattco.reeva.ast

import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*

interface ASTVisitor {
    fun visit(node: ASTNode) {
        when (node) {
            is StatementNode -> visitStatement(node)
            is ExpressionNode -> visitExpression(node)
            is ASTListNode<*> -> visitASTListNode(node)
            is MethodDefinitionNode -> visitMethodDefinition(node)
            is ScriptNode -> visitScript(node)
            is PropertyName -> visitPropertyName(node)
            else -> throw IllegalArgumentException("Unrecognized ASTNode ${node.astNodeName}")
        }
    }

    fun visitStatement(node: StatementNode) {
        when (node) {
            is BlockStatementNode -> visit(node.block)
            is BlockNode -> visitBlock(node)
            is ExpressionStatementNode -> visitExpressionStatement(node)
            is IfStatementNode -> visitIfStatement(node)
            is DoWhileStatementNode -> visitDoWhileStatement(node)
            is WhileStatementNode -> visitWhileStatement(node)
            is ForStatementNode -> visitForStatement(node)
            is ForInNode -> visitForIn(node)
            is ForOfNode -> visitForOf(node)
            is ForAwaitOfNode -> visitForAwaitOf(node)
            is ThrowStatementNode -> visitThrowStatement(node)
            is TryStatementNode -> visitTryStatement(node)
            is BreakStatementNode -> visitBreakStatement(node)
            is ContinueStatementNode -> visitContinueStatement(node)
            is ReturnStatementNode -> visitReturnStatement(node)
            is LexicalDeclarationNode -> visitLexicalDeclaration(node)
            is VariableDeclarationNode -> visitVariableDeclaration(node)
            is DebuggerStatementNode -> visitDebuggerStatement()
            is ImportDeclarationNode -> visitImportDeclaration(node)
            is ExportNode -> visitExport(node)
            is FunctionDeclarationNode -> visitFunctionDeclaration(node)
            is ClassDeclarationNode -> visitClassDeclaration(node)
            is EmptyStatementNode -> {
            }
            else -> throw IllegalArgumentException("Unrecognized StatementNode ${node.astNodeName}")
        }
    }

    fun visitExpression(node: ExpressionNode) {
        when (node) {
            is ArgumentNode -> visitArgument(node)
            is IdentifierReferenceNode -> visitIdentifierReference(node)
            is IdentifierNode -> visitIdentifier(node)
            is FunctionExpressionNode -> visitFunctionExpression(node)
            is ArrowFunctionNode -> visitArrowFunction(node)
            is ClassExpressionNode -> visitClassExpression(node)
            is BinaryExpressionNode -> visitBinaryExpression(node)
            is UnaryExpressionNode -> visitUnaryExpression(node)
            is UpdateExpressionNode -> visitUpdateExpression(node)
            is AssignmentExpressionNode -> visitAssignmentExpression(node)
            is AwaitExpressionNode -> visitAwaitExpression(node)
            is CallExpressionNode -> visitCallExpression(node)
            is CommaExpressionNode -> visitCommaExpression(node)
            is ConditionalExpressionNode -> visitConditionalExpression(node)
            is MemberExpressionNode -> visitMemberExpression(node)
            is NewExpressionNode -> visitNewExpression(node)
            is SuperPropertyExpressionNode -> visitSuperPropertyExpression(node)
            is SuperCallExpressionNode -> visitSuperCallExpression(node)
            is ImportCallExpressionNode -> visitImportCallExpression(node)
            is YieldExpressionNode -> visitYieldExpression(node)
            is ParenthesizedExpressionNode -> visitParenthesizedExpression(node)
            is TemplateLiteralNode -> visitTemplateLiteral(node)
            is RegExpLiteralNode -> visitRegExpLiteral(node)
            is ImportMetaExpressionNode -> visitImportMetaExpression()
            is NewTargetNode -> visitNewTargetExpression(node)
            is ArrayLiteralNode -> visitArrayLiteral(node)
            is ObjectLiteralNode -> visitObjectLiteral(node)
            is BooleanLiteralNode -> visitBooleanLiteral(node)
            is StringLiteralNode -> visitStringLiteral(node)
            is NumericLiteralNode -> visitNumericLiteral(node)
            is BigIntLiteralNode -> visitBigIntLiteral(node)
            is NullLiteralNode -> visitNullLiteral()
            is ThisLiteralNode -> visitThisLiteral(node)
            else -> throw IllegalArgumentException("Unrecognized ExpressionNode ${node.astNodeName}")
        }
    }

    fun visitScript(node: ScriptNode) {
        visit(node.statements)
    }

    fun visitASTListNode(node: ASTListNode<*>) {
        node.children.forEach(::visit)
    }

    fun visitBlock(node: BlockNode) {
        node.statements.forEach(::visit)
    }

    fun visitExpressionStatement(node: ExpressionStatementNode) {
        visit(node.node)
    }

    fun visitIfStatement(node: IfStatementNode) {
        visit(node.condition)
        visit(node.trueBlock)
        if (node.falseBlock != null)
            visit(node.falseBlock)
    }

    fun visitDoWhileStatement(node: DoWhileStatementNode) {
        visit(node.condition)
        visit(node.body)
    }

    fun visitWhileStatement(node: WhileStatementNode) {
        visit(node.condition)
        visit(node.body)
    }

    fun visitForStatement(node: ForStatementNode) {
        node.initializer?.also(::visit)
        node.condition?.also(::visit)
        node.incrementer?.also(::visit)
        visit(node.body)
    }

    fun visitForIn(node: ForInNode) {
        visit(node.decl)
        visit(node.expression)
        visit(node.body)
    }

    fun visitForOf(node: ForOfNode) {
        visit(node.decl)
        visit(node.expression)
        visit(node.body)
    }

    fun visitForAwaitOf(node: ForAwaitOfNode) {
        visit(node.decl)
        visit(node.expression)
        visit(node.body)
    }

    fun visitThrowStatement(node: ThrowStatementNode) {
        visit(node.expr)
    }

    fun visitTryStatement(node: TryStatementNode) {
        visit(node.tryBlock)
        node.catchNode?.also {
            it.parameter?.identifier?.also(::visitIdentifier)
            visit(it.block)
        }
        node.finallyBlock?.also(::visit)
    }

    fun visitBreakStatement(node: BreakStatementNode) {}

    fun visitContinueStatement(node: ContinueStatementNode) {}

    fun visitReturnStatement(node: ReturnStatementNode) {
        node.expression?.also(::visit)
    }

    fun visitLexicalDeclaration(node: LexicalDeclarationNode) {
        node.declarations.forEach { binding ->
            visit(binding.identifier)
            binding.initializer?.also(::visit)
        }
    }

    fun visitVariableDeclaration(node: VariableDeclarationNode) {
        node.declarations.forEach { declaration ->
            visit(declaration.identifier)
            declaration.initializer?.also(::visit)
        }
    }

    fun visitDebuggerStatement() {}

    fun visitImportDeclaration(node: ImportDeclarationNode) {}

    fun visitExport(node: ExportNode) {}

    fun visitArgument(node: ArgumentNode) {
        visit(node.expression)
    }

    fun visitIdentifierReference(node: IdentifierReferenceNode) {}

    // Should only ever happen in CPEAAPL nodes
    fun visitIdentifier(node: IdentifierNode) {}

    fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        for (param in node.parameters) {
            if (param.initializer != null)
                visit(param.initializer)
        }
        visit(node.body)
    }

    fun visitPropertyName(node: PropertyName) {
        when (node.type) {
            PropertyName.Type.Identifier -> (node.expression as IdentifierNode).name
            PropertyName.Type.Computed -> visit(node.expression)
            else -> {
            }
        }
    }

    fun visitMethodDefinition(node: MethodDefinitionNode) {
        for (param in node.parameters) {
            if (param.initializer != null)
                visit(param.initializer)
        }
        visit(node.body)
    }

    fun visitFunctionExpression(node: FunctionExpressionNode) {
        for (param in node.parameters) {
            if (param.initializer != null)
                visit(param.initializer)
        }
        visit(node.body)
    }

    fun visitArrowFunction(node: ArrowFunctionNode) {
        for (param in node.parameters) {
            if (param.initializer != null)
                visit(param.initializer)
        }
        visit(node.body)
    }

    fun visitClassDeclaration(node: ClassDeclarationNode) {
        // TODO: Default handling
    }

    fun visitClassExpression(node: ClassExpressionNode) {
        // TODO: Default handling
    }

    fun visitBinaryExpression(node: BinaryExpressionNode) {
        visit(node.lhs)
        visit(node.rhs)
    }

    fun visitUnaryExpression(node: UnaryExpressionNode) {
        visit(node.expression)
    }

    fun visitUpdateExpression(node: UpdateExpressionNode) {
        visit(node.target)
    }

    fun visitAssignmentExpression(node: AssignmentExpressionNode) {
        visit(node.lhs)
        visit(node.rhs)
    }

    fun visitAwaitExpression(node: AwaitExpressionNode) {
        visit(node.expression)
    }

    fun visitCallExpression(node: CallExpressionNode) {
        visit(node.target)
        node.arguments.forEach { visit(it.expression) }
    }

    fun visitCommaExpression(node: CommaExpressionNode) {
        node.expressions.forEach(::visit)
    }

    fun visitConditionalExpression(node: ConditionalExpressionNode) {
        visit(node.predicate)
        visit(node.ifTrue)
        visit(node.ifFalse)
    }

    fun visitMemberExpression(node: MemberExpressionNode) {
        visit(node.lhs)
        visit(node.rhs)
    }

    fun visitNewExpression(node: NewExpressionNode) {
        visit(node.target)
        node.arguments.forEach { visit(it.expression) }
    }

    fun visitSuperPropertyExpression(node: SuperPropertyExpressionNode) {
        visit(node.target)
    }

    fun visitSuperCallExpression(node: SuperCallExpressionNode) {
        node.arguments.forEach { visit(it.expression) }
    }

    fun visitImportCallExpression(node: ImportCallExpressionNode) {
        visit(node.expression)
    }

    fun visitYieldExpression(node: YieldExpressionNode) {
        node.expression?.also(::visit)
    }

    fun visitParenthesizedExpression(node: ParenthesizedExpressionNode) {
        visit(node.expression)
    }

    fun visitTemplateLiteral(node: TemplateLiteralNode) {
        node.parts.forEach(::visit)
    }

    fun visitRegExpLiteral(node: RegExpLiteralNode) {}

    fun visitImportMetaExpression() {}

    fun visitNewTargetExpression(node: NewTargetNode) {}

    fun visitArrayLiteral(node: ArrayLiteralNode) {
        node.elements.forEach {
            it.expression?.also(::visit)
        }
    }

    fun visitObjectLiteral(node: ObjectLiteralNode) {
        node.list.forEach {
            when (it) {
                is KeyValueProperty -> {
                    visit(it.key.expression)
                    visit(it.value)
                }
                is MethodProperty -> visit(it.method)
                is ShorthandProperty -> visit(it.key)
                is SpreadProperty -> visit(it.target)
            }
        }
    }

    fun visitBooleanLiteral(node: BooleanLiteralNode) {}

    fun visitStringLiteral(node: StringLiteralNode) {}

    fun visitNumericLiteral(node: NumericLiteralNode) {}

    fun visitBigIntLiteral(node: BigIntLiteralNode) {}

    fun visitNullLiteral() {}

    fun visitThisLiteral(node: ThisLiteralNode) {}
}
