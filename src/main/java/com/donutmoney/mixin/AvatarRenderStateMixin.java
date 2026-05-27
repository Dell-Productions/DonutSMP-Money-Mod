package com.donutmoney.mixin;

import com.donutmoney.client.IMoneyRenderState;
import com.donutmoney.client.LeaderboardManager;
import java.util.List;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements IMoneyRenderState {
    @Unique
    private String donutMoneyText;

    @Unique
    private List<LeaderboardManager.RankData> donutRanks;

    @Unique
    private boolean donutShouldRenderMoney;

    @Unique
    private long donutLastRenderedTick = -1L;

    @Unique
    private String donutPlayerName = "";

    @Unique
    private float donutTagShiftY = 0.0F;

    @Override
    public String getMoneyText() {
        return donutMoneyText;
    }

    @Override
    public void setMoneyText(String moneyText) {
        this.donutMoneyText = moneyText;
    }

    @Override
    public List<LeaderboardManager.RankData> getRanks() {
        return donutRanks;
    }

    @Override
    public void setRanks(List<LeaderboardManager.RankData> ranks) {
        this.donutRanks = ranks;
    }

    @Override
    public boolean shouldRenderMoney() {
        return donutShouldRenderMoney;
    }

    @Override
    public void setShouldRenderMoney(boolean shouldRender) {
        this.donutShouldRenderMoney = shouldRender;
    }

    @Override
    public long getLastRenderedTick() {
        return donutLastRenderedTick;
    }

    @Override
    public void setLastRenderedTick(long tick) {
        this.donutLastRenderedTick = tick;
    }

    @Override
    public String getPlayerName() {
        return donutPlayerName;
    }

    @Override
    public void setPlayerName(String playerName) {
        this.donutPlayerName = String.valueOf(playerName == null ? "" : playerName);
    }

    @Override
    public float getTagShiftY() {
        return donutTagShiftY;
    }

    @Override
    public void setTagShiftY(float shiftY) {
        this.donutTagShiftY = shiftY;
    }
}

