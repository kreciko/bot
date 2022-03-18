package pl.kordek.forex.bot.utils;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;
import org.ta4j.core.*;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MyStrategyCharts {
    /**
     * Builds a JFreeChart time series from a Ta4j bar series and an indicator.
     *
     * @param barSeries the ta4j bar series
     * @param indicator the indicator
     * @param name      the name of the chart time series
     * @return the JFreeChart time series
     */
    private static org.jfree.data.time.TimeSeries buildChartTimeSeries(BarSeries barSeries, Indicator<Num> indicator,
                                                                       String name) {
        org.jfree.data.time.TimeSeries chartTimeSeries = new org.jfree.data.time.TimeSeries(name);
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            Bar bar = barSeries.getBar(i);
            chartTimeSeries.add(new Minute(Date.from(bar.getEndTime().toInstant())),
                    indicator.getValue(i).doubleValue());
        }
        return chartTimeSeries;
    }

    private static org.jfree.data.time.TimeSeries buildChartBarSeries(BarSeries BarSeries, Indicator<Num> indicator, String name) {
        org.jfree.data.time.TimeSeries chartBarSeries = new org.jfree.data.time.TimeSeries(name);
        for (int i = 0; i < BarSeries.getBarCount(); i++) {
            Bar Bar = BarSeries.getBar(i);
            chartBarSeries.addOrUpdate(new Minute(Date.from(Bar.getEndTime().toInstant())), indicator.getValue(i).doubleValue());
        }
        return chartBarSeries;
    }

    /**
     * Runs a strategy over a bar series and adds the value markers corresponding to
     * buy/sell signals to the plot.
     *
     * @param series   the bar series
     * @param strategy the trading strategy
     * @param plot     the plot
     */
    private static void addBuySellSignals(BarSeries series, Strategy strategy, XYPlot plot) {
        // Running the strategy
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        List<Position> trades = seriesManager.run(strategy).getPositions();
        // Adding markers to plot
        for (Position trade : trades) {
            // Buy signal
            double buySignalBarTime = new Minute(
                    Date.from(series.getBar(trade.getEntry().getIndex()).getEndTime().toInstant()))
                    .getFirstMillisecond();
            Marker buyMarker = new ValueMarker(buySignalBarTime);
            buyMarker.setPaint(Color.GREEN);
            buyMarker.setLabel("B");
            plot.addDomainMarker(buyMarker);
            // Sell signal
            double sellSignalBarTime = new Minute(
                    Date.from(series.getBar(trade.getExit().getIndex()).getEndTime().toInstant()))
                    .getFirstMillisecond();
            Marker sellMarker = new ValueMarker(sellSignalBarTime);
            sellMarker.setPaint(Color.RED);
            sellMarker.setLabel("S");
            plot.addDomainMarker(sellMarker);
        }
    }

    /**
     * Displays a chart in a frame.
     *
     * @param chart the chart to be displayed
     */
    private static void displayChart(JFreeChart chart) {
        // Chart panel
        ChartPanel panel = new ChartPanel(chart);
        panel.setFillZoomRectangle(true);
        panel.setMouseWheelEnabled(true);
        panel.setPreferredSize(new Dimension(1024, 400));
        // Application frame
        ApplicationFrame frame = new ApplicationFrame("Ta4j example - Buy and sell signals to chart");
        frame.setContentPane(panel);
        frame.pack();
        RefineryUtilities.centerFrameOnScreen(frame);
        frame.setVisible(true);
    }

    public static void buildCharts(BarSeries series, Strategy strategy, Indicator<Num> additionalInd, String title) {
        /*
         * Building chart datasets
         */
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(buildChartBarSeries(series, new ClosePriceIndicator(series), title));

        dataset.addSeries(buildChartBarSeries(series, additionalInd, title));

        /*
         * Creating the chart
         */
        JFreeChart chart = ChartFactory.createTimeSeriesChart(title, // title
                "Date", // x-axis label
                "Price", // y-axis label
                dataset, // data
                true, // create legend?
                true, // generate tooltips?
                false // generate URLs?
        );
        XYPlot plot = (XYPlot) chart.getPlot();
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("MM-dd HH:mm"));

        /*
         * Running the strategy and adding the buy and sell signals to plot
         */
        addBuySellSignals(series, strategy, plot);

        /*
         * Displaying the chart
         */
        displayChart(chart);
    }

    public static void buildIndicatorChart(BarSeries series, List<Indicator<Num>> additionalInds, String title) {
        /*
         * Building chart datasets
         */
        TimeSeriesCollection dataset = new TimeSeriesCollection();

        for(Indicator<Num> ind: additionalInds)
            dataset.addSeries(buildChartBarSeries(series, ind, title));

        /*
         * Creating the chart
         */
        JFreeChart chart = ChartFactory.createTimeSeriesChart(title, // title
                "Date", // x-axis label
                "Price", // y-axis label
                dataset, // data
                true, // create legend?
                true, // generate tooltips?
                false // generate URLs?
        );
        XYPlot plot = (XYPlot) chart.getPlot();
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("MM-dd HH:mm"));


        /*
         * Displaying the chart
         */
        displayChart(chart);
    }
}
