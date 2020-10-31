package me.mattco.reeva.runtime.objects

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.errors.JSErrorObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.runtime.wrappers.JSBooleanObject
import me.mattco.reeva.runtime.wrappers.JSNumberObject
import me.mattco.reeva.runtime.wrappers.JSStringObject
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.toValue

class JSObjectProto private constructor(realm: Realm) : JSObject(realm, JSNull) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.objectCtor, 0)
    }

    @JSThrows
    @ECMAImpl("19.1.3.2")
    @JSMethod("hasOwnProperty", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun hasOwnProperty(thisValue: JSValue, arguments: JSArguments): JSValue {
        val key = Operations.toPropertyKey(arguments.argument(0))
        val o = Operations.toObject(thisValue)
        return Operations.hasOwnProperty(o, key)
    }

    @JSThrows
    @ECMAImpl("19.1.3.3")
    @JSMethod("isPrototypeOf", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun isPrototypeOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        var arg = arguments.argument(0)
        if (arg !is JSObject)
            return JSFalse
        val thisObj = Operations.toObject(thisValue)
        while (true) {
            arg = (arg as JSObject).getPrototype()
            if (arg == JSNull)
                return JSFalse
            if (arg.sameValue(thisObj))
                return JSTrue
        }
    }

    @JSThrows
    @ECMAImpl("19.1.3.4")
    @JSMethod("propertyIsEnumerable", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun propertyIsEnumerable(thisValue: JSValue, arguments: JSArguments): JSValue {
        val key = Operations.toPropertyKey(arguments.argument(0))
        val thisObj = Operations.toObject(thisValue)
        val desc = thisObj.getOwnPropertyDescriptor(key) ?: return JSFalse
        return desc.isEnumerable.toValue()
    }

    @JSThrows
    @ECMAImpl("19.1.3.5")
    @JSMethod("toLocaleString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toLocaleString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        return Operations.invoke(thisObj, "toString".toValue())
    }

    // Doesn't throw because Symbol overrides this method,
    @JSMethod("toString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toString(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        if (thisValue == JSUndefined)
            return "[object Undefined]".toValue()
        if (thisValue == JSNull)
            return "[object Null]".toValue()

        val obj = Operations.toObject(thisValue)
        val tag = obj.get(Realm.`@@toStringTag`).let {
            if (it is JSString) {
                it.string
            } else {
                when (obj) {
                    is JSArrayObject -> "Array"
                    is JSFunction -> "Function"
                    is JSErrorObject -> "Error"
                    is JSBooleanObject -> "Boolean"
                    is JSNumberObject -> "Number"
                    is JSStringObject -> "String"
                    else -> "Object"
                }
            }
        }

        // TODO: @@toStringTag
        return "[object $tag]".toValue()
    }

    @JSThrows
    @JSMethod("valueOf", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun valueOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Operations.toObject(thisValue)
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm) = JSObjectProto(realm)
    }
}