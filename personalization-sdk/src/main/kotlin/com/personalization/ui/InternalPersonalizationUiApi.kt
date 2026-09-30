package com.personalization.ui

/**
 * Маркер UI-кита дизайн-системы, который пока не является публичным API SDK.
 *
 * Кит ещё собирается и ничем в SDK не используется, поэтому до релиза вся его
 * поверхность (тема и компоненты) закрыта этим маркером: хост не подхватит её
 * случайно, а кто знает, что делает, подключает кит напрямую — одной строкой
 * на модуль, как это делает демо-приложение:
 *
 * ```groovy
 * kotlinOptions {
 *     freeCompilerArgs += ['-opt-in=com.personalization.ui.InternalPersonalizationUiApi']
 * }
 * ```
 *
 * Перед релизом маркер снимается, и компоненты становятся обычным публичным API.
 * Ресурсы (`@color/personalization_*`, атрибуты темы) маркером не закрываются:
 * это ресурсы библиотеки, и без `public.xml` они видны хосту как есть.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "UI-кит дизайн-системы пока не публичный API. Подключите его явно: " +
        "@OptIn(InternalPersonalizationUiApi::class) или " +
        "-opt-in=com.personalization.ui.InternalPersonalizationUiApi.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
annotation class InternalPersonalizationUiApi
