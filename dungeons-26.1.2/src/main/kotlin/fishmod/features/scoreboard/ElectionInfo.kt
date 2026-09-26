package fishmod.features.scoreboard

import fishmod.utils.MayorApi

object ElectionInfo {

    @JvmStatic
    fun lines(): List<String> = MayorApi.mayorLine()?.let { listOf(it) } ?: emptyList()
}
