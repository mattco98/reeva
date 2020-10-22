@file:Suppress("unused")

package me.mattco.reeva.runtime

import me.mattco.reeva.compiler.JSScriptFunction
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.contexts.ExecutionContext
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.environment.FunctionEnvRecord
import me.mattco.reeva.runtime.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.JSReference
import me.mattco.reeva.runtime.values.arrays.JSArray
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.Attributes.Companion.WRITABLE
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.objects.PropertyKey
import me.mattco.reeva.runtime.values.primitives.*
import me.mattco.reeva.runtime.values.wrappers.JSStringObject
import me.mattco.reeva.runtime.values.wrappers.JSSymbolObject
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.toValue
import me.mattco.reeva.utils.unreachable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

object Operations {
    @JvmStatic
    fun wrapInValue(value: Any?): JSValue = when (value) {
        null -> throw Error("Ambiguous use of null in Operations.wrapInValue")
        is Double -> JSNumber(value)
        is Number -> JSNumber(value.toDouble())
        is String -> JSString(value)
        is Boolean -> if (value) JSTrue else JSFalse
        else -> throw Error("Cannot wrap type ${value::class.java.simpleName}")
    }

    @JvmStatic
    fun checkNotBigInt(value: JSValue) {
        expect(value !is JSBigInt)
    }

    @JvmStatic @ECMAImpl("Number::unaryMinus(x)", "6.1.6.1.1")
    fun numericUnaryMinus(value: JSValue): JSValue {
        expect(value is JSNumber)
        if (value.isNaN)
            return value
        if (value.isPositiveInfinity)
            return JSNumber(Double.NEGATIVE_INFINITY)
        if (value.isNegativeInfinity)
            return JSNumber(Double.POSITIVE_INFINITY)
        // TODO: -0 -> +0? +0 -> -0?
        return JSNumber(-value.number)
    }

    @JvmStatic @ECMAImpl("Number::bitwiseNOT", "6.1.6.1.2")
    fun numericBitwiseNOT(value: JSValue): JSValue {
        expect(value is JSNumber)
        val oldValue = toInt32(value)
        return JSNumber(oldValue.asDouble.toInt().inv())
    }

    @JvmStatic @ECMAImpl("Number::exponentiate", "6.1.6.1.3")
    fun numericExponentiate(base: JSValue, exponent: JSValue): JSValue {
        expect(base is JSNumber)
        expect(exponent is JSNumber)
        if (exponent.isNaN)
            return exponent
        if (exponent.isZero)
            return JSNumber(1)
        if (base.isNaN && !exponent.isZero)
            return base

        val baseMag = abs(base.asDouble)
        when {
            baseMag > 1 && exponent.isPositiveInfinity -> return exponent
            baseMag > 1 && exponent.isNegativeInfinity -> return JSNumber(0)
            baseMag == 1.0 && exponent.isInfinite -> return JSNumber(Double.NaN)
            baseMag < 1 && exponent.isPositiveInfinity -> return JSNumber(0)
            baseMag < 1 && exponent.isNegativeInfinity -> return JSNumber(Double.POSITIVE_INFINITY)
        }

        // TODO: Other requirements here
        return JSNumber(base.asDouble.pow(exponent.asDouble))
    }

    @JvmStatic @ECMAImpl("Number::multiply", "6.1.6.1.4")
    fun numericMultiply(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        if ((lhs.isZero || rhs.isZero) && (lhs.isInfinite || rhs.isInfinite))
            return JSNumber(Double.NaN)
        // TODO: Other requirements
        return JSNumber(lhs.asDouble * rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::divide", "6.1.6.1.5")
    fun numericDivide(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        // TODO: Other requirements
        return JSNumber(lhs.asDouble / rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::remainder", "6.1.6.1.6")
    fun numericRemainder(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        // TODO: Other requirements
        return JSNumber(lhs.asDouble.rem(rhs.asDouble))
    }

    @JvmStatic @ECMAImpl("Number::add", "6.1.6.1.7")
    fun numericAdd(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        if (lhs.isInfinite && rhs.isInfinite) {
            if (lhs.isPositiveInfinity != rhs.isPositiveInfinity)
                return JSNumber(Double.NaN)
            return lhs
        }
        if (lhs.isInfinite)
            return lhs
        if (rhs.isInfinite)
            return rhs
        if (lhs.isNegativeZero && rhs.isNegativeZero)
            return lhs
        if (lhs.isZero && rhs.isZero)
            return JSNumber(0)
        // TODO: Overflow
        return JSNumber(lhs.number + rhs.number)
    }

    @JvmStatic @ECMAImpl("Number::subtract", "6.1.6.1.8")
    fun numericSubtract(lhs: JSValue, rhs: JSValue): JSValue {
        return numericAdd(lhs, numericUnaryMinus(rhs))
    }

    @JvmStatic @ECMAImpl("Number::leftShift", "6.1.6.1.9")
    fun numericLeftShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        return JSNumber(toInt32(lhs).asInt shl (toUint32(rhs).asInt % 32))
    }

    @JvmStatic @ECMAImpl("Number::signedRightShift", "6.1.6.1.10")
    fun numericSignedRightShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        return JSNumber(toInt32(lhs).asInt shr (toUint32(rhs).asInt % 32))
    }

    @JvmStatic @ECMAImpl("Number::unsignedRightShift", "6.1.6.1.11")
    fun numericUnsignedRightShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        return JSNumber(toInt32(lhs).asInt ushr (toUint32(rhs).asInt % 32))
    }

    @JvmStatic @ECMAImpl("Number::lessThan", "6.1.6.1.12")
    fun numericLessThan(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.isNaN || rhs.isNaN)
            return JSUndefined
        // TODO: Other requirements
        return wrapInValue(lhs.asDouble < rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::equal", "6.1.6.1.13")
    fun numericEqual(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.isNaN || rhs.isNaN)
            return JSUndefined
        // TODO: Other requirements
        return wrapInValue(lhs.asDouble == rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::sameValue", "6.1.6.1.14")
    fun numericSameValue(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.isNaN || rhs.isNaN)
            return JSUndefined
        // TODO: Other requirements
        return wrapInValue(lhs.asDouble == rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::sameValueZero", "6.1.6.1.15")
    fun numericSameValueZero(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.isNaN || rhs.isNaN)
            return JSUndefined
        // TODO: Other requirements
        return wrapInValue(lhs.asDouble == rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::bitwiseAND", "6.1.6.1.17")
    fun numericBitwiseAND(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(lhs).asInt and toInt32(rhs).asInt)
    }

    @JvmStatic @ECMAImpl("Number::bitwiseXOR", "6.1.6.1.18")
    fun numericBitwiseXOR(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(lhs).asInt xor toInt32(rhs).asInt)
    }

    @JvmStatic @ECMAImpl("Number::bitwiseOR", "6.1.6.1.19")
    fun numericBitwiseOR(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(lhs).asInt or toInt32(rhs).asInt)
    }

    @JvmStatic @ECMAImpl("Number::toString", "6.1.6.1.20")
    fun numericToString(lhs: JSValue): JSValue {
        TODO()
    }

    /**************
     * REFERENCES
     **************/

    @JvmStatic @ECMAImpl("GetValue", "6.2.4.8")
    fun getValue(reference: JSValue): JSValue {
        if (reference !is JSReference)
            return reference
        if (reference.isUnresolvableReference)
            shouldThrowError("ReferenceError")
        var base = reference.baseValue
        if (reference.isPropertyReference) {
            if (reference.hasPrimitiveBase) {
                expect(base != JSUndefined && base != JSNull)
                base = toObject(base as JSValue)
            }
            val value = (base as JSObject).get(reference.name, reference.getThisValue())
            if (value.isAccessor)
                return value.asAccessor.callGetter(base)
            return value
        }

        expect(base is EnvRecord)
        expect(reference.name.isString)
        return base.getBindingValue(reference.name.asString, reference.isStrict)
    }

    @JvmStatic @ECMAImpl("PutValue", "6.2.4.9")
    fun putValue(reference: JSValue, value: JSValue) {
        if (reference !is JSReference)
            shouldThrowError("ReferenceError")
        var base = reference.baseValue
        if (reference.isUnresolvableReference) {
            if (reference.isStrict)
                shouldThrowError("ReferenceError")
            Agent.runningContext.realm.globalObject.set(reference.name, value)
        } else if (reference.isPropertyReference) {
            if (reference.hasPrimitiveBase) {
                expect(base != JSUndefined && base != JSNull)
                base = toObject(base as JSValue)
            }
            val succeeded = (base as JSObject).set(reference.name, value, reference.getThisValue())
            if (!succeeded && reference.isStrict)
                shouldThrowError("TypeError")
        } else {
            expect(base is EnvRecord)
            expect(reference.name.isString)
            base.setMutableBinding(reference.name.asString, value, reference.isStrict)
        }
    }

    @JvmStatic @ECMAImpl("InitializeReferencedBinding", "6.2.4.11")
    fun initializeReferencedBinding(reference: JSReference, value: JSValue) {
        expect(!reference.isUnresolvableReference, "Unknown reference with identifier ${reference.name}")
        val base = reference.baseValue
        expect(base is EnvRecord)
        expect(reference.name.isString)
        base.initializeBinding(reference.name.asString, value)
    }

    enum class ToPrimitiveHint(private val _text: String) {
        AsDefault("default"),
        AsString("string"),
        AsNumber("number");

        val value by lazy { JSString(_text) }
    }

    @JvmStatic @ECMAImpl("ToPrimitive", "7.1.1")
    fun toPrimitive(value: JSValue, type: ToPrimitiveHint? = null): JSValue {
        if (value !is JSObject)
            return value

        // TODO Get @@toPrimitive method
        return ordinaryToPrimitive(value, type ?: ToPrimitiveHint.AsNumber)
    }

    @JvmStatic @ECMAImpl("OrdinaryToPrimitive", "7.1.1.1")
    fun ordinaryToPrimitive(value: JSValue, hint: ToPrimitiveHint): JSValue {
        expect(value is JSObject)
        expect(hint != ToPrimitiveHint.AsDefault)
        val methodNames = when (hint) {
            ToPrimitiveHint.AsString -> listOf("toString", "valueOf")
            else -> listOf("valueOf", "toString")
        }
        methodNames.forEach { methodName ->
            val method = value.get(methodName)
            if (isCallable(method)) {
                val result = call(method, value)
                if (result !is JSObject)
                    return result
            }
        }
        shouldThrowError("TypeError")
    }

    @JvmStatic @ECMAImpl("ToBoolean", "7.1.2")
    fun toBoolean(value: JSValue) = when (value.type) {
        JSValue.Type.Empty -> unreachable()
        JSValue.Type.Undefined -> JSFalse
        JSValue.Type.Null -> JSFalse
        JSValue.Type.Boolean -> value
        JSValue.Type.String -> value.asString.isNotEmpty().toValue()
        JSValue.Type.Number -> (!value.isZero && !value.isNaN).toValue()
        JSValue.Type.BigInt -> TODO()
        JSValue.Type.Symbol -> TODO()
        JSValue.Type.Accessor -> TODO()
        JSValue.Type.Object -> JSTrue
    }

    @JvmStatic @ECMAImpl("ToNumeric", "7.1.3")
    fun toNumeric(value: JSValue): JSValue {
        val primValue = toPrimitive(value, ToPrimitiveHint.AsNumber)
        if (primValue is JSBigInt)
            return primValue
        return toNumber(primValue)
    }

    @JvmStatic @ECMAImpl("ToNumber", "7.1.4")
    fun toNumber(value: JSValue): JSValue {
        return when (value) {
            JSUndefined -> JSNumber(Double.NaN)
            JSNull, JSFalse -> JSNumber(0)
            JSTrue -> JSNumber(1)
            is JSNumber -> return value
            is JSString -> TODO()
            is JSSymbol, is JSBigInt -> shouldThrowError("TypeError")
            is JSObject -> return toNumber(toPrimitive(value, ToPrimitiveHint.AsNumber))
            else -> unreachable()
        }
    }

    @JvmStatic @ECMAImpl("ToInt32", "7.1.6")
    fun toInt32(value: JSValue): JSValue {
        val number = toNumber(value)
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber(0)

        var int = floor(abs(number.asDouble)).toInt()
        if (number.asDouble < 0)
            int *= -1

        val int32bit = int % 2.0.pow(32).toInt()
        if (int32bit >= 2.0.pow(31).toInt())
            return JSNumber(int32bit - 2.0.pow(32))
        return JSNumber(int32bit)
    }

    @JvmStatic @ECMAImpl("ToUint32", "7.1.7")
    fun toUint32(value: JSValue): JSValue {
        val number = toNumber(value)
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber(0)

        var int = floor(abs(number.asDouble)).toInt()
        if (number.asDouble < 0)
            int *= -1
        return JSNumber(int % 2.0.pow(32).toInt())
    }

    @JvmStatic @ECMAImpl("ToString", "7.1.17")
    fun toString(value: JSValue): JSString {
        return when (value) {
            is JSString -> return value
            JSUndefined -> "undefined"
            JSNull -> "null"
            JSTrue -> "true"
            JSFalse -> "false"
            // TODO: Make sure to follow all of JS's number conversion rules here
            is JSNumber -> if (value.isInt) {
                value.number.toInt().toString()
            } else value.number.toString()
            is JSSymbol -> shouldThrowError("TypeError")
            is JSObject -> return toString(toPrimitive(value, ToPrimitiveHint.AsString))
            else -> unreachable()
        }.let(::JSString)
    }

    fun toPrintableString(value: JSValue): JSString {
        return when (value) {
            is JSSymbol -> value.descriptiveString().toValue()
            else -> toString(value)
        }
    }

    @JvmStatic @ECMAImpl("ToObject", "7.1.18")
    fun toObject(value: JSValue): JSObject {
        return when (value) {
            is JSObject -> value
            is JSUndefined, JSNull -> shouldThrowError("TypeError")
            is JSBoolean -> TODO()
            is JSNumber -> TODO()
            is JSString -> JSStringObject.create(Agent.runningContext.realm, value)
            is JSSymbol -> JSSymbolObject.create(Agent.runningContext.realm, value)
            is JSBigInt -> TODO()
            else -> TODO()
        }
    }

    @JvmStatic @ECMAImpl("ToPropertyKey", "7.1.19")
    fun toPropertyKey(value: JSValue): PropertyKey {
        val key = toPrimitive(value, ToPrimitiveHint.AsString)
        if (key is JSSymbol)
            return PropertyKey(key)
        return PropertyKey(toString(key).string)
    }

    @JvmStatic @ECMAImpl("RequireObjectCoercible", "7.2.1")
    fun requireObjectCoercible(value: JSValue): JSValue {
        if (value is JSUndefined || value is JSNull)
            shouldThrowError("TypeError")
        return value
    }

    @JvmStatic @ECMAImpl("IsCallable", "7.2.3")
    fun isCallable(value: JSValue): Boolean {
        if (value !is JSFunction)
            return false
        return value.isCallable
    }

    @JvmStatic @ECMAImpl("IsPropertyKey", "7.2.7")
    fun isPropertyKey(value: JSValue) = value is JSString /* || value is JSSymbol */

    @JvmStatic @ECMAImpl("GetV", "7.3.3")
    fun getV(target: JSValue, property: JSValue): JSValue {
        expect(isPropertyKey(property))
        val obj = toObject(target)
        return obj.get(toPropertyKey(property))
    }

    @JvmStatic @ECMAImpl("CreateDataProperty", "7.3.5")
    fun createDataProperty(target: JSValue, property: JSValue, value: JSValue): Boolean {
        expect(target is JSObject)
        expect(isPropertyKey(property))
        val newDesc = Descriptor(value, Attributes(Attributes.defaultAttributes))
        return target.defineOwnProperty(toPropertyKey(property), newDesc)
    }

    @JvmStatic @ECMAImpl("CreateDataPropertyOrThrow", "7.3.7")
    fun createDataPropertyOrThrow(target: JSValue, property: JSValue, value: JSValue): Boolean {
        if (!createDataProperty(target, property, value))
            shouldThrowError("TypeError")
        return true
    }

    @JvmStatic @ECMAImpl("GetMethod", "7.3.10")
    fun getMethod(value: JSValue, key: JSValue): JSValue {
        expect(isPropertyKey(key))
        val func = getV(value, key)
        if (func is JSUndefined || func is JSNull)
            return JSUndefined
        if (!isCallable(func))
            shouldThrowError("TypeError")
        return func
    }

    @JvmStatic @ECMAImpl("Call", "7.3.13")
    fun call(function: JSValue, thisValue: JSValue, arguments: List<JSValue> = emptyList()): JSValue {
        if (!isCallable(function))
            shouldThrowError("TypeError")
        return (function as JSFunction).call(thisValue, arguments)
    }

    @JvmStatic @ECMAImpl("Construct", "7.3.14")
    fun construct(constructor: JSValue, arguments: List<JSValue>, newTarget: JSValue = constructor): JSValue {
        expect(isConstructor(constructor))
        expect(isConstructor(newTarget))
        return (constructor as JSFunction).construct(arguments, newTarget as JSFunction)
    }

    @JvmStatic @ECMAImpl("IsConstructor", "7.2.4")
    fun isConstructor(value: JSValue): Boolean {
        if (value !is JSFunction)
            return false
        return value.isConstructable
    }

    @JvmStatic @ECMAImpl("GetIdentifierReference", "8.1.2.1")
    fun getIdentifierReference(env: EnvRecord?, name: String, isStrict: Boolean): JSReference {
        return when {
            env == null -> JSReference(JSUndefined, PropertyKey(name), isStrict)
            env.hasBinding(name) -> JSReference(env, PropertyKey(name), isStrict)
            else -> getIdentifierReference(env.outerEnv, name, isStrict)
        }
    }

    @JvmStatic @ECMAImpl("GetActiveScriptOrModule", "8.3.1")
    fun getActiveScriptOrModule() {
        TODO()
    }

    @JvmStatic @ECMAImpl("ResolveBinding", "8.3.2")
    fun resolveBinding(name: String, env: EnvRecord?): JSReference {
        val actualEnv = env ?: Agent.runningContext.lexicalEnv!!
        // TODO: Strict mode checking
        return getIdentifierReference(actualEnv, name, false)
    }

    @JvmStatic @ECMAImpl("GetThisEnvironment", "8.3.3")
    fun getThisEnvironment(): EnvRecord {
        // As the spec states, this is guaranteed to resolve without
        // any NPEs as there is always at least a global environment
        // with a this-binding
        var env = Agent.runningContext.lexicalEnv!!
        while (!env.hasThisBinding())
            env = env.outerEnv!!
        return env
    }

    @JvmStatic @ECMAImpl("ResolveThisBinding", "8.3.4")
    fun resolveThisBinding(): JSValue {
        return when (val env = getThisEnvironment()) {
            is FunctionEnvRecord -> env.getThisBinding()
            is GlobalEnvRecord -> env.getThisBinding()
            // is ModuleEnvRecord -> env.getThisBinding()
            else -> unreachable()
        }
    }

    @JvmStatic @ECMAImpl("GetGlobalObject", "8.3.6")
    fun getGlobalObject(): JSObject {
        return Agent.runningContext.realm.globalObject
    }

    @JvmStatic @ECMAImpl("GetPrototypeFromConstructor", "9.1.14")
    fun getPrototypeFromConstructor(constructor: JSFunction, intrinsicDefaultProto: JSObject): JSObject {
        val proto = constructor.get("prototype")
        if (proto is JSObject)
            return proto
        return intrinsicDefaultProto
    }

    @JvmStatic @ECMAImpl("PrepareForOrdinaryCall", "9.2.1.1")
    fun prepareForOrdinaryCall(function: JSScriptFunction, newTarget: JSValue): ExecutionContext {
        expect(newTarget is JSUndefined || newTarget is JSObject)
        val callerContext = Agent.runningContext
        val calleeContext = ExecutionContext(
            callerContext.agent,
            function.realm,
            function,
        )
        val localEnv = FunctionEnvRecord.create(function, newTarget)
        calleeContext.lexicalEnv = localEnv
        calleeContext.variableEnv = localEnv
        Agent.pushContext(calleeContext)
        return calleeContext
    }

    // TODO: Do we really need the calleeContext here?
    // prepareForOrdinaryCall will have just set it as the running
    // execution context
    @JvmStatic @ECMAImpl("OrdinaryCallBindThis", "9.2.1.2")
    fun ordinaryCallBindThis(function: JSScriptFunction, calleeContext: ExecutionContext, thisArgument: JSValue): JSValue {
        if (function.thisMode == JSFunction.ThisMode.Lexical)
            return JSUndefined
        val thisValue = if (function.thisMode == JSFunction.ThisMode.Strict) {
            thisArgument
        } else if (thisArgument == JSUndefined || thisArgument == JSNull) {
            function.realm.globalEnv!!.globalThis
        } else {
            toObject(thisArgument)
        }

        val localEnv = calleeContext.lexicalEnv
        expect(localEnv is FunctionEnvRecord)
        return localEnv.bindThisValue(thisValue)
    }

    @JvmStatic @ECMAImpl("FunctionDeclarationInstantiation", "9.2.10")
    fun functionDeclarationInstantiation(function: JSScriptFunction, arguments: List<JSValue>): JSValue {
        TODO()
    }

    @JvmStatic @JvmOverloads @ECMAImpl("ArrayCreate", "9.4.2.2")
    fun arrayCreate(length: Int, proto: JSObject? = Agent.runningContext.realm.arrayProto): JSValue {
        if (length >= 2.0.pow(32) - 1)
            shouldThrowError("RangeError")
        val array = JSArray.create(Agent.runningContext.realm)
        array.defineOwnProperty("length", Descriptor(JSNumber(length), Attributes(WRITABLE)))
        return array
    }

    @JvmStatic @ECMAImpl("EvaluatePropertyAccessWithExpressionKey", "12.3.3")
    fun evaluatePropertyAccessWithExpressionKey(baseValue: JSValue, property: JSValue, isStrict: Boolean): JSValue {
        val propertyValue = getValue(property)
        val bv = requireObjectCoercible(baseValue)
        val propertyKey = toPropertyKey(propertyValue)
        return JSReference(bv, propertyKey, isStrict)
    }

    @JvmStatic @ECMAImpl("EvaluatePropertyAccessWithIdentifierKey", "12.3.4")
    fun evaluatePropertyAccessWithIdentifierKey(baseValue: JSValue, property: String, isStrict: Boolean): JSValue {
        val bv = requireObjectCoercible(baseValue)
        return JSReference(bv, PropertyKey(property), isStrict)
    }

    @JvmStatic @ECMAImpl("EvaluateNew", "12.3.5.1.1")
    fun evaluateNew(target: JSValue, arguments: Array<JSValue>): JSValue {
        val constructor = getValue(target)
        if (!isConstructor(constructor))
            shouldThrowError("TypeError")
        return construct(constructor, arguments.toList())
    }

    @JvmStatic @ECMAImpl("EvaluateCall", "12.3.6.2")
    fun evaluateCall(target: JSValue, reference: JSValue, arguments: Array<JSValue>, tailPosition: Boolean): JSValue {
        val thisValue = if (reference is JSReference) {
            if (reference.isPropertyReference) {
                reference.getThisValue()
            } else {
                expect(reference.baseValue is EnvRecord)
                reference.baseValue.withBaseObject()
            }
        } else JSUndefined

        if (!isCallable(target))
            shouldThrowError("TypeError")
        if (tailPosition)
            TODO()
        return call(target as JSFunction, thisValue, arguments.toList())
    }

    @JvmStatic @ECMAImpl("ApplyStringOrNumericBinaryOperator", "12.15.5")
    fun applyStringOrNumericBinaryOperator(lhs: JSValue, rhs: JSValue, op: String): JSValue {
        if (op == "+") {
            val lprim = toPrimitive(lhs)
            val rprim = toPrimitive(rhs)
            if (lprim.isString || rprim.isString) {
                val lstr = toString(lprim)
                val rstr = toString(rprim)
                return JSString(lstr.string + rstr.string)

            }
        }

        val lnum = toNumeric(lhs)
        val rnum = toNumeric(rhs)
        if (lnum.type != rnum.type)
            shouldThrowError("TypeError")

        if (lnum.type == JSValue.Type.BigInt)
            TODO()

        return when (op) {
            "**" -> numericExponentiate(lnum, rnum)
            "*" -> numericMultiply(lnum, rnum)
            "/" -> numericDivide(lnum, rnum)
            "%" -> numericRemainder(lnum, rnum)
            "+" -> numericAdd(lnum, rnum)
            "-" -> numericSubtract(lnum, rnum)
            "<<" -> numericLeftShift(lnum, rnum)
            ">>" -> numericSignedRightShift(lnum, rnum)
            ">>>" -> numericUnsignedRightShift(lnum, rnum)
            "&" -> numericBitwiseAND(lnum, rnum)
            "^" -> numericBitwiseXOR(lnum, rnum)
            "|" -> numericBitwiseOR(lnum, rnum)
            else -> unreachable()
        }
    }

    @JvmStatic
    fun applyFunctionArguments(function: JSScriptFunction, arguments: List<JSValue>, env: EnvRecord?) {
        function.getParameterNames().forEachIndexed { index, name ->
            val lhs = resolveBinding(name, env)
            var value = if (index > arguments.lastIndex) {
                JSUndefined
            } else arguments[index]

            if (value == JSUndefined && function.getParamHasDefaultValue(index))
                value = function.getDefaultParameterValue(index)

            if (env == null) {
                 putValue(lhs, value)
            } else {
                initializeReferencedBinding(lhs, value)
            }
        }
    }
}
