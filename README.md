# CommitDuck

`Commit Duck` — плагин для JetBrains IDE, представляющий собой виртуального питомца и небольшого напарника разработчика. Утка живёт прямо в среде разработки, реагирует на действия пользователя и сопровождает его в процессе работы: радуется активности, коммитам и завершению задач, предлагает мотивирующие фразы, а при длительном отсутствии активности может скучать или засыпать.

Основная идея — сделать привычный процесс разработки немного веселее, добавив в него игровые механики и элемент эмоционального взаимодействия.

## Overview

This repository implements an IntelliJ Platform plugin.

## Duck animations

The duck's animations are generated from Telegram animated stickers in `assets/stickers/`.
See [docs/assets.md](docs/assets.md) for how to download a sticker and convert it into sprite sheets for the plugin.

## Debug Duck

Stuck on a bug? Open the **Debug Duck** tool window and talk the problem through with the duck. It asks leading
questions from a built-in debugging checklist, or, if enabled in *Settings | Tools | Commit Duck*, uses any
OpenAI-compatible language model (OpenAI, OpenRouter, a local Ollama, ...). See [docs/debug-duck.md](docs/debug-duck.md).
