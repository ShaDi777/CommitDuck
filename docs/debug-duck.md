# Debug Duck

Rubber duck debugging inside the IDE: open the **Debug Duck** tool window (right sidebar), describe the problem, and
the duck asks leading questions until you find the cause yourself.

```
src/main/kotlin/ru/itmo/debugduck/
  LocalDebugDuck.kt              offline mode: question pool and the logic that picks the next question
  RussianPhrases.kt              Russian versions of all offline phrases
  DuckLanguage.kt                language detection (Russian / English)
  DebugDuckPrompt.kt             LLM mode: system prompt, chat history -> API messages
  LlmClient.kt                   OpenAI-compatible Chat Completions client: streaming, user-readable errors
  MiniJson.kt                    tiny JSON writer/reader, so no JSON library is needed on the IDE classpath
  DebugChat.kt                   chat model: messages, attached code, IDE problems
  DebugDuckContext.kt            IDE problems: editor errors, last build, last test run (+ the two listeners)
  AskDebugDuckAction.kt          "Ask Debug Duck" in the editor context menu
  DebugDuckService.kt            per-project conversation: offline or LLM, streaming, stop, solved
  DebugDuckHistory.kt            keeps the conversation between IDE restarts (workspace file)
  ConversationCodec.kt           conversation <-> JSON
  DebugDuckStats.kt              number of problems solved with the duck
  DebugDuckCredentials.kt        API key in the IDE password safe
  ChatHtml.kt                    chat history -> HTML for the Swing JEditorPane
  DebugDuckToolWindowFactory.kt  the tool window UI
```

## Talking to the duck

- Type the problem and press **Enter** (Shift+Enter for a new line).
- **Ask Debug Duck** in the editor's context menu attaches the selection (or 3 lines around the caret) with the errors
  highlighted there and opens the duck: just type the question, or press Enter right away. **Attach from editor** under
  the input field does the same from the tool window. The attachment is shown above the input field and can be removed.
- **IDE errors are sent automatically** (can be turned off in the settings): errors highlighted in the open file (in the
  selection, if there is one), errors of the last build (IntelliJ build system; Gradle-delegated builds don't report
  here) and failed tests of the last run. Each error is sent once per conversation.
- **Solved!** in the tool window header (or a message like "fixed it" / "заработало"): the duck congratulates, the pet
  in the status bar is happy (`Event.ProblemSolved`), and the problem is counted. The counter is shown under the input.
- The small duck next to the mode line works while the Debug Duck answers and otherwise mirrors the pet.
- The conversation survives IDE restarts (stored in `.idea/workspace.xml`, which is not committed).
  **New Conversation** forgets it.

## Offline mode (default)

No network, no setup. `LocalDebugDuck` combines several sources of questions:

1. **IDE errors** are quoted back ("The IDE reports an error at Main.java:13: "';' expected"") and pick the topic.
2. **A debugging checklist** (`DebugPhase`), asked in order: expected vs. actual behaviour -> reproduce -> what changed
   -> locate -> assumptions -> experiment -> explain the code line by line. A step is skipped if the user already
   covered it ("it fails *every time*" skips "Can you reproduce it?") or if it makes no sense for the main problem
   (`DebugTopic.irrelevantPhases`: no "Can you reproduce it?" for a compile error).
3. **Light hints by topic** (`DebugTopic`): compile error, null, exception, test, build, performance, concurrency,
   network, database, UI, environment/git, wrong result. Keywords are English and Russian. Topic hints alternate with
   checklist questions. The topic of the first message that has one is remembered as the main problem, so the duck keeps
   coming back to it when later answers have no keywords ("yes, they behave").
4. **Reactions**: "I don't know / stuck" -> a concrete strategy; "fixed / it works" -> congratulations;
   attached code -> "read it to me line by line".

Questions are never repeated within one conversation, also after a restart (`LocalDebugDuck.restore`).

**Language**: the duck answers in the language of the user's message (Russian as soon as it has a few Cyrillic letters,
because code names are Latin), or always in the language chosen in the settings. All phrases are kept in English in
`LocalDebugDuck.kt` and translated in `RussianPhrases.kt`; `DuckLanguageTest` fails if a phrase has no translation.

## LLM mode

*Settings | Tools | Commit Duck | Debug Duck* -> **Answer with a language model**. Any API that implements the OpenAI
Chat Completions format works; only the URL, model and key differ. **Test Connection** tries the values before they are
applied.

| Provider            | API URL                          | Model (example)             | API key     |
|---------------------|----------------------------------|-----------------------------|-------------|
| OpenAI              | `https://api.openai.com/v1`      | `gpt-5-mini`                | required    |
| OpenRouter          | `https://openrouter.ai/api/v1`   | `openai/gpt-5-mini`         | required    |
| DeepSeek            | `https://api.deepseek.com/v1`    | `deepseek-chat`             | required    |
| Ollama (local)      | `http://localhost:11434/v1`      | `llama3.2`, `qwen2.5-coder` | leave empty |
| LM Studio (local)   | `http://localhost:1234/v1`       | the loaded model            | leave empty |

Model names change often, check the provider's model list. The API key is stored in the IDE password safe (the OS
keychain by default), not in `commitDuck.xml` and not in the repository. For development, the
`COMMIT_DUCK_LLM_API_KEY` environment variable is used when no key is saved (handy with `./gradlew runIde`).

**Privacy**: before the first message to a remote server, the user confirms that messages, attached code and IDE
errors are sent there. Local servers (localhost) don't ask.

### Request format

`POST {API URL}/chat/completions`:

```json
{
  "model": "gpt-5-mini",
  "messages": [
    {"role": "system", "content": "You are Debug Duck, a friendly rubber duck ..."},
    {"role": "assistant", "content": "Quack! I'm your debug duck. ..."},
    {"role": "user", "content": "Why?\n\nCode from Main.java:13-15 (JAVA):\n```java\n...\n```\n\nProblems reported by the IDE:\n- Error Main.java:13: ';' expected"}
  ],
  "stream": true
}
```

The whole conversation (the last 30 messages) is sent every time, so the model sees the context. Plugin notices are
not sent. `temperature` and `max_tokens` are not set, because reasoning models reject them; the length is limited by
the prompt instead.

The answer is **streamed** (server-sent events) and shown as it arrives; servers that ignore `stream` and return plain
JSON work too. `<think>...</think>` blocks of reasoning models are hidden. **Stop** (the Send button while the duck
answers) cancels the request and keeps what was written so far.

The system prompt is `DebugDuckPrompt.SYSTEM_PROMPT`: the duck asks one or two specific leading questions, refers to
the details the user mentioned, doesn't hand over a complete solution unless asked or clearly stuck, answers in the
user's language (or the one chosen in the settings) and keeps answers under about 80 words.

### Errors

Every failure becomes a readable notice in the chat (wrong URL, server not running, timeout, HTTP 401/403/404/429/5xx
with the server's message, non-JSON answer, error in the middle of a stream, empty answer), and the duck continues
with an offline question, so the conversation never gets stuck. The timeout (60 seconds by default) limits the wait
for the start of the answer.

## Tests

`src/test/kotlin/ru/itmo/debugduck/`: question selection, Russian phrases, IDE problems, composing messages, saving
conversations, JSON, prompt building, HTML rendering, and the LLM client against a fake HTTP server on localhost
(success, streaming, cancellation, auth errors, Ollama-style errors, timeout, connection refused, bad JSON).
`src/test/kotlin/ru/itmo/pet/PetModelTest.kt` covers `Event.ProblemSolved`.
