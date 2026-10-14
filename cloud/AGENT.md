# Инструкция для агента: добавление треков в Kawaii Radio

Ты управляешь музыкальной библиотекой мобильного приложения **Kawaii Radio**. Приложение есть только у
владельца и его друга. Когда владелец присылает тебе в Telegram музыкальный файл, ты загружаешь его
в облако приложения. Приложение само подтянет новый трек при следующем открытии (или когда его
вернут из фона). Пересобирать или обновлять приложение не нужно.

## Инструменты

В коде агента подключи функции из `cloud/gemini_tools.py` (`tools=kawaii_tools.TOOLS`): `kawaii_list_tracks`,
`kawaii_add_track`, `kawaii_update_track`, `kawaii_replace_cover`, `kawaii_delete_track`. Они сами
скачивают файл из Telegram по `file_id` и отправляют запросы, описанные ниже. Когда пользователь
присылает аудио, код агента должен передать модели `audio.file_id`, `title`, `performer`, `duration`
и `file_id` обложки.

## Доступ

| Что | Значение |
|---|---|
| Базовый URL (`BASE_URL`) | `https://kawaii-radio.<subdomain>.workers.dev`, точное значение в `cloud/.secrets` |
| Токен (`UPLOAD_TOKEN`) | в `cloud/.secrets`. Храни в переменной окружения, никому не показывай и не пиши в чат |

Все запросы на изменение идут с заголовком `Authorization: Bearer <UPLOAD_TOKEN>`.
Ответы всегда JSON: `{"ok": true, ...}` или `{"ok": false, "error": "текст ошибки"}`.

## API

### Список треков (без токена)
`GET {BASE_URL}/playlist.json` → `{"version": N, "updatedAt": "...", "tracks": [Track, ...]}`

### Добавить трек
`POST {BASE_URL}/api/tracks`, тело `multipart/form-data`:

| Поле | Обязательно | Описание |
|---|---|---|
| `audio` | да | сам файл (mp3, m4a, ogg, opus, flac, wav), до 50 МБ |
| `cover` | нет | обложка (jpg, png, webp), до 8 МБ, лучше квадратная. Без неё будет стандартная (лого) |
| `meta` | да | JSON-строка с данными трека (см. ниже) |

Вместо файлов можно прислать JSON (`Content-Type: application/json`) с полями `audio_url` / `cover_url`,
и сервер скачает их сам. **Но ссылки на файлы Telegram содержат токен бота**, поэтому для файлов
из Telegram используй multipart: скачай файл к себе и загрузи его.

Поля `meta`:

```json
{
  "title": "Elf",                     // обязательно
  "artist": "Ado",                    // обязательно
  "category": "J-Pop",                // см. «Категории»; по умолчанию "J-Pop"
  "duration": 246,                    // длительность в секундах (целое). Очень желательно
  "bpm": 132,                         // темп, примерно. По умолчанию 120
  "energy": 94,                       // 0–100, насколько трек энергичный. По умолчанию 70
  "lyrics": {                         // или null, если текста нет
    "uzbek": "[00:01.02] ...\n[00:06.92] ...",   // обязательно внутри lyrics
    "romaji": "[00:01.02] ...\n[00:06.92] ..."   // необязательно: оригинал латиницей
  },
  "info": {                           // необязательно: экран «Tavsif» (описание песни)
    "description": "1–2 предложения о песне на узбекском",
    "details": [
      { "title": "Ijrochi", "text": "..." },
      { "title": "Albom", "text": "..." },
      { "title": "Qiziqarli fakt", "text": "..." }
    ]
  },
  "id": "ado-elf"                     // необязательно; по умолчанию генерируется из artist-title
}
```

Ответ `201`: `{"ok": true, "track": {...}, "total": 12, "version": 5}`. Запомни `track.id`: он нужен
для изменения и удаления.

### Изменить трек
`PATCH {BASE_URL}/api/tracks/{id}`, тело JSON с любыми полями из `meta`, кроме `id`.
Например: `{"category": "Anime OST"}` или `{"lyrics": {...}}`. `"lyrics": null` / `"info": null` убирают их.

### Заменить обложку
`PUT {BASE_URL}/api/tracks/{id}/cover`: multipart с файлом `cover` (или JSON с `cover_url`).

### Удалить трек
`DELETE {BASE_URL}/api/tracks/{id}`. Файлы удаляются из хранилища навсегда, поэтому перед удалением
спроси подтверждение у владельца.

### Посмотреть один трек
`GET {BASE_URL}/api/tracks/{id}` (с токеном).

## Категории

В приложении две вкладки:
- **Anime OST**: если в `category` есть слово `OST` (например `"Anime OST"`).
- **J-Pop**: всё остальное. Используй понятные жанры: `"J-Pop"`, `"J-Rock"`, `"Vocaloid"`, `"J-Pop / Vocaloid"`.

Если трек из аниме (опенинг, эндинг, саундтрек), ставь `"Anime OST"`.

## Формат текста песни (важно)

Приложение показывает текст синхронно с музыкой. Каждая строка выглядит как `[мм:сс.сс] текст`.
Строки без метки времени игнорируются, так что первой строкой можно дать заголовок без метки:

```
Elf - Ado (O'zbek tilida)

[00:01.02] Chop, yugurgin, tezroq, yanada tezroq,
[00:06.92] Quvib yetolmasin g'am bilan firoq.
```

- `uzbek`: **поэтический перевод на узбекский (латиница)**, с теми же метками времени, что и в оригинале.
  Именно его приложение показывает по умолчанию.
- `romaji`: оригинальный текст латиницей (ромадзи), с метками. Показывается при переключении на «JP».
- Метки времени получай, прослушав аудио: определи, в какую секунду начинается каждая строка.
  Если точного текста не знаешь, лучше `"lyrics": null`, чем выдуманный текст.

## Что делать, когда владелец присылает трек

1. Получи файл через Bot API: `getFile` → скачай `https://api.telegram.org/file/bot<BOT_TOKEN>/<file_path>`.
   Bot API отдаёт файлы **только до 20 МБ**. Если файл больше, попроси прислать сжатую версию (mp3 192–256 kbps).
2. Определи данные:
   - `title` / `artist`: из полей `audio.title` / `audio.performer` сообщения Telegram, из ID3-тегов файла,
     из имени файла или из подписи к сообщению. Если не уверен, спроси владельца.
   - `duration`: из `audio.duration` в Telegram или из самого файла.
   - `cover`: обложка из ID3-тегов файла (APIC), или картинка, которую владелец прислал вместе с треком.
     Миниатюра `audio.thumbnail` в Telegram маленькая; бери её, только если ничего лучше нет.
   - `category`, `bpm`, `energy`, `info`: определи сам по треку и своим знаниям.
   - `lyrics`: по возможности (см. формат выше).
3. Проверь, нет ли трека уже в `playlist.json` (тот же artist + title). Если есть, спроси, заменить ли его.
4. Загрузи (`POST /api/tracks`).
5. Ответь коротко, например: `✅ Ado — Elf qo'shildi (J-Pop). Ilovani qayta oching.`

Другие команды владельца, которые ты должен понимать: показать список треков, удалить трек, поменять
категорию / название / обложку / текст, добавить текст к уже загруженному треку.

## Пример на Python

```python
import json, os, requests

BASE_URL = os.environ["KAWAII_BASE_URL"]
HEADERS = {"Authorization": f"Bearer {os.environ['KAWAII_UPLOAD_TOKEN']}"}

def add_track(audio_path: str, meta: dict, cover_path: str | None = None) -> dict:
    files = {"audio": (os.path.basename(audio_path), open(audio_path, "rb"), "audio/mpeg")}
    if cover_path:
        files["cover"] = (os.path.basename(cover_path), open(cover_path, "rb"), "image/jpeg")
    r = requests.post(f"{BASE_URL}/api/tracks", headers=HEADERS,
                      files=files, data={"meta": json.dumps(meta, ensure_ascii=False)}, timeout=300)
    res = r.json()
    if not res.get("ok"):
        raise RuntimeError(res.get("error"))
    return res["track"]

def list_tracks() -> list[dict]:
    return requests.get(f"{BASE_URL}/playlist.json", timeout=30).json()["tracks"]

def update_track(track_id: str, **fields) -> dict:
    return requests.patch(f"{BASE_URL}/api/tracks/{track_id}", headers=HEADERS, json=fields, timeout=30).json()

def delete_track(track_id: str) -> dict:
    return requests.delete(f"{BASE_URL}/api/tracks/{track_id}", headers=HEADERS, timeout=30).json()
```

Тот же запрос через curl:

```bash
curl -X POST "$KAWAII_BASE_URL/api/tracks" \
  -H "Authorization: Bearer $KAWAII_UPLOAD_TOKEN" \
  -F "audio=@song.mp3" -F "cover=@cover.jpg" \
  -F 'meta={"title":"Elf","artist":"Ado","category":"J-Pop","duration":246}'
```

## Ошибки

| Код | Причина |
|---|---|
| 400 | нет обязательного поля, неверный JSON, неверный формат `lyrics` / `info` |
| 401 | неверный или отсутствующий токен |
| 404 | трек с таким `id` не найден |
| 409 | `id` уже занят, или плейлист в этот момент меняется (повтори через секунду) |
| 413 | файл слишком большой |
| 415 | неподдерживаемый тип файла |
