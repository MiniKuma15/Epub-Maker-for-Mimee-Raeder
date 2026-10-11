import base64
import json
import hmac
import os
import tempfile
from enum import Enum
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel

app = FastAPI(
    title="EPUB Maker OCR Service",
    description="Selectable local-server and Google online OCR engines for Thai/English books.",
    version="0.2.0",
)


class Engine(str, Enum):
    paddleocr = "paddleocr"
    paddleocr_v4 = "paddleocr-v4"
    paddleocr_v5 = "paddleocr-v5"
    easyocr = "easyocr"
    paddleocr_vl = "paddleocr-vl"
    google_vision = "google-vision"
    gemini = "gemini"
    google_document_ai = "google-document-ai"
    qwen2_5_vl = "qwen2.5-vl"
    gpt_4o = "gpt-4o"
    claude_vision = "claude-vision"


class OcrResponse(BaseModel):
    engine: str
    text: str


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/engines")
def engines() -> dict[str, Any]:
    return {
        "engines": [
            {"id": "paddleocr-v4", "name": "PaddleOCR PP-OCRv4", "type": "self-hosted", "languages": ["th", "en"]},
            {"id": "paddleocr-v5", "name": "PaddleOCR PP-OCRv5", "type": "self-hosted", "languages": ["th", "en"]},
            {"id": "easyocr", "name": "EasyOCR", "type": "self-hosted", "languages": ["th", "en"]},
            {"id": "paddleocr-vl", "name": "PaddleOCR-VL", "type": "self-hosted", "languages": ["multilingual"]},
            {"id": "google-vision", "name": "Google Cloud Vision OCR", "type": "online", "languages": ["th", "en"]},
            {"id": "gemini", "name": "Google Gemini Vision", "type": "online", "languages": ["multilingual"]},
            {"id": "google-document-ai", "name": "Google Document AI OCR", "type": "online", "languages": ["th", "en"]},
            {"id": "qwen2.5-vl", "name": "Qwen2.5-VL Vision", "type": "online", "languages": ["multilingual"]},
            {"id": "gpt-4o", "name": "GPT-4o Vision", "type": "online", "languages": ["multilingual"]},
            {"id": "claude-vision", "name": "Claude Vision", "type": "online", "languages": ["multilingual"]},
        ]
    }


@app.post("/ocr", response_model=OcrResponse)
async def recognize(
    engine: Engine,
    file: UploadFile = File(...),
    authorization: str | None = Header(default=None),
) -> OcrResponse:
    expected_token = os.getenv("OCR_API_TOKEN", "").strip()
    if expected_token:
        supplied_token = (authorization or "").removeprefix("Bearer ").strip()
        if not hmac.compare_digest(supplied_token, expected_token):
            raise HTTPException(status_code=401, detail="Invalid or missing OCR API token")

    suffix = Path(file.filename or "upload.png").suffix or ".png"
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Uploaded file is empty")
    if len(data) > int(os.getenv("MAX_UPLOAD_BYTES", str(40 * 1024 * 1024))):
        raise HTTPException(status_code=413, detail="File exceeds upload limit")

    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(data)
        path = tmp.name
    try:
        text = await run_in_threadpool(_run_engine, engine, path, data)
        if not text.strip():
            raise HTTPException(status_code=422, detail="OCR did not recognize readable text")
        return OcrResponse(engine=engine.value, text=text.strip())
    except HTTPException:
        raise
    except ImportError as exc:
        raise HTTPException(status_code=503, detail=f"OCR dependency is not installed: {exc.name}") from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"OCR failed: {type(exc).__name__}: {exc}") from exc
    finally:
        try:
            os.unlink(path)
        except OSError:
            pass


def _run_engine(engine: Engine, path: str, image_bytes: bytes) -> str:
    if engine == Engine.easyocr:
        import easyocr
        return "\n".join(str(line) for line in _easy_reader().readtext(path, detail=0, paragraph=True))

    if engine in (Engine.paddleocr, Engine.paddleocr_v4, Engine.paddleocr_v5):
        version = "PP-OCRv4" if engine == Engine.paddleocr_v4 else "PP-OCRv5"
        reader = _paddle_reader(version)
        if hasattr(reader, "predict"):
            return _extract_paddle_text(reader.predict(path))
        return _extract_paddle_text(reader.ocr(path, cls=True))

    if engine == Engine.paddleocr_vl:
        from paddleocr import PaddleOCRVL
        return _extract_paddle_text(_paddle_vl_reader().predict(path))

    if engine == Engine.google_vision:
        return _google_vision(image_bytes)

    if engine == Engine.gemini:
        return _gemini_vision(image_bytes, Path(path).suffix.lower())

    if engine == Engine.google_document_ai:
        return _google_document_ai(image_bytes, Path(path).suffix.lower())

    if engine == Engine.qwen2_5_vl:
        return _openai_compatible_vision("QWEN_API_KEY", "QWEN_VL_MODEL", "qwen2.5-vl-72b-instruct",
            os.getenv("QWEN_BASE_URL", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1").strip(),
            image_bytes, Path(path).suffix.lower())

    if engine == Engine.gpt_4o:
        return _openai_compatible_vision("OPENAI_API_KEY", "OPENAI_VISION_MODEL", "gpt-4o",
            os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").strip(),
            image_bytes, Path(path).suffix.lower())

    if engine == Engine.claude_vision:
        return _claude_vision(image_bytes, Path(path).suffix.lower())

    raise ValueError(f"Unsupported engine: {engine}")


_easy_reader_instance = None
_paddle_reader_instances: dict[str, Any] = {}
_paddle_vl_reader_instance = None


def _easy_reader():
    global _easy_reader_instance
    if _easy_reader_instance is None:
        import easyocr
        _easy_reader_instance = easyocr.Reader(["th", "en"], gpu=os.getenv("OCR_GPU", "false").lower() == "true")
    return _easy_reader_instance


def _paddle_reader(version: str = "PP-OCRv5"):
    if version not in _paddle_reader_instances:
        from paddleocr import PaddleOCR
        _paddle_reader_instances[version] = PaddleOCR(lang="th", ocr_version=version)
    return _paddle_reader_instances[version]


def _paddle_vl_reader():
    global _paddle_vl_reader_instance
    if _paddle_vl_reader_instance is None:
        from paddleocr import PaddleOCRVL
        _paddle_vl_reader_instance = PaddleOCRVL()
    return _paddle_vl_reader_instance


def _google_vision(image_bytes: bytes) -> str:
    api_key = os.getenv("GOOGLE_CLOUD_VISION_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("Set GOOGLE_CLOUD_VISION_API_KEY on the OCR server")
    body = {
        "requests": [{
            "image": {"content": base64.b64encode(image_bytes).decode("ascii")},
            "features": [{"type": "DOCUMENT_TEXT_DETECTION"}],
            "imageContext": {"languageHints": ["th", "en"]},
        }]
    }
    response = _google_post_json(
        f"https://vision.googleapis.com/v1/images:annotate?key={api_key}", body
    )
    item = (response.get("responses") or [{}])[0]
    if item.get("error"):
        raise RuntimeError(item["error"].get("message", "Google Cloud Vision error"))
    return (item.get("fullTextAnnotation") or {}).get("text", "")


def _gemini_vision(image_bytes: bytes, suffix: str) -> str:
    api_key = os.getenv("GEMINI_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("Set GEMINI_API_KEY on the OCR server")
    model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash").strip()
    mime = _mime_type(suffix)
    prompt = (
        "Transcribe all visible text in this scanned book page exactly as printed. "
        "The page may contain Thai and English. Preserve paragraph and line order. "
        "Do not summarize, translate, correct spelling, or add commentary. "
        "Return only the recognized text."
    )
    body = {
        "contents": [{
            "parts": [
                {"text": prompt},
                {"inline_data": {"mime_type": mime, "data": base64.b64encode(image_bytes).decode("ascii")}},
            ]
        }],
        "generationConfig": {"temperature": 0, "maxOutputTokens": 8192},
    }
    response = _google_post_json(
        f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}",
        body,
    )
    candidates = response.get("candidates") or []
    if not candidates:
        raise RuntimeError("Gemini returned no OCR candidate")
    parts = candidates[0].get("content", {}).get("parts", [])
    return "\n".join(part.get("text", "") for part in parts if part.get("text"))


def _google_document_ai(image_bytes: bytes, suffix: str) -> str:
    project = os.getenv("GOOGLE_DOCUMENT_AI_PROJECT_ID", "").strip()
    location = os.getenv("GOOGLE_DOCUMENT_AI_LOCATION", "us").strip()
    processor = os.getenv("GOOGLE_DOCUMENT_AI_PROCESSOR_ID", "").strip()
    if not project or not processor:
        raise RuntimeError(
            "Set GOOGLE_DOCUMENT_AI_PROJECT_ID and GOOGLE_DOCUMENT_AI_PROCESSOR_ID on the OCR server"
        )
    from google.auth import default
    from google.auth.transport.requests import Request as GoogleAuthRequest

    credentials, _ = default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
    credentials.refresh(GoogleAuthRequest())
    mime = _mime_type(suffix)
    body = {
        "rawDocument": {
            "content": base64.b64encode(image_bytes).decode("ascii"),
            "mimeType": mime,
        },
        "processOptions": {"ocrConfig": {"languageHints": ["th", "en"]}},
    }
    endpoint = (
        f"https://{location}-documentai.googleapis.com/v1/projects/{project}/locations/"
        f"{location}/processors/{processor}:process"
    )
    response = _google_post_json(endpoint, body, headers={"Authorization": f"Bearer {credentials.token}"})
    return (response.get("document") or {}).get("text", "")


def _openai_compatible_vision(api_key_env: str, model_env: str, default_model: str,
                             base_url: str, image_bytes: bytes, suffix: str) -> str:
    api_key = os.getenv(api_key_env, "").strip()
    if not api_key:
        raise RuntimeError(f"Set {api_key_env} on the OCR server")
    data_url = f"data:{_mime_type(suffix)};base64,{base64.b64encode(image_bytes).decode('ascii')}"
    body = {
        "model": os.getenv(model_env, default_model).strip(),
        "temperature": 0,
        "max_tokens": 8192,
        "messages": [{"role": "user", "content": [
            {"type": "text", "text": (
                "Transcribe all visible text in this scanned book page exactly as printed. "
                "It may contain Thai and English. Preserve reading order, paragraphs and punctuation. "
                "Do not summarize, translate, rewrite, correct spelling, or add commentary. Return only the text."
            )},
            {"type": "image_url", "image_url": {"url": data_url, "detail": "high"}},
        ]}],
    }
    response = _google_post_json(base_url.rstrip("/") + "/chat/completions", body,
                                 headers={"Authorization": f"Bearer {api_key}"})
    choices = response.get("choices") or []
    if not choices:
        raise RuntimeError("Vision model returned no OCR result")
    content = (choices[0].get("message") or {}).get("content", "")
    if isinstance(content, list):
        return "\n".join(str(part.get("text", "")) for part in content
                           if isinstance(part, dict) and part.get("text"))
    return str(content)


def _claude_vision(image_bytes: bytes, suffix: str) -> str:
    api_key = os.getenv("ANTHROPIC_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("Set ANTHROPIC_API_KEY on the OCR server")
    body = {
        "model": os.getenv("CLAUDE_VISION_MODEL", "claude-3-5-sonnet-latest").strip(),
        "max_tokens": 8192,
        "temperature": 0,
        "messages": [{"role": "user", "content": [
            {"type": "text", "text": (
                "Transcribe all visible text in this scanned book page exactly as printed. "
                "It may contain Thai and English. Preserve reading order, paragraphs and punctuation. "
                "Do not summarize, translate, rewrite, correct spelling, or add commentary. Return only the text."
            )},
            {"type": "image", "source": {"type": "base64", "media_type": _mime_type(suffix),
                                         "data": base64.b64encode(image_bytes).decode("ascii")}},
        ]}],
    }
    response = _google_post_json("https://api.anthropic.com/v1/messages", body, headers={
        "x-api-key": api_key,
        "anthropic-version": os.getenv("ANTHROPIC_API_VERSION", "2023-06-01"),
    })
    return "\n".join(str(part.get("text", "")) for part in response.get("content", [])
                       if isinstance(part, dict) and part.get("type") == "text" and part.get("text"))


def _google_post_json(url: str, body: dict[str, Any], headers: dict[str, str] | None = None) -> dict[str, Any]:
    request_headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if headers:
        request_headers.update(headers)
    request = Request(url, data=json.dumps(body).encode("utf-8"), headers=request_headers, method="POST")
    try:
        with urlopen(request, timeout=180) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")[:1200]
        raise RuntimeError(f"Google API HTTP {exc.code}: {details}") from exc
    except URLError as exc:
        raise RuntimeError(f"Google API connection failed: {exc.reason}") from exc


def _mime_type(suffix: str) -> str:
    return {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".webp": "image/webp",
        ".tif": "image/tiff",
        ".tiff": "image/tiff",
        ".pdf": "application/pdf",
    }.get(suffix, "image/png")


def _extract_paddle_text(result: Any) -> str:
    lines: list[str] = []
    for item in result or []:
        payload = item
        if hasattr(item, "json"):
            payload = item.json
            if callable(payload):
                payload = payload()
        if isinstance(payload, str):
            try:
                decoded = json.loads(payload)
                if isinstance(decoded, (dict, list)):
                    payload = decoded
            except (TypeError, json.JSONDecodeError):
                pass
        if isinstance(payload, dict) and "res" in payload:
            payload = payload["res"]
        if isinstance(payload, dict):
            for key in ("rec_texts", "texts", "text"):
                value = payload.get(key)
                if isinstance(value, list):
                    lines.extend(str(part) for part in value if str(part).strip())
                    break
                if isinstance(value, str) and value.strip():
                    lines.append(value)
                    break
        elif isinstance(payload, str) and payload.strip():
            lines.append(payload)
    return "\n".join(lines)
