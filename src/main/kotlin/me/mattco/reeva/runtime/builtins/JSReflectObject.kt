package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSReflectObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()
        defineOwnProperty(Realm.`@@toStringTag`, "Reflect".toValue(), Descriptor.CONFIGURABLE)
    }

    @JSMethod("apply", 3)
    fun apply(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, thisArg, argumentsList) = arguments.takeArgs(0..2)

        if (!Operations.isCallable(target))
            Errors.NotCallable(Operations.toPrintableString(target)).throwTypeError()

        val args = Operations.createListFromArrayLike(argumentsList)
        return Operations.call(target, thisArg, args)
    }

    @JSMethod("construct", 2)
    fun construct(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, argumentsList) = arguments.takeArgs(0..1)
        val newTarget = if (arguments.size <= 2) {
            target
        } else arguments.argument(2).also {
            if (!Operations.isConstructor(it))
                Errors.NotACtor(Operations.toPrintableString(it)).throwTypeError()
        }

        if (!Operations.isCallable(target))
            Errors.NotCallable(Operations.toPrintableString(target)).throwTypeError()

        val args = Operations.createListFromArrayLike(argumentsList)
        return Operations.construct(target, args, newTarget)
    }

    @JSMethod("defineProperty", 3)
    fun defineProperty(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey, attributes) = arguments.takeArgs(0..2)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("defineProperty").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        val desc = Descriptor.fromObject(attributes)
        return target.defineOwnProperty(key, desc).toValue()
    }

    @JSMethod("deleteProperty", 2)
    fun deleteProperty(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("deleteProperty").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        return target.delete(key).toValue()
    }

    @JSMethod("get", 2)
    fun get(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey, receiver) = arguments.takeArgs(0..2)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("get").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        return target.get(key, receiver.ifUndefined(target))
    }

    @JSMethod("getOwnPropertyDescriptor", 2)
    fun getOwnPropertyDescriptor(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("getOwnPropertyDescriptor").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        return target.getOwnPropertyDescriptor(key)?.toObject(realm, JSUndefined) ?: JSUndefined
    }

    @JSMethod("getPrototypeOf", 1)
    fun getPrototypeOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("getPrototypeOf").throwTypeError()
        return target.getPrototype()
    }

    @JSMethod("has", 2)
    fun has(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("has").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        return target.hasProperty(key).toValue()
    }

    @JSMethod("isExtensible", 1)
    fun isExtensible(thisValue: JSValue, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("isExtensible").throwTypeError()
        return target.isExtensible().toValue()
    }

    @JSMethod("ownKeys", 1)
    fun ownKeys(thisValue: JSValue, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("ownKeys").throwTypeError()
        val keys = target.ownPropertyKeys()
        return Operations.createArrayFromList(keys.map { it.asValue })
    }

    @JSMethod("preventExtensions", 1)
    fun preventExtensions(thisValue: JSValue, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("preventExtensions").throwTypeError()
        return target.preventExtensions().toValue()
    }

    @JSMethod("set", 2)
    fun set(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey, value, receiver) = arguments.takeArgs(0..3)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("set").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        return target.set(key, value, receiver.ifUndefined(target)).toValue()
    }

    @JSMethod("setPrototypeOf", 2)
    fun setPrototypeOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, proto) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("setPrototypeOf").throwTypeError()
        if (proto !is JSObject && proto != JSNull)
            Errors.Reflect.BadProto.throwTypeError()
        return target.setPrototype(proto).toValue()
    }

    companion object {
        fun create(realm: Realm) = JSReflectObject(realm).initialize()
    }
}
