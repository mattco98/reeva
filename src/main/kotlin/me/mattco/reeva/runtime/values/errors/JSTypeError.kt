package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Realm

class JSTypeErrorObject private constructor(realm: Realm, message: String? = null) : JSErrorObject(realm, message, realm.typeErrorProto) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun create(realm: Realm, message: String? = null) = JSTypeErrorObject(realm, message).also { it.init() }
    }
}

class JSTypeErrorProto private constructor(realm: Realm) : JSErrorProto(realm, "TypeError") {
    companion object {
        fun create(realm: Realm) = JSTypeErrorProto(realm).also { it.init() }
    }
}

class JSTypeErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "TypeError") {
    override fun constructErrorObj(): JSErrorObject {
        return JSTypeErrorObject.create(realm)
    }

    companion object {
        fun create(realm: Realm) = JSTypeErrorCtor(realm).also { it.init() }
    }
}
