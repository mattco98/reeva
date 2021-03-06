package me.mattco.reeva.runtime.errors

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject

class JSReferenceErrorObject private constructor(realm: Realm, message: String? = null) : JSErrorObject(realm, message, realm.referenceErrorProto) {
    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSReferenceErrorObject(realm, message).initialize()
    }
}

class JSReferenceErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm, realm.referenceErrorCtor, realm.errorProto, "ReferenceError"
) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.referenceErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm) = JSReferenceErrorProto(realm).initialize()
    }
}

class JSReferenceErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "ReferenceError") {
    override fun errorProto(): JSObject = realm.referenceErrorProto

    companion object {
        fun create(realm: Realm) = JSReferenceErrorCtor(realm).initialize()
    }
}
