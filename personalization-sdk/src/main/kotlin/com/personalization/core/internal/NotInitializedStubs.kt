package com.personalization.core.internal

import android.os.Handler
import android.os.Looper
import com.personalization.LogLevel
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation

/**
 * What the public managers of an `SDK` hold before `initialize` puts the real ones in their place — `tracking`,
 * `searchManager` and the other fields of an interface type — so that reading one does not throw
 * `UninitializedPropertyAccessException` into the app.
 *
 * A stub runs nothing and sends nothing. A call of it is logged, and the call's callbacks hear
 * [SdkError.NotInitialized] at once: an `OnApiCallbackListener` `onError(-1)` with the error behind it
 * ([CurrentError]), an `onError` lambda `(code, message)` `-1`, a [CompletionListener] the failure on the main
 * thread; a suspending method throws [SdkException]. What it returns is the empty value of its type — no
 * text, an empty list, `false`, 0 — or a stub of an interface, null otherwise.
 */
internal object NotInitializedStubs {

    private val stubs = ConcurrentHashMap<Class<*>, Any>()
    private val delivery = LegacyDelivery { level, code, message, throwable -> SdkLogs.log(level, code, message, throwable = throwable) }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** The stub of [type], an interface; one per type, shared, as it keeps nothing. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> of(type: Class<T>): T = stubs.getOrPut(type) {
        Proxy.newProxyInstance(type.classLoader, arrayOf(type), Calls(type.simpleName))
    } as T

    /** Whether [value] is a stub, not a manager `initialize` put in. */
    fun isStub(value: Any?): Boolean = value != null && Proxy.isProxyClass(value.javaClass) && Proxy.getInvocationHandler(value) is Calls

    private class Calls(private val name: String) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            if (method.declaringClass == Any::class.java) {
                return when (method.name) {
                    "equals" -> proxy === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    else -> "NotInitialized$name"
                }
            }
            val message = "The SDK instance is not initialized: $name.${method.name} did nothing"
            SdkLogs.log(LogLevel.WARNING, LogCodes.SDK_LOG, message, context = mapOf("reason" to REASON, "call" to "$name.${method.name}"))
            args?.forEach { tell(it, message) }
            if (args?.any { it is Continuation<*> } == true) throw SdkException(SdkError.NotInitialized, message)
            return empty(method.returnType)
        }

        @Suppress("UNCHECKED_CAST")
        private fun tell(argument: Any?, message: String) {
            when (argument) {
                is OnApiCallbackListener -> delivery.failure(argument, LegacyErrors.NO_ANSWER, message, SdkError.NotInitialized)
                is CompletionListener<*> -> main.post {
                    try {
                        (argument as CompletionListener<Any?>).onComplete(SdkResult.Failure(SdkError.NotInitialized))
                    } catch (e: Exception) {
                        SdkLogs.log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "The app's CompletionListener threw", throwable = e)
                    }
                }
                // The old managers' `onError: (code, message) -> Unit`; their other callbacks take one value.
                is Function2<*, *, *> -> try {
                    (argument as Function2<Any?, Any?, Any?>).invoke(LegacyErrors.NO_ANSWER, message)
                } catch (e: ClassCastException) {
                    // Not an onError: a callback of other values is not called.
                } catch (e: Exception) {
                    SdkLogs.log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "onError of a call threw", throwable = e)
                }
            }
        }

        private fun empty(type: Class<*>): Any? = when {
            type == Void.TYPE || type == Any::class.java -> null
            type == java.lang.Boolean.TYPE -> false
            type == Integer.TYPE -> 0
            type == java.lang.Long.TYPE -> 0L
            type == java.lang.Double.TYPE -> 0.0
            type == java.lang.Float.TYPE -> 0f
            type == java.lang.Short.TYPE -> 0.toShort()
            type == java.lang.Byte.TYPE -> 0.toByte()
            type == Character.TYPE -> '\u0000'
            type == String::class.java -> ""
            type.isAssignableFrom(List::class.java) -> emptyList<Any?>()
            type.isAssignableFrom(Set::class.java) -> emptySet<Any?>()
            type.isAssignableFrom(Map::class.java) -> emptyMap<Any?, Any?>()
            type.isInterface -> of(type)
            else -> null
        }
    }

    private const val REASON = "not_initialized"
}
