package ru.itmo.service

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import ru.itmo.achievement.AchievementType

@Service(Service.Level.APP)
class AchievementService {

    private val achievementState = AchievementState.getInstance()
    private val notificationService = DuckNotificationService.getInstance()

    /**
     * Checks whether new character-count achievements have been reached.
     */
    @Synchronized
    fun checkTypedChars(typedChars: Int) {
        for (achievement in AchievementType.entries) {
            if (typedChars >= achievement.requiredChars &&
                !achievementState.isUnlocked(achievement)
            ) {
                achievementState.unlock(achievement)
                notificationService.showAchievement(achievement)
            }
        }
    }

    fun isUnlocked(achievement: AchievementType): Boolean {
        return achievementState.isUnlocked(achievement)
    }

    companion object {
        fun getInstance(): AchievementService = service()
    }
}