package com.personalization

/**
 * How much the SDK writes to its log, from the most detailed to nothing.
 *
 * A message is written when its level is at least the configured one: the default [WARNING] writes
 * warnings and errors; [DEBUG] adds requests (method and path), state changes and decisions; [NONE]
 * turns the SDK's log off. Whatever the level, the log never carries request data, response bodies,
 * personal data, push payloads or the `/init` answer.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    NONE;

    internal fun allows(message: LogLevel): Boolean =
        this != NONE && message != NONE && message.ordinal >= ordinal
}
