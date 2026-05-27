package com.donutmoney.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    // Disabled in favor of WorldRenderEvents
}
