"""
Kawaii Radio tools for a Gemini (google-genai) Telegram agent.

Drop this file into the agent's project and pass the functions as tools:

    from google import genai
    from google.genai import types
    import kawaii_tools as kt

    config = types.GenerateContentConfig(
        system_instruction=open("AGENT.md", encoding="utf-8").read(),
        tools=kt.TOOLS,          # automatic function calling: the SDK calls these for you
    )

Environment variables (values are in cloud/.secrets of the app repo, plus the bot's own token):
    KAWAII_BASE_URL       https://kawaii-radio.kawaii-radio-cloud.workers.dev
    KAWAII_UPLOAD_TOKEN   upload token
    TELEGRAM_BOT_TOKEN    token of the bot the agent runs as (to download files users send)

The model only sees Telegram `file_id`s, never file bytes: when a user sends audio, put the
message's `audio.file_id` (and title / performer / duration / thumbnail file_id) into the prompt
so the model can call `kawaii_add_track` with them.
"""

from __future__ import annotations

import json
import os

import requests

BASE_URL = os.environ.get("KAWAII_BASE_URL", "").rstrip("/")
UPLOAD_TOKEN = os.environ.get("KAWAII_UPLOAD_TOKEN", "")
BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")

_AUTH = {"Authorization": f"Bearer {UPLOAD_TOKEN}"}


def _tg_download(file_id: str) -> tuple[bytes, str]:
    """Downloads a file the user sent to the bot (Bot API limit: 20 MB)."""
    r = requests.get(f"https://api.telegram.org/bot{BOT_TOKEN}/getFile", params={"file_id": file_id}, timeout=30).json()
    if not r.get("ok"):
        raise RuntimeError(f"Telegram getFile failed: {r.get('description')} (files over 20 MB cannot be downloaded by bots)")
    path = r["result"]["file_path"]
    data = requests.get(f"https://api.telegram.org/file/bot{BOT_TOKEN}/{path}", timeout=300).content
    return data, os.path.basename(path)


def _result(r: requests.Response) -> dict:
    try:
        return r.json()
    except ValueError:
        return {"ok": False, "error": f"HTTP {r.status_code}: {r.text[:200]}"}


def kawaii_list_tracks() -> dict:
    """Returns every track that was added to the Kawaii Radio cloud (id, title, artist, category)."""
    pl = requests.get(f"{BASE_URL}/playlist.json", timeout=30).json()
    return {
        "total": len(pl["tracks"]),
        "tracks": [{"id": t["id"], "title": t["title"], "artist": t["artist"], "category": t["category"]} for t in pl["tracks"]],
    }


def kawaii_add_track(
    audio_file_id: str,
    title: str,
    artist: str,
    category: str = "J-Pop",
    duration: int = 0,
    bpm: int = 120,
    energy: int = 70,
    cover_file_id: str = "",
    lyrics_uzbek_lrc: str = "",
    lyrics_romaji_lrc: str = "",
    description_uz: str = "",
    details_json: str = "",
) -> dict:
    """Adds a song the user sent in Telegram to the Kawaii Radio app.

    Args:
        audio_file_id: Telegram file_id of the audio the user sent.
        title: Song title.
        artist: Performer.
        category: decides the radio station. Songs with vocals (J-Pop, J-Rock, Vocaloid, anime openings/endings) ->
            a genre like "J-Pop", "J-Rock", "Vocaloid", "Anime"; music without words (orchestra, piano, soundtrack
            covers) -> "OST Instrumental"; music from Riot Games (League of Legends, Valorant, Arcane) -> "Riot Games".
        duration: Length in seconds (from Telegram audio.duration).
        bpm: Approximate tempo.
        energy: 0-100, how energetic the song is.
        cover_file_id: Telegram file_id of a cover image (photo sent with the song, or audio.thumbnail). Empty for the default cover.
        lyrics_uzbek_lrc: Uzbek translation, one line per "[mm:ss.xx] text". Empty if unknown.
        lyrics_romaji_lrc: Original lyrics in romaji with the same timestamps. Empty if unknown.
        description_uz: 1-2 sentences about the song in Uzbek (shown on the "Tavsif" screen).
        details_json: JSON list of facts in Uzbek: [{"title": "Albom", "text": "..."}, ...]. Empty if none.
    """
    audio, audio_name = _tg_download(audio_file_id)
    files = {"audio": (audio_name or "track.mp3", audio)}
    if cover_file_id:
        cover, cover_name = _tg_download(cover_file_id)
        files["cover"] = (cover_name or "cover.jpg", cover)

    meta: dict = {"title": title, "artist": artist, "category": category, "bpm": bpm, "energy": energy}
    if duration:
        meta["duration"] = duration
    if lyrics_uzbek_lrc.strip():
        meta["lyrics"] = {"uzbek": lyrics_uzbek_lrc}
        if lyrics_romaji_lrc.strip():
            meta["lyrics"]["romaji"] = lyrics_romaji_lrc
    if description_uz.strip():
        details = json.loads(details_json) if details_json.strip() else []
        meta["info"] = {"description": description_uz, "details": details}

    r = requests.post(f"{BASE_URL}/api/tracks", headers=_AUTH, files=files,
                      data={"meta": json.dumps(meta, ensure_ascii=False)}, timeout=300)
    res = _result(r)
    if res.get("ok"):
        t = res["track"]
        return {"ok": True, "id": t["id"], "title": t["title"], "artist": t["artist"], "category": t["category"], "total": res["total"]}
    return res


def kawaii_update_track(track_id: str, fields_json: str) -> dict:
    """Changes an existing track.

    Args:
        track_id: id from kawaii_list_tracks.
        fields_json: JSON object with the fields to change, e.g. {"category": "OST Instrumental"},
            {"title": "..."}, {"lyrics": {"uzbek": "[00:01.00] ...", "romaji": "..."}},
            {"info": {"description": "...", "details": [{"title": "...", "text": "..."}]}}.
            Use null to remove lyrics or info.
    """
    r = requests.patch(f"{BASE_URL}/api/tracks/{track_id}", headers=_AUTH, json=json.loads(fields_json), timeout=30)
    return _result(r)


def kawaii_replace_cover(track_id: str, cover_file_id: str) -> dict:
    """Replaces the cover of a track with an image the user sent in Telegram."""
    data, name = _tg_download(cover_file_id)
    r = requests.put(f"{BASE_URL}/api/tracks/{track_id}/cover", headers=_AUTH, files={"cover": (name or "cover.jpg", data)}, timeout=60)
    return _result(r)


def kawaii_delete_track(track_id: str) -> dict:
    """Deletes a track and its files forever. Only call after the user explicitly confirmed."""
    r = requests.delete(f"{BASE_URL}/api/tracks/{track_id}", headers=_AUTH, timeout=30)
    return _result(r)


TOOLS = [kawaii_list_tracks, kawaii_add_track, kawaii_update_track, kawaii_replace_cover, kawaii_delete_track]
