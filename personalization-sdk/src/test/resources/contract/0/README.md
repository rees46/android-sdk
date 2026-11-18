# Фикстуры

Общие тестовые данные для юнит-тестов всех SDK. Один файл — один неймспейс или механика ядра. SDK копируют файлы в свои тесты целиком и проверяют по ним свои реализации; при обновлении копируется новая версия файла.

## Формат

```json
{
  "contract": "0",
  "cases": [ … ]
}
```

`contract` — версия контракта, по которой написан файл.

### Вызов → запрос

```json
{
  "name": "productView с источником",
  "call": { "namespace": "tracking", "method": "productView",
            "args": { "itemId": "37", "source": { "type": "dynamic", "code": "block-1" } } },
  "request": { "method": "POST", "path": "push",
               "body": { "event": "view", "items": [ { "id": "37" } ],
                         "recommended_by": "dynamic", "recommended_code": "block-1" } }
}
```

- `args` — аргументы метода по именам контракта.
- `request.path` — путь без хоста и без ведущей косой черты.
- `request.query` для `GET`, `request.body` для `POST`. Сравниваются только перечисленные поля; служебные `shop_id`, `did`, `sid`, `stream`, `segment`, `time` добавляет ядро, и они проверяются отдельными тестами ядра.
- Исключение — серверные методы с секретом магазина ([решение 0006](../decisions/0006-server-methods.md)): `request.commonFields` перечисляет служебные поля, которые в запросе есть, — `[ "shop_id" ]`; остальных (`did`, `sid`, `seance`, `segment`, `stream`) в запросе нет. Служебное поле в `query` — поле метода: так уходит `did`, который передал вызывающий код.
- `error` вместо `request` — метод не должен отправлять запрос и должен вернуть эту ошибку: `{ "type": "invalidArgument" }`.
- `"request": null` с `"expect": { "result": null }` — метод не отправляет запроса и завершается успехом: так `tracking.storyView` пропускает виртуальный слайд.

### Ответ → результат

```json
{
  "name": "402 на /init — отказ магазина",
  "response": { "status": 402, "body": { "status": "error", "message": "Restricted Mode" } },
  "expect": { "state": "refused" }
}
```

- `response.status: null` — ответа нет (нет сети или таймаут).
- `expect` — результат метода, `error` (`SdkError`) или состояние ядра.
- `expect.result` — значение метода по именам полей контракта; `null` — успех без значения.

### Что ещё бывает в файлах неймспейсов

- `platform` (`android`, `ios`) — случай только этой платформы, другая его пропускает. Так в `notifications.json` расходятся `setPushToken`: платформы шлют токен с разными `platform` и `push_provider` и отказывают в чужом провайдере.
- Хоста в формате нет. Путь под `api/v3/` идёт на `appDomain` (опросы `reviews`, агент поиска, `predict`), остальные — на `apiDomain`, как в таблице методов неймспейса.
- Поле запроса со списком уходит списком Rails: `promo_codes[]=A&promo_codes[]=B`.
- В `call.args` модель запроса — объект по именам полей контракта, значение набора — по имени в коде (`salesRate`).
- Сравнение `expect.result`:
  - каталог и клиент (`products`, `collections`, `recommendations`, `search`, `profile`, `subscriptions`, `segments`) — объект целиком; поле без значения считается отсутствующим с обеих сторон; так же сравниваются поля запроса в `query` и `body`;
  - остальные файлы — объект перечисляет только поля, которые случай проверяет; `null` — поле без значения;
  - `profile.getOrders` — тоже только перечисленные поля. Результат — выпущенная модель `Order` (решение 0006): товар в `items[].item` платформы читают в разные модели с разными именами полей, а поле, которого нет в ответе, iOS заполняет значением по умолчанию. Случай перечисляет поля, которые обе платформы называют и читают одинаково;
  - `notifications.getHistory` — у выпущенных моделей ответа нет общих имён полей: Android `GetAllNotificationsResponse` (`payload.messages`), iOS `UserPayloadResponse` (`notifications`). Поэтому успешный ответ проверяют два случая с `platform`, а запросы, отказы и ошибки — общие.
- В `tracking.json` `response` — ответ сервера на событие, которое ушло из очереди; `expect.result: null` — сервер событие принял, и колбэк выпущенной формы метода слышит успех.
- В `stories.json` элемент слайда — объект с `type` (`button`, `product`, `products`, `textBlock`) и его полями.
- События баннеров `tracking.bannerView` и `bannerClick` пока лежат в `sliders.json`, рядом с `sliders.get`.

Android и iOS проверяют по этим файлам неймспейсы мостов (ветки `feat/DEV-4686-namespace-base`); копии в SDK совпадают с файлами здесь побайтно.
