# Recon map: CleverType AI Keyboard (Android)

Scope: the core loop only. A system keyboard with AI writing actions (fix grammar, change tone, smart reply, translate, AI assistants, voice typing) acting on the text field. Chrome extension, desktop app and iOS are out of this slice.
For: Anu's own product (audience and niche to be decided at /replica-architect)
Date: 2026-10-20

Method note: public sources only (store listing, marketing site). No account was used, no app binary or network traffic was inspected. The pricing page and the FAQ answers render client-side and came back empty, so those areas are marked "unverified" instead of guessed.

## Sources

| # | source | URL | notes |
| --- | --- | --- | --- |
| 1 | Google Play listing | https://play.google.com/store/apps/details?id=com.clevertype.ai.keyboard | feature list, what's new, data safety, 4.4 stars / 38.1K reviews, 1M+ downloads, updated Aug 21, 2026 |
| 2 | Marketing site | https://www.clevertype.co/ | six feature blocks with demo-video descriptions, tone list, platforms |
| 3 | Pricing page | https://www.clevertype.co/pricing | only the meta description loaded: free basic tier, Premium "starting at $4.99/month". Plan table unverified |
| 4 | FAQ | https://www.clevertype.co/faq | only question titles loaded (free trial, offline, devices, auto payment, cancel, voice typing, themes). Answers unverified |
| 5 | Play reviews (3 visible) | listing above | too few to rank complaints; see Review signals |

## Core loop

The user types in any app, taps an AI key on the keyboard, and the keyboard rewrites, replies to or translates the text in place, so they never leave the app they are typing in.

## Screens

Screens below are inferred from the listing and site copy. The Play listing has 24 screenshots I could not view as images in this pass, so states are partial. Review them with the user (reference only, saved under replica/screens/).

| ID | screen | route / how to reach | purpose | key components | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | Onboarding / enable keyboard | first launch | enable keyboard in system settings, select it as input method | step list, system-settings handoff | unverified |
| S02 | Typing keyboard | any text field | normal typing, auto-correct, punctuation | key grid, suggestion bar, AI key row | typing, floating mode (per What's new) |
| S03 | AI toolbar | tap AI entry on keyboard | pick an action: grammar, tone, reply, translate, assistants, voice | action tiles | unverified |
| S04 | Fix Grammar result | one tap from toolbar | show corrected text, Replace button | result card, replace button | filled (site video) |
| S05 | Tone picker | toolbar > tone | pick a tone or add a custom tone | tone chips (13 named tones + custom) | filled |
| S06 | Clever Reply | toolbar > reply | read last message (needs Accessibility permission), suggest neutral / positive / negative replies, expand a short draft | reply options, tone of reply selector | filled |
| S07 | AI assistants list | toolbar > assistants | choose from 20 to 30+ assistants (summarize, captions, Humanise, Emojify, phrase-to-emoji, pickup line) or ChatGPT-style prompt | assistant list, prompt input | filled |
| S08 | Custom assistant editor | assistants > create | write own instructions to make an assistant | name field, prompt field | unverified |
| S09 | Translate | toolbar > translate | translate while typing, keeps tone, 40+ languages | language picker, result | filled |
| S10 | Voice typing | mic key | record, live waveform bar, insert cleaned text | recording bar, waveform | recording, result (site video) |
| S11 | Themes / layout settings | app home or keyboard settings | colors, layouts, keyboard height | theme gallery, sliders | unverified |
| S12 | Paywall / subscription | tapping a gated feature | upsell Premium | plan cards | unverified |
| S13 | Settings | app home | languages, permissions, account, privacy, manage plan | list rows, toggles | unverified |

## Flows

```
F01 Fix grammar on a typed message
    S02 type -> S03 open toolbar -> S04 corrected text -> tap Replace
    happy path clicks: about 3 (open toolbar, Fix Grammar, Replace)  [estimate from site description]
    edge: free quota used up -> S12, no network, text too long

F02 Change tone
    S02 -> S03 -> S05 pick tone -> result -> Replace
    edge: custom tone, tone not available on free

F03 Reply to the last message (WhatsApp, email)
    S02 -> S03 -> S06 -> pick neutral/positive/negative -> insert
    edge: Accessibility permission denied, no last message found

F04 Use an AI assistant / ChatGPT-style prompt
    S02 -> S03 -> S07 -> type prompt -> result -> insert
F05 Create a custom assistant
    S07 -> S08 -> save -> appears in list
F06 Translate
    S02 -> S03 -> S09 -> choose language -> insert
F07 Voice typing
    S02 mic key -> S10 speak -> cleaned text inserted
    edge: mic permission denied, silence, mixed languages
F08 First-run setup
    S01 -> enable in system settings -> select keyboard -> grant permissions
F09 Upgrade to Premium
    S12 -> store billing -> unlock
```

## Components

| component | variants | states | used on |
| --- | --- | --- | --- |
| Key (letter, symbol, action) | normal, shift, long-press | default, pressed, disabled | S02 |
| Suggestion / correction bar | words, AI shortcut | empty, filled | S02 |
| AI action tile | grammar, tone, reply, translate, assistant, voice | default, loading, locked | S03 |
| Result card with Replace / Insert | grammar, tone, reply, translate | loading, filled, error | S04-S09 |
| Tone chip | 13 preset + custom | default, selected | S05 |
| Recording bar with waveform | | idle, recording, processing | S10 |
| Theme swatch / preview | | default, selected | S11 |
| Plan card | monthly, other (unverified) | default, selected | S12 |

## Inferred data model

```
User        id, auth provider, plan, plan_expires_at, preferred_languages
            evidence: S12, S13, FAQ titles (auto payment, cancel, multiple devices)
            confidence: medium
Subscription id, user_id, store (play | app store | web), status, renews_at
            evidence: FAQ titles "auto payment", "cancel premium"
            confidence: medium
Assistant   id, owner_id (null = built-in), name, instruction_prompt, icon
            evidence: S07, S08, "create custom assistants with your own instructions"
            confidence: high
Tone        id, name, owner_id (null = built-in)
            evidence: S05 ("add your own custom tone")
            confidence: high
UsageCounter user_id, feature, period, count
            evidence: "free basic features" vs "unlimited" on Premium
            confidence: guess (limits unverified)
Theme       id, name, colors, layout, owner_id
            evidence: Theme Customization section
            confidence: medium
AIRequest   id, user_id, feature, input_text (transient), output_text (transient), lang
            evidence: developer reply says text is sent only when an AI feature is chosen
            confidence: medium. Decide your own retention (store nothing by default)
```

Relationships: User 1-n Subscription, User 1-n Assistant (custom), User 1-n Tone (custom), User 1-n Theme, User 1-n UsageCounter.

## Feature matrix

See `features.csv`. Counts: must 9, should 8, could 4, skip 4.

## Review signals (thin, not a ranked list)

Only 3 Play reviews were visible, so nothing here is a real theme yet. Run /replica-entrepreneur on a proper review export (Play, App Store, Reddit) before deciding positioning. What the three say:
- Keyboard can be made taller but not moved upward from below; user points to SwiftKey's resize option (Play review, Aug 14, 2026; dev says it is on their list).
- Auto-capitalisation at sentence starts and punctuation correction not working for one new user (Play review, Aug 19, 2026).
- Privacy worry in a third review; developer replies that text is sent to AI only when a feature is chosen.

## Out of scope (cannot or should not be cloned)

- CleverType's name, logo, icon, screenshots, marketing copy, demo videos and review quotes.
- Their 4M-user network, ratings and store ranking.
- The underlying AI models and prompts. Use your own provider; the built-in assistants' wording must be written fresh.
- Their predictive-text / auto-correct engine tuning and dictionaries. Start from open ones.
- Trademark risk: tone names and "ChatGPT keyboard" style claims. Use generic wording in the brand step.

## Size

Screens 13 (partly inferred), flows 9, entities 7. Hard parts: (1) a real Android input method (IME) with solid typing, auto-correct and multi-language support; (2) Accessibility-service reading of the last message for Clever Reply, which is permission-sensitive on Play; (3) in-app voice capture plus cleanup; also subscriptions via Play Billing and keeping latency low on AI calls. Size: L for a full keyboard. M if you ship the AI actions on top of an existing open-source keyboard base and add the keyboard engine later.

Next step: /replica-architect
