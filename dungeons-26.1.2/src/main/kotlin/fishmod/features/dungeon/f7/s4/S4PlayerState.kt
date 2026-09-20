package fishmod.features.dungeon.f7.s4

data class S4PlayerState(
    val name: String,
    var contributionCount: Int = 0,
    var lastContributionTime: Long? = null,
    var coreEntryTime: Long? = null,
    var died: Boolean = false,
    var deathTime: Long? = null,
    var status: S4Status = S4Status.ACTIVE,
)
