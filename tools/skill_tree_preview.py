"""Builds a review page for the skill trees: every tree drawn as it's laid out in game, for checking the shape and
feel outside the game. Reads build/skill-trees-preview.json (written by tools/skill_trees.py) and writes
build/skill-tree-review.html.

Run from the project root, after tools/skill_trees.py: python tools/skill_tree_preview.py
"""
import json
from pathlib import Path

PREVIEW = Path("build/skill-trees-preview.json")
OUT = Path("build/skill-tree-review.html")
TEMPLATE = Path(__file__).with_name("skill_tree_review.html")

data = json.loads(PREVIEW.read_text(encoding="utf-8"))
page = TEMPLATE.read_text(encoding="utf-8").replace("__DATA__", json.dumps(data, ensure_ascii=False).replace("</", "<\\/"))
OUT.write_text(page, encoding="utf-8", newline="\n")
print("Wrote", OUT)
