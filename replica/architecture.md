# Architecture: your AI keyboard (a rebuild of CleverType's core features)

Target device: Samsung Galaxy S25 Ultra (Android). Everything free, no account, no server.
Name: placeholder "Your Keyboard" until /replica-brand.

## Stack

| layer | choice | why |
| --- | --- | --- |
| keyboard engine | Fork of FlorisBoard (Kotlin, Apache 2.0) | Typing, layouts, auto-correct, themes and settings already exist. Apache 2.0 allows a modified, renamed product. Verify the repo builds and is current before committing (first build step) |
| UI | Jetpack Compose (what Floris uses) | AI toolbar, result card and settings are Compose screens |
| on-device AI | LiteRT-LM (Kotlin API) with Gemma 4 E2B (.litertlm, about 2.6 GB) | Google's runtime; Kotlin marked stable; runs on CPU or GPU. No API key, offline, text stays on phone. Gemma 4 E2B decodes about 47-52 tokens/s on an S26 Ultra per Google's page, so an S25 Ultra should be similar |
| model delivery | Download on first run to app storage, with progress and a Wi-Fi prompt | Keeps the APK small. Model is Apache-2.0 per its Hugging Face page |
| voice typing | Android SpeechRecognizer first (on-device where the phone supports it), then Gemma cleanup for punctuation, fillers and lists | No extra model to ship |
| local data | Room (SQLite) + DataStore | Custom assistants, tones, themes, usage history. No cloud DB |
| optional cloud AI | Switch in Settings to use the user's own Gemini API key | Better translation quality later. Off by default. Key stored in Android Keystore-backed storage |
| payments | None | You want the pro version unlocked for everyone |
| backend | None | Nothing to host. Removes the original's hardest billing and server parts |

Decision notes: one app module, no microservices. The AI sits behind one interface (`AiEngine`) so Gemma on-device and Gemini-by-key are swappable.

## Schema

Tables: 5. Access model: single user on one device, so no row-level security; data is app-private storage. Nothing syncs.

```sql
-- see replica/schema.sql
```

## API (internal, no HTTP)

The "API" is the AiEngine interface plus the keyboard's service hooks.

| call | does | who | input | output | flow |
| --- | --- | --- | --- | --- | --- |
| AiEngine.fixGrammar(text) | corrected text | keyboard UI | field text | corrected text (stream) | F01 |
| AiEngine.changeTone(text, toneId) | rewrite in tone | keyboard UI | text, tone | rewritten text | F02 |
| AiEngine.reply(lastMessage, intent, draft?) | suggest or expand a reply | keyboard UI | message, neutral/positive/negative, optional draft | 1-3 replies | F03 |
| AiEngine.runAssistant(assistantId, text) | run a built-in or custom assistant | keyboard UI | assistant, text or prompt | text | F04, F05 |
| AiEngine.translate(text, targetLang) | translate, keep tone | keyboard UI | text, language | text | F06 |
| VoiceTyping.start / stop | record, transcribe, clean up | mic key | audio | text | F07 |
| ReplyReader.lastMessage() | read last chat message via AccessibilityService | Clever-Reply feature only | none | text or null | F03 |
| ModelManager.ensureModel() | download, verify and load the model | first run, settings | none | ready / progress / error | F08 |
| InputConnection commit/replace | write text into the active field | keyboard service | text | none | all |

Webhooks: none. Jobs: none (model download uses WorkManager so it survives app close).

## The parts that bite

- Input method lifecycle: the IME service is killed and restarted by Android. Keep the model loaded in a bound process or reload lazily; first call after a cold start must show a "warming up" state, not freeze typing.
- Memory: a 2.6 GB model plus the keyboard. Load only when an AI key is first tapped, unload after idle. Typing must never wait on the model.
- Latency and quality: grammar and tone should be fine; translation across 40+ languages and long assistant prompts will be weaker than a cloud model. Measure on your S25 Ultra before promising parity.
- Accessibility permission for Clever Reply: sensitive on Google Play and needs a clear disclosure. Fine for a personal APK; plan a policy review before any Play release.
- Prompt safety: field text and chat messages are untrusted input to the model. Wrap them as data, never as instructions.
- Privacy: because everything is on-device, say so honestly and add no analytics.
- Model download: large file, resume support, free-space check, checksum.
- Text replacement: some apps block selection or composing regions. Fall back to inserting at the cursor.

## Build order

1. Vertical slice (F01): fork FlorisBoard, build and install it on the phone. Add an AI key to the keyboard (S03), call Gemma through AiEngine, show the result card (S04), Replace writes back. Tables: ai_usage only. Screens: S02, S03, S04, S01 (basic).
2. Must-haves: tone presets (S05), AI assistants list with prompt input (S07), auto-correct and capitalisation checks, onboarding (S01), model download manager.
3. Should-haves: custom tone and custom assistant (S08, tables custom_tone, assistant), Clever Reply (S06), translate (S09), voice typing (S10), themes (S11), keyboard height and position resize (an opening the original misses).
4. Could-haves: floating keyboard, paraphrase alternatives, extra languages.
5. Fixes from replica-entrepreneur, once a real review export exists.
