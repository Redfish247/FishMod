package fishmod.utils.dungeon

import fishmod.utils.Constants
import net.minecraft.client.MinecraftClient

object FillHelper {

    const val ENDER_PEARL: String = "Ender Pearl"
    const val SUPERBOOM_TNT: String = "Superboom TNT"
    const val INFLATABLE_JERRY: String = "Inflatable Jerry"
    const val DECOY: String = "Decoy"

    /**
     * @param itemName Name of item
     * @param minThreshold an integer that the item count has to be lower than to fill
     * @param maxCount the max count of the item
     * @return 1 if it succeeds else 0 for fail
     */
    @JvmStatic
    fun fillItem(itemName: String, minThreshold: Int, maxCount: Int, needAtleastOne: Boolean): Int {
        val player = MinecraftClient.getInstance().player ?: return Constants.FAIL

        val inventory = player.inventory

        var currentCount = 0

        for (i in 0 until inventory.size()) {
            val item = inventory.getStack(i)

            if (item.name.toString().contains(itemName)) {
                val highestCount = item.count

                if (highestCount > currentCount) {
                    currentCount = highestCount

                    if (highestCount >= maxCount) return Constants.FAIL
                }
            }
        }

        if ((needAtleastOne && currentCount == 0) || minThreshold < currentCount) return Constants.FAIL

        val itemsToGive = maxCount - currentCount
        player.networkHandler.sendChatCommand("gfs $itemName $itemsToGive")
        return Constants.SUCCESS
    }

    @JvmStatic
    fun fillItem(client: MinecraftClient, itemName: String, minThreshold: Int, maxCount: Int): Int {
        val player = client.player ?: return Constants.FAIL

        val inventory = player.inventory

        var currentCount = 0

        for (i in 0 until inventory.size()) {
            val item = inventory.getStack(i)

            if (item.name.toString().contains(itemName)) {
                val highestCount = item.count

                if (highestCount > currentCount) {
                    currentCount = highestCount

                    if (highestCount >= maxCount) return Constants.FAIL
                }
            }
        }

        if (minThreshold < currentCount) return Constants.FAIL

        val itemsToGive = maxCount - currentCount
        player.networkHandler.sendChatCommand("gfs $itemName $itemsToGive")
        return Constants.SUCCESS
    }
}
