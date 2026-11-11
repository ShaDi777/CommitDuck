package ru.itmo.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import ru.itmo.achievement.AchievementType
import ru.itmo.ui.AchievementPopup

@Service(Service.Level.APP)
class DuckNotificationService {

    fun showAchievement(achievement: AchievementType) {
        ApplicationManager.getApplication().invokeLater {
            AchievementPopup(achievement).show()
        }
    }

    companion object {
        fun getInstance(): DuckNotificationService = service()
    }
}