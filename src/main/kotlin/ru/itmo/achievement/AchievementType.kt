package ru.itmo.achievement

import ru.itmo.config.MyMessageBundle

enum class AchievementType(
    val id: String,
    val animation: String,
    private val titleKey: String,
    private val descriptionKey: String,
    val requiredChars: Int,
) {
    FIRST_STEPS1(
        id = "first_steps",
        animation = "egg",
        titleKey = "achievement.first_steps.title",
        descriptionKey = "achievement.first_steps.description",
        requiredChars = 200,
    ),

    GETTING_STARTED(
        id = "getting_started",
        animation = "happy",
        titleKey = "achievement.getting_started.title",
        descriptionKey = "achievement.getting_started.description",
        requiredChars = 1_000,
    ),

    WARM_UP(
        id = "warm_up",
        animation = "ovation",
        titleKey = "achievement.warm_up.title",
        descriptionKey = "achievement.warm_up.description",
        requiredChars = 5_000,
    ),

    HARD_WORKER(
        id = "hard_worker",
        animation = "wow",
        titleKey = "achievement.hard_worker.title",
        descriptionKey = "achievement.hard_worker.description",
        requiredChars = 10_000,
    ),

    KEYBOARD_MASTER(
        id = "keyboard_master",
        animation = "cool",
        titleKey = "achievement.keyboard_master.title",
        descriptionKey = "achievement.keyboard_master.description",
        requiredChars = 50_000,
    ),

    CODING_LEGEND(
        id = "coding_legend",
        animation = "cool",
        titleKey = "achievement.coding_legend.title",
        descriptionKey = "achievement.coding_legend.description",
        requiredChars = 100_000,
    );

    val title: String
        get() = MyMessageBundle.message(titleKey)

    val description: String
        get() = MyMessageBundle.message(descriptionKey)
}