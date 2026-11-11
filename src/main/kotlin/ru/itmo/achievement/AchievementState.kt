import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.XCollection
import ru.itmo.achievement.AchievementType

@Service(Service.Level.APP)
@State(
    name = "CommitDuckAchievements",
    storages = [Storage("commitDuckAchievements.xml")]
)
class AchievementState :
    SimplePersistentStateComponent<AchievementState.Options>(Options()) {

    class Options : BaseState() {
        @get:XCollection
        var unlockedIds: MutableSet<String> = mutableSetOf()
    }

    fun isUnlocked(achievement: AchievementType): Boolean =
        achievement.id in state.unlockedIds

    fun unlock(achievement: AchievementType) {
        state.unlockedIds.add(achievement.id)
    }

    companion object {
        fun getInstance(): AchievementState = service()
    }
}