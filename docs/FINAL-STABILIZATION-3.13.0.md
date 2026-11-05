# WHITEOUT 3.13.0 — final stabilization audit

## Base and scope

Base: `12a798194aea2f597a7d32fe2a200f3e1ebfd251`, branch
`fix/whiteout-313-duo-lobby-audit`. PR #3 supplies the textured lobby UI;
PR #4 contains board/Duo fixes; merged PR #5 supplies pending-dialogue
persistence. Both #3 and #4 remain draft/open. Git ancestry confirms the
base includes `lobby-ui-redesign` and `feature/whiteout-roadmap-stabilization`.
The separate #5 branch is a pre-squash history, not missing work to merge again.
No merge into main, public release, music replacement or balance change.

## Confirmed defects fixed

1. **Offline/nonblocking dialogue loss.** A new narrative replaced the single
   pending reader entry. Continuing alone could silently lose an offline
   partner's earlier dialogue. Each existing reader entry now holds a bounded
   FIFO (256 total). Progress and queue are saved together; only the head may
   be acknowledged. The next cue is sent after the save succeeds. A full queue
   rejects the new event without discarding pending dialogue. Old records
   without `queue` load as an empty tail. Public snapshots omit the entire ledger.
2. **Duo Test Mode restart discrepancy.** Its in-memory reader map disappeared
   on stop/disable/restart even though narrative progress remained completed.
   The adapter now uses the same durable ledger and acknowledgement operation
   in its existing isolated test Store. Explicit `start` redelivers the head;
   human and bot acknowledgements advance independently. No fake network player
   is created. `reset confirm` retains its existing test-file-only scope.
3. **Lost settings drafts.** Reinitializing PartyScreen on resize replaced
   unsaved start coordinates/event and custom difficulty with server values.
   The same tab now preserves its text fields. Preset values are unchanged.
4. **Editor JSON truncation.** Pretty printing could expand a valid network
   definition past the 16000-character EditBox limit. Formatting now falls
   back to complete compact JSON; oversized edits are rejected before replacing
   the draft. Loading and template edits use the same formatting rule.
5. **Board chunk access.** Use/validation, removal and placement could read a
   neighboring unloaded chunk despite the scheduled-tick guard. These paths
   now check all board/support chunks before reading them. Deferred scheduled
   validation is retained.

## Audit coverage and limits

| Area | Evidence / remaining boundary |
| --- | --- |
| Invitations, both ready, launch, FIRST/SECOND, continue-alone, host return | Reviewed Party authorization, revision checks, save/teleport rollback and connection callbacks. Real two-client sequence still required. |
| Dialogue queue, replay, duplicate/out-of-order acknowledgements, restart, disk failure | Added Fabric probes against actual Store/Mixin and production ledger operations. |
| Shared/private/secret progress, variables, objectives, checkpoints, endings | Existing scoped Store probes and three isolated endings retained; added pre-Duo fixture without any new Store fields. |
| Votes, revoting, cinematics, consent to skip | Existing dedicated adapter scenarios and state guards; real simultaneous clients and network delay remain manual. |
| Duo Test Mode | Shared persistence, existing readiness/role/offline-count/photo/timeout probes; packaged command scenarios include both roles and three endings. Not every command is exercised by the existing automated harness. |
| BoardScreen, PartyScreen, PhotoScreen, PersonalScreen, AuthorScreen | Source review; targeted draft and JSON fixes; existing texture/model/UV, transfer, permission, revision and retry checks. No visual certification. |
| 3.12.18 migration | Synthetic legacy DTO/Store fixtures only. No actual user world supplied. Existing grid, zones, device and story field formats preserved. |
| Lake story, generators, circuits/doors, lantern, radio, portraits, weather/cold, objectives, zones, backpack, commands | Existing behavior suites, foundation probes and packaged resource/ABI checks; no balance or scene rewrites. End-to-end gameplay remains manual. |
| Performance / lifecycle | Bounded queues, existing disconnect/shutdown cleanup reviewed, chunk guards added. No FPS benchmark, load soak or leak profiler run. |
| Iris/BSL | No graphical client/shader run. Common/client separation and packaged classes are checked; shader compatibility is not certified. |

## Reproducible verification

Java 21, Gradle 8.10.2, Minecraft 1.21.1, Fabric Loader 0.18.4:

```text
python tools/private_music.py Whiteout-3.12.9-1.21.1.jar --extract src/main/resources
gradlew build -I qa/foundation.gradle -I qa/dialogue-routes.gradle foundationTests routeProbeClient routeProbeServer --no-daemon --console=plain
python -X utf8 tests/run_resources.py
python tools/private_music.py Whiteout-3.12.9-1.21.1.jar --compare build/release/Whiteout-3.13.0-1.21.1.jar
python -u tests/dedicated_lobby.py
```

The complete mod is `build/release/Whiteout-3.13.0-1.21.1.jar`, not the overlay
in `build/libs`. CI retains it as a development artifact, not a public release.
The soundtrack is restored from the existing JAR only during assembly and is
not committed as separate audio files.

### Results from this audit

- Java 21 `build`: PASS; 37 main regression tasks, foundation checks and both
  Fabric route probes passed. New probes cover bounded FIFO, legacy reader
  JSON, independent human/bot queues, restart, duplicate/out-of-order read IDs,
  staging rollback and injected acknowledgement disk failure.
- All 19 `check_*.py` resource suites: PASS. JAR integrity, models, textures,
  registration, mixin ABI and common/client separation checked.
- Existing additional `visualRegression`: PASS (five suites; wind, doors,
  billboard geometry and barricades). This is not graphical-client testing.
- Resource auditor fault injection: 3 tests PASS; 32 shipped model/physical
  layout comparisons and 3835 connected geometry checks PASS.
- Seven private music tracks: byte-identical to the existing source JAR.
  The optional decoded waveform check was skipped locally because FFmpeg was
  unavailable; OGG/CRC and byte-preservation checks passed.
- Packaged dedicated Fabric: four boots PASS, including board persistence,
  support removal, isolated Duo commands, three endings, camera fallback,
  restart, explicit resume and absence of orphan test markers.
- Local full-JAR SHA-256:
  `011a5480bee3b6fb7057ba031d0294cbba3e464b551782145607ba257876fb83`.

Environment issues were resolved without disabling tests: use the direct JDK
21 path rather than the hanging Windows Java shim; allow local Gradle daemon
communication; place Python temporary fixtures in the writable workspace.
One intermediate probe compile ran against classes built before the final
editor helper was added; the full final source set was rebuilt successfully.
Logs containing deliberately corrupted Store input are expected fault-injection
output, not unhandled production failures.

## Required manual acceptance

Use a copy of a real 3.12.18 world and two actual Minecraft clients.

1. Record existing networks, zones/devices, variables, objectives and player
   inventories. Upgrade, save, stop normally, restart and compare. Play the
   complete lake sequence, including its existing reset; verify unrelated progress.
2. Invite/decline/reinvite/accept. Test both readiness buttons and stale screens.
   Disconnect FIRST before launch, then during story; SECOND continues alone.
   Reconnect FIRST and verify original FIRST/SECOND roles and control restoration.
   Repeat with SECOND disconnecting and with both clients reconnecting after restart.
3. While SECOND is offline, enqueue two different dialogue events. Restart,
   reconnect SECOND and read both in order. Retry the head; interrupt it; ensure
   neither interruption nor duplicate acknowledgement discards the next entry.
   Repeat with a conditional route and personal/secret dialogues.
4. Choose different shared answers, change one vote, submit repeated choices,
   disconnect during voting. Verify one commit. Skip a shared scene first with
   one vote, then two; test reconnect and server restart during the scene.
5. At GUI Scale 2/3/4 and two window sizes, open all five lobby screens. Inspect
   all board orientations, seams, borders, text clipping and buttons. Type drafts
   in card/start/custom difficulty/author fields, resize, then save and reopen.
   Load a near-limit JSON definition and confirm it remains valid and complete.
6. Upload/replace/delete/reorder five photos; drag/rotate/crop; resize during
   editing and close during file selection. Restart and compare hashes/layout.
   Test two editors, stale revisions and failed transfers.
7. Place a board across a chunk boundary; unload its neighboring chunk, use/remove
   a visible part, reload and verify no forced generation, duplicated drop or
   persistent unsupported part. Confirm all four orientations after restart.
8. Run `/whiteout test-duo` commands from `DUO-TEST-MODE-RU.md`, including manual
   reading, delayed/missing camera, role SECOND, stop/start, disable/enable and
   restart while a dialogue is pending. Confirm real world progress and online
   players are unaffected and the marker disappears.
9. Repeat representative weather, lantern, radio, portraits, cutscene, backpack
   and board tests with Iris/BSL on and off. Record visual evidence and FPS.

A passing automated build is a candidate for these playtests, not a claim that
all gameplay, visual layouts, real-world migrations or shaders are verified.
