# MVP test scenarios

Manual verification checklist for the CommitDuck MVP (Day 1–2). Mark a scenario
as passed only when the expected result is observed.

## Setup

1. Start the plugin: `./gradlew runIde`.
2. Open the CommitDuck tool window (View → Tool Windows → CommitDuck).

## Scenarios

### S1 — Plugin launches without breaking the IDE
- [ ] IDE starts with the plugin installed.
- [ ] The IDE interface is intact (no exceptions in the log).

### S2 — Duck is visible in a tool window
- [ ] The CommitDuck tool window is available and opens.
- [ ] A duck image and its status line are visible.

### S3 — Duck shows its current state image
- [ ] The image matches the current state (`IDLE` by default).
- [ ] Switching state (see S4–S7) swaps the image accordingly.

### S4 — Activity → WORKING
- [ ] On an `Activity` event the duck becomes `WORKING` and shows the working image.

### S5 — Idle → bored / sad / asleep
- [ ] After the first idle threshold the duck is `IDLE` (bored).
- [ ] After the second threshold the duck is `SAD`.
- [ ] After the long-idle threshold the duck is `SLEEPING`.

### S6 — Returning user → wake up
- [ ] After a period of idle, the first new `Activity` wakes the duck into `WORKING`.

### S7 — Commit / milestone → HAPPY
- [ ] On a `Commit` event the duck becomes `HAPPY` and shows a celebrating image + message.

### S8 — Click interaction
- [ ] Clicking the duck transitions it to `HAPPY`.
- [ ] A random motivational phrase appears.

### S9 — Persistence across restart
- [ ] The duck's last state and the character counter are restored after an IDE restart
      (i.e. they are not reset to defaults).

## Notes

- Thresholds and idle timings are configured as constants in the pet logic
  (Day 2) and will be user-configurable later.
- State changes that don't naturally occur (e.g. `Commit`) can be triggered from
  a debug action or a test event while the editor tracking isn't wired up yet.