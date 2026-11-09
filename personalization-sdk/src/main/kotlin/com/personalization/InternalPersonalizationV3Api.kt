package com.personalization

/**
 * Маркер v3, который не является публичным API SDK: попапы нового конструктора
 * ([com.personalization.popups.PopupsV3], `sdk.popups`) и сторис на API v3 — `apiV3Domain`,
 * открытие кампании по ссылке «Поделиться», сама кнопка и её ссылка, опросы на слайдах.
 *
 * Код v3 работает в наших демо, но хост не подхватит его случайно: кто знает, что делает,
 * подключает v3 явно — одной строкой на модуль, как демо-приложение:
 *
 * ```groovy
 * kotlinOptions {
 *     freeCompilerArgs += ['-opt-in=com.personalization.InternalPersonalizationV3Api']
 * }
 * ```
 *
 * Маркер снимается, только когда v3 станет публичным API.
 *
 * Java opt-in не проверяет, поэтому входы в v3 (`SDK.popups`, члены сторис для v3) помечены
 * `@JvmSynthetic` и из Java не видны.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "v3 (попапы нового конструктора, сторис на API v3) пока не публичный API. Подключите его явно: " +
        "@OptIn(InternalPersonalizationV3Api::class) или " +
        "-opt-in=com.personalization.InternalPersonalizationV3Api.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
annotation class InternalPersonalizationV3Api
