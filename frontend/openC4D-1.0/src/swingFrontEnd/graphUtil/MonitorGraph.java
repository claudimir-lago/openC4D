package swingFrontEnd.graphUtil;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JPanel;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.data.time.TimeSeries;
import java.awt.Font;
import javax.swing.BorderFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.chart.ui.RectangleInsets;

/**
 * Allows creating a monitoring graph in which old data are automatically removed
 * 
 * @author Claudimir Lucio do Lago
 */
public class MonitorGraph extends JPanel
{

    public TimeSeries xy;
    private final TimeSeriesCollection dados;
    public DateAxis domain;
    public NumberAxis range;
    private final XYItemRenderer renderer;
    private final XYPlot plot;
    private final JFreeChart chart;
    private final ChartPanel chartPanel;

    /**
     * creator
     * @param MaxAge maximum age (in milliseconds) of a datum before it is removed
     */
    public MonitorGraph(int MaxAge)
    {
        super(new BorderLayout());
        
        xy = new TimeSeries("dados");
        xy.setMaximumItemAge(MaxAge);
        dados = new TimeSeriesCollection(xy);
        
        domain = new DateAxis("time");
        domain.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        domain.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));
        range = new NumberAxis("temperature");
        range.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        range.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));
        
        renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, Color.RED);
        
        plot = new XYPlot(dados, domain, range, renderer);
        plot.setBackgroundPaint(Color.LIGHT_GRAY);
        plot.setDomainGridlinePaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.WHITE);
        plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        domain.setAutoRange(true);
        domain.setLowerMargin(0.0);
        domain.setUpperMargin(0.0);
        domain.setTickLabelsVisible(true);
        range.setAutoRange(true);
        range.setAutoRangeIncludesZero(false);
        range.setTickLabelsVisible(true);
        
        chart = new JFreeChart("", new Font("SansSerif", Font.BOLD, 24), plot, false);
        chart.setBackgroundPaint(Color.WHITE);
        chart.setAntiAlias(true);
        
        chartPanel = new ChartPanel(chart);
        chartPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 4, 4, 4),
                BorderFactory.createLineBorder(Color.BLACK)));
        add(chartPanel);
    }

    /**
     * Adds a new (y,t) point to the set. The time is the value in milliseconds
     * of the moment when the y value is added.
     * 
     * @param y datum
     */
    public void addPoint(double y)
    {
        xy.add(new Millisecond(), y);
    }

    /**
     * remove all data
     */
    public void clear()
    {
        xy.clear();
    }
}
