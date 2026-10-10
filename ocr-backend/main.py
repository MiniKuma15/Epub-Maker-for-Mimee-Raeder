import os
import tempfile
from enum import Enum
from pathlib import Path
from typing import Any

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel

app = FastAPI(
    title="EPUB Maker OCR Service",
    description="Selectable Thai/English OCR engines: PaddleOCR, EasyOCR, and PaddleOCR-VL.",
    version="0.1.0",
)


class Engine(str, Enum):
    paddleocr = "paddleocr"
    easyocr = "easyocr"
    paddleocr_vl = "paddleocr-vl"


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
            {"id": "paddleocr", "name": "PaddleOCR PP-OCR", "languages": ["th", "en"]},
            {"id": "easyocr", "name": "EasyOCR", "languages": ["th", "en"]},
            {"id": "paddleocr-vl", "name": "PaddleOCR-VL", "languages": ["multilingual"]},
        ]
    }


@app.post("/ocr", response_model=OcrResponse)
async def recognize(
    engine: Engine,
    file: UploadFile = File(...),
) -> OcrResponse:
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
        text = await run_in_threadpool(_run_engine, engine, path)
        if not text.strip():
            raise HTTPException(status_code=422, detail="OCR did not recognize readable text")
        return OcrResponse(engine=engine.value, text=text.strip())
    except HTTPException:
        raise
    except ImportError as exc:
        raise HTTPException(
            status_code=503,
            detail=f"OCR engine dependency is not installed: {exc.name}",
        ) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"OCR failed: {type(exc).__name__}: {exc}") from exc
    finally:
        try:
            os.unlink(path)
        except OSError:
            pass


def _run_engine(engine: Engine, path: str) -> str:
    if engine == Engine.easyocr:
        import easyocr

        reader = _easy_reader()
        result = reader.readtext(path, detail=0, paragraph=True)
        return "\n".join(str(line) for line in result)

    if engine == Engine.paddleocr:
        # PaddleOCR 3.x API; the model is initialized lazily on first request.
        from paddleocr import PaddleOCR

        result = _paddle_reader().predict(path)
        return _extract_paddle_text(result)

    if engine == Engine.paddleocr_vl:
        # PaddleOCR-VL is a document-oriented model; requires a compatible
        # PaddleOCR/PaddleX installation and a machine with sufficient memory.
        from paddleocr import PaddleOCRVL

        result = _paddle_vl_reader().predict(path)
        return _extract_paddle_text(result)

    raise ValueError(f"Unsupported engine: {engine}")


_easy_reader_instance = None
_paddle_reader_instance = None
_paddle_vl_reader_instance = None


def _easy_reader():
    global _easy_reader_instance
    if _easy_reader_instance is None:
        import easyocr
        _easy_reader_instance = easyocr.Reader(["th", "en"], gpu=os.getenv("OCR_GPU", "false").lower() == "true")
    return _easy_reader_instance


def _paddle_reader():
    global _paddle_reader_instance
    if _paddle_reader_instance is None:
        from paddleocr import PaddleOCR
        _paddle_reader_instance = PaddleOCR(lang="th")
    return _paddle_reader_instance


def _paddle_vl_reader():
    global _paddle_vl_reader_instance
    if _paddle_vl_reader_instance is None:
        from paddleocr import PaddleOCRVL
        _paddle_vl_reader_instance = PaddleOCRVL()
    return _paddle_vl_reader_instance


def _extract_paddle_text(result: Any) -> str:
    lines: list[str] = []
    for item in result or []:
        # PaddleOCR 3.x result objects commonly expose a JSON representation.
        payload = item
        if hasattr(item, "json"):
            payload = item.json
            if callable(payload):
                payload = payload()
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
