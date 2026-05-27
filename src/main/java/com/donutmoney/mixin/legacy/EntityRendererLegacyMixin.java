package com.donutmoney.mixin.legacy;

import com.donutmoney.client.LeaderboardManager;
import com.donutmoney.client.ModConfig;
import com.donutmoney.client.MoneyManager;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererLegacyMixin<T extends Entity> {
    @Inject(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift = At.Shift.BEFORE), require = 0)
    private void donutmoney$renderMoneyTagLegacy(T entity, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float tickDelta, CallbackInfo ci) {
        boolean pushed = false;
        try {
            if (!(entity instanceof Player p)) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            ModConfig cfg = ModConfig.get();
            if (cfg == null || !cfg.showMoney) return;

            String disp = displayName == null ? "" : String.valueOf(displayName.getString());
            String ign = p.getScoreboardName();
            String dispLower = disp.toLowerCase();
            boolean isNameTag = disp.contains(ign)
                || dispLower.equals("you")
                || dispLower.startsWith("you ")
                || dispLower.startsWith("you:");
            if (!isNameTag) return;

            if (entity.isInvisible() || entity.isDiscrete()) return;
            if (mc.player.distanceToSqr(entity) > 4096.0D) return;

            boolean fast = mc.player.distanceToSqr(entity) <= 256.0D;
            String money = MoneyManager.getFormattedMoney(ign, fast);
            if (money == null || money.isEmpty()) money = "...";
            String moneyText = "$" + money;

            Font font = mc.font;
            float scale = cfg.scalePercent / 100.0F;

            List<LeaderboardManager.RankData> ranks = null;
            if (cfg.showLeaderboardRank) {
                LeaderboardManager.tick();
                ranks = LeaderboardManager.getPlayerRanks(ign);
            }
            boolean hasRanks = ranks != null && !ranks.isEmpty();

            int moneyWidth = font.width(moneyText);
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
            float y = (float) (cfg.yOffset + 2.0F);

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
                    font.drawInBatch(icon, xCursor, y, iconCol, true, m, bufferSource, Font.DisplayMode.NORMAL, lbBackground, light);
                    xCursor += iconW - overlap;

                    boolean pushedInner = false;
                    try {
                        poseStack.pushPose();
                        pushedInner = true;
                        poseStack.translate(xCursor, y + 1.5F, 0.0F);
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

            font.drawInBatch(moneyText, xCursor, y, 0x55FF55, false, m, bufferSource, Font.DisplayMode.NORMAL, background, light);
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
}
