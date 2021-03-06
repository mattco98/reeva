package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty

class JSMapObject private constructor(realm: Realm) : JSObject(realm, realm.mapProto) {
    val mapData by slot(SlotName.MapData, MapData())

    data class MapData(
        val map: MutableMap<JSValue, JSValue> = mutableMapOf(),
        val keyInsertionOrder: MutableList<JSValue> = mutableListOf(),
    ) {
        var iterationCount = 0
            set(value) {
                if (value == 0)
                    keyInsertionOrder.removeIf { it == JSEmpty }
                field = value
            }
    }

    companion object {
        fun create(realm: Realm) = JSMapObject(realm).initialize()
    }
}
