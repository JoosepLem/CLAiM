"""Parser for JSON-format invoice (synthetic data and future structured exports)."""
from __future__ import annotations
import json
from pathlib import Path

from ..models import Invoice


def parse_invoice_json(path: str | Path) -> Invoice:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return Invoice.model_validate(data)
