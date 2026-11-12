## What and why

<!-- One or two sentences. -->

## Checked

- [ ] The tests of the CI list pass (see `.github/workflows/build.yml`), and `python3 tools/i18n_extract.py --check` says OK.
- [ ] New texts are English (American spelling) and have Turkish, German and Chinese translations (`app/src/main/assets/i18n`). If a Chinese text
      is new, `python3 tools/make_fonts.py` rebuilt the Chinese font.
- [ ] A user-visible change is in the README, `CHANGELOG.md` and `docs/release-notes/`.
- [ ] New source files start with the licence header (a test checks it).
- [ ] Nothing private is in the change (positions, names, keys, logs).
