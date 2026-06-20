"""Parser for JSON-format partner invoice appendix."""
from __future__ import annotations
import json
from pathlib import Path

from ..models import Appendix


def parse_appendix_json(path: str | Path) -> Appendix:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return Appendix.model_validate(data)
