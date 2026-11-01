# Inkly test plan (phone, Samsung S25 Ultra)

Mark each line P (pass) / F (fail) and note what you saw. Failures go in bug-report.md.

## Install
- [ ] Old Inkly uninstalled, new APK installs (no "App not installed")
- [ ] Enable Inkly in keyboard settings, select it, name shows as "Inkly"

## Typing
- [ ] English: type "teh" + space -> becomes "the"
- [ ] English: "recieve" + space -> "receive"
- [ ] New sentence auto-capitalises; ". " after a sentence works
- [ ] Add Malayalam (ml_IN) in languages; layout appears; word suggestions show
- [ ] No duplicated letters while typing fast
- [ ] Number row visible

## AI setup
- [ ] Tap an AI key; setup card appears
- [ ] Model download starts, progress moves, survives screen off (Pause/resume works)
- [ ] If download refused: "choose the file" route works
- [ ] After setup, panel does not close by itself
- [ ] Diagnostics shows a log (copy it if anything fails)

## AI features (use a 3-paragraph text, ~1500 characters)
- [ ] Fix grammar: Before/After shown, changed words highlighted, length not much shorter
- [ ] Replace puts the result into the field; Copy works; Try again gives another result
- [ ] Each of 13 tones works; a custom tone can be saved and used
- [ ] Assistants: Email writer, Summarize, Ask AI anything
- [ ] Translate to Malayalam and back to English
- [ ] Copy a message in another app -> Reply chip appears above keys -> 3 replies -> Insert works
- [ ] Voice: Speak asks for mic permission, text appears (English, then Malayalam)
- [ ] Text with Malayalam script survives Fix grammar without damage

## Toolbar and keyboard
- [ ] Four AI keys (fix, tone, assistants, translate) show at the start of the toolbar
- [ ] Emoji, clipboard, settings keys work
- [ ] Height resize, theme change, floating keyboard work

## Stress
- [ ] Switch apps mid-generation and come back: no crash
- [ ] Battery/heat during a long text: note %/min
