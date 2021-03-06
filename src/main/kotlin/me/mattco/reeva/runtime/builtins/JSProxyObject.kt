package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.toValue

class JSProxyObject private constructor(
    realm: Realm,
    target: JSObject,
    handler: JSObject,
) : JSFunction(
    realm,
    if (target is JSFunction) target.isStrict else false,
    if (target is JSFunction) realm.functionProto else realm.objectProto,
) {
    override val isCallable = Operations.isCallable(target)

    val target by slot(SlotName.ProxyTarget, target)
    var handler: JSObject? by slot(SlotName.ProxyHandler, handler)

    override fun isConstructor() = Operations.isConstructor(target)

    fun revoke() {
        expect(handler != null)
        handler = null
    }

    private inline fun getTrapAndHandler(name: String, block: () -> Nothing): Pair<JSObject, JSValue> {
        val handler = handler.let {
            if (it == null)
                Errors.Proxy.Revoked(name).throwTypeError(realm)
            it
        }
        val trap = Operations.getMethod(realm, handler, name.toValue())
        if (trap is JSUndefined)
            block()
        return handler to trap
    }

    @ECMAImpl("9.5.1")
    override fun getPrototype(): JSValue {
        val (handler, trap) = getTrapAndHandler("getPrototypeOf") {
            return target.getPrototype()
        }
        val handlerProto = Operations.call(realm, trap, handler, listOf(target))
        if (handlerProto !is JSObject && handlerProto != JSNull)
            Errors.Proxy.GetPrototypeOf.ReturnObjectOrNull.throwTypeError(realm)
        if (target.isExtensible())
            return handlerProto

        val targetProto = target.getPrototype()
        if (!handlerProto.sameValue(targetProto))
            Errors.Proxy.GetPrototypeOf.NonExtensibleReturn.throwTypeError(realm)
        return handlerProto
    }

    override fun setPrototype(newPrototype: JSValue): Boolean {
        ecmaAssert(newPrototype is JSObject || newPrototype is JSNull)
        val (handler, trap) = getTrapAndHandler("setPrototypeOf") {
            return target.setPrototype(newPrototype)
        }
        val booleanTrapResult =
            Operations.toBoolean(Operations.call(realm, trap, handler, listOf(target, newPrototype)))
        if (!booleanTrapResult)
            return false
        if (target.isExtensible())
            return true
        val targetProto = target.getPrototype()
        if (!newPrototype.sameValue(targetProto))
            Errors.Proxy.SetPrototypeOf.NonExtensibleReturn.throwTypeError(realm)
        return true
    }

    override fun isExtensible(): Boolean {
        val (handler, trap) = getTrapAndHandler("isExtensible") {
            return target.isExtensible()
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(realm, trap, handler, listOf(target)))
        if (booleanTrapResult != target.isExtensible())
            Errors.Proxy.IsExtensible.DifferentReturn.throwTypeError(realm)
        return booleanTrapResult
    }

    override fun preventExtensions(): Boolean {
        val (handler, trap) = getTrapAndHandler("preventExtensions") {
            return target.preventExtensions()
        }
        val booleanTrapResult = Operations.toBoolean(Operations.call(realm, trap, handler, listOf(target)))
        if (booleanTrapResult && target.isExtensible())
            Errors.Proxy.PreventExtensions.ExtensibleReturn.throwTypeError(realm)
        return booleanTrapResult
    }

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        val (handler, trap) = getTrapAndHandler("getOwnPropertyDescriptor") {
            return target.getOwnPropertyDescriptor(property)
        }
        val trapResultObj = Operations.call(realm, trap, handler, listOf(target, property.asValue))
        if (trapResultObj !is JSObject && trapResultObj != JSUndefined)
            Errors.Proxy.GetOwnPropertyDesc.ReturnObjectOrUndefined.throwTypeError(realm)
        val targetDesc = target.getOwnPropertyDescriptor(property)
        if (trapResultObj == JSUndefined) {
            if (targetDesc == null)
                return null
            if (!targetDesc.isConfigurable)
                Errors.Proxy.GetOwnPropertyDesc.ExistingNonConf(property).throwTypeError(realm)
            if (!target.isExtensible())
                Errors.Proxy.GetOwnPropertyDesc.NonExtensibleOwnProp(property).throwTypeError(realm)
            return null
        }
        val resultDesc = Descriptor.fromObject(realm, trapResultObj).complete()
        if (!Operations.isCompatiblePropertyDescriptor(realm, target.isExtensible(), resultDesc, targetDesc))
            Errors.Proxy.GetOwnPropertyDesc.NonExistentNonExtensible(property).throwTypeError(realm)
        if (!resultDesc.isConfigurable) {
            if (targetDesc == null)
                Errors.Proxy.GetOwnPropertyDesc.NonExistentNonConf(property).throwTypeError(realm)
            if (targetDesc.isConfigurable)
                Errors.Proxy.GetOwnPropertyDesc.ConfAsNonConf(property).throwTypeError(realm)
            if (resultDesc.hasWritable && !resultDesc.isWritable && targetDesc.isWritable)
                Errors.Proxy.GetOwnPropertyDesc.WritableAsNonWritable(property).throwTypeError(realm)
        }
        return resultDesc
    }

    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        val (handler, trap) = getTrapAndHandler("defineProperty") {
            return target.defineOwnProperty(property, descriptor)
        }

        val descObj = descriptor.toObject(realm, target)
        val booleanTrapResult =
            Operations.toBoolean(Operations.call(realm, trap, handler, listOf(target, property.asValue, descObj)))
        if (!booleanTrapResult)
            return false
        val targetDesc = target.getOwnPropertyDescriptor(property)
        val isExtensible = target.isExtensible()
        val settingConfigFalse = descriptor.hasConfigurable && !descriptor.isConfigurable
        if (targetDesc == null) {
            if (!isExtensible)
                Errors.Proxy.DefineOwnProperty.AddToNonExtensible(property).throwTypeError(realm)
            if (settingConfigFalse)
                Errors.Proxy.DefineOwnProperty.AddNonConf(property).throwTypeError(realm)
        } else {
            if (!Operations.isCompatiblePropertyDescriptor(realm, isExtensible, descriptor, targetDesc))
                Errors.Proxy.DefineOwnProperty.IncompatibleDesc(property).throwTypeError(realm)
            if (settingConfigFalse && targetDesc.isConfigurable)
                Errors.Proxy.DefineOwnProperty.ChangeConf(property).throwTypeError(realm)
            if (targetDesc.isDataDescriptor && !targetDesc.isConfigurable && targetDesc.isWritable && descriptor.hasWritable && !descriptor.isWritable)
                Errors.Proxy.DefineOwnProperty.ChangeWritable(property).throwTypeError(realm)
        }
        return true
    }

    override fun hasProperty(property: PropertyKey): Boolean {
        val (handler, trap) = getTrapAndHandler("has") {
            return target.hasProperty(property)
        }
        val booleanTrapResult =
            Operations.toBoolean(Operations.call(realm, trap, handler, listOf(target, property.asValue)))
        if (!booleanTrapResult) {
            val targetDesc = target.getOwnPropertyDescriptor(property)
            if (targetDesc != null) {
                if (!targetDesc.isConfigurable)
                    Errors.Proxy.HasProperty.ExistingNonConf(property).throwTypeError(realm)
                if (!target.isExtensible())
                    Errors.Proxy.HasProperty.ExistingNonExtensible(property).throwTypeError(realm)
            }
        }
        return booleanTrapResult
    }

    override fun get(property: PropertyKey, receiver: JSValue): JSValue {
        val (handler, trap) = getTrapAndHandler("get") {
            return target.get(property, receiver)
        }
        val trapResult = Operations.call(realm, trap, handler, listOf(target, property.asValue, receiver))
        val targetDesc = target.getOwnPropertyDescriptor(property)
        if (targetDesc != null && !targetDesc.isConfigurable) {
            if (targetDesc.isDataDescriptor && !targetDesc.isWritable && !trapResult.sameValue(targetDesc.getRawValue()))
                Errors.Proxy.Get.DifferentValue(property).throwTypeError(realm)
            if (targetDesc.isAccessorDescriptor && !targetDesc.hasGetterFunction && trapResult != JSUndefined)
                Errors.Proxy.Get.NonConfAccessor(property).throwTypeError(realm)
        }
        return trapResult
    }

    override fun set(property: PropertyKey, value: JSValue, receiver: JSValue): Boolean {
        val (handler, trap) = getTrapAndHandler("set") {
            return target.set(property, value, receiver)
        }
        val trapResult =
            Operations.toBoolean(Operations.call(realm, trap, handler, listOf(target, property.asValue, receiver)))
        if (!trapResult)
            return false
        val targetDesc = target.getOwnPropertyDescriptor(property)
        if (targetDesc != null && !targetDesc.isConfigurable) {
            if (targetDesc.isDataDescriptor && !targetDesc.isWritable && !value.sameValue(targetDesc.getRawValue()))
                Errors.Proxy.Set.NonConfNonWritable(property).throwTypeError(realm)
            if (targetDesc.isAccessorDescriptor && !targetDesc.hasSetterFunction)
                Errors.Proxy.Set.NonConfAccessor(property).throwTypeError(realm)
        }
        return true
    }

    override fun delete(property: PropertyKey): Boolean {
        val (handler, trap) = getTrapAndHandler("deleteProperty") {
            return target.delete(property)
        }
        val booleanTrapResult = Operations.toBoolean(
            Operations.call(
                realm,
                trap,
                handler,
                listOf(target, Operations.toString(realm, property.asValue))
            )
        )
        if (!booleanTrapResult)
            return false
        val targetDesc = target.getOwnPropertyDescriptor(property) ?: return true
        if (!targetDesc.isConfigurable)
            Errors.Proxy.Delete.NonConf(property).throwTypeError(realm)
        if (!target.isExtensible())
            Errors.Proxy.Delete.NonExtensible(property).throwTypeError(realm)
        return true
    }

    override fun ownPropertyKeys(onlyEnumerable: Boolean): List<PropertyKey> {
        val (handler, trap) = getTrapAndHandler("ownKeys") {
            return target.ownPropertyKeys(onlyEnumerable)
        }
        val trapResultArray = Operations.call(realm, trap, handler, listOf(target))
        // Spec deviation: We use numbers as keys, so we need to include the number type in this list
        val trapResult = Operations.createListFromArrayLike(
            realm,
            trapResultArray,
            listOf(Type.String, Type.Symbol, Type.Number)
        ).map {
            PropertyKey.from(it)
        }
        if (trapResult.distinct().size != trapResult.size)
            Errors.Proxy.OwnPropertyKeys.DuplicateKeys.throwTypeError(realm)
        val isExtensible = target.isExtensible()
        val targetKeys = target.ownPropertyKeys(onlyEnumerable)
        val targetConfigurableKeys = mutableListOf<PropertyKey>()
        val targetNonconfigurableKeys = mutableListOf<PropertyKey>()
        targetKeys.forEach { key ->
            val desc = target.getOwnPropertyDescriptor(key)
            if (desc != null && !desc.isConfigurable) {
                targetNonconfigurableKeys.add(key)
            } else targetConfigurableKeys.add(key)
        }
        if (isExtensible && targetNonconfigurableKeys.isEmpty())
            return trapResult

        val uncheckedResultKeys = trapResult.toMutableList()
        targetNonconfigurableKeys.forEach { key ->
            if (key !in uncheckedResultKeys)
                Errors.Proxy.OwnPropertyKeys.NonConf(key).throwTypeError(realm)
            uncheckedResultKeys.remove(key)
        }
        if (isExtensible)
            return trapResult
        targetConfigurableKeys.forEach { key ->
            if (key !in uncheckedResultKeys)
                Errors.Proxy.OwnPropertyKeys.NonExtensibleMissingKey(key).throwTypeError(realm)
            uncheckedResultKeys.remove(key)
        }
        if (uncheckedResultKeys.isNotEmpty())
            Errors.Proxy.OwnPropertyKeys.NonExtensibleExtraProp.throwTypeError(realm)
        return trapResult
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined) {
            val (_, trap) = getTrapAndHandler("apply") {
                return Operations.call(realm, target as JSFunction, arguments)
            }
            val argArray = Operations.createArrayFromList(realm, arguments)
            return Operations.call(realm, trap, arguments.thisValue, listOf(target, arguments.thisValue, argArray))
        } else {
            val (handler, trap) = getTrapAndHandler("construct") {
                return Operations.construct(target as JSFunction, arguments, arguments.newTarget)
            }
            val argArray = Operations.createArrayFromList(realm, arguments)
            val newObj = Operations.call(realm, trap, handler, listOf(target, argArray, arguments.newTarget))
            if (newObj !is JSObject)
                Errors.Proxy.Construct.NonObject.throwTypeError(realm)
            return newObj
        }
    }

    companion object {
        fun create(realm: Realm, target: JSObject, handler: JSObject) =
            JSProxyObject(realm, target, handler).initialize()
    }
}
