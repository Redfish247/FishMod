package fishmod.features.gamble

import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputSetting
import fishmod.features.FishModScreen.LabelSetting
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.gamble.GambleSettings as S

// The Gamble Points tab in /fm
object GambleMenu {

    fun column(): Column {
        val col = Column("Gamble Points")

        val main = Feature("Gamble Points", S::gambleEnabled)
        main.sub.add(LabelSetting("/gamble <amount|NN%|all>", "Results show in your chat only; /gamble top shows the board"))
        main.sub.add(InputSetting("Server URL", "Gamble worker base URL", S::gambleApiBase))
        main.sub.add(ToggleSetting("Playtime Tracking", "Send input counts every 5 minutes to earn points (counts only, never keys or text)", S::gamblePlaytime))
        main.sub.add(ToggleSetting("Show Points Earned", "Print points from each playtime block in chat", S::gambleShowPoints))
        main.sub.add(ToggleSetting("Leaderboard", "Allow /gamble top", S::gambleLeaderboard))
        col.features.add(main)

        val crown = Feature("Crown Badge", S::gambleCrown)
        crown.sub.add(ToggleSetting("Chat", "", S::gambleCrownChat))
        crown.sub.add(ToggleSetting("Nametag", "", S::gambleCrownNametag))
        crown.sub.add(ToggleSetting("Tab List", "", S::gambleCrownTab))
        col.features.add(crown)
        return col
    }

    fun describe(name: String): String? = when (name) {
        "Gamble Points" -> "Playtime points, /gamble and the crown for the top player"
        "Crown Badge" -> "Crown next to the top gamble player's name"
        else -> null
    }
}
