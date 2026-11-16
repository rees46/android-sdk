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
- `error` вместо `request` — метод не должен отправлять запрос и должен вернуть эту ошибку: `{ "type": "invalidArgument" }`.

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
