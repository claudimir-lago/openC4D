package swingFrontEnd.graphUtil;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JPanel;
import org.jfree.chart.axis.NumberAxis;
import java.awt.Font;
import javax.swing.BorderFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.ui.RectangleInsets;

/**
 * Allows creating a simplified xy graph based on jFreeChart
 *
 * @author Claudimir Lucio do Lago
 */
public class XYGraph extends JPanel {

    public XYSeries xy;
    private final XYSeriesCollection dados;
    public NumberAxis xAxis;
    public NumberAxis yAxis;
    public XYItemRenderer renderer;
    public XYPlot plot;
    private final JFreeChart chart;
    private final ChartPanel chartPanel;
    private double factorXAxis = 1.1;
    private boolean letSupdate = true;

    /**
     * creator
     *
     * @param nameY the label for the x axis
     * @param nameX the label tor the y axis
     * @param MaxNumberOfPoints maximum number of (x,y) points
     */
    public XYGraph(String nameY, String nameX, int MaxNumberOfPoints) {
        
        super(new BorderLayout());
        
        xy = new XYSeries("xyData");
        xy.setMaximumItemCount(MaxNumberOfPoints);
        dados = new XYSeriesCollection(xy);
        
        xAxis = new NumberAxis(nameX);
        xAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        xAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));
        yAxis = new NumberAxis(nameY);
        yAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        yAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));
        
        renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, Color.RED);
        
        plot = new XYPlot(dados, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(Color.LIGHT_GRAY);
        plot.setDomainGridlinePaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.WHITE);
        plot.setAxisOffset(new RectangleInsets(1.0, 1.0, 1.0, 1.0));
        xAxis.setAutoRange(true);
        xAxis.setLowerMargin(0.0);
        xAxis.setUpperMargin(0.0);
        xAxis.setTickLabelsVisible(true);
        yAxis.setAutoRange(true);
        yAxis.setAutoRangeIncludesZero(false);
        yAxis.setTickLabelsVisible(true);
        
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
     * Remove all the points
     */
    public void removeAllPoints() {
        xy.clear();
    }

    /**
     * adds a (x,y) point to the graph
     * 
     * @param x
     * @param y
     * @param update true to enforce the update
     */
    public void addPoint(double x, double y, boolean update) {
        xy.add(x, y, update);
        if (letSupdate) {
            if (!xAxis.isAutoRange() && (x > xAxis.getUpperBound())) {
                xAxis.setUpperBound(x * factorXAxis);
            }
        }
    }

    public void letUpdate(boolean notHoldOn){
        letSupdate = notHoldOn;
    }
    
    /**
     * Sets a margin to prevent the x axis is rescaled at every new point.
     *
     * @param factor for example, a value 1.2 makes the end of the x axis 20% greater
     */
    public void setFactorXAxis(double factor) {
        factorXAxis = factor;
        xAxis.setAutoRange(false);
    }
}
