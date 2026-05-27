package com.donutmoney.mixin;

import com.donutmoney.client.IMoneyRenderState;
import com.donutmoney.client.LeaderboardManager;
import com.donutmoney.client.ModConfig;
import com.donutmoney.client.MoneyManager;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void donutmoney$extractState(Avatar avatar, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        try {
            if (!(state instanceof IMoneyRenderState moneyState)) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (avatar == null) return;

            ModConfig cfg = ModConfig.get();
            if (cfg == null || !cfg.showMoney) {
                moneyState.setShouldRenderMoney(false);
                return;
            }

            boolean shouldRender = !avatar.isInvisible() && !avatar.isDiscrete() && mc.player.distanceToSqr(avatar) <= 4096.0D;
            moneyState.setShouldRenderMoney(shouldRender);
            moneyState.setPlayerName(avatar.getScoreboardName());

            if (!shouldRender) return;

            boolean fast = mc.player.distanceToSqr(avatar) <= 256.0D;
            String name = String.valueOf(moneyState.getPlayerName() == null ? "" : moneyState.getPlayerName());
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

    @Inject(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift = At.Shift.BEFORE), require = 0)
    private void donutmoney$renderMoneyTag(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
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
            float yMoney = (float) (cfg.yOffset + 2.0F);

            poseStack.pushPose();
            pushed = true;
            poseStack.scale(scale, scale, 1.0F);

            int light = 15728880;
            int background = 0;
            if (cfg.showBackground) {
                float bgOp = mc.options.getBackgroundOpacity(0.25F);
                background = (int) (bgOp * 255.0F) << 24;
            }
            int lbBackground = 0;
            OrderedSubmitNodeCollector ordered = collector.order(0);

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
                    FormattedCharSequence iconSeq = Component.literal(icon).getVisualOrderText();
                    ordered.submitText(poseStack, xCursor, yMoney, iconSeq, true, Font.DisplayMode.NORMAL, iconCol, lbBackground, light, 0);
                    xCursor += iconW - overlap;

                    boolean pushedInner = false;
                    try {
                        poseStack.pushPose();
                        pushedInner = true;
                        poseStack.translate(xCursor, yMoney + 1.5F, 0.0F);
                        poseStack.scale(mini, mini, 1.0F);
                        FormattedCharSequence prefixSeq = Component.literal(prefix).getVisualOrderText();
                        ordered.submitText(poseStack, 0.0F, 0.0F, prefixSeq, true, Font.DisplayMode.NORMAL, labelCol, lbBackground, light, 0);
                        float rx = font.width(prefix);
                        FormattedCharSequence rankSeq = Component.literal(rankStr).getVisualOrderText();
                        ordered.submitText(poseStack, rx, 0.0F, rankSeq, true, Font.DisplayMode.NORMAL, rankCol, lbBackground, light, 0);
                    } finally {
                        if (pushedInner) poseStack.popPose();
                    }

                    xCursor += (font.width(prefix) + font.width(rankStr)) * mini;
                }
                if (xCursor != startX) xCursor += mainGap;
            }

            FormattedCharSequence moneySeq = Component.literal(text).getVisualOrderText();
            ordered.submitText(poseStack, xCursor, yMoney, moneySeq, false, Font.DisplayMode.NORMAL, 0x55FF55, background, light, 0);
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
