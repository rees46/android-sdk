package com.personalization.popups

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.personalization.SDK
import com.personalization.features.inAppNotification.impl.ForegroundActivity
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.popups.PopupV3Presenter
import com.personalization.ui.widgets.MainThread

/**
 * Попапы v3 — нативный рендер документов нового конструктора попапов (`sdk.popups`).
 *
 * Спящий режим: движок не шлёт ни одного запроса, пока хост не включил его ([enabled]) и
 * пока `/init` не вернул флаг `popups_v3: true` — или хост не попросил обойтись без флага
 * ([ignoreInitFlag]). Хост сообщает об открытых экранах ([screenOpened]) кодами, которые
 * маркетолог пишет в `target_screens`; остальное движок делает сам: список, триггеры,
 * частоту, очередь, промокод, показ и события.
 *
 * Пока за шлюзом кита: API не публичный и может поменяться.
 */
@InternalPersonalizationUiApi
class PopupsV3 internal constructor(private val sdk: SDK) {

    /** Включить движок. Без этого попапы v3 не запрашиваются и не показываются. */
    var enabled: Boolean = false
        set(value) {
            field = value
            MainThread.run { maybeStart() }
        }

    /** Не ждать флага `popups_v3` из `/init`: список запрашивается после любого успешного `/init`. */
    var ignoreInitFlag: Boolean = false
        set(value) {
            field = value
            MainThread.run { maybeStart() }
        }

    /**
     * Тестовый режим: попапы в статусе «Тест» тоже приходят (`testmode=true` в списке, ручном
     * показе и `/init` — для `/init` выставить до инициализации), частота не записывается.
     */
    var testMode: Boolean = false

    /** `debug=1` в списке и каждое решение движка в лог SDK. */
    var debug: Boolean = false

    /** Хост личного кабинета, на котором живёт API попапов. */
    var appHost: String = DEFAULT_APP_HOST

    /**
     * Ссылки попапа (кнопки, элементы, «применить» промокода): `true` — хост открыл сам,
     * `false` — SDK откроет системно (`ACTION_VIEW`).
     */
    var linkHandler: ((url: String) -> Boolean)? = null

    /** Нажатие на товар ленты рекомендаций: `true` — хост открыл сам, иначе ссылка товара. */
    var productHandler: ((product: PersonalizationProduct) -> Boolean)? = null

    /** Флаг последнего `/init`: `null` — ещё не отвечал. */
    @Volatile
    private var initFlag: Boolean? = sdk.popupsV3InitFlag

    private val handler = Handler(Looper.getMainLooper())

    internal val presenter: PopupV3Presenter by lazy { PopupV3Presenter(sdk, this) }

    internal val transport: PopupsV3Transport by lazy { PopupsV3Api(sdk) { appHost } }

    private val engine: PopupsV3Engine by lazy {
        val shopId = runCatching { sdk.getUserSettingsValueUseCase.getShopId() }.getOrDefault("")
        val sid = { runCatching { sdk.getSid() }.getOrNull().orEmpty() }
        PopupsV3Engine(
            transport = transport,
            presenter = presenter,
            frequency = PopupsV3Frequency(
                storage = PopupsV3Preferences.forShop(sdk.context, shopId),
                now = System::currentTimeMillis,
                sid = sid,
                testMode = { testMode }
            ),
            scheduler = { delayMs, task ->
                val runnable = Runnable(task)
                handler.postDelayed(runnable, delayMs)
                ({ handler.removeCallbacks(runnable) })
            },
            clock = System::currentTimeMillis,
            sid = sid,
            testMode = { testMode },
            debug = { debug },
            log = SDK::debug,
            autoPresentation = { sdk.enableAutoPopupPresentation }
        ).also { ForegroundActivity.addListener(lifecycle) }
    }

    private val lifecycle = object : ForegroundActivity.Listener {
        override fun onForeground() = engine.onForeground()
        override fun onBackground() = engine.onBackground()
        override fun onActivityResumed(activity: Activity) = Unit
    }

    /**
     * Открыт экран с кодом [name] (`home`, `category`, `product`, `cart`, `search`…): звать
     * при каждом показе экрана. По нему стреляют `screen_open` и `time_on_screen`, ждущий
     * экрана `app_start`, и проверяются `target_screens`.
     */
    fun screenOpened(name: String) {
        val screen = name.trim()
        if (screen.isEmpty()) return
        MainThread.run { engine.screenOpened(screen) }
    }

    /**
     * Показать попап [id] вручную: без триггера, экранов и частоты (частота не пишется).
     * Работает при [enabled] и без флага `/init` — это явное намерение хоста.
     */
    fun show(id: Int) {
        MainThread.run {
            if (!enabled) {
                SDK.debug("popups v3: show($id) ignored: popups are not enabled")
                return@run
            }
            engine.showManual(id)
        }
    }

    /** `/init` ответил; [flag] — `popups_v3` из ответа. */
    internal fun onInit(flag: Boolean) {
        MainThread.run {
            initFlag = flag
            if (debug) SDK.debug("popups v3: /init popups_v3=$flag")
            maybeStart(refresh = true)
        }
    }

    /** Кастомное событие SDK — для триггера `track_event`. */
    internal fun onTrackEvent(name: String) {
        MainThread.run { if (enabled) engine.onTrackEvent(name) }
    }

    internal fun onPurchase() {
        MainThread.run { if (enabled) engine.onPurchase() }
    }

    /** Профиль, подписка, сегмент поменялись — список перезапрашивается (повтор тех же данных — нет). */
    internal fun onContextChange(type: String, signature: String) {
        MainThread.run { if (enabled) engine.onContextChange(type, signature) }
    }

    /** [refresh] — новый ответ `/init`: уже работающий движок обновляет список. */
    private fun maybeStart(refresh: Boolean = false) {
        if (!enabled) return
        val flag = initFlag ?: return
        if (!flag && !ignoreInitFlag) {
            if (debug) SDK.debug("popups v3: off — /init did not send popups_v3")
            return
        }
        if (engine.isStarted && !refresh) return
        engine.start()
    }

    companion object {
        /** Хост ЛК по умолчанию. */
        const val DEFAULT_APP_HOST = "app.rees46.ru"
    }
}
