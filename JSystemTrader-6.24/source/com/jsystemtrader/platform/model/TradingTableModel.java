package com.jsystemtrader.platform.model;

import com.jsystemtrader.platform.optimizer.*;
import com.jsystemtrader.platform.position.*;
import com.jsystemtrader.platform.quote.*;
import com.jsystemtrader.platform.strategy.*;

import java.util.prefs.Preferences;
import java.lang.reflect.*;
import java.text.*;
import java.util.*;

/**
 */
public class TradingTableModel extends TableDataModel {

    // inner class to represent table schema
    enum Column {
        Active("Activated", Boolean.class),
        Strategy("Strategy", String.class),
        Ticker("Ticker", String.class),
        BarSize("Bar Size", String.class),
        LastBarTime("Last Bar Time", String.class),
        LastBarClose("Last Bar Close", Double.class),
        Position("Position", Integer.class),
        Trades("Trades", Integer.class),
        PL("P&L", Double.class),
        MaxDD("Max DD", Double.class),
        PF("PF", Double.class),
        K("Kelly", Double.class),
        TradeDistribution("Trade Distribution", String.class);

        
        private final String columnName;
        private final Class<?> columnClass;

        Column(String columnName, Class<?> columnClass) {
            this.columnName = columnName;
            this.columnClass = columnClass;
        }
    }


    private final Map<Integer, Strategy> rows = new HashMap<Integer, Strategy>();
    private final DecimalFormat nf4;
    private final Preferences jprefs;
    private final String strategyStatusPrefix = "run.strategy.";

    public TradingTableModel() throws JSystemTraderException {

        Column[] columns = Column.values();
        ArrayList<String> allColumns = new ArrayList<String>();
        for (Column column : columns) {
            allColumns.add(column.columnName);

        }

        setSchema(allColumns.toArray(new String[columns.length]));

        jprefs = Preferences.userNodeForPackage(getClass());

        nf4 = (DecimalFormat) NumberFormat.getNumberInstance();
        nf4.setMaximumFractionDigits(4);
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        // only the "Active" column can be edited
        return (col == Column.Active.ordinal());
    }


    @Override
    public Class<?> getColumnClass(int col) {
        Column column = Column.values()[col];
        return column.columnClass;
    }

    public void reset() {
        int rowCount = rows.size();
        for (int row = 0; row < rowCount; row++) {
            Object[] rowData = getRow(row);
            for (int column = Column.LastBarTime.ordinal(); column <= Column.PF.ordinal(); column++) {
                rowData[column] = null;
            }
        }
        fireTableDataChanged();

    }

    public Strategy getStrategyForRow(int row) {
        return rows.get(row);
    }

    public ArrayList<Strategy> getSelectedStrategies() throws JSystemTraderException {
        ArrayList<Strategy> selectedStrategies = new ArrayList<Strategy>();

        int rowCount = getRowCount();
        for (int row = 0; row < rowCount; row++) {
            Object[] rowData = getRow(row);
            boolean isSelected = (Boolean) rowData[0];
            if (isSelected) {
                Strategy strategy = getStrategyForRow(row);
                try {
                    Class<?> clazz = Class.forName(strategy.getClass().getName());
                    Constructor<?> ct = clazz.getConstructor(StrategyParams.class);
                    strategy = (Strategy) ct.newInstance(new StrategyParams());
                    rows.put(row, strategy);

                } catch (Exception e) {
                    throw new JSystemTraderException(e);
                }


                selectedStrategies.add(strategy);
            }
        }
        return selectedStrategies;
    }


    private int findStrategy(Strategy strategy) {
        int row = -1;
        for (Map.Entry<Integer, Strategy> mapEntry : rows.entrySet()) {
            Strategy thisStrategy = mapEntry.getValue();
            if (thisStrategy == strategy) {
                row = mapEntry.getKey();
                break;
            }
        }
        return row;
    }


    public synchronized void updateStrategy(Strategy strategy) {
        int row = findStrategy(strategy);
        PriceBar lastPriceBar = strategy.getQuoteHistory().size()>0 ? strategy.getLastPriceBar() : null;
        PositionManager positionManager = strategy.getPositionManager();

        if (row >= 0 )
        {
            setValueAt(positionManager.getPosition(), row, Column.Position.ordinal());
            setValueAt(positionManager.getTrades(), row, Column.Trades.ordinal());
            if(lastPriceBar != null)
            {
                double close = lastPriceBar.getClose();
                setValueAt(lastPriceBar.getShortDate(), row, Column.LastBarTime.ordinal());               
                setValueAt(close, row, Column.LastBarClose.ordinal());
            }            

            try
            {
                String formattedPL = nf4.format(positionManager.getTotalProfitAndLoss());
                double totalPnL = nf4.parse(formattedPL).doubleValue();
                setValueAt(totalPnL, row, Column.PL.ordinal());

                String formattedMaxDD = nf4.format(positionManager.getMaxDrawdown());
                double maxDD = nf4.parse(formattedMaxDD).doubleValue();
                setValueAt(maxDD, row, Column.MaxDD.ordinal());

                String formattedProfitFactor = nf4.format(positionManager.getProfitFactor());
                double profitFactor = nf4.parse(formattedProfitFactor).doubleValue();
                setValueAt(profitFactor, row, Column.PF.ordinal());
                
                String formattedKelly = nf4.format(positionManager.getKelly());
                double kelly = nf4.parse(formattedKelly).doubleValue();
                setValueAt(kelly, row, Column.K.ordinal());
                
                setValueAt(" "+strategy.getTradeDistribution(), row, Column.TradeDistribution.ordinal());
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public void addStrategy(Strategy strategy) {
        Object[] row = new Object[getColumnCount()];

        String strategyName = strategy.getClass().getName();
        boolean strategyStatus;

        strategyStatus = (jprefs.getBoolean(strategyStatusPrefix + strategyName, false));

        row[Column.Active.ordinal()] = strategyStatus;
        row[Column.Strategy.ordinal()] = strategy.getName();
        row[Column.Ticker.ordinal()] = strategy.getContract().m_symbol;
        row[Column.BarSize.ordinal()] = strategy.getBarSize();

        addRow(row);
        rows.put(getRowCount() - 1, strategy);
    }

    /// "reminds" running strategies 
    public void saveStrategyStatus() throws JSystemTraderException {
        int rowCount = getRowCount();
        for (int row = 0; row < rowCount; row++) {
            Object[] rowData = getRow(row);

            String strategyName = getStrategyForRow(row).getClass().getName();
            jprefs.putBoolean(strategyStatusPrefix + strategyName, (Boolean) rowData[0]);
        }
    }

}
