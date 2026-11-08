#!/usr/bin/env python3
# AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
# Copyright (C) 2026 eminesrefakbay
# SPDX-License-Identifier: GPL-3.0-or-later
# With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

"""Rebuilds the font files of app/src/main/assets/fonts from the variable fonts of the Google Fonts repository.

Needs: pip install fonttools brotli. Downloads Teko, Manrope and Noto Sans SC (all SIL OFL 1.1), cuts the weights the app uses, and trims
every font to the characters it needs. Run it again whenever a Chinese text is added (the characters of i18n/zh.tsv are kept), then run
the tests: FontTests checks that every character of the Chinese texts is in the font.
"""
import os, sys, urllib.parse, urllib.request
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools import subset

BASE = 'https://raw.githubusercontent.com/google/fonts/main/'
ASSETS = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets')
LATIN = [(0x20, 0x7E), (0xA0, 0xFF), (0x100, 0x17F), (0x2010, 0x2015), (0x2018, 0x201E), (0x2022, 0x2022), (0x2026, 0x2026),
         (0x2030, 0x2030), (0x2212, 0x2212), (0x2190, 0x2193), (0x2264, 0x2265), (0xD7, 0xD7)]


def fetch(path, dst):
    with urllib.request.urlopen(BASE + urllib.parse.quote(path), timeout=300) as r, open(dst, 'wb') as f:
        f.write(r.read())


def cut(src, wght, unicodes, name, out, features):
    f = TTFont(src)
    f = instancer.instantiateVariableFont(f, {'wght': wght}, inplace=False)
    o = subset.Options()
    o.layout_features = features
    o.name_IDs = [1, 2, 3, 4, 5, 6]
    o.notdef_outline = True
    o.hinting = False
    o.glyph_names = False
    s = subset.Subsetter(o)
    s.populate(unicodes=sorted(unicodes))
    s.subset(f)
    for rec in list(f['name'].names):
        if rec.nameID in (1, 2, 4, 6, 16, 17):
            f['name'].removeNames(nameID=rec.nameID)
    f['name'].setName(name, 1, 3, 1, 0x409)
    f['name'].setName('Regular', 2, 3, 1, 0x409)
    f['name'].setName(name, 4, 3, 1, 0x409)
    f['name'].setName(name.replace(' ', ''), 6, 3, 1, 0x409)
    f.save(os.path.join(ASSETS, 'fonts', out))


def main():
    tmp = os.path.join(os.path.dirname(__file__), '_fonts_tmp')
    os.makedirs(tmp, exist_ok=True)
    latin = {u for a, b in LATIN for u in range(a, b + 1)}
    for path, local in (('ofl/teko/Teko[wght].ttf', 'Teko.ttf'), ('ofl/manrope/Manrope[wght].ttf', 'Manrope.ttf'), ('ofl/notosanssc/NotoSansSC[wght].ttf', 'NotoSansSC.ttf')):
        if not os.path.exists(os.path.join(tmp, local)):
            print('downloading', path)
            fetch(path, os.path.join(tmp, local))
    feat = ['kern', 'liga', 'tnum', 'case', 'ccmp', 'locl', 'mark', 'mkmk']
    for w, n in ((600, 'Teko SemiBold'), (700, 'Teko Bold')):
        cut(os.path.join(tmp, 'Teko.ttf'), w, latin, n, n.replace(' ', '') + '.ttf', feat)
    for w, n in ((400, 'Manrope Regular'), (500, 'Manrope Medium'), (600, 'Manrope SemiBold'), (700, 'Manrope Bold')):
        cut(os.path.join(tmp, 'Manrope.ttf'), w, latin, n, n.replace(' ', '') + '.ttf', feat)
    chars = set()
    for line in open(os.path.join(ASSETS, 'i18n', 'zh.tsv'), encoding='utf-8'):
        if line.startswith('#') or '\t' not in line:
            continue
        chars |= set(ord(c) for c in line.rstrip('\n').split('\t', 1)[1])
    zh = set(chars) | {u for a, b in ((0x20, 0x7E), (0xA0, 0xFF), (0x2010, 0x2027), (0x2030, 0x2030), (0x3000, 0x303F), (0xFF01, 0xFF5E)) for u in range(a, b + 1)}
    for w, n, out in ((400, 'Noto Sans SC AX Subset Regular', 'NotoSansSCRegular.ttf'), (700, 'Noto Sans SC AX Subset Bold', 'NotoSansSCBold.ttf')):
        cut(os.path.join(tmp, 'NotoSansSC.ttf'), w, zh, n, out, ['kern', 'locl', 'ccmp', 'liga'])
    print('done: the fonts are in', os.path.join(ASSETS, 'fonts'))


if __name__ == '__main__':
    sys.exit(main())
