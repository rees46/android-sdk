package com.personalization.ui.widgets

/** Ошибка запроса виджета: код и текст из колбэка SDK. */
internal data class LoadError(val code: Int, val message: String?)

/**
 * Состояние загрузки виджета с данными: ещё не грузили, грузим, есть данные, сервер
 * ответил пусто, запрос упал. Пустой ответ — отдельное состояние, а не [Content] с пустым
 * списком: виджет показывает на нём пустое состояние или прячется.
 */
internal sealed class LoadState<out T> {
    object Idle : LoadState<Nothing>()
    object Loading : LoadState<Nothing>()
    data class Content<out T>(val data: T) : LoadState<T>()
    object Empty : LoadState<Nothing>()
    data class Error(val error: LoadError) : LoadState<Nothing>()
}

/**
 * Ведёт [LoadState] одного запроса: [load] запускает его, [retry] повторяет последний,
 * [reset] возвращает в [LoadState.Idle].
 *
 * Ответ запроса, который уже сменился следующим (или сброшен), отбрасывается — состояние
 * всегда про последний запрос. Пустой по [isEmpty] ответ — [LoadState.Empty].
 * Колбэки запроса приходят с любого потока; новое состояние [onChange] получает через
 * [post] — по умолчанию на главном потоке.
 */
internal class LoadController<T>(
    private val isEmpty: (T) -> Boolean = { false },
    private val post: (() -> Unit) -> Unit = MainThread::run,
    private val onChange: (LoadState<T>) -> Unit
) {

    var state: LoadState<T> = LoadState.Idle
        private set

    private var request: LoadRequest<T>? = null
    private var sequence = 0

    /** Запустить [request] вместо прежнего. */
    fun load(request: LoadRequest<T>) {
        this.request = request
        start(request)
    }

    /** Повторить последний запрос, например по кнопке на ошибке. Без запроса — ничего. */
    fun retry() {
        request?.let(::start)
    }

    /** Забыть запрос; его поздний ответ отбросится. */
    fun reset() {
        sequence++
        request = null
        set(LoadState.Idle)
    }

    private fun start(request: LoadRequest<T>) {
        val current = ++sequence
        set(LoadState.Loading)
        request(
            { data ->
                post { if (current == sequence) set(if (isEmpty(data)) LoadState.Empty else LoadState.Content(data)) }
            },
            { code, message ->
                post { if (current == sequence) set(LoadState.Error(LoadError(code, message))) }
            }
        )
    }

    private fun set(next: LoadState<T>) {
        state = next
        onChange(next)
    }
}

/** Запрос данных в форме колбэков SDK. */
internal typealias LoadRequest<T> = (onSuccess: (T) -> Unit, onError: (Int, String?) -> Unit) -> Unit
