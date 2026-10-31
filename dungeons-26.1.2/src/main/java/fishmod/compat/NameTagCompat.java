package fishmod.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;

// 26.1 side: submitNameTag still takes the squared camera distance; the 26.2 module overlays this file.
public final class NameTagCompat {
    private NameTagCompat() {}

    public static void submit(SubmitNodeCollector collector, PoseStack ps, EntityRenderState state, int y, Component line, CameraRenderState camera) {
        collector.submitNameTag(ps, state.nameTagAttachment, y, line, !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
    }
}
