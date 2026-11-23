"""Builds public/fonts/sheet-digits.woff: Playfair Display's lining figures (its `lnum` glyphs)
as the plain digits 0-9, variable weight 400-900, nothing else.

Canvas 2D has no font-variant-numeric, so the tear-off sheet's painted copy (lib/domPaint.ts,
used by the page curl) drew Playfair's default old-style figures where the page shows lining
ones (`lining-nums`). The painter puts this font first for such text.

Renamed: Playfair Display is OFL with the Reserved Font Name "Playfair Display", which a
modified version may not use. Source: the Android app's copy of the font (Google Fonts).

    python -m pip install fonttools
    python apps/web/scripts/sheet-digits.py
"""
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[3]
SRC = ROOT / 'apps/android/app/src/main/res/font/playfair_display.ttf'
OUT = ROOT / 'apps/web/public/fonts/sheet-digits.woff'
FAMILY = 'Sheet Digits'

font = TTFont(SRC)
lining = {ord(c): f'{n}.lf' for c, n in zip('0123456789', ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine'])}
assert all(g in font.getGlyphOrder() for g in lining.values())
for table in font['cmap'].tables:
    if table.isUnicode():
        table.cmap.update({u: g for u, g in lining.items() if u in table.cmap})

options = subset.Options()
options.layout_features = []
options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]
options.flavor = 'woff'
subsetter = subset.Subsetter(options)
subsetter.populate(unicodes=[ord(c) for c in '0123456789'])
subsetter.subset(font)

name = font['name']
name.setName(FAMILY, 1, 3, 1, 0x409)
name.setName(f'{FAMILY} Regular', 4, 3, 1, 0x409)
name.setName(f'{FAMILY.replace(" ", "")}-Regular', 6, 3, 1, 0x409)
name.setName(f'{FAMILY}; derived from Playfair Display (lining figures only)', 3, 3, 1, 0x409)
for rec in list(name.names):
    if rec.platformID != 3:
        name.removeNames(platformID=rec.platformID)
for inst in font['fvar'].instances:
    inst.postscriptNameID = 0xFFFF

OUT.parent.mkdir(parents=True, exist_ok=True)
font.flavor = 'woff'
font.save(OUT)
print(OUT, OUT.stat().st_size, 'bytes')
