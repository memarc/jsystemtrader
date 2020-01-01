package com.jsystemtrader.strategy;

import com.ib.client.*;
import com.jsystemtrader.indicator.*;
import com.jsystemtrader.platform.model.*;
import com.jsystemtrader.platform.optimizer.*;
import com.jsystemtrader.platform.quote.*;
import com.jsystemtrader.platform.schedule.*;
import com.jsystemtrader.platform.strategy.*;
import com.jsystemtrader.platform.util.*;

/**
 * This sample strategy trades the S&P E-Mini futures contract using a combination
 * of two indicators. One measures a shorter term noise-adjusted trend, and the other
 * one measures a longer term noise-adjusted trend.
 */
public class ForexTrendArbitrage extends Strategy {

    // Technical indicators
    private final Indicator fastTrendInd, slowTrendInd;

    // Strategy parameters names
    private static final String FAST_TREND_LENGTH = "Fast trend length";
    private static final String SLOW_TREND_LENGTH = "Slow trend length";
    private static final String ENTRY = "Entry";
    private static final String EXIT = "Exit";

    // Strategy parameters values
    private final int fastTrendLength, slowTrendLength;
    private final double entry, exit;


    public ForexTrendArbitrage(StrategyParams params) throws JSystemTraderException {
        // Define the contract to trade
        Contract contract = ContractFactory.makeCashContract("EUR", "USD");
        setStrategy(contract, BarSize.Min5, false);

        // Initialize strategy parameter values. If the strategy is running in the optimization
        // mode, the parameter values will be taken from the "params" object. Otherwise, the
        // "params" object will be empty and the parameter values will be initialized to the
        // specified default values.
        fastTrendLength = (int) params.get(FAST_TREND_LENGTH, 3);
        slowTrendLength = (int) params.get(SLOW_TREND_LENGTH, 12);
        entry = params.get(ENTRY, 150);
        exit = params.get(EXIT, 120);

        // Instantiate technical indicators
        fastTrendInd = new NoiseAdjustedRSI(quoteHistory, fastTrendLength);
        slowTrendInd = new NoiseAdjustedRSI(quoteHistory, slowTrendLength);

        // Specify the title and the chart number for each indicator
        // "0" = the same chart as the price chart; "1+" = separate subchart (below the price chart)
        addIndicator("Fast Trend", fastTrendInd, 1);
        addIndicator("Slow Trend", slowTrendInd, 1);
    }

    /**
     * Returns min/max/step values for each strategy parameter. This method is
     * invoked by the strategy optimizer to obtain the strategy parameter ranges.
     */
    @Override
    public StrategyParams initParams() {
        StrategyParams params = new StrategyParams();
        params.add(FAST_TREND_LENGTH, 3, 6, 1);
        params.add(SLOW_TREND_LENGTH, 8, 16, 1);
        params.add(ENTRY, 120, 160, 10);
        params.add(EXIT, 100, 140, 10);
        return params;
    }

    /**
     * Define the trading interval and the time zone for that interval
     */
    @Override
    public TradingInterval initTradingInterval() throws JSystemTraderException {
        return new TradingInterval("0:20", "23:40", "America/New_York", true);
    }

    /**
     * This method is invoked by the framework when a new bar is completed and the technical
     * indicators are recalculated. This is where the strategy itself should be defined.
     */
    @Override
    public void onBar() {
        double fastTrend = fastTrendInd.getValue();
        double slowTrend = slowTrendInd.getValue();

        double delta = fastTrend - slowTrend;

        int currentPosition = getPositionManager().getPosition();
        boolean target = (currentPosition > 0 && delta > exit);
        target = target || (currentPosition < 0 && delta < -exit);

        if (target) {
            position = 0;
        } else {
            if (delta > entry) {
                position = -100000;// slow trend is down, fast trend is up
            } else if (delta < -entry) {
                position = 100000;// slow term trend is up, fast trend is down
            }
        }
    }
}
