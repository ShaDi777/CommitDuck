# Debug Duck

Rubber duck debugging inside the IDE: open the **Debug Duck** tool window (right sidebar), describe the problem, and
the duck asks leading questions until you find the cause yourself.

```
src/main/kotlin/ru/itmo/debugduck/
  LocalDebugDuck.kt              offline mode: question pool and the logic that picks the next question
  DebugDuckPrompt.kt             LLM mode: system prompt, chat history -> API messages
  LlmClient.kt                   OpenAI-compatible Chat Completions client with user-readable errors
  MiniJson.kt                    tiny JSON writer/reader, so no JSON library is needed on the IDE classpath
  DebugDuckService.kt            per-project conversation, picks offline or LLM mode, falls back on errors
  DebugDuckCredentials.kt        API key in the IDE password safe
  ChatHtml.kt                    chat history -> HTML for the Swing JEditorPane
  DebugDuckToolWindowFactory.kt  the tool window UI
```

## Offline mode (default)

No network, no setup. `LocalDebugDuck` combines three sources of questions:

1. **A debugging checklist** (`DebugPhase`), asked in order: expected vs. actual behaviour -> reproduce -> what changed
   -> locate -> assumptions -> experiment -> explain the code line by line. A step is skipped if the user already
   covered it ("it fails *every time*" skips "Can you reproduce it?").
2. **Light hints by topic** (`DebugTopic`): if the message mentions a well-known kind of bug (compile error, null,
   exception, test, build, performance, concurrency, network, database, UI, environment/git, wrong result), the duck
   asks a targeted question. Keywords are English and Russian. Topic hints alternate with checklist questions. The
   topic of the first message that has one is remembered as the main problem, so the duck keeps coming back to it
   when later answers have no keywords ("yes, they behave").
3. **Reactions**: "I don't know / stuck" -> a concrete strategy (bisect the code, log before/after, ...);
   "fixed / it works" -> congratulations and a question about the root cause; attached code -> "read it to me line by line".

Questions are never repeated within one conversation. To add questions, extend the lists in `LocalDebugDuck.kt`;
`LocalDebugDuckTest` checks the selection logic.

**Attach editor selection** sends the code selected in the active editor along with the message (up to 6000
characters). Without a selection, only the file name is sent.

## LLM mode

*Settings | Tools | Commit Duck | Debug Duck* -> **Answer with a language model**. Any API that implements the OpenAI
Chat Completions format works; only the URL, model and key differ:

| Provider            | API URL                          | Model (example)        | API key          |
|---------------------|----------------------------------|------------------------|------------------|
| OpenAI              | `https://api.openai.com/v1`      | `gpt-5-mini`           | required         |
| OpenRouter          | `https://openrouter.ai/api/v1`   | `openai/gpt-5-mini`    | required         |
| DeepSeek            | `https://api.deepseek.com/v1`    | `deepseek-chat`        | required         |
| Ollama (local)      | `http://localhost:11434/v1`      | `llama3.2`, `qwen2.5-coder` | leave empty |
| LM Studio (local)   | `http://localhost:1234/v1`       | the loaded model       | leave empty      |

Model names change often, check the provider's model list. The API key is stored in the IDE password safe (the OS
keychain by default), not in `commitDuck.xml` and not in the repository. For development, the
`COMMIT_DUCK_LLM_API_KEY` environment variable is used when no key is saved (handy with `./gradlew runIde`).

### Request format

`POST {API URL}/chat/completions`:

```json
{
  "model": "gpt-5-mini",
  "messages": [
    {"role": "system", "content": "You are Debug Duck, a friendly rubber duck ..."},
    {"role": "assistant", "content": "Quack! I'm your debug duck. ..."},
    {"role": "user", "content": "My test fails\n\nCode from ParserTest.kt (Kotlin):\n```kotlin\n...\n```"}
  ],
  "stream": false
}
```

The whole conversation (the last 30 messages) is sent every time, so the model sees the context. Plugin notices
(errors) are not sent. `temperature` and `max_tokens` are not set, because reasoning models reject them; the length is
limited by the prompt instead. `<think>...</think>` blocks of reasoning models are removed from the answer.

The system prompt is `DebugDuckPrompt.SYSTEM_PROMPT`: the duck asks one or two specific leading questions, refers to
the details the user mentioned, doesn't hand over a complete solution unless asked or clearly stuck, answers in the
user's language and keeps answers under about 80 words.

### Errors

The request runs in the background and can be cancelled with **New Conversation**. Every failure becomes a readable
notice in the chat (wrong URL, server not running, timeout, HTTP 401/403/404/429/5xx with the server's message,
non-JSON answer, empty answer), and the duck continues with an offline question, so the conversation never gets stuck.
The timeout is configurable (60 seconds by default).

## Tests

`src/test/kotlin/ru/itmo/debugduck/`: question selection, JSON, prompt building, HTML rendering, and the LLM client against a
fake HTTP server on localhost (success, auth errors, Ollama-style errors, timeout, connection refused, bad JSON).
