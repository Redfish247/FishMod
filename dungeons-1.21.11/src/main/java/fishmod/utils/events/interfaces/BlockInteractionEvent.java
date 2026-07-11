package fishmod.utils.events.interfaces;

import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;

public interface BlockInteractionEvent {

    boolean interact(BlockHitResult result, ItemStack item);

}
