package com.donutmoney.mixin;

import com.donutmoney.client.IMoneyRenderState;
import com.donutmoney.client.ModConfig;
import com.donutmoney.client.MoneyManager;
import com.donutmoney.client.LeaderboardManager;
import com.donutmoney.client.DebugLog;
import com.donutmoney.client.DonutMoneyClient;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    @Inject(method = "extractRenderState", at = @At("RETURN"), require = 0)
    private void donutmoney$extractRenderState(Entity entity, EntityRenderState state, float f, CallbackInfo ci) {
        try {
            if (!(state instanceof IMoneyRenderState moneyState)) return;
            ModConfig cfg = ModConfig.get();
            if (cfg == null || !cfg.showMoney) {
                moneyState.setShouldRenderMoney(false);
                return;
            }
            if (!(entity instanceof Player p)) {
                moneyState.setShouldRenderMoney(false);
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                moneyState.setShouldRenderMoney(false);
                return;
            }

            boolean shouldRender = !entity.isInvisible() && !entity.isDiscrete() && mc.player.distanceToSqr(entity) <= 4096.0D;
            moneyState.setShouldRenderMoney(shouldRender);
            if (!shouldRender) return;

            double distSq = mc.player.distanceToSqr(entity);
            boolean fast = distSq <= 256.0D;

            String name = p.getScoreboardName();
            moneyState.setPlayerName(name);
            String moneyText = MoneyManager.getFormattedMoney(name, fast);
            if (moneyText == null || moneyText.isEmpty()) moneyText = "...";
            moneyState.setMoneyText("$" + moneyText);

            if (cfg.showLeaderboardRank) {
                LeaderboardManager.tick();
                moneyState.setRanks(LeaderboardManager.getPlayerRanks(name));
            } else {
                moneyState.setRanks(null);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), require = 0)
    private void donutmoney$shiftOtherNameTags(EntityRenderState state, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        try {
            if (!(state instanceof IMoneyRenderState moneyState)) return;
            if (!moneyState.shouldRenderMoney()) return;
            if (moneyState.getTagShiftY() != 0.0F) return;

            String playerName = String.valueOf(moneyState.getPlayerName() == null ? "" : moneyState.getPlayerName()).trim();
            if (playerName.isEmpty()) return;
            String disp = displayName == null ? "" : String.valueOf(displayName.getString());
            if (disp.contains(playerName)) return;

            ModConfig cfg = ModConfig.get();
            if (cfg == null || !cfg.showMoney) return;

            Font font = Minecraft.getInstance().font;
            float line = font.lineHeight + 2.0F;
            float scale = cfg.scalePercent / 100.0F;
            float shiftY = (line * scale) + 2.0F;

            moneyState.setTagShiftY(shiftY);
            poseStack.translate(0.0F, shiftY, 0.0F);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift = At.Shift.BEFORE), require = 0)
    private void donutmoney$renderMoneyTagState(EntityRenderState state, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        boolean pushed = false;
        try {
            if (!(state instanceof IMoneyRenderState moneyState)) return;
            if (!moneyState.shouldRenderMoney()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            long tick = mc.level.getGameTime();
            if (moneyState.getLastRenderedTick() == tick) return;
            moneyState.setLastRenderedTick(tick);
            ModConfig cfg = ModConfig.get();
            if (cfg == null || !cfg.showMoney) return;
            String playerName = String.valueOf(moneyState.getPlayerName() == null ? "" : moneyState.getPlayerName()).trim();
            if (playerName.isEmpty()) return;
            String text = moneyState.getMoneyText();
            if (text == null || text.isEmpty()) return;

            Font font = mc.font;
            float scale = cfg.scalePercent / 100.0F;
            List<LeaderboardManager.RankData> ranks = moneyState.getRanks();
            boolean hasRanks = cfg.showLeaderboardRank && ranks != null && !ranks.isEmpty();
            int moneyWidth = font.width(text);
            int spaceGap = font.width(" ");
            int segGap = spaceGap;
            int mainGap = hasRanks ? spaceGap : 0;
            float mini = 0.85F;
            float ranksWidth = 0.0F;
            if (hasRanks) {
                for (LeaderboardManager.RankData r : ranks) {
                    if (r == null) continue;
                    int rk = r.rank();
                    if (rk <= 0 || rk > 1000) continue;
                    String icon = String.valueOf(r.symbol());
                    String prefix = String.valueOf(r.type()) + " #";
                    String rankStr = String.valueOf(rk);
                    float iconW = font.width(icon);
                    float overlap = iconW * 0.5F;
                    float w = iconW + ((font.width(prefix) + font.width(rankStr)) * mini) - overlap;
                    if (ranksWidth > 0.0F) ranksWidth += segGap;
                    ranksWidth += w;
                }
            }
            float startX = -((ranksWidth + mainGap + moneyWidth) / 2.0F);

            float line = font.lineHeight + 2.0F;
            float yMoney = (float) (cfg.yOffset + 2.0F);
            poseStack.pushPose();
            pushed = true;
            poseStack.scale(scale, scale, 1.0F);


            Matrix4f m = poseStack.last().pose();
            int light = 15728880;
            int background = 0;
            if (cfg.showBackground) {
                float bgOp = mc.options.getBackgroundOpacity(0.25F);
                background = (int) (bgOp * 255.0F) << 24;
            }
            int lbBackground = 0;

            float xCursor = startX;
            if (hasRanks) {
                boolean first = true;
                for (LeaderboardManager.RankData r : ranks) {
                    if (r == null) continue;
                    int rk = r.rank();
                    if (rk <= 0 || rk > 1000) continue;
                    String icon = String.valueOf(r.symbol());
                    String prefix = String.valueOf(r.type()) + " #";
                    String rankStr = String.valueOf(rk);
                    if (!first) xCursor += segGap;
                    first = false;

                    int iconRgb = r.color() & 0x00FFFFFF;
                    int alpha70 = 0xB3;
                    int iconCol = (alpha70 << 24) | iconRgb;
                    int labelCol = 0xFFFFFFFF;
                    int rankCol = (0xFF << 24) | iconRgb;

                    float iconW = font.width(icon);
                    float overlap = iconW * 0.5F;
                    font.drawInBatch(icon, xCursor, yMoney, iconCol, true, m, bufferSource, Font.DisplayMode.NORMAL, lbBackground, light);
                    xCursor += iconW - overlap;

                    boolean pushedInner = false;
                    try {
                        poseStack.pushPose();
                        pushedInner = true;
                        poseStack.translate(xCursor, yMoney + 1.5F, 0.0F);
                        poseStack.scale(mini, mini, 1.0F);
                        Matrix4f m2 = poseStack.last().pose();
                        font.drawInBatch(prefix, 0.0F, 0.0F, labelCol, true, m2, bufferSource, Font.DisplayMode.NORMAL, lbBackground, light);
                        float rx = font.width(prefix);
                        font.drawInBatch(rankStr, rx, 0.0F, rankCol, true, m2, bufferSource, Font.DisplayMode.NORMAL, lbBackground, light);
                    } finally {
                        if (pushedInner) poseStack.popPose();
                    }

                    xCursor += (font.width(prefix) + font.width(rankStr)) * mini;
                }
                if (xCursor != startX) xCursor += mainGap;
            }
            font.drawInBatch(text, xCursor, yMoney, 0x55FF55, false, m, bufferSource, Font.DisplayMode.NORMAL, background, light);
        } catch (Throwable ignored) {
        } finally {
            if (pushed) {
                try {
                    poseStack.popPose();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"), require = 0)
    private void donutmoney$unshiftOtherNameTags(EntityRenderState state, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        try {
            if (!(state instanceof IMoneyRenderState moneyState)) return;
            float shiftY = moneyState.getTagShiftY();
            if (shiftY == 0.0F) return;
            moneyState.setTagShiftY(0.0F);
            poseStack.translate(0.0F, -shiftY, 0.0F);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At("HEAD"), require = 0)
    private void donutmoney$shiftOtherNameTags(EntityRenderState state, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float tickDelta, CallbackInfo ci) {
        try {
            if (!(state instanceof IMoneyRenderState moneyState)) return;
            if (!moneyState.shouldRenderMoney()) return;
            if (moneyState.getTagShiftY() != 0.0F) return;

            String playerName = String.valueOf(moneyState.getPlayerName() == null ? "" : moneyState.getPlayerName()).trim();
            if (playerName.isEmpty()) return;
            String disp = displayName == null ? "" : String.valueOf(displayName.getString());
            if (disp.contains(playerName)) return;

            ModConfig cfg = ModConfig.get();
            if (cfg == null || !cfg.showMoney) return;

            Font font = Minecraft.getInstance().font;
            float line = font.lineHeight + 2.0F;
            float scale = cfg.scalePercent / 100.0F;
            float shiftY = (line * scale) + 2.0F;

            moneyState.setTagShiftY(shiftY);
            poseStack.translate(0.0F, shiftY, 0.0F);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift = At.Shift.BEFORE), require = 0)
    private void donutmoney$renderMoneyTagState(EntityRenderState state, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float tickDelta, CallbackInfo ci) {
        donutmoney$renderMoneyTagState(state, displayName, poseStack, bufferSource, packedLight, ci);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At("RETURN"), require = 0)
    private void donutmoney$unshiftOtherNameTags(EntityRenderState state, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float tickDelta, CallbackInfo ci) {
        try {
            if (!(state instanceof IMoneyRenderState moneyState)) return;
            float shiftY = moneyState.getTagShiftY();
            if (shiftY == 0.0F) return;
            moneyState.setTagShiftY(0.0F);
            poseStack.translate(0.0F, -shiftY, 0.0F);
        } catch (Throwable ignored) {
        }
    }

}
