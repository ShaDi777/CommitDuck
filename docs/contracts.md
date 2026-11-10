# Component contracts

This document defines the shared contracts that let the pet's components
(UI, pet logic, persistence) talk to each other without knowing each other's
internals.

## States (`PetState`)

```kotlin
enum class PetState {
    IDLE, WORKING, HAPPY, SAD, SLEEPING
}
```

Adding a new state = add one enum constant + (optionally) an icon file
`duck_<state>.svg` + a phrase pool entry. No other logic needs to change.

## Events (`PetEvent`)

| Event | Payload | Meaning |
| ----- | ------- | ------- |
| `Activity` | `typedChars: Int` | User typed in the editor |
| `IdleStarted` | – | First idle threshold reached (bored) |
| `IdleExtended` | – | Second idle threshold (sad) |
| `Sleep` | – | Long idle → asleep |
| `WakeUp` | – | User returned → wake up |
| `DuckClicked` | – | User clicked the duck |
| `Commit` | – | A commit was created / task finished |
| `ThresholdReached` | `chars: Long` | Activity milestone reached |

## State machine (`PetModel`)

- `PetModel.handle(PetEvent)` → may transition to a new `PetState`.
- `PetModel.addListener((PetState) -> Unit)` → notified on every actual state change.
- `PetModel.state` → current state.

Current mapping:

| Event | Resulting state |
| ----- | --------------- |
| `Activity` / `WakeUp` | `WORKING` |
| `IdleStarted` | `IDLE` |
| `IdleExtended` | `SAD` |
| `Sleep` | `SLEEPING` |
| `DuckClicked` | `HAPPY` |
| `Commit` / `ThresholdReached` | `HAPPY` |

## Phrases (`MotivationPhrases`)

- `random()` → a random general message.
- `forState(PetState)` → a random message from the state-specific pool.

## Persistence (`PetPersistence`)

Application-scoped `PersistentStateComponent` (stored in `commitduck.xml`):

| Field | Purpose |
| ----- | ------- |
| `typedCharacters: Long` | Total characters typed |
| `lastPetState: String` | Last known state (restored on restart) |
| `isAwake: Boolean` | Whether the duck was awake at shutdown |

Access via `PetPersistence.getInstance()`.

## Data flow (Day 1)

```
Editor / UI  --PetEvent-->  PetModel.handle()  --new PetState-->  listeners
                                                                    |
                                                            PetPanel.render()
                                                            PetPersistence.save()
```