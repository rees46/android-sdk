package com.personalization.ui.popups

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import com.personalization.InternalPersonalizationV3Api
import com.personalization.R
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.params.ProfileParams
import com.personalization.features.inAppNotification.impl.ForegroundActivity
import com.personalization.inAppNotification.view.component.dialog.alert.ALERT_DIALOG_TAG
import com.personalization.inAppNotification.view.component.dialog.bottom.BOTTOM_DIALOG_TAG
import com.personalization.inAppNotification.view.component.dialog.fullScreen.FULL_SCREEN_DIALOG_TAG
import com.personalization.inAppNotification.view.component.dialog.top.TOP_DIALOG_TAG
import com.personalization.popups.PopupV3
import com.personalization.popups.PopupV3Design
import com.personalization.popups.PopupV3Frame
import com.personalization.popups.PopupV3Links
import com.personalization.popups.PopupV3Specs
import com.personalization.popups.PopupV3Texts
import com.personalization.popups.PopupsV3
import com.personalization.popups.PopupsV3Presenter
import com.personalization.popups.PopupsV3Transport
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.widgets.MainThread
import com.personalization.ui.widgets.StoriesPollsApi
import com.personalization.ui.widgets.StoriesPollsResult
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import org.json.JSONObject

/** Тексты по умолчанию из ресурсов SDK — по локали устройства. */
internal fun popupTexts(context: Context): PopupV3Texts = with(context) {
    PopupV3Texts(
        close = getString(R.string.personalization_popups_close),
        detail = getString(R.string.personalization_popups_detail),
        image = getString(R.string.personalization_popups_image),
        email = getString(R.string.personalization_popups_email),
        subscribe = getString(R.string.personalization_popups_subscribe),
        emailConsent = getString(R.string.personalization_popups_email_consent),
        emailError = getString(R.string.personalization_popups_email_error),
        formError = getString(R.string.personalization_popups_form_error),
        send = getString(R.string.personalization_popups_send),
        copy = getString(R.string.personalization_popups_copy),
        copied = getString(R.string.personalization_popups_copied),
        thanks = getString(R.string.personalization_popups_thanks),
        genderMale = getString(R.string.personalization_popups_gender_male),
        genderFemale = getString(R.string.personalization_popups_gender_female),
        sendFailed = getString(R.string.personalization_popups_send_failed)
    )
}

/**
 * Слоты попапов на процесс (все инстансы SDK): модалка одна — общая со старыми попапами,
 * баннер — по одному сверху и снизу.
 */
internal object PopupV3Slots {

    private var modal: Any? = null
    private val banners = HashMap<PopupV3.Placement, Any>()
    private val modalWaiters = ArrayList<() -> Unit>()

    /** Открыта модалка v3: старый попап ждёт её закрытия. */
    val isModalOpen: Boolean get() = modal != null

    fun reason(popup: PopupV3): String? = when {
        popup.isModal && modal != null -> "another modal is open"
        popup.isModal && oldPopupOpen() -> "an old popup is open"
        !popup.isModal && banners.containsKey(popup.placement) -> "a banner is already shown at ${popup.placement.name.lowercase()}"
        else -> null
    }

    fun take(popup: PopupV3, owner: Any) {
        if (popup.isModal) modal = owner else banners[popup.placement] = owner
    }

    fun release(owner: Any) {
        if (modal === owner) {
            modal = null
            val waiting = modalWaiters.toList()
            modalWaiters.clear()
            waiting.forEach { it() }
        }
        banners.entries.removeAll { it.value === owner }
    }

    /** Старый попап покажется после закрытия модалки v3. */
    fun whenModalClosed(task: () -> Unit) {
        if (modal == null) task() else modalWaiters += task
    }

    private fun oldPopupOpen(): Boolean {
        val activity = ForegroundActivity.get() as? FragmentActivity ?: return false
        val manager = activity.supportFragmentManager
        return listOf(ALERT_DIALOG_TAG, BOTTOM_DIALOG_TAG, TOP_DIALOG_TAG, FULL_SCREEN_DIALOG_TAG)
            .any { tag -> manager.findFragmentByTag(tag)?.isAdded == true }
    }

    /** Для тестов. */
    fun reset() {
        modal = null
        banners.clear()
        modalWaiters.clear()
    }
}

/**
 * Показ попапов v3 на устройстве: в активити на экране. Модалка, фуллскрин и баннер
 * с затемнением или блокировкой фона — своим окном ([PopupV3Dialog]); баннер без них —
 * вью поверх окна активити, касания вне него уходят в приложение.
 */
@OptIn(InternalPersonalizationV3Api::class)
internal class PopupV3Presenter(private val sdk: SDK, private val popups: PopupsV3) : PopupsV3Presenter {

    override fun canOpen(popup: PopupV3): String? = PopupV3Slots.reason(popup)

    override fun present(popup: PopupV3, variables: Map<String, String>, listener: PopupsV3Presenter.Listener) {
        PopupV3Session(sdk, popups, popup, variables, listener).start()
    }
}

/**
 * Один показ попапа: окно на текущей активити. Если активити пересоздаётся (поворот) или
 * закрывается, попап снимается без события и встаёт на следующую активити с тем же
 * состоянием элементов.
 */
@OptIn(InternalPersonalizationV3Api::class)
internal class PopupV3Session(
    private val sdk: SDK,
    private val popups: PopupsV3,
    private val popup: PopupV3,
    private val variables: Map<String, String>,
    private val listener: PopupsV3Presenter.Listener
) : ForegroundActivity.Listener {

    private val design: PopupV3Design = popup.design
    private val state = PopupV3State()
    private var activity: WeakReference<Activity>? = null
    private var dialog: PopupV3Dialog? = null
    private var root: PopupV3RootView? = null
    private var shown = false
    private var closing = false

    private val transport: PopupsV3Transport get() = popups.transport

    private val actions by lazy {
        PopupV3ActionHandler(
            sdk = sdk,
            popupId = popup.id,
            transport = transport,
            appHost = { popups.appHost },
            linkHandler = { popups.linkHandler },
            productHandler = { popups.productHandler },
            texts = { activity?.get()?.let(::popupTexts) ?: PopupV3Texts() },
            context = { activity?.get() ?: sdk.context },
            onClose = { close(sendEvent = true) },
            onNavigate = { close(sendEvent = false) }
        )
    }

    /** Модалка, фуллскрин или баннер, который держит фон, — в своём окне. */
    private val blocking: Boolean get() {
        if (popup.isModal) return true
        val frame = design.frame.resolve(0.0)
        return frame.overlayEnabled == true || frame.lockBackgroundScroll
    }

    fun start() {
        PopupV3Slots.take(popup, this)
        ForegroundActivity.addListener(this)
        val current = ForegroundActivity.get()
        if (current == null) {
            SDK.debug("popups v3: popup ${popup.id} waits for an activity")
            return
        }
        attach(current)
    }

    override fun onActivityResumed(activity: Activity) {
        if (closing || this.activity?.get() != null) return
        attach(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (this.activity?.get() !== activity) return
        detach()
    }

    private fun attach(host: Activity) {
        if (host.isFinishing) return
        val view = try {
            PopupV3RootView(host, popup, design, variables, state, actions, sdk, blocking, onOutside = { close(sendEvent = true) })
        } catch (throwable: Throwable) {
            fail("render failed: ${throwable.message}")
            return
        }
        try {
            if (blocking) {
                dialog = PopupV3Dialog(host, view, lightBars(host) && view.overlayAlpha == 0f) { close(sendEvent = true) }.also { it.show() }
            } else {
                val decor = host.window?.decorView as? ViewGroup ?: throw IllegalStateException("no window")
                decor.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        } catch (throwable: Throwable) {
            dialog = null
            (view.parent as? ViewGroup)?.removeView(view)
            if (!shown) fail("no window: ${throwable.message}") else SDK.debug("popups v3: popup ${popup.id} could not come back: ${throwable.message}")
            return
        }
        activity = WeakReference(host)
        root = view
        if (!shown) {
            shown = true
            view.animateIn()
            listener.onShown()
        }
    }

    /** Снять окно без события и без закрытия показа. */
    private fun detach() {
        dialog?.let { runCatching { it.dismiss() } }
        dialog = null
        root?.let { (it.parent as? ViewGroup)?.removeView(it) }
        root = null
        activity = null
    }

    fun close(sendEvent: Boolean) {
        if (closing) return
        closing = true
        if (sendEvent) transport.event(popup.id, EVENT_CLOSE)
        val view = root
        val finish = {
            detach()
            ForegroundActivity.removeListener(this)
            PopupV3Slots.release(this)
            listener.onClosed()
        }
        if (view == null) finish() else view.animateOut(finish)
    }

    private fun fail(reason: String) {
        closing = true
        ForegroundActivity.removeListener(this)
        PopupV3Slots.release(this)
        listener.onFailed(reason)
    }

    private fun lightBars(host: Activity): Boolean {
        val window = host.window ?: return false
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
    }

    companion object {
        const val EVENT_CLOSE = "close"
    }
}

/**
 * Окно модалки: во весь экран, прозрачное, edge-to-edge, клавиатура сжимает доступную высоту
 * (IME-инсеты); «назад» закрывает попап с событием.
 */
internal class PopupV3Dialog(
    context: Context,
    private val root: View,
    private val lightBars: Boolean,
    private val onBack: () -> Unit
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCanceledOnTouchOutside(false)
        val window = window
        if (window != null) {
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
        setContentView(root)
    }

    @Deprecated("Dialog still routes back here, also through OnBackInvokedDispatcher on API 33+")
    override fun onBackPressed() = onBack()
}

/**
 * Корень попапа: затемнение (если есть) и рамка на своём месте. Рамка считает размер по
 * `frameStyle` и breakpoints на текущей ширине экрана; её высота не больше доступной
 * (экран без безопасных зон, клавиатуры и полей макета), лишнее прокручивается внутри.
 */
@SuppressLint("ViewConstructor")
internal class PopupV3RootView(
    context: Context,
    private val popup: PopupV3,
    private val design: PopupV3Design,
    variables: Map<String, String>,
    state: PopupV3State,
    actions: PopupV3Actions,
    sdk: SDK?,
    private val blocking: Boolean,
    private val onOutside: () -> Unit
) : FrameLayout(context) {

    private val overlay: View?
    val frame: PopupV3FrameView
    val overlayAlpha: Float
    private var lastInsets: List<Int> = emptyList()
    private var frameX = 0
    private var frameY = 0
    private var anchoredBottom = false
    private var resolvedFor = -1.0
    private lateinit var resolved: PopupV3Frame.Resolved

    private val layoutWatcher = ViewTreeObserver.OnGlobalLayoutListener {
        if (insets() != lastInsets) requestLayout()
    }

    init {
        val metrics = resources.displayMetrics
        val probe = design.frame.resolve(metrics.widthPixels / metrics.density.toDouble())
        val overlayOn = when (popup.layout) {
            PopupV3.Layout.FULLSCREEN -> false
            PopupV3.Layout.MODAL -> probe.overlayEnabled ?: true
            PopupV3.Layout.BANNER -> probe.overlayEnabled ?: false
        }
        overlayAlpha = if (overlayOn) probe.overlayOpacity.toFloat() else 0f
        overlay = if (blocking) View(context).apply {
            setBackgroundColor(if (overlayOn) (probe.overlayColor and 0x00FFFFFF) or (0xFF shl 24) else Color.TRANSPARENT)
            alpha = overlayAlpha
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (overlayOn) setOnClickListener { tappedOutside() }
            addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else {
            null
        }
        if (popup.layout == PopupV3.Layout.FULLSCREEN) setBackgroundColor(probe.background)
        val render = PopupV3RenderContext(
            context = context,
            texts = popupTexts(context),
            variables = variables,
            state = state,
            actions = actions,
            sdk = sdk,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            frameBackground = probe.background
        )
        frame = PopupV3FrameView(context, swipeEdge = when {
            popup.layout != PopupV3.Layout.BANNER -> 0
            popup.placement == PopupV3.Placement.BOTTOM -> 1
            else -> -1
        }, onSwiped = { actions.close() })
        frame.setContent(PopupV3Renderer.content(design, render))
        addView(frame)
        isClickable = false
        clipChildren = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutWatcher)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(layoutWatcher)
        super.onDetachedFromWindow()
    }

    /** Тап мимо рамки по затемнению: открытая клавиатура только убирается, иначе попап закрывается. */
    private fun tappedOutside() {
        val field = frame.findFocus()
        val keyboard = ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        if (field != null && keyboard) {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(field.windowToken, 0)
            field.clearFocus()
            return
        }
        onOutside()
    }

    /** Безопасные зоны (лево, верх, право, низ) и клавиатура — px. */
    private fun insets(): List<Int> {
        // До API 23 инсеты окна не узнать — считаются нулевыми.
        val root = ViewCompat.getRootWindowInsets(this) ?: return listOf(0, 0, 0, 0, 0)
        val bars = root.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = root.getInsets(WindowInsetsCompat.Type.ime())
        return listOf(bars.left, bars.top, bars.right, bars.bottom, ime.bottom)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val insets = insets()
        lastInsets = insets
        val (left, top, right, barBottom, ime) = insets
        val bottom = max(barBottom, ime)
        val density = resources.displayMetrics.density.toDouble()
        val widthDp = width / density
        val heightDp = height / density
        if (widthDp != resolvedFor) {
            resolvedFor = widthDp
            resolved = design.frame.resolve(widthDp)
            frame.applyFrame(resolved, fullscreen = popup.layout == PopupV3.Layout.FULLSCREEN)
        }
        val margin = context.popupDp(LAYOUT_MARGIN)
        val safeWidth = (width - left - right).coerceAtLeast(0)
        when (popup.layout) {
            PopupV3.Layout.FULLSCREEN -> {
                val safeHeight = (height - top - bottom).coerceAtLeast(0)
                frame.configure(safeWidth, 0, safeHeight, safeHeight)
                frameX = left
                frameY = top
                anchoredBottom = false
            }
            PopupV3.Layout.MODAL -> {
                val frameWidth = minOf(context.popupDp(resolved.frameWidth(widthDp)), safeWidth)
                frame.configure(
                    frameWidth,
                    context.popupDp(resolved.frameMinHeight(heightDp)),
                    resolved.frameHeight(heightDp)?.let(context::popupDp),
                    (height - top - bottom - 2 * margin).coerceAtLeast(0)
                )
                frameX = left + (safeWidth - frameWidth) / 2
                frameY = top
                anchoredBottom = false
            }
            PopupV3.Layout.BANNER -> {
                val offsetX = context.popupDp(resolved.offsetX)
                val offsetY = context.popupDp(resolved.offsetY)
                val frameWidth = minOf(context.popupDp(resolved.frameWidth(widthDp)), (safeWidth - 2 * offsetX).coerceAtLeast(0))
                frame.configure(
                    frameWidth,
                    context.popupDp(resolved.frameMinHeight(heightDp)),
                    resolved.frameHeight(heightDp)?.let(context::popupDp),
                    (height - top - bottom - offsetY - margin).coerceAtLeast(0)
                )
                frameX = left + (safeWidth - frameWidth) / 2
                anchoredBottom = popup.placement == PopupV3.Placement.BOTTOM
                frameY = if (anchoredBottom) height - bottom - offsetY else top + offsetY
            }
        }
        // Настоящие ограничения в спеках: с теми же спеками вью не перемеряется, и клавиатура не
        // сжала бы рамку.
        frame.measure(
            MeasureSpec.makeMeasureSpec(frame.frameWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(frame.frameMaxHeight, MeasureSpec.AT_MOST)
        )
        if (popup.layout == PopupV3.Layout.MODAL) {
            val area = height - top - bottom
            frameY = top + ((area - frame.measuredHeight) / 2).coerceAtLeast(margin)
        }
        if (anchoredBottom) frameY -= frame.measuredHeight
        overlay?.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        overlay?.layout(0, 0, r - l, b - t)
        frame.layout(frameX, frameY, frameX + frame.measuredWidth, frameY + frame.measuredHeight)
    }

    fun animateIn() {
        overlay?.let { view ->
            view.alpha = 0f
            view.animate().alpha(overlayAlpha).setDuration(ANIMATION_MS).start()
        }
        frame.alpha = 0f
        frame.post {
            when (popup.layout) {
                PopupV3.Layout.BANNER -> {
                    frame.alpha = 1f
                    frame.translationY = if (anchoredBottom) (height - frame.top).toFloat() else -frame.bottom.toFloat()
                    frame.animate().translationY(0f).setDuration(ANIMATION_MS).setInterpolator(DecelerateInterpolator()).start()
                }
                else -> {
                    frame.scaleX = 0.96f
                    frame.scaleY = 0.96f
                    frame.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ANIMATION_MS).setInterpolator(DecelerateInterpolator()).start()
                }
            }
        }
    }

    fun animateOut(end: () -> Unit) {
        var finished = false
        val done = {
            if (!finished) {
                finished = true
                end()
            }
        }
        overlay?.animate()?.alpha(0f)?.setDuration(CLOSE_MS)?.start()
        val animator = frame.animate().setDuration(CLOSE_MS)
        when (popup.layout) {
            PopupV3.Layout.BANNER -> animator.translationY(if (anchoredBottom) (height - frame.top).toFloat() else -frame.bottom.toFloat())
            else -> animator.alpha(0f).scaleX(0.96f).scaleY(0.96f)
        }
        animator.withEndAction(done).start()
        // Окно могли снять раньше конца анимации: показ всё равно закрывается.
        postDelayed(done, CLOSE_MS + 100)
        if (!isAttachedToWindow) done()
    }

    companion object {
        const val LAYOUT_MARGIN = 16.0
        const val ANIMATION_MS = 220L
        const val CLOSE_MS = 180L
    }
}

/**
 * Рамка попапа: фон, граница, радиус и отступ из `frameStyle`; содержимое прокручивается
 * внутри, если выше доступной высоты, — рамка остаётся на месте. У баннера свайп к своему
 * краю закрывает попап, когда прокручивать дальше некуда.
 */
@SuppressLint("ViewConstructor")
internal class PopupV3FrameView(
    context: Context,
    /** -1 — баннер сверху (свайп вверх), 1 — снизу (свайп вниз), 0 — без свайпа. */
    private val swipeEdge: Int,
    private val onSwiped: () -> Unit
) : FrameLayout(context) {

    private val scroll = ScrollView(context).apply {
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        isVerticalScrollBarEnabled = true
    }
    private var widthPx = 0
    private var minHeightPx = 0
    private var fixedHeightPx: Int? = null
    private var maxHeightPx = MAX_HEIGHT
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var dragging = false
    private var velocity: VelocityTracker? = null

    init {
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Касания внутри рамки не уходят затемнению и приложению.
        isClickable = true
    }

    fun setContent(content: View) {
        scroll.removeAllViews()
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun applyFrame(frame: PopupV3Frame.Resolved, fullscreen: Boolean) {
        val border = if (fullscreen) 0.0 else frame.borderWidth
        val radius = if (fullscreen) 0.0 else frame.radius
        background = popupBackground(context, frame.background, radius, border, frame.borderColor)
        val inset = context.popupDp(frame.padding) + if (border > 0) context.popupDp(border).coerceAtLeast(1) else 0
        setPadding(inset, inset, inset, inset)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) clipToOutline = radius > 0
    }

    val frameWidth: Int get() = widthPx
    val frameMaxHeight: Int get() = maxHeightPx

    fun configure(width: Int, minHeight: Int, fixedHeight: Int?, maxHeight: Int) {
        if (width == widthPx && minHeight == minHeightPx && fixedHeight == fixedHeightPx && maxHeight == maxHeightPx) return
        widthPx = width
        minHeightPx = minHeight
        fixedHeightPx = fixedHeight
        maxHeightPx = maxHeight
        forceLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val inner = (widthPx - paddingLeft - paddingRight).coerceAtLeast(0)
        val padding = paddingTop + paddingBottom
        scroll.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val content = (scroll.getChildAt(0)?.measuredHeight ?: 0) + padding
        val wanted = (fixedHeightPx ?: content).coerceAtLeast(minHeightPx)
        val height = wanted.coerceAtMost(maxHeightPx).coerceAtLeast(padding)
        scroll.measure(
            MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((height - padding).coerceAtLeast(0), MeasureSpec.EXACTLY)
        )
        setMeasuredDimension(widthPx, height)
    }

    /** Свайп к краю, когда содержимое дальше в эту сторону не прокручивается. */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (swipeEdge == 0) return super.onInterceptTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.rawY
                dragging = false
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                val dy = event.rawY - downY
                val towardEdge = if (swipeEdge < 0) dy < -touchSlop else dy > touchSlop
                val canScroll = scroll.canScrollVertically(if (swipeEdge < 0) 1 else -1)
                if (towardEdge && !canScroll) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        velocity?.addMovement(event)
        val dy = event.rawY - downY
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> translationY = if (swipeEdge < 0) dy.coerceAtMost(0f) else dy.coerceAtLeast(0f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                val tracker = velocity
                tracker?.computeCurrentVelocity(1000)
                val speed = (tracker?.yVelocity ?: 0f) * swipeEdge
                velocity?.recycle()
                velocity = null
                if (abs(translationY) > height / 3f || speed > FLING_DP * resources.displayMetrics.density) {
                    onSwiped()
                } else {
                    animate().translationY(0f).setDuration(150).start()
                }
            }
        }
        return true
    }

    private companion object {
        const val FLING_DP = 800f
        /** Без предела: верхняя граница размера в `MeasureSpec`. */
        const val MAX_HEIGHT = (1 shl 30) - 1
    }
}

/**
 * Действия элементов на устройстве: события попапа, ссылки (сначала обработчик хоста),
 * буфер, подписка и сегмент, профиль, отзыв опроса через API v3 опросов, клик по товару.
 */
@OptIn(InternalPersonalizationV3Api::class)
internal class PopupV3ActionHandler(
    private val sdk: SDK,
    private val popupId: Int,
    private val transport: PopupsV3Transport,
    private val appHost: () -> String,
    private val linkHandler: () -> ((String) -> Boolean)?,
    private val productHandler: () -> ((PersonalizationProduct) -> Boolean)?,
    private val texts: () -> PopupV3Texts,
    private val context: () -> Context,
    private val onClose: () -> Unit,
    /** Хост открыл ссылку или товар у себя: попап уходит, как со страницы web при переходе, — без `close`. */
    private val onNavigate: () -> Unit = {}
) : PopupV3Actions {

    /** Транспорт опросов; в тестах — подмена. */
    internal var reviews: com.personalization.ui.widgets.StoriesPollsTransport? = null

    override fun close() = onClose()

    override fun click() = transport.event(popupId, EVENT_CLICK)

    /**
     * Ссылка попапа (кнопка, ссылка элемента, «применить» промокода, товар): сначала обработчик
     * хоста — открыл сам, попап уходит без `close`, как после товара; нет обработчика, он вернул
     * `false` или упал — системно (`ACTION_VIEW`), попап остаётся.
     */
    override fun openLink(url: String?) {
        val link = PopupV3Links.safe(url) ?: return
        val handler = linkHandler()
        if (handler != null) {
            val handled = try {
                handler(link)
            } catch (throwable: Throwable) {
                SDK.error("popups v3: link handler failed on $link", throwable)
                false
            }
            if (handled) {
                onNavigate()
                return
            }
        }
        val host = context()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        if (host !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            host.startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            SDK.error("popups v3: no app can open $link")
        } catch (exception: RuntimeException) {
            SDK.error("popups v3: cannot open $link", exception)
        }
    }

    override fun copy(text: String) {
        val clipboard = context().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("promo", text))
    }

    /** Подписка email с флагами согласия, затем сегмент по did; события `subscribe` и `submit`. */
    override fun subscribe(spec: PopupV3Specs.Email, email: String, callback: (String?) -> Unit) {
        sdk.manageSubscription(email, null, flags(spec.consentFields), object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                spec.segmentId?.let { sdk.addToSegment(it, null, null) }
                transport.event(popupId, EVENT_SUBSCRIBE, CHANNEL_EMAIL)
                transport.event(popupId, EVENT_SUBMIT)
                MainThread.run { callback(null) }
            }

            override fun onError(code: Int, msg: String?) {
                MainThread.run { callback(errorText(msg)) }
            }
        })
    }

    /**
     * Профиль (стандартные поля и свои свойства), затем флаги согласия, если галочка
     * отмечена, затем сегмент по did; событие `submit`.
     */
    override fun saveProfile(spec: PopupV3Specs.Form, values: Map<String, String>, consent: Boolean, callback: (String?) -> Unit) {
        val finish = {
            spec.segmentId?.let { sdk.addToSegment(it, null, null) }
            transport.event(popupId, EVENT_SUBMIT)
            MainThread.run { callback(null) }
        }
        val fail = { message: String? -> MainThread.run { callback(errorText(message)) } }
        val consentStep = {
            if (consent && spec.consentFields.isNotEmpty()) {
                sdk.manageSubscription(values["email"], values["phone"], flags(spec.consentFields), object : OnApiCallbackListener() {
                    override fun onSuccess(response: JSONObject?) = finish()
                    override fun onError(code: Int, msg: String?) = fail(msg)
                })
            } else {
                finish()
            }
        }
        if (values.isEmpty()) {
            consentStep()
            return
        }
        val params = ProfileParams.Builder().apply { values.forEach { (key, value) -> put(key, value) } }.build()
        sdk.profile(params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) = consentStep()
            override fun onError(code: Int, msg: String?) = fail(msg)
        })
    }

    /** Отзыв: `POST /api/v3/polls/{type}/reviews` на хосте ЛК; событие `submit`. */
    override fun review(spec: PopupV3Specs.Survey, rate: Int, comment: String?, callback: (String?) -> Unit) {
        val transport = reviews ?: StoriesPollsApi(sdk, appHost())
        transport.review(spec.type, spec.reviewBody(rate, comment)) { result ->
            when (result) {
                is StoriesPollsResult.Success -> {
                    this.transport.event(popupId, EVENT_SUBMIT)
                    callback(null)
                }
                is StoriesPollsResult.Failure -> callback(result.message?.takeIf { it.isNotBlank() } ?: texts().sendFailed)
            }
        }
    }

    /**
     * Тап по товару ленты: `click`, затем обработчик хоста — открыл сам, попап уходит без `close`;
     * нет обработчика, он вернул `false` или упал — ссылка товара (`deeplink`, иначе `url`)
     * обычным путём ссылок ([openLink]).
     */
    override fun product(product: PersonalizationProduct) {
        click()
        val handler = productHandler()
        if (handler != null) {
            val handled = try {
                handler(product)
            } catch (throwable: Throwable) {
                SDK.error("popups v3: product handler failed on ${product.id}", throwable)
                false
            }
            if (handled) {
                onNavigate()
                return
            }
        }
        openLink(product.deeplink?.takeIf { it.isNotBlank() } ?: product.url)
    }

    private fun flags(fields: List<String>): HashMap<String, Boolean> = HashMap(fields.associateWith { true })

    /** Сообщение сервера (`message` тела ошибки), иначе общий текст. */
    private fun errorText(raw: String?): String {
        val message = raw?.let { body ->
            runCatching { JSONObject(body).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
        }
        return message ?: texts().sendFailed
    }

    companion object {
        const val EVENT_CLICK = "click"
        const val EVENT_SUBMIT = "submit"
        const val EVENT_SUBSCRIBE = "subscribe"
        const val CHANNEL_EMAIL = "email"
    }
}
