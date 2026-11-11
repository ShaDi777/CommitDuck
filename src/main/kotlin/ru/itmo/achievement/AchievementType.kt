package ru.itmo.achievement

enum class AchievementType(
    val id: String,
    val animation: String,
    val title: String,
    val description: String,
    val requiredChars: Int,
) {
    FIRST_STEPS1(
        id = "first_steps",
        animation = "egg",
        title = "Первые шаги!",
        description = "Ты набрал первые 200 символов. Отличное начало!",
        requiredChars = 200,
    ),

    GETTING_STARTED(
        id = "getting_started",
        animation = "happy",
        title = "Набираем обороты!",
        description = "Ты набрал первые 1 000 символов. Так держать!",
        requiredChars = 1000,
    ),

    WARM_UP(
        id = "warm_up",
        animation = "ovation",
        title = "Разогрелся!",
        description = "Уже 5 000 символов! Утка гордится тобой.",
        requiredChars = 5000,
    ),

    HARD_WORKER(
        id = "hard_worker",
        animation = "wow",
        title = "Неутомимый печатник!",
        description = "Ты набрал 10 000 символов. Так держать!",
        requiredChars = 10_000,
    ),

    KEYBOARD_MASTER(
        id = "keyboard_master",
        animation = "cool",
        title = "Мастер клавиатуры!",
        description = "50 000 символов! Ты настоящий мастер.",
        requiredChars = 50_000,
    ),

    CODING_LEGEND(
        id = "coding_legend",
        animation = "cool",
        title = "Легенда кодинга!",
        description = "100 000 символов! Невероятный результат!",
        requiredChars = 100_000,
    ),
}