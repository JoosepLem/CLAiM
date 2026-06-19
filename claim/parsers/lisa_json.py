"""Parser for JSON-format partnerarve lisa (synthetic & future structured exports)."""
from __future__ import annotations
import json
from pathlib import Path

from ..models import Lisa


def parse_lisa_json(path: str | Path) -> Lisa:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return Lisa.model_validate(data)
