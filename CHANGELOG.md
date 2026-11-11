<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Commit-duck Changelog

## [Unreleased]

### Added

- Debug Duck tool window: discuss a problem with the duck, which asks leading questions from a built-in pool
  (debugging checklist and topic hints), optionally with the selected code attached.
- Optional LLM mode for the Debug Duck via any OpenAI-compatible API; the API key is kept in the IDE password safe.
- Debug Duck sees IDE errors: highlighted errors, the last build and failed tests are sent along with messages.
- "Ask Debug Duck" in the editor context menu; attachments are shown as a removable chip.
- The offline duck speaks Russian and skips checklist questions that don't fit the problem.
- LLM answers are streamed and can be stopped; Test Connection in the settings; a privacy confirmation for remote APIs.
- "Solved!" makes the pet happy and counts solved problems; the conversation survives IDE restarts.
