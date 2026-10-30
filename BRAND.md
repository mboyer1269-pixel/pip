# BRAND.md — Pip

Brand charter for Pip (Phase 4). Written from `SPEC.md` and `CLAUDE.md`
only. Every name, phrase, color, and shape here is original to Pip.

- Visual direction: **A+B — "Le pépin dans la poche"** (pocket + seed),
  chosen by the owner on 2026-10-29.
- Internal package: `ca.michael.pip`.

## 1. Name and slogans

| | |
|---|---|
| Name | **Pip** (same in French and English, never translated, never in capitals except at sentence start) |
| Slogan FR | « Petit agent, grande poche » |
| Slogan EN | "Small agent, big pocket" |
| Debug build label | **Pip (debug)** — SEC-5 requires a distinct label |

## 2. Personality

Pip is a pocket companion: small, close, always visible when it runs, and
it holds a lot in a very small pocket.

- **Smart** — answers short and to the point; says what it knows and what
  it does not.
- **Cute** — light, warm, a little playful; one touch of humor at most per
  message, never at the owner's expense.
- **Memorable** — the pocket image comes back often (in the pocket, out of
  the pocket, stored away), but never twice in the same message.

## 3. Voice and tone

### 3.1 Rules

1. **French first.** French is the source text; English is a faithful
   alternative, not a literal translation.
2. **Short.** One idea per line. Notifications ≤ 60 characters per line
   when possible. Buttons are 1–3 words.
3. **Tutoiement** in French ("tu"), plain "you" in English.
4. **Pip speaks in the first or third person** ("je", or "Pip") — pick one
   per surface and keep it. Notifications use the third person; spoken
   answers and dialogs use "je".
5. **Honest and specific.** An error says what happened and what the owner
   can do (SPEC principle 4). Never blame the owner.
6. **Always visible.** Pip says when it listens, when it looks at the
   screen, when something leaves the device.
7. **The owner decides.** Every request for consent states exactly what
   happens on yes and on no.

### 3.2 Forbidden

- System tone: "Erreur 0x…", "Opération effectuée avec succès", "Veuillez
  patienter", "Processus en arrière-plan", "Service démarré".
- Any vocabulary of invisibility or stealth: *invisible, caché, discret,
  furtif, silencieux, en douce, à ton insu, hidden, stealth, silent,
  undetectable, in the background without you knowing*.
- "Always" options for consent: *toujours autoriser, ne plus demander,
  always allow, don't ask again*.
- Fake urgency, guilt, or pressure ("Dépêche-toi !", "Tu vas manquer…").
- Showing a key or pairing secret anywhere except the one-time pairing
  view (ACP-13).

### 3.3 Signature lines

The owner's voice examples, rewritten in Pip's own words for the app.

| Moment | FR | EN |
|---|---|---|
| Greeting | Allô, moi c'est Pip. Je loge dans ta poche — on y va quand tu veux. | Hi, I'm Pip. I live in your pocket — ready when you are. |
| State | Pip est là. | Pip's here. |
| Approval | Pip attend ton oui — sans lui, rien ne bouge. | Pip is waiting for your yes — nothing moves without it. |
| Success | Voilà, c'est rangé dans la poche. | Done — tucked in the pocket. |
| Stop | Pip retourne dans la poche. À tantôt ! | Pip's heading back to the pocket. See you soon! |

## 4. Messages per surface (SPEC section 3)

Placeholders are in `{braces}`. Final string resources are written in
Phase 5 from these lines.

### S1 — Persistent notification

Title is always **Pip**. Text is the state line, then the ACP line, then
the last task line when there is one.

| State | FR | EN |
|---|---|---|
| Ready | Pip est là. | Pip's here. |
| Listening | Je t'écoute… | Listening… |
| Thinking | Je réfléchis… | Thinking… |
| Speaking | Je te réponds. | Answering you. |
| Waiting for approval | Une tâche attend ton oui. | A task is waiting for your yes. |
| Stopped by error | Pip s'est arrêté : {raison courte}. | Pip stopped: {short reason}. |
| ACP connected | Orchestrateur : branché | Orchestrator: connected |
| ACP off | Orchestrateur : débranché | Orchestrator: off |
| Last task | Dernière tâche : {titre} — {résultat} | Last task: {title} — {outcome} |
| Reminder due | Rappel : {texte} | Reminder: {text} |

Actions: **Arrêter** / **Stop** (always present) · **Ouvrir** / **Open** ·
**Voir la tâche** / **See task** (only when a task is pending).

### S2 — Home

| Element | FR | EN |
|---|---|---|
| First launch | Allô, moi c'est Pip. Je loge dans ta poche — on y va quand tu veux. | Hi, I'm Pip. I live in your pocket — ready when you are. |
| Talk button | Parler | Talk |
| Talk button while listening | J'écoute — touche pour finir | Listening — tap to finish |
| Type field hint | Ou écris-moi ici… | Or type here… |
| Screen toggle | Regarde mon écran (cette fois-ci) | Look at my screen (this time) |
| Screen toggle when accessibility is off | Pour regarder ton écran, active Pip dans Accessibilité. | To look at your screen, turn Pip on in Accessibility. |
| Show attached snapshot | Voir ce que j'ai lu | See what I read |
| Interrupt speech | Touche pour me couper | Tap to stop me |
| Indicator: brain | Cerveau : prêt / pas configuré | Brain: ready / not set up |
| Indicator: accessibility | Écran : accessible / fermé | Screen: available / off |
| Indicator: ACP | Orchestrateur : branché / débranché | Orchestrator: connected / off |
| No brain key (FR-4.4) | Je n'ai pas encore de clé pour mon cerveau. Ajoute-la dans Réglages › Cerveau. | I don't have a brain key yet. Add it in Settings › Brain. |
| No network (FR-4.5) | Pas de réseau en ce moment. Je réessaie quand tu veux. | No network right now. I'll try again when you want. |
| Key refused | Le serveur a refusé la clé. Vérifie-la dans Réglages › Cerveau. | The server refused the key. Check it in Settings › Brain. |
| Server error | Le serveur a eu un pépin de son côté. Réessaie dans un instant. | The server had a hiccup on its side. Try again in a moment. |
| Timeout | Le serveur met trop de temps à répondre. On réessaie ? | The server is taking too long. Try again? |
| Malformed answer | J'ai reçu une réponse que je n'arrive pas à lire. | I got an answer I can't read. |
| On-device recognizer missing (FR-2.2) | Je n'ai pas de reconnaissance vocale sur l'appareil. Tu peux m'écrire, ou activer celle du système dans Réglages. | There's no on-device speech recognition. You can type, or turn on the system one in Settings. |
| Message from orchestrator (`message.show`) | Message de ton orchestrateur : {texte} | Message from your orchestrator: {text} |

Navigation: **Réglages** / **Settings** · **Carnet** / **Notebook** ·
**Tâches** / **Tasks**.

### S3 — Setup & permissions

Intro: « Je te demande seulement ce qui sert. Tu peux dire non à tout sauf
aux notifications. » / "I only ask for what I use. You can say no to
anything except notifications."

| Capability | What it gives (FR / EN) | If refused (FR / EN) |
|---|---|---|
| Notifications | Ma notification te montre que je suis là, avec Arrêter toujours à portée. / My notification shows I'm here, with Stop always in reach. | Sans elle, je ne reste pas actif. / Without it, I don't stay running. |
| Microphone | Tu me parles, je t'entends. / You talk, I hear you. | Tu pourras m'écrire. / You can type to me. |
| Accessibility | Je lis ton écran quand tu me le demandes, jamais autrement. / I read your screen when you ask, never otherwise. | Je ne vois pas ton écran ; le reste marche. / I can't see your screen; everything else works. |
| Network | Je parle à mon cerveau et à ton orchestrateur. / I talk to my brain and your orchestrator. | Carnet et voix fonctionnent encore. / Notebook and voice still work. |

Buttons: **Autoriser** / **Allow** · **Ouvrir les réglages** / **Open
settings** · **Plus tard** / **Later**. State: **Accordé** / **Granted** ·
**Refusé** / **Refused**.

### S4 — Settings › Brain

| Element | FR | EN |
|---|---|---|
| URL label | Adresse du cerveau (https://) | Brain address (https://) |
| URL refused (FR-4.2) | L'adresse doit commencer par https://. | The address must start with https://. |
| Model label | Modèle | Model |
| Key label | Clé | Key |
| Key state | Clé : enregistrée / aucune | Key: saved / none |
| Key note | Une fois enregistrée, je ne te la montre plus. | Once saved, I won't show it again. |
| Buttons | Enregistrer · Tester · Effacer la clé | Save · Test · Clear key |
| Test ok | Le cerveau répond. C'est dans la poche. | The brain answers. All set. |
| Test fails | Same wording as the S2 error lines | Same as S2 |
| System recognizer opt-in | Utiliser la reconnaissance vocale du système. Attention : ton fournisseur peut traiter l'audio hors de l'appareil. | Use the system speech recognition. Heads-up: your provider may process audio off the device. |

### S5 — Settings › ACP

| Element | FR | EN |
|---|---|---|
| Host label | Orchestrateur | Orchestrator |
| Empty allowlist (Q-1 open) | Aucun orchestrateur n'est prévu dans cette version. | No orchestrator is set in this build. |
| Pairing state | Jumelé depuis le {date} / Pas jumelé | Paired since {date} / Not paired |
| Buttons | Jumeler · Rejumeler · Révoquer · Brancher · Débrancher | Pair · Re-pair · Revoke · Connect · Disconnect |
| One-time pairing view | Copie ce code dans ton orchestrateur. Je ne l'afficherai qu'une fois. | Copy this code into your orchestrator. I'll show it only once. |
| Re-pair confirm | L'ancien code ne marchera plus. On continue ? | The old code will stop working. Continue? |
| Revoke confirm | Je débranche et j'oublie le code. Plus rien n'entre avant un nouveau jumelage. | I'll disconnect and forget the code. Nothing gets in until you pair again. |

### S6 — Notebook

| Element | FR | EN |
|---|---|---|
| Title | Mon carnet | My notebook |
| Kinds | Note · Promesse · Rappel | Note · Promise · Reminder |
| Empty | Ton carnet est vide. Il reste sur ton téléphone, chiffré. | Your notebook is empty. It stays on your phone, encrypted. |
| Add / edit / delete | Ajouter · Modifier · Supprimer | Add · Edit · Delete |
| Overdue (FR-5.6) | En retard depuis le {date} | Overdue since {date} |
| Erase all button | Tout effacer | Erase everything |
| Erase all confirm | J'efface tout le carnet et sa clé. On ne pourra pas revenir en arrière. | I'll erase the whole notebook and its key. This can't be undone. |
| Erased | Carnet vidé. | Notebook cleared. |

### S7 — Task approval

| Element | FR | EN |
|---|---|---|
| Header | Pip attend ton oui — sans lui, rien ne bouge. | Pip is waiting for your yes — nothing moves without it. |
| What happens | Si tu dis oui : {description de Pip} | If you say yes: {Pip's description} |
| Orchestrator wording | L'orchestrateur dit : « {summary} » | The orchestrator says: "{summary}" |
| Arguments | Détails complets | Full details |
| Time left | Il reste {m:ss} | {m:ss} left |
| Buttons | Approuver · Refuser | Approve · Refuse |
| Approved | Approuvé. Je m'en occupe. | Approved. On it. |
| Executed | Voilà, c'est rangé dans la poche. | Done — tucked in the pocket. |
| Refused | Refusé. Rien n'a été fait. | Refused. Nothing was done. |
| Expired | Trop tard, la tâche a expiré. Rien n'a été fait. | Too late, the task expired. Nothing was done. |

Pip's own descriptions per intent (section 7.5):

| Intent | FR | EN |
|---|---|---|
| `device.status` | J'envoie la version de Pip, la batterie, la recharge, le type de réseau et mon état. | I'll send Pip's version, battery, charging, network type and my state. |
| `snapshot.last` | J'envoie la dernière lecture d'écran que **toi** as faite. Je n'en fais pas de nouvelle. | I'll send the last screen reading **you** made. I won't take a new one. |
| `message.show` | J'affiche ce message ici et dans une notification : « {texte} » | I'll show this message here and in a notification: "{text}" |
| `reminder.create` | J'ajoute un rappel à ton carnet : « {texte} », {échéance ou « sans date »}. | I'll add a reminder to your notebook: "{text}", {due or "no date"}. |

### S8 — Task journal

| Element | FR | EN |
|---|---|---|
| Title | Journal des tâches | Task journal |
| Empty | Rien encore. Tout ce qui arrive sera noté ici. | Nothing yet. Everything that comes in gets written here. |
| Events | Reçue · En attente · Approuvée · Refusée · Expirée · Rejetée ({raison}) · Exécutée · Échouée · Annulée | Received · Pending · Approved · Refused · Expired · Rejected ({reason}) · Executed · Failed · Cancelled |
| Cancelled by stop | Annulée : Pip a été arrêté. | Cancelled: Pip was stopped. |
| Rejection reasons | signature invalide · mauvais destinataire · trop vieux · déjà reçu · version inconnue · message illisible · tâche inconnue · tâche interdite | bad signature · wrong recipient · too old · already received · unknown version · unreadable message · unknown task · forbidden task |
| Clear | Vider le journal | Clear journal |
| Clear confirm | J'efface tout l'historique des tâches. On continue ? | I'll erase all task history. Continue? |

### S9 — Notebook-excerpt approval

| Element | FR | EN |
|---|---|---|
| Header | Ce bout de ton carnet m'aiderait à répondre. | This bit of your notebook would help me answer. |
| Body | Voici exactement ce que j'enverrais à mon cerveau, pour cette fois seulement : | Here's exactly what I'd send to my brain, this time only: |
| Buttons | Envoyer cette fois · Ne pas envoyer | Send this once · Don't send |
| After refusal | D'accord, je réponds sans. | Okay, I'll answer without it. |

### S10 — Action approval (Block A)

Title: the S7 line « Pip attend ton oui — sans lui, rien ne bouge. » /
"Pip is waiting for your yes — nothing moves without it." Buttons
**Approuver** / **Refuser**, Approve armed after one second.

| Action | FR | EN |
|---|---|---|
| `open_app` | J'ouvre l'app « {nom} ». | I'll open the app "{name}". |
| `call` | Je vais appeler : {nom (numéro)} | I'll call: {name (number)} |
| `send_sms` | Je vais envoyer ce texto : « {texte} » À : {nom (numéro)} | I'll send this text: "{text}" To: {name (number)} |
| `find_contact` | Je cherche « {nom} » dans tes contacts et j'envoie les noms et numéros trouvés à mon cerveau. | I'll look up "{name}" in your contacts and send the names and numbers I find to my brain. |
| `read_sms` | Je lis tes {n} derniers textos reçus et je les envoie à mon cerveau. | I'll read your last {n} received texts and send them to my brain. |
| `read_call_log` | Je lis tes {n} derniers appels et je les envoie à mon cerveau. | I'll read your last {n} calls and send them to my brain. |
| `read_calendar` | Je lis ton calendrier à partir du {date} ({n} jour(s)) et j'envoie les événements à mon cerveau. | I'll read your calendar from {date} ({n} day(s)) and send the events to my brain. |
| `add_calendar_event` | J'ajoute à ton calendrier : « {titre} », {date}, pour {n} minutes. | I'll add to your calendar: "{title}", {date}, for {n} minutes. |
| `get_location` | Je lis ta position actuelle et je l'envoie à mon cerveau. | I'll read your current position and send it to my brain. |
| `get_steps` | Je lis ton compteur de pas (depuis le dernier redémarrage du téléphone) et je l'envoie à mon cerveau. | I'll read your step counter (since the phone last restarted) and send it to my brain. |
| Several contacts | Plusieurs contacts correspondent. Choisis le bon : | Several contacts match. Pick the right one: |
| Read actions | Ce que je lis part à mon cerveau pour cette réponse seulement. Si tu refuses, rien ne part. | What I read goes to my brain for this answer only. If you refuse, nothing leaves. |
| Permission refused | Sans cette permission, je ne peux pas le faire. Rien n'a été fait. | Without that permission I can't do it. Nothing was done. |

## 5. Visual identity — direction A+B "Le pépin dans la poche"

### 5.1 Mark

A front-facing clothing pocket: a rounded U shape, flat top edge, with a
dashed stitch line just under the top edge. Peeking over the top edge is
the upper half of a small **seed** (a rounded drop shape, point up) with
two round eyes and **one small leaf** sprouting from its tip. Pip is a
little seed tucked warm in the owner's pocket. No mouth, no arms, no
other detail.

- Meaning: "pocket" carries the slogan in both languages; "seed" adds the
  English double meaning of *pip* without relying on it in French.
- Proportions: pocket width ≈ 1.1 × its height; corner radius ≈ 40 % of
  the width at the bottom; seed width ≈ 45 % of pocket width, its lower
  half hidden behind the top edge; eyes ≈ 22 % of seed width each, spaced
  by one eye width; leaf ≈ 35 % of seed width, tilted about 30°.
- Seed, eyes and leaf together are what makes it Pip; the mark is never
  used as an empty pocket.

### 5.2 Palette

| Token | Hex | Role |
|---|---|---|
| `pip_indigo` | `#2F3A8F` | Primary: the pocket, headers, icon background |
| `pip_clementine` | `#FF7A45` | Accent: Talk, Approve, the stitch line |
| `pip_cream` | `#FFF4E8` | Light background, the pocket on the icon |
| `pip_seed` | `#7A4E2D` | The seed (Pip's body) |
| `pip_sprout` | `#2FA36B` | The leaf; success, "connected" |
| `pip_ink` | `#1A1D33` | Text on light, eyes, dark-theme background |
| `pip_coral_alert` | `#E5484D` | Errors, Refuse, destructive confirmations |

Contrast pairs (WCAG AA target, checked in Phase 5 with a real tool):
ink on cream, cream on indigo, ink on clementine, ink on seed (eyes), and
sprout on cream for success text. Clementine is never used
for body text on cream. Each value marked here is a design choice, not a
measured result: contrast ratios are **UNKNOWN** until Phase 5 checks them.

### 5.3 App icon (adaptive)

- Background layer: flat `pip_indigo`.
- Foreground layer: the pocket in `pip_cream`, stitch line in
  `pip_clementine`, the seed in `pip_seed`, eyes in `pip_ink` with a
  small cream highlight dot, leaf in `pip_sprout`.
- The whole mark stays inside the central safe zone of the 108 dp
  foreground (Android's adaptive-icon guidance; exact safe-zone size to be
  confirmed against
  <https://developer.android.com/develop/ui/views/launch/icon_design_adaptive>).
- Monochrome layer (themed icons): pocket outline, seed outline, leaf,
  two eye dots.
- Debug build: same icon with a small clementine "D" badge at bottom-right,
  so it is never confused with a future release build.
- All icons are drawn as original vector drawables in Phase 5. No external
  asset, font, or icon pack.

### 5.4 Notification small icon

Single-color silhouette: the filled pocket with the leaf rising above its
top edge; no seed, eyes, or stitch at this size. Must read at 24 dp — a
risk to check on a device in Phase 5 (fallback: drop the leaf, keep the
pocket). The state is carried by the notification text (S1), not by the
icon.

### 5.5 Pip's face on Home (S2)

The eyes, the leaf, and the seed's height in the pocket are the only
animated parts.

| State | Animation |
|---|---|
| Ready | Eyes open, slow blink every few seconds |
| Listening | Pupils slightly larger, seed rises a little |
| Thinking | Eyes look up, leaf sways gently |
| Speaking | Gentle bounce of the seed |
| Waiting for approval | Eyes look at the approval button |
| Success | One wink, leaf perks up |
| Error | Eyes half-closed, leaf droops, no motion |
| Stopping | Seed slides down into the pocket |

Animations respect the system "remove animations" setting.

### 5.6 Type and shape

- Type: the platform default sans-serif (no bundled font in v1). Titles
  medium weight, body regular.
- Shapes: generous rounded corners (pocket-like) on cards and buttons;
  the Talk button is a large rounded pocket shape in clementine.

## 6. Checklist for Phase 5

- [ ] Strings written in `values/` (FR) and `values-en/` (EN) from section 4.
- [ ] Vector drawables drawn from section 5, never from an external file.
- [ ] Debug label and badge distinct (SEC-5).
- [ ] Contrast of section 5.2 pairs checked and recorded.
- [ ] No forbidden word from 3.2 in any visible string.
