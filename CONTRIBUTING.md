# Contributing to AX-HUD

**Status:** the repository is not open for outside contributions yet. This file says how contributions will work once it is.

AX-HUD is licensed under the GNU General Public License, version 3 or later, with an additional permission for the RadioMaster AX SDK
(see [LICENSE](LICENSE)). The maintainer can also grant a separate written license on other terms to a particular party (see
"Other terms" in the README). So that this stays possible, every contribution needs the short agreement below.

## Contributor agreement

By submitting a contribution (code, text, images, translations) to this repository, you confirm that:

1. **It is yours to give.** You wrote it, or you have the right to submit it, and it contains no third-party code or material that you
   are not allowed to license in this way.
2. **Public license.** You license it under the GNU General Public License, version 3 or later, with the same additional permission for
   the RadioMaster AX SDK as the rest of AX-HUD (see LICENSE).
3. **Extra right for the maintainer.** You also grant the maintainer (GitHub user `eminesrefakbay`) a perpetual, worldwide, non-exclusive,
   irrevocable, royalty-free license to use, copy, modify, sublicense and distribute your contribution under any other terms, including
   proprietary terms, so that the maintainer can license AX-HUD separately to a particular party. You keep the copyright in your contribution.
4. **No warranty.** Your contribution is provided as it is, without any warranty.

## How to agree

* Sign off every commit: `git commit -s` adds a line `Signed-off-by: Your Name <you@example.com>`.
* Write "I agree to the AX-HUD contributor agreement" in the description of your pull request.

A contribution without both cannot be merged.

## Other ground rules

* Keep the style of the code and of the texts: plain rectangles, American spelling, sentence case, every text through `tr("...")` and a
  translation in Turkish, German and Chinese.
* Run the tests (the list is in `.github/workflows/build.yml`) and `python3 tools/i18n_extract.py --check` before you submit.
* Every source file keeps its license header (a test checks it).
* Do not add the RadioMaster SDK or the Qt libraries to the repository: the build downloads them.

*This agreement is a plain-language template. If it matters to you or to a company, have a lawyer read it.*
