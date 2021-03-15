package me.mattco.reeva.interpreter

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.EvaluationResult
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.ir.*
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.JSObject.Companion.initialize
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.runtime.toPrintableString
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.toValue
import me.mattco.reeva.utils.unreachable
import java.io.File
import kotlin.system.exitProcess

fun main() {
    val script = File("./demo/index.js").readText()

    Reeva.setup()

    val agent = Agent().apply {
        printIR = true
    }

    Reeva.setAgent(agent)
    val realm = Reeva.makeRealm()

    when (val result = agent.run(script, realm)) {
        is EvaluationResult.ParseFailure -> println("\u001b[31mParse failure: ${result.value}\u001B[0m")
        is EvaluationResult.RuntimeError -> println("\u001b[31m${result.value.toPrintableString()}\u001B[0m")
        else -> println(result.value.toPrintableString())
    }

    Reeva.teardown()
}

class IRInterpreter(private val function: IRFunction, private val arguments: List<JSValue>) {
    private val globalEnv: GlobalEnvRecord

    private val info = function.info

    private val registers = Registers(info.registerCount)
    private var accumulator by registers::accumulator
    private var ip = 0
    private var isDone = false
    private var exception: ThrowException? = null
    private val mappedCPool = Array<JSValue?>(info.constantPool.size) { null }

    private val envStack = mutableListOf<EnvRecord>()
    private var currentEnv = function.envRecord

    init {
        var env: EnvRecord? = currentEnv
        while (env != null) {
            envStack.add(env)
            env = env.outer
        }
        envStack.reverse()
        val topEnv = envStack.first()
        expect(topEnv is GlobalEnvRecord)
        globalEnv = topEnv
    }

    fun interpret(): EvaluationResult {
        try {
            while (!isDone) {
                try {
                    visit(info.code[ip++])
                } catch (e: ThrowException) {
                    exception = e
                    isDone = true
                }
            }
        } catch (e: Throwable) {
            println("Exception in FunctionInfo ${info.name} (length=${info.code.size}), opcode ${ip - 1}")
            e.printStackTrace()
            exitProcess(1)
        }

        return if (exception != null) {
            EvaluationResult.RuntimeError(exception!!.value)
        } else EvaluationResult.Success(accumulator)
    }

    private fun visit(opcode: Opcode) {
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        when (opcode) {
            LdaZero -> {
                accumulator = JSNumber.ZERO
            }
            LdaUndefined -> {
                accumulator = JSUndefined
            }
            LdaNull -> {
                accumulator = JSNull
            }
            LdaTrue -> {
                accumulator = JSTrue
            }
            LdaFalse -> {
                accumulator = JSFalse
            }
            is LdaConstant -> {
                accumulator = getMappedConstant(opcode.cpIndex)
            }
            is LdaInt -> {
                accumulator = JSNumber(opcode.int)
            }
            is LdaDouble -> {
                accumulator = JSNumber(opcode.double)
            }
            is Ldar -> {
                accumulator = registers[opcode.reg]
            }
            is Star -> {
                registers[opcode.reg] = accumulator
            }
            is Mov -> {
                registers[opcode.toReg] = registers[opcode.fromReg]
            }
            is LdaNamedProperty -> {
                val name = loadConstant<String>(opcode.nameCpIndex)
                val obj = registers[opcode.objectReg] as JSObject
                accumulator = obj.get(name)
            }
            is LdaKeyedProperty -> {
                val obj = registers[opcode.objectReg] as JSObject
                accumulator = obj.get(Operations.toPropertyKey(accumulator))
            }
            is StaNamedProperty -> {
                val name = loadConstant<String>(opcode.nameCpIndex)
                val obj = registers[opcode.objectReg] as JSObject
                obj.set(name, accumulator)
            }
            is StaKeyedProperty -> {
                val obj = registers[opcode.objectReg] as JSObject
                val key = registers[opcode.keyReg]
                obj.set(Operations.toPropertyKey(key), accumulator)
            }
            CreateArrayLiteral -> {
                accumulator = JSArrayObject.create(function.realm)
            }
            is StaArrayLiteral -> {
                val array = registers[opcode.arrayReg] as JSObject
                val index = (registers[opcode.indexReg] as JSNumber).asInt
                array.indexedProperties.set(array, index, accumulator)
            }
            is StaArrayLiteralIndex -> {
                val array = registers[opcode.arrayReg] as JSObject
                array.indexedProperties.set(array, opcode.arrayIndex, accumulator)
            }
            is CreateObjectLiteral -> {
                accumulator = JSObject.create(function.realm)
            }
            is Add -> binaryOp(opcode.valueReg, "+")
            is Sub -> binaryOp(opcode.valueReg, "-")
            is Mul -> binaryOp(opcode.valueReg, "*")
            is Div -> binaryOp(opcode.valueReg, "/")
            is Mod -> binaryOp(opcode.valueReg, "%")
            is Exp -> binaryOp(opcode.valueReg, "**")
            is BitwiseOr -> binaryOp(opcode.valueReg, "|")
            is BitwiseXor -> binaryOp(opcode.valueReg, "^")
            is BitwiseAnd -> binaryOp(opcode.valueReg, "&")
            is ShiftLeft -> binaryOp(opcode.valueReg, "<<")
            is ShiftRight -> binaryOp(opcode.valueReg, ">>")
            is ShiftRightUnsigned -> binaryOp(opcode.valueReg, ">>>")
            Inc -> {
                accumulator = JSNumber(accumulator.asInt + 1)
            }
            Dec -> {
                accumulator = JSNumber(accumulator.asInt - 1)
            }
            Negate -> TODO()
            BitwiseNot -> TODO()
            is TemplateAppend -> {
                val template = registers[opcode.templateReg] as JSString
                registers[opcode.templateReg] = JSString(
                    template.string + (accumulator as JSString).string
                )
            }
            ToBooleanLogicalNot -> {
                accumulator = (!Operations.toBoolean(accumulator)).toValue()
            }
            LogicalNot -> {
                accumulator = (!accumulator.asBoolean).toValue()
            }
            TypeOf -> {
                accumulator = Operations.typeofOperator(accumulator)
            }
            is DeletePropertyStrict -> TODO()
            is DeletePropertySloppy -> TODO()
            is LdaGlobal -> {
                val name = loadConstant<String>(opcode.nameCpIndex)
                accumulator = globalEnv.extension().get(name)
            }
            is LdaCurrentEnv -> {
                accumulator = currentEnv.getBinding(opcode.slot)
            }
            is StaCurrentEnv -> {
                currentEnv.setBinding(opcode.slot, accumulator)
            }
            is LdaEnv -> {
                val envIndex = envStack.lastIndex - opcode.depthOffset
                accumulator = envStack[envIndex].getBinding(opcode.slot)
            }
            is StaEnv -> {
                val envIndex = envStack.lastIndex - opcode.depthOffset
                envStack[envIndex].setBinding(opcode.slot, accumulator)
            }
            is PushEnv -> {
                val newEnv = EnvRecord(currentEnv, currentEnv.isStrict, opcode.numSlots)
                envStack.add(newEnv)
                currentEnv = newEnv
            }
            is PopCurrentEnv -> {
                currentEnv = envStack.removeLast()
            }
            is PopEnvs -> {
                repeat(opcode.count - 1) {
                    envStack.removeLast()
                }
                currentEnv = envStack.removeLast()
            }
            is Call -> call(opcode.callableReg, opcode.receiverReg, opcode.argCount, CallMode.Normal)
            is Call0 -> call(opcode.callableReg, opcode.receiverReg, 0, CallMode.Normal)
            is Call1 -> call(opcode.callableReg, opcode.receiverReg, -1, CallMode.OneArg)
            is CallLastSpread -> call(opcode.callableReg, opcode.receiverReg, opcode.argCount, CallMode.LastSpread)
            is CallFromArray -> call(opcode.callableReg, opcode.receiverReg, 1, CallMode.Spread)
            is CallRuntime -> {
                val args = getRegisterBlock(opcode.firstArgReg, opcode.argCount)
                accumulator = InterpRuntime.values()[opcode.id].function(args)
            }
            is Construct0 -> {
                accumulator = Operations.construct(
                    registers[opcode.targetReg],
                    emptyList(),
                    accumulator
                )
            }
            is Construct -> {
                accumulator = Operations.construct(
                    registers[opcode.targetReg],
                    getRegisterBlock(opcode.firstArgReg, opcode.argCount),
                    accumulator
                )
            }
            is ConstructLastSpread -> TODO()
            is TestEqual -> {
                accumulator = Operations.abstractEqualityComparison(registers[opcode.targetReg], accumulator)
            }
            is TestNotEqual -> {
                accumulator = Operations.abstractEqualityComparison(registers[opcode.targetReg], accumulator).inv()
            }
            is TestEqualStrict -> {
                accumulator = Operations.strictEqualityComparison(registers[opcode.targetReg], accumulator)
            }
            is TestNotEqualStrict -> {
                accumulator = Operations.strictEqualityComparison(registers[opcode.targetReg], accumulator).inv()
            }
            is TestLessThan -> {
                val lhs = registers[opcode.targetReg]
                val rhs = accumulator
                val result = Operations.abstractRelationalComparison(lhs, rhs, true)
                accumulator = if (result == JSUndefined) JSFalse else result
            }
            is TestGreaterThan -> {
                val lhs = registers[opcode.targetReg]
                val rhs = accumulator
                val result = Operations.abstractRelationalComparison(rhs, lhs, false)
                accumulator = if (result == JSUndefined) JSFalse else result
            }
            is TestLessThanOrEqual -> {
                val lhs = registers[opcode.targetReg]
                val rhs = accumulator
                val result = Operations.abstractRelationalComparison(rhs, lhs, false)
                accumulator = if (result == JSFalse) JSTrue else JSFalse
            }
            is TestGreaterThanOrEqual -> {
                val lhs = registers[opcode.targetReg]
                val rhs = accumulator
                val result = Operations.abstractRelationalComparison(lhs, rhs, true)
                accumulator = if (result == JSFalse) JSTrue else JSFalse
            }
            is TestReferenceEqual -> {
                accumulator = (accumulator == registers[opcode.targetReg]).toValue()
            }
            is TestInstanceOf -> {
                accumulator = Operations.instanceofOperator(registers[opcode.targetReg], accumulator)
            }
            is TestIn -> TODO()
            TestNullish -> {
                accumulator = accumulator.let {
                    it == JSNull || it == JSUndefined
                }.toValue()
            }
            TestNull -> {
                accumulator = (accumulator == JSNull).toValue()
            }
            TestUndefined -> {
                accumulator = (accumulator == JSUndefined).toValue()
            }
            ToBoolean -> {
                accumulator = Operations.toBoolean(accumulator).toValue()
            }
            ToNumber -> {
                accumulator = Operations.toNumber(accumulator)
            }
            ToNumeric -> {
                accumulator = Operations.toNumeric(accumulator)
            }
            ToObject -> {
                accumulator = Operations.toObject(accumulator)
            }
            ToString -> {
                accumulator = Operations.toString(accumulator)
            }
            is Jump -> {
                ip = opcode.offset
            }
            is JumpIfTrue -> {
                if (accumulator == JSTrue)
                    ip = opcode.offset
            }
            is JumpIfFalse -> {
                if (accumulator == JSFalse)
                    ip = opcode.offset
            }
            is JumpIfToBooleanTrue -> {
                if (Operations.toBoolean(accumulator))
                    ip = opcode.offset
            }
            is JumpIfToBooleanFalse -> {
                if (!Operations.toBoolean(accumulator))
                    ip = opcode.offset
            }
            is JumpIfNull -> {
                if (accumulator == JSNull)
                    ip = opcode.offset
            }
            is JumpIfNotNull -> {
                if (accumulator != JSNull)
                    ip = opcode.offset
            }
            is JumpIfUndefined -> {
                if (accumulator == JSUndefined)
                    ip = opcode.offset
            }
            is JumpIfNotUndefined -> {
                if (accumulator != JSUndefined)
                    ip = opcode.offset
            }
            is JumpIfObject -> {
                if (accumulator is JSObject)
                    ip = opcode.offset
            }
            is JumpIfNullish -> {
                if (accumulator.let { it == JSNull || it == JSUndefined })
                    ip = opcode.offset
            }
            is JumpIfNotNullish -> {
                if (!accumulator.let { it == JSNull || it == JSUndefined })
                    ip = opcode.offset
            }
            JumpPlaceholder -> throw IllegalStateException("Illegal opcode: JumpPlaceholder")
            Throw -> {
                val handler = info.handlers.firstOrNull { ip - 1 in it.start..it.end }
                    ?: throw ThrowException(accumulator)
                repeat(envStack.size - handler.contextDepth) {
                    currentEnv = envStack.removeLast()
                }
                ip = handler.handler
            }
            is ThrowConstReassignment -> {
                Errors.AssignmentToConstant(loadConstant(opcode.nameCpIndex)).throwTypeError()
            }
            is ThrowUseBeforeInitIfEmpty -> {
                if (accumulator == JSEmpty)
                    Errors.AccessBeforeInitialization(loadConstant(opcode.nameCpIndex)).throwTypeError()
            }
            Return -> {
                isDone = true
            }
            GetIterator -> {
                accumulator = Operations.getIterator(Operations.toObject(accumulator)).iterator
                if (accumulator !is JSObject)
                    Errors.NonObjectIterator.throwTypeError()
            }
            is CreateClosure -> {
                val newInfo = loadConstant<FunctionInfo>(opcode.cpIndex)
                val newEnv = EnvRecord(currentEnv, currentEnv.isStrict || newInfo.isStrict, newInfo.topLevelSlots)
                accumulator = IRFunction(function.realm, newInfo, newEnv).initialize()
            }
            DebugBreakpoint -> TODO()
            else -> TODO("Unrecognized opcode: ${opcode::class.simpleName}")
        }
    }

    private fun binaryOp(lhs: Int, op: String) {
        accumulator = Operations.applyStringOrNumericBinaryOperator(
            registers[lhs], accumulator, op
        )
    }

    private fun call(callableReg: Int, firstArgReg: Int, argCount: Int, mode: CallMode) {
        val target = registers[callableReg]
        val receiver = registers[firstArgReg]

        accumulator = when (mode) {
            CallMode.Normal -> {
                val args = if (argCount > 0) {
                    getRegisterBlock(firstArgReg + 1, argCount)
                } else emptyList()

                Operations.call(target, receiver, args)
            }
            CallMode.OneArg -> {
                Operations.call(target, receiver, listOf(accumulator))
            }
            CallMode.LastSpread -> {
                val nonSpreadArgs = if (argCount > 0) {
                    getRegisterBlock(firstArgReg + 1, argCount - 1)
                } else emptyList()

                val spreadValues = Operations.iterableToList(registers[firstArgReg + argCount + 1])

                Operations.call(target, receiver, nonSpreadArgs + spreadValues)
            }
            CallMode.Spread -> {
                val argArray = registers[firstArgReg + 1] as JSArrayObject
                val args = (0 until argArray.indexedProperties.arrayLikeSize).map {
                    argArray.indexedProperties.get(argArray, it.toInt())
                }

                Operations.call(target, receiver, args)
            }
        }
    }

    enum class CallMode {
        Normal,
        OneArg,
        LastSpread,
        Spread,
    }

    private fun getRegisterBlock(reg: Int, argCount: Int): List<JSValue> {
        val args = mutableListOf<JSValue>()
        for (i in reg until (reg + argCount))
            args.add(registers[i])
        return args
    }

    private fun getMappedConstant(index: Int): JSValue {
        return mappedCPool[index] ?: when (val value = info.constantPool[index]) {
            is Int -> JSNumber(value)
            is Double -> JSNumber(value)
            is String -> JSString(value)
            is FunctionInfo -> TODO()
            else -> unreachable()
        }.also { mappedCPool[index] = it }
    }

    private inline fun <reified T> loadConstant(index: Int): T {
        return info.constantPool[index] as T
    }

    inner class Registers(size: Int) {
        var accumulator: JSValue = JSEmpty
        private val registers = Array(size) {
            if (it < info.argCount) JSUndefined else JSEmpty
        }

        init {
            arguments.forEachIndexed { index, value ->
                registers[index] = value
            }
        }

        operator fun get(index: Int) = registers[index]

        operator fun set(index: Int, value: JSValue) {
            registers[index] = value
        }
    }

    class IRFunction(
        realm: Realm,
        val info: FunctionInfo,
        val envRecord: EnvRecord,
    ) : JSFunction(realm, info.isStrict) {
        init {
            isConstructable = true
        }

        override fun init() {
            super.init()

            defineOwnProperty("prototype", realm.functionProto)
        }

        override fun evaluate(arguments: JSArguments): JSValue {
            val args = listOf(arguments.thisValue) + arguments
            val result = IRInterpreter(this, args).interpret()
            if (result is EvaluationResult.RuntimeError)
                throw ThrowException(result.value)
            return result.value
        }
    }

    companion object {
        fun wrap(info: FunctionInfo, realm: Realm): JSFunction {
            return IRFunction(
                realm,
                info,
                GlobalEnvRecord(realm, info.isStrict, info.topLevelSlots)
            ).initialize()
        }
    }
}
