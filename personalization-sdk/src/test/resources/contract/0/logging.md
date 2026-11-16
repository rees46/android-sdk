# Логи и приватность

Что SDK пишет в лог, как приложение получает эти сообщения и что SDK никогда не пишет. Решение: [0005](../decisions/0005-sdk-logs.md). Отправка логов на сервер — отдельная задача DEV-4730, здесь только локальные логи.

## Уровни

`LogLevel`: `debug`, `info`, `warning`, `error`, `none`.

- По умолчанию — `warning`: пишутся `warning` и `error`.
- `Rees46Config.logLevel = debug` включает подробный лог для разработки.
- `none` выключает лог SDK полностью.

| Уровень | Что пишется |
|---|---|
| `debug` | запросы (метод и путь), смены состояния, решения очереди |
| `info` | инициализация завершена, сессия сменилась, токен отправлен |
| `warning` | повтор `/init`, событие выброшено очередью, ответ не разобрался |
| `error` | отказ магазина, `4xx` на запрос SDK, исключение, пойманное внутри SDK |

## Что не пишется никогда

На любом уровне, в том числе `debug`:
- данные запроса: query, тело, значения `customFields`;
- тело ответа, в том числе тело ошибки: сервер часто повторяет в нём присланные данные;
- телефон, email, имя, рекламный ID, пуш-токен, содержимое пуша;
- ответ `/init` целиком.

Запрос пишется только методом и путём: `POST push/custom`, `404: GET products/get`.

## Обработчик приложения

`Rees46.setLogHandler(handler)` ([корень](../contract/root.md)) — приложение получает каждое сообщение, прошедшее `logLevel`, и может переслать его в свой Crashlytics или Sentry. Сообщение — `SdkLogMessage`:

| Поле | Что |
|---|---|
| `level` | уровень |
| `code` | стабильный код события: `init_refused`, `init_retry`, `queue_dropped`, `decoding_failed`, `callback_failed` |
| `message` | текст по-английски, без персональных данных |
| `shopId` | магазин |
| `context` | словарь без персональных данных: путь запроса, HTTP-код, число попыток |

Обработчик вызывается в фоне; исключение из него ловится. Тот же формат получит эндпоинт логов, когда он появится (DEV-4730).

### Коды

Код — часть контракта: одинаковый на Android и iOS, мосты передают его как есть. Новый код добавляется в эту таблицу, прежде чем появится в SDK.

| Код | Уровень | Когда |
|---|---|---|
| `request` | `debug` | запрос получил ответ; `context`: метод, путь, HTTP-код |
| `request_failed` | `error` на `4xx`, иначе `warning` | ответ не `2xx` или ответа нет; `context`: метод, путь, HTTP-код |
| `request_build_failed` | `error` | запрос не удалось собрать |
| `request_cancelled` | `debug` | запрос отменён (`release()`); `context`: метод, путь |
| `invalid_argument` | `warning` | вызов отклонён до отправки; `context.reason`, например `invalid_json` |
| `invalid_config` | `warning` | значение конфига не принято, SDK продолжает с прежним; `context.reason`: `already_initialized`, `device_id_kept`, `url_session_delegate`, … |
| `decoding_failed` | `warning` | ответ не разобрался |
| `callback_failed` | `error` | колбэк приложения бросил исключение |
| `init_succeeded` | `info` | `/init` прошёл |
| `init_saved_answer` | `warning` | SDK работает на сохранённом ответе `/init` |
| `init_retry` | `warning` | `/init` будет повторён |
| `init_failed` | `error` на `4xx`, иначе `warning` | `/init` не прошёл |
| `init_skipped` | `info` | `/init` не отправлен намеренно, например на устройстве предзапусковой проверки Google Play |
| `init_refused` | `error` | магазин отказал, состояние `refused` |
| `device_id_reset` | `info` | `did` забыт (`needReInitialization`, `deleteUserCredentials`) |
| `storage_failed` | `error`, если не удалось стереть данные покупателя; иначе `warning` | чтение или запись на диск не удались; `context.op` |
| `queue_dropped` | `warning` | событие выброшено очередью; `context.reason`: `expired`, `overflow`, `attempts`, `rejected`, `service_refused`, `refused`, `delete_user_credentials`, `unreadable` |
| `push_received` | `debug` | пуш разобран и отдан магазину; повтор события того же сообщения — с `context.repeat = true` |
| `push_dropped` | `debug`, если пуш не от SDK; иначе `warning` | пуш не обработан; `context.reason`: `not_sdk_push`, `unknown_shop`, `ambiguous_shop`, `no_did` |
| `push_failed` | `error` | пуш не удалось обработать |
| `push_token_received` | `debug` | SDK получил токен от APNs, FCM или HMS |
| `push_permission` | `info` | ответ на запрос разрешения на уведомления (iOS) |
| `push_permission_failed` | `warning` | разрешение запросить не удалось (iOS) |
| `push_token_sent` | `info` | токен отправлен |
| `push_token_skipped` | `debug` | токен не отправлен по правилам; `context.reason`: `sent_recently`, `no_device_id`, `auto_send_off`, `push_only` (Android: экземпляр поднят только ради пуша) |
| `push_token_failed` | `warning` | отправка токена не удалась |
| `advertising_id_sent` | `debug` | рекламный идентификатор отправлен |
| `advertising_id_failed` | `warning` | рекламный идентификатор отправить не удалось |
| `popup_not_shown` | `debug`, если так задумано (`app_declined`, `auto_presentation_off`, `late_event`, `app_in_background`); иначе `warning` | попап не показан; `context.reason` |
| `core_flag_mismatch` | `warning` | экземпляр просит другое ядро, чем процесс |
| `not_implemented` | `error` | вызван метод, у которого нет реализации (протокол iOS) |

Сообщения старого кода без своего события и промежуточные шаги идут под кодом области: `sdk_log`, `ui`, `stories`, `stories_v3`, `popups_v3`, `push`. Отказ запроса без сети — `request_failed` с `context.reason = offline`. Сбой самого обработчика пишется только в системный лог: `log_handler_failed`.


## Приватность

- Рекламный идентификатор отправляется только с `sendAdvertisingId = true`; без него SDK его не читает.
- Согласие покупателя на трекинг (флаг, при котором SDK не отправляет события) — DEV-4328, правила здесь появятся вместе с ним.

## Отличия от выпущенного

- **Android:** флага уровня нет. С 2.48.0 запросы пишутся только методом и путём, без данных и тел ошибок.
- **iOS:** `enableLogs` ни на что не влияет; ответ `/init`, пуши и сообщения сервера печатаются всегда.
- **React Native:** каждый запрос пишется в консоль всегда.
- **Flutter:** логирует натив; свои сообщения у попапов v3.
- **Все:** обработчика логов нет.
