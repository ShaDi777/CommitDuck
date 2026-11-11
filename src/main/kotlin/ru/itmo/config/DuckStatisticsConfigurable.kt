package ru.itmo.config

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.panel
import ru.itmo.achievement.AchievementType
import ru.itmo.statistics.DuckStatistics
import java.text.NumberFormat
import java.util.*
import javax.swing.JSeparator
import javax.swing.SwingConstants

class DuckStatisticsConfigurable : BoundConfigurable(
    MyMessageBundle.message("statistics.settings.title")
) {

    override fun createPanel() = panel {
        val statistics = DuckStatistics.getInstance()
        val typedChars = statistics.typedChars.value
        val numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

        val achievements = AchievementType.entries
            .sortedBy { it.requiredChars }

        val unlockedCount = achievements.count {
            typedChars >= it.requiredChars
        }

        group(MyMessageBundle.message("statistics.group.overview")) {
            row(MyMessageBundle.message("statistics.characters")) {
                label(numberFormat.format(typedChars))
            }

            row(MyMessageBundle.message("statistics.commits")) {
                label(numberFormat.format(statistics.state.commits))
            }

            row(MyMessageBundle.message("statistics.builds")) {
                label(numberFormat.format(statistics.state.successfulBuilds))
            }

            row(MyMessageBundle.message("statistics.tests")) {
                label(numberFormat.format(statistics.state.passedTests))
            }
        }

        group(MyMessageBundle.message("statistics.group.achievements")) {
            row(MyMessageBundle.message("statistics.achievements.unlocked")) {
                label("$unlockedCount / ${achievements.size}")
            }

            achievements.forEach { achievement ->
                val unlocked = typedChars >= achievement.requiredChars
                val remaining = (achievement.requiredChars - typedChars)
                    .coerceAtLeast(0)

                row {
                    panel {
                        row {
                            label(achievement.title)
                                .bold()

                            label(
                                MyMessageBundle.message(
                                    if (unlocked) {
                                        "statistics.status.unlocked"
                                    } else {
                                        "statistics.status.locked"
                                    }
                                )
                            )
                                .applyToComponent {
                                    foreground = JBColor.namedColor(
                                        if (unlocked) {
                                            "CommitDuck.Achievement.Unlocked"
                                        } else {
                                            "CommitDuck.Achievement.Locked"
                                        },
                                        if (unlocked) {
                                            JBColor(0x38965A, 0x7BC88C)
                                        } else {
                                            JBColor(0x8C8C8C, 0x999999)
                                        }
                                    )
                                }
                        }

                        row {
                            label(achievement.description)
                        }

                        row {
                            label(
                                MyMessageBundle.message(
                                    if (unlocked) {
                                        "statistics.achievement.completed"
                                    } else {
                                        "statistics.achievement.remaining"
                                    },
                                    numberFormat.format(remaining)
                                )
                            ).applyToComponent {
                                foreground = JBColor.namedColor(
                                    "Label.infoForeground",
                                    JBColor(0x6B7280, 0xA0A0A0)
                                )
                            }
                        }
                    }
                }

                row {
                    cell(
                        JSeparator(SwingConstants.HORIZONTAL)
                    ).resizableColumn()
                }
            }
        }
    }
}