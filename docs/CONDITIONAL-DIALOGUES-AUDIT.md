# Conditional Dialogues 2.0: extension audit

Base: `feature/whiteout-3.12.19`, `5146801`; original binary API: Whiteout 3.4.2, inspected with `javap -c -private`.

- Existing `DialogueManager` owns scripts, aliases, NPC bindings, lines, choices and auto replies. Choice conditions/actions already use `StoryRules`. No replacement dialogue registry is needed.
- `StoryRules.Condition.test` evaluates typed boolean/integer story variables. Server `Store.data.storyVariables` is the authoritative source; a missing variable or mismatched type must be UNKNOWN, not an implicit zero/false.
- Director's DIALOGUE event invokes `DialogueManager.openDirector(String,int)`. Both `open(String)` and `openDirector(String,int)` look up the script before opening the original `DialogueScreen`.
- The existing build redirects the two non-NPC screen calls through `StoryDialogueHud.route`. This preserves the original lines, portraits and interactive screen; informational lines already have a FIFO HUD queue.
- `DialogueTriggers.playSimpleScene` is the existing adapter for generated dialogue-only scenes. Its existence check must explicitly accept route references.
- `WhiteoutWorldState.network`, `generator` and `weather` expose server facts without loading chunks. Network readings can be CACHED; conditional routes accept only AVAILABLE readings using `WinterCrisisConditions.observed` and its three-valued `Truth`.
- Strong blizzard uses the existing authoritative active weather zone's `blizzard` flag; no new weather threshold or visual smoothing is introduced.
- Dialogue scripts, Director library, trigger definitions and portrait rendering are client-side. Route metadata belongs to the server world; scripts remain in the existing client catalogue, as before.

Integration: one client-only Mixin at the verified `DialogueManager.open/openDirector` entry points intercepts the reserved `whiteout-route:<id>` reference before ordinary lookup. There is no public route/event callback in the base API. Ordinary calls pass through unchanged unless waiting behind a route resolution; a bounded FIFO barrier preserves their invocation order. The selected script returns to the existing entry point and original screen/HUD. No additional ASM transformation is needed.

Routes contain IDs, references, priorities and condition parameters only. The new `whiteout-dialogue-routes.json` does not contain dialogue text and does not modify `dialogues.json`. Writes reuse `GridFileIO.writeAtomic`, reject stale editor revisions and refuse to replace a damaged file.
