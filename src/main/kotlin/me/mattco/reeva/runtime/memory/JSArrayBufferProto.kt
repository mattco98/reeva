package me.mattco.reeva.runtime.memory

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.*
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.attrs
import me.mattco.reeva.utils.toValue
import kotlin.math.max
import kotlin.math.min

class JSArrayBufferProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.`@@toStringTag`, "ArrayBuffer".toValue(), attrs { +conf })
        defineOwnProperty("constructor", realm.arrayBufferCtor, attrs { +conf -enum +writ })
        defineNativeAccessor("byteLength", attrs { +conf -enum }, ::getByteLength)
        defineNativeFunction("slice", 2, ::slice)
    }

    private fun getByteLength(realm: Realm, thisValue: JSValue): JSValue {
        if (!Operations.requireInternalSlot(thisValue, SlotName.ArrayBufferData))
            Errors.IncompatibleMethodCall("ArrayBuffer.prototype.byteLength").throwTypeError(realm)
        if (Operations.isSharedArrayBuffer(thisValue))
            Errors.TODO("ArrayBuffer.prototype.getByteLength isSharedArrayBuffer").throwTypeError(realm)

        if (Operations.isDetachedBuffer(thisValue))
            return JSNumber.ZERO
        return thisValue.getSlotAs<Int>(SlotName.ArrayBufferByteLength).toValue()
    }

    private fun slice(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        if (!Operations.requireInternalSlot(thisValue, SlotName.ArrayBufferData))
            Errors.IncompatibleMethodCall("ArrayBuffer.prototype.slice").throwTypeError(realm)
        if (Operations.isSharedArrayBuffer(thisValue))
            Errors.TODO("ArrayBuffer.prototype.slice isSharedArrayBuffer 1").throwTypeError(realm)
        if (Operations.isDetachedBuffer(thisValue))
            Errors.TODO("ArrayBuffer.prototype.slice isDetachedBuffer").throwTypeError(realm)

        val length = thisValue.getSlotAs<Int>(SlotName.ArrayBufferByteLength)
        val relativeStart = arguments.argument(0).toIntegerOrInfinity(realm, )
        val first = when {
            relativeStart.number < 0 -> max(length + relativeStart.asInt, 0)
            relativeStart.isNegativeInfinity -> 0
            else -> min(relativeStart.asInt, length)
        }

        val relativeEnd = arguments.argument(1).let {
            if (it == JSUndefined) {
                length.toValue()
            } else it.toIntegerOrInfinity(realm, )
        }

        val final = when {
            relativeEnd.isNegativeInfinity -> 0
            relativeEnd.number < 0 -> max(length + relativeEnd.asInt, 0)
            else -> min(relativeEnd.asInt, length)
        }

        val newLength = max(final - first, 0)

        val ctor = Operations.speciesConstructor(realm, thisValue, realm.arrayBufferCtor)
        val new = Operations.construct(ctor, listOf(newLength.toValue()))
        if (!Operations.requireInternalSlot(new, SlotName.ArrayBufferData))
            Errors.ArrayBuffer.BadSpecies(new.toPrintableString()).throwTypeError(realm)

        if (Operations.isSharedArrayBuffer(new))
            Errors.TODO("ArrayBuffer.prototype.slice isSharedArrayBuffer 2").throwTypeError(realm)
        if (Operations.isDetachedBuffer(new))
            Errors.TODO("ArrayBuffer.prototype.slice isDetachedBuffer 1").throwTypeError(realm)

        if (new.sameValue(thisValue))
            Errors.TODO("ArrayBuffer.prototype.slice SameValue").throwTypeError(realm)

        if (new.getSlotAs<Int>(SlotName.ArrayBufferByteLength) < newLength)
            Errors.TODO("ArrayBuffer.prototype.slice newLength").throwTypeError(realm)
        if (Operations.isDetachedBuffer(new))
            Errors.TODO("ArrayBuffer.prototype.slice isDetachedBuffer 2").throwTypeError(realm)

        val fromBuf = thisValue.getSlotAs<DataBlock>(SlotName.ArrayBufferData)
        val toBuf = new.getSlotAs<DataBlock>(SlotName.ArrayBufferData)
        Operations.copyDataBlockBytes(toBuf, 0, fromBuf, first, newLength)
        return new
    }

    companion object {
        fun create(realm: Realm) = JSArrayBufferProto(realm).initialize()
    }
}
