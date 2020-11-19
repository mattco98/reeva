package me.mattco.reeva.runtime.annotations

import me.mattco.reeva.runtime.objects.Descriptor

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JSMethod(
    val name: String,
    val length: Int,
    val attributes: String = "CeW"
)
