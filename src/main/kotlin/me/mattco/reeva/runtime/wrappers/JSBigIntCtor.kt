package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSBigInt
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.toValue
import java.math.BigInteger
import kotlin.math.pow

class JSBigIntCtor private constructor(realm: Realm) : JSNativeFunction(realm, "BigInt", 1) {
    init {
        isConstructable = true
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (newTarget != JSUndefined)
            Errors.BigInt.CtorCalledWithNew.throwTypeError()
        val prim = Operations.toPrimitive(arguments.argument(0), Operations.ToPrimitiveHint.AsNumber)
        if (prim is JSNumber) {
            if (!Operations.isIntegralNumber(prim))
                Errors.BigInt.Conversion(Operations.toPrintableString(prim)).throwRangeError()
            return BigInteger.valueOf(prim.asLong).toValue()
        }
        return Operations.toBigInt(prim)
    }

    @JSMethod("asIntN", 2)
    fun asIntN(thisValue: JSValue, arguments: JSArguments): JSValue {
        val bits = Operations.toIndex(arguments.argument(0))
        val bigint = Operations.toBigInt(arguments.argument(1))
        if (bits == 0)
            return JSBigInt.ZERO
        val modRhs = BigInteger.valueOf(2L).shiftLeft(bits - 1)
        val mod = bigint.number.mod(modRhs)
        if (mod >= modRhs.divide(BigInteger.valueOf(2)))
            return (mod - modRhs).toValue()
        return mod.toValue()
    }

    @JSMethod("asUintN", 2)
    fun asUintN(thisValue: JSValue, arguments: JSArguments): JSValue {
        val bits = Operations.toIndex(arguments.argument(0))
        val bigint = Operations.toBigInt(arguments.argument(1))
        return bigint.number.mod(BigInteger.valueOf(2L).shiftLeft(bits - 1)).toValue()
    }

    companion object {
        fun create(realm: Realm) = JSBigIntCtor(realm).initialize()
    }
}