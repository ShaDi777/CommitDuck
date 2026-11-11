package ru.itmo.debugduck

/** The language the offline duck speaks. */
enum class DuckLanguage {
    ENGLISH,
    RUSSIAN;

    companion object {
        private const val MIN_CYRILLIC = 3

        /**
         * The language of [text]: Russian as soon as there are a few Cyrillic letters, because code names in a Russian
         * message are Latin ("у меня NPE в Parser.kt" has more Latin than Cyrillic letters), while an English message
         * has no Cyrillic at all. `null` if there are no letters ("?", "13"): then the duck keeps its current language.
         */
        fun detect(text: String): DuckLanguage? {
            var cyrillic = 0
            var latin = 0
            for (c in text) {
                when {
                    c in 'а'..'я' || c in 'А'..'Я' || c == 'ё' || c == 'Ё' -> cyrillic++
                    c in 'a'..'z' || c in 'A'..'Z' -> latin++
                }
            }
            return when {
                cyrillic == 0 && latin == 0 -> null
                cyrillic >= MIN_CYRILLIC || cyrillic > latin -> RUSSIAN
                else -> ENGLISH
            }
        }

        /** The language for [languageTag] such as `ru` or `en-US`: Russian for Russian, English for anything else. */
        fun forLocale(languageTag: String): DuckLanguage =
            if (languageTag.substringBefore('-').substringBefore('_').equals("ru", ignoreCase = true)) RUSSIAN else ENGLISH
    }
}

/** "1 line", "3 lines" / "1 строка", "3 строки", "5 строк". */
internal fun linesText(count: Int, language: DuckLanguage): String = when (language) {
    DuckLanguage.ENGLISH -> if (count == 1) "1 line" else "$count lines"
    DuckLanguage.RUSSIAN -> {
        val word = when {
            count % 100 in 11..14 -> "строк"
            count % 10 == 1 -> "строка"
            count % 10 in 2..4 -> "строки"
            else -> "строк"
        }
        "$count $word"
    }
}
