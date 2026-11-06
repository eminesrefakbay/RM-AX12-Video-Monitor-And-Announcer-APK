# Translating AX-HUD

AX-HUD speaks **English, Turkish (Türkçe), German (Deutsch) and Simplified Chinese (中文)**. Choose the language in
*Settings > Language*; "Automatic" follows Android. The voice can speak another language than the screen
(*Settings > Speech language*), as long as Android has that voice installed (*Android Settings > Text-to-speech*).

The German and Chinese texts were written with machine help and have not been checked by native speakers.
**Corrections are very welcome**: they are plain text files and anyone can edit them.

## How it works

The English text in the code is the key. A line in `app/src/main/assets/i18n/<code>.tsv` has the English text, a TAB, and
the translation. A text with no translation shows in English, so nothing can ever be blank or broken.

```
Save<TAB>Speichern
Imported {0} rules<TAB>{0} Regeln importiert
```

* Keep every `{0}`, `{1}` ... in the translation (they are filled in by the app; you may change their order).
* Keep `%d`, `%.1f` and `%%` exactly as they are in the one line that has them.
* `\n` is a new line. Do not translate rule keywords or variable names (`and`, `or`, `vbat`, `up_lq`, `ch5`...).
* Short texts on buttons and cards should stay short.

## Improving a translation

1. Edit the line in `app/src/main/assets/i18n/<code>.tsv` (`tr`, `de` or `zh`).
2. Run `python3 tools/i18n_extract.py --check`. It tells you about missing, extra or damaged lines.
3. Send a pull request, or just open an issue with the corrected text.

## The starting rules

The starting sound rules, warnings and checks are one text file per language: `rules.<code>.txt`, `warnings.<code>.txt`,
`checks.<code>.txt` in the same folder. Only the spoken words, the labels and the comments are translated; the conditions
(`vbat / 6 < 3.70`, `ch5 >= 1950`) are the same in every language, and a test fails if a translation changes them.
Existing users keep their own rules: *Settings > Reset the rules to the defaults* loads the starting rules in the
voice's language.

## Adding a language

1. Copy `source.txt` to `<code>.tsv`, put a TAB and the translation after each line, and add the `rules`, `warnings` and
   `checks` files.
2. Add the code to `I18n.LANGUAGES` (`I18n.java`), to `Lang.choices` and `Lang.nativeName` / `Lang.locale` (`Lang.kt`), to the speech
   language cycle in `MainActivity.kt`, and to the three loops in `tools/i18n_extract.py` and `I18nTests.java`.
3. Run the tests: `javac` all sources and run `I18nTests`, then `python3 tools/i18n_extract.py --check`.

When you change or add an English text in the code, run `python3 tools/i18n_extract.py` to refresh `source.txt`, then add the
translations; the build's check fails until every language has the new text.
