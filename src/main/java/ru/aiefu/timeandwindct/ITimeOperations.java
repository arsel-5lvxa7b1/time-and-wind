package ru.aiefu.timeandwindct;

import ru.aiefu.timeandwindct.tickers.Ticker;

public interface ITimeOperations {
    Ticker getTimeTicker();
    void setTimeTicker(Ticker timeTicker);
    void setTimeOfDayTAW(long time);
    long getTimeTAW();
    long getTimeOfDayTAW();
}
