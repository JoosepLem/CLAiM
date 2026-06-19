"""Parser for JSON-format arve (synthetic data)."""
from __future__ import annotations
import json
from pathlib import Path

from ..models import Arve


def parse_arve_json(path: str | Path) -> Arve:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return Arve.model_validate(data)
