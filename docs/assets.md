# Duck animations: from Telegram stickers to plugin assets

The duck's animations are made from Telegram animated stickers. The raw stickers are kept in the repository and
converted into PNG sprite sheets, which the plugin loads at runtime.

```
assets/stickers/<state>.tgs          raw Telegram stickers (source of truth, not packaged into the plugin)
tools/sticker_to_sprites.py          converter
src/main/resources/duck/status/      generated sprite sheets (committed, packaged into the plugin)
```

## 1. Get a sticker as a `.tgs` file

Only **animated** stickers work. They are `.tgs` files: gzipped Lottie (vector) animations, 512×512, usually 60 fps and
up to 3 seconds long. Static stickers (`.webp`) and video stickers (`.webm`) are not supported by the converter.

**Option A. Telegram Desktop (simplest):** open a chat with the sticker, right-click it and choose **Save As…**.
Telegram saves it as a `.tgs` file.

**Option B. Bot API (a whole pack at once, needs `curl` and `jq`):** (untested)

1. Create a bot with [@BotFather](https://t.me/BotFather) and copy its token.
2. Find the pack name in its share link: `https://t.me/addstickers/<pack-name>`.
   If you only have a single sticker, send it to your bot and run
   `curl -s "https://api.telegram.org/bot$TOKEN/getUpdates" | jq '.result[].message.sticker'`: the output contains
   `set_name` (the pack name) and `file_id` (that sticker).
3. Download the animated stickers of the pack:
   ```bash
   TOKEN=123456:ABC...   # your bot token
   PACK=<pack-name>
   mkdir -p stickers && cd stickers
   curl -s "https://api.telegram.org/bot$TOKEN/getStickerSet?name=$PACK" > pack.json
   # file_id -> file_path -> .tgs file; files are named <index>-<file_unique_id>.tgs
   jq -r '.result.stickers | to_entries[] | select(.value.is_animated) | "\(.key) \(.value.file_unique_id) \(.value.file_id)"' pack.json |
     while read -r index uid id; do
       path=$(curl -s "https://api.telegram.org/bot$TOKEN/getFile?file_id=$id" | jq -r .result.file_path)
       curl -s -o "$index-$uid.tgs" "https://api.telegram.org/file/bot$TOKEN/$path"
     done
   ```
   `index` is the sticker's 0-based position in the pack as shown in Telegram, and `pack.json` has the emoji of every sticker,
   which helps to find the right one.

## 2. Name it after the state and put it into `assets/stickers/`

The file name is the state name: the lowercase name of the `PetState` enum value (`PetState.SLEEPING` → `sleeping.tgs`).

| File | State | Shown when |
|---|---|---|
| `idle.tgs` | `IDLE` | the user hasn't typed for 30 s (configurable) |
| `working.tgs` | `WORKING` | the user is typing in a code editor |
| `sleeping.tgs` | `SLEEPING` | the user hasn't typed for 10 min (configurable) |
| `deleted.tgs` | not used yet | spy duck burning a note; fits a reaction to deleting code or files |

Sprite sheets of a sticker without a `PetState` value are packaged but never loaded, so a sticker can be converted
before the state exists.

To see what's inside a `.tgs` file without Telegram: `gunzip -c sleeping.tgs | head -c 300`. The `nm` field often has a
descriptive name such as `_DUCK25_ZZZ_OUT`.

## 3. Install the converter's dependencies (once)

Python 3.9+ is required. Use a virtual environment so the system Python stays untouched:

```bash
python3 -m venv .venv
.venv/bin/pip install -r tools/requirements.txt
```

`rlottie-python` ships prebuilt wheels with Samsung's rlottie renderer (the same one Telegram uses), so nothing else
needs to be installed.

## 4. Generate the sprite sheets

```bash
# all stickers
.venv/bin/python tools/sticker_to_sprites.py assets/stickers/*.tgs --preview build/duck-preview

# or a single one
.venv/bin/python tools/sticker_to_sprites.py assets/stickers/sleeping.tgs --preview build/duck-preview
```

For every sticker this writes into `src/main/resources/duck/status/`:

| File | Content |
|---|---|
| `<state>.png` | sprite sheet, one frame per cell, frames left-to-right then top-to-bottom, 18 px high |
| `<state>@2x.png` | the same at 36 px for HiDPI/Retina screens |
| `<state>.properties` | `frameCount`, `fps`, `frameWidth`, `frameHeight` (logical px) and `columns`, read by `DuckAnimation.kt` |

What the converter does:

1. Renders the sticker at 512×512 and resamples it from 60 to 30 fps (`--fps` to change; 3 s → 90 frames).
2. Crops the empty space around the duck. The crop box is shared by all frames, so the duck doesn't jump around.
3. Scales the frames down in premultiplied alpha, so edges have no dark fringe on light or dark themes.

## 5. Check the result

Open `build/duck-preview/<state>.gif`. It plays the animation on a light and a dark background side by side, the same
colors as the IntelliJ Light and Dark themes. The GIF is for review only and is not used by the plugin.

Then run the `Run IDE with Plugin` configuration (Gradle task `runIde`), open any project and look at the right end of
the status bar. To check `sleeping` without waiting 10 minutes, lower the timeout in
**Settings → Tools → Commit Duck**.

Commit both the `.tgs` file and the generated files in `src/main/resources/duck/`, so building the plugin doesn't need
Python.

## Adding a new state

1. Put the sticker into `assets/stickers/happy.tgs` and generate the sprite sheets as above.
2. Add `HAPPY` to `PetState` in `src/main/kotlin/DuckStateMachine.kt`, plus the transition rules in `DuckStateMachine.handle()`.

The status bar widget loads an animation for **every** `PetState` value, so a state without sprite sheets fails with
`Duck animation resource not found: /duck/status/happy.properties`.

## Changing the size

Sizes are configured in `SIZES` at the top of `tools/sticker_to_sprites.py` (name → frame height in px). For a big duck
in the tool window, add `"large": 128` and load it with `DuckAnimation.load(state, "large")`. A 128 px state costs about
3 MB, so keep only the sizes that are actually used.

## Sounds

```
assets/sounds/<name>.mp3             raw sounds (source of truth, not packaged into the plugin)
src/main/resources/duck/sounds/      converted WAV files (committed, packaged into the plugin)
```

Java Sound, which the plugin uses to play sounds (`DuckSound.kt`), can't decode MP3, so convert the sound to 16-bit PCM WAV:

```bash
ffmpeg -i assets/sounds/quack.mp3 -map_metadata -1 -c:a pcm_s16le src/main/resources/duck/sounds/quack.wav
```

`quack.wav` plays when the user clicks the duck in the status bar. It can be turned off in
**Settings → Tools → Commit Duck**.
