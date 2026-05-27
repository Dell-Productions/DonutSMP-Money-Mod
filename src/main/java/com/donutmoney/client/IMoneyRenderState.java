package com.donutmoney.client;

import com.donutmoney.client.LeaderboardManager.RankData;
import java.util.List;

public interface IMoneyRenderState {
    String getMoneyText();
    void setMoneyText(String moneyText);
    
    List<RankData> getRanks();
    void setRanks(List<RankData> ranks);
    
    boolean shouldRenderMoney();
    void setShouldRenderMoney(boolean shouldRender);

    long getLastRenderedTick();
    void setLastRenderedTick(long tick);

    String getPlayerName();
    void setPlayerName(String playerName);

    float getTagShiftY();
    void setTagShiftY(float shiftY);
}
