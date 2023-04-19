package v.Charting;

import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTick;  // ticks along a number axis.This determines the spacing of the tick marks on an axis. 
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PolarPlot;
import org.jfree.chart.renderer.DefaultPolarItemRenderer;
import org.jfree.chart.renderer.PolarItemRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.ui.TextAnchor;
import java.awt.Paint;
import java.awt.AlphaComposite;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import java.awt.geom.Arc2D;
import java.awt.Stroke;
import java.awt.BasicStroke;
import java.awt.Toolkit;
import v.Log.Logfile;
import v.Log.LogfileFactory;
import m.Common;
import org.jfree.chart.util.ShapeUtils;





/**
 * @see http://en.wikipedia.org/wiki/Polar_coordinate_system
 * @see https://stackoverflow.com/questions/3458824
 */
public class ContactsPolarChart extends JFrame {

private static String chartTitle = "Chart Title";
private XYDataset xyDatasetLocal;
private Logfile logger;
public Color xAxisColor;
public Color seriesColorObj;
public Color plotOutlineColor;
public Color radiusGridlineColor;
public Color plotBackgroundColor;
public Color chartBackgroundColor;
private int frameHeight, frameWidth;
private PolarPlot plotObj;
private XYSeriesCollection xySeriesCollectionObj;;



public ContactsPolarChart(String ftitle, String ctitle, int plotLimit)
  {
  this(ftitle,ctitle, 0,0, plotLimit);
  }

public ContactsPolarChart(String frameTitle, String chartTitle, int width, int height, int plotLimit)
  {
  super(frameTitle);
  this.logger = Common.logger;
  if(logger==null)logger = LogfileFactory.getLogfile();
  this.chartTitle = chartTitle;
  this.xAxisColor=Color.GREEN;
  this.seriesColorObj = Color.RED;  // flagme.  needs to be set for each series.
  this.radiusGridlineColor = Color.gray;
  this.plotBackgroundColor = new Color(0x00f0f0f0);
  this.chartBackgroundColor = Color.white;
  this.xySeriesCollectionObj = this.createDatasetSetLimit(plotLimit);
  //this.xyDatasetLocal = new XYSeriesCollection();
  // Set size of Chart
  Dimension screenSize= this.setWidthHeight(width, height);
  this.frameHeight=(int)screenSize.getHeight();
  this.frameWidth=(int)screenSize.getWidth();
  //System.out.println("Width=" + this.frameWidth + ", Height=" + this.frameHeight);
  this.setSize(screenSize);
  // Notice that I set series 0 color to plot background color because I can't make series 0 invisible.
  JFreeChart chart = createChart(this.xySeriesCollectionObj, this.chartTitle, this.xAxisColor, this.plotBackgroundColor, 
          this.plotOutlineColor, this.radiusGridlineColor, this.plotBackgroundColor,
          this.chartBackgroundColor, plotLimit);
  this.plotObj = (PolarPlot)chart.getPlot();
  ChartPanel panel = new ChartPanel(chart);
  //panel.setForeground(new Color(00,00,255));
  panel.setPreferredSize(screenSize);
  //System.out.println("Panel width=" + panel.getWidth() + ", height=" + panel.getHeight());
  panel.setMouseZoomable(true);
  this.add(panel);    
  setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
  setLocationRelativeTo(null);
  this.setVisible(true);
  }  


public ContactsPolarChart(String frameTitle) 
  {
  this(frameTitle, frameTitle, 0, 0, 10000);
  }


private static XYSeriesCollection createDataset(String seriesTitlel) 
  {
  XYSeriesCollection result = new XYSeriesCollection();
  XYSeries series = new XYSeries(seriesTitlel);
  for (int t = 0; t <= 3 * 359; t++) 
    {
    System.out.println("Theta=" + (90 - t) + ", radius=" + t);
    series.add(90 - t, t);
    }
  //result.addSeries(series);
  return result;
  }


/**
 * Add XYDataset to PolarPlot this.xySeriesCollectionObj
 */
public boolean addDataset(int series, String legend, Color sColor, double[][] darray)
  {
  //XYSeriesCollection dataset = new XYSeriesCollection();
  XYSeries seriesl = new XYSeries(legend);
  for(int x=0; x < darray.length; x++)
    {
    seriesl.add(darray[x][0], darray[x][1]);    
    }
  this.xySeriesCollectionObj.addSeries(seriesl);
  this.plotObj.setDataset(series, this.xySeriesCollectionObj);
  this.plotObj.setRenderer(this.setLineTypeColor(this.plotObj, series, "Shapes", sColor));
  return true;
  }


private XYSeriesCollection createDatasetSetLimit(int plotLimit) 
  {
  XYSeriesCollection dataset = new XYSeriesCollection();
  XYSeries series1 = new XYSeries("");
  System.out.println("PlotLimit=" + plotLimit);
  series1.add(0, plotLimit); 
  //series1.add(90,20000);
  //series1.add(180,20000);
  dataset.addSeries(series1);
  return dataset;
  }




private JFreeChart createChart(XYDataset dataset, String chartTitlel, Color xAxisColorl, 
          Color seriesColorl, Color plotOutlineColorl, Color radiusGridlineColorl, 
          Color plotBackgroundColorl, Color chartBackgroundColorl, int plotLimit) 
  {
  ValueAxis radiusAxis = new NumberAxis();
  radiusAxis.setTickLabelsVisible(false);
  radiusAxis.setAxisLinePaint(xAxisColorl);  // sets x axis color
  DefaultPolarItemRenderer renderer = new DefaultPolarItemRenderer();
  renderer.setFillComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.5f)); // nothing
  //renderer.setSeriesFillPaint(0, colorol);
  //renderer.setSeriesFilled(0,true);  // fills the area within the dataset values.
  PolarPlot plot = new PolarPlot(dataset, radiusAxis, renderer) 
    {// the below rotates the angle markers 90 degrees.
    /*
    @Override
    protected List refreshAngleTicks() // set the tick marks at 1 degree intervals.
      {
      List<NumberTick> ticks = new ArrayList<NumberTick>();
      int delta = (int) this.getAngleTickUnit().getSize();
      //System.out.println("Delta=" + delta);
      for (int t = 0; t < 360; t += delta) 
        {
        int tp = (360 + 90 - t) % 360;
        //System.out.println("angle=" + t + ", value=" + tp);
        NumberTick tick = new NumberTick(Double.valueOf(t), String.valueOf(tp),TextAnchor.CENTER, TextAnchor.CENTER, 0.0);
        ticks.add(tick);
        }
      return ticks;
      }
    */
    };
  radiusAxis.getPlot().setOutlinePaint(plotOutlineColorl); 
  plot.setRenderer(this.setLineTypeColor(plot, 0, "Shapes", seriesColorl));
  plot.setBackgroundPaint(plotBackgroundColorl); // chart background
  plot.setRadiusGridlinePaint(radiusGridlineColorl);
  int seriesnum=0;
  //plot.getRendererForDataset(plot.getDataset(seriesnum)).setSeriesPaint(0,colorol);
  plot.addCornerTextItem("Outer limit " + plotLimit + " km");
  JFreeChart chart = new JFreeChart(chartTitlel, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
  chart.setBackgroundPaint(chartBackgroundColorl);
  /* this doesn't make invisible series 0 either.
  DefaultPolarItemRenderer dpir = (DefaultPolarItemRenderer)plot.getRenderer();
  dpir.setSeriesVisible(0, false); 
  */
  return chart;
  } // end createChart()



/** 
 * Sets the line type and color
 * You will get the wrong color if you do not setSeriesPaint(0,color).  Each line is a new series for that renderer.
 * This method utilized solely by  method addXYSeriesCollection().
*/
private PolarItemRenderer setLineTypeColor(PolarPlot plotl, int series, String lineTypel, Color color)
  {
  DefaultPolarItemRenderer r = (DefaultPolarItemRenderer) plotl.getRenderer(); 
  if(r==null){logger.e("Failed to get renderer from plot."); return null;}
  // If in the future we want to include the option to vary line thickness...
  //r.setSeriesStroke(0, new BasicStroke(20.0f)); fat line
  //r.setSeriesStroke(0,new BasicStroke(0.0f));  
  //r.setAutoPopulateSeriesOutlineStroke(false);
  // NOTE that setSeriesPaint number refers to the paint color for this series, and not for this dataset.
  // if(series==0){r.setSeriesVisible(0,false); return r;}  // nope, this doesn't make it invisible either.
  r.setSeriesPaint(series, color); 
  //System.out.println("getSeriesShape() class=" + r.getSeriesShape(series).getClass());  fails. returns null.  Default shape is square.
  if(lineTypel.equals("Lines"))
    {
    // Create a shape/point/node of size=0.
    r.setSeriesShape(series, ShapeUtils.createDiamond(0), true);
    }
  // lines and shapes, i.e. strokes and nodes, is the default behaviour.
 else if (lineTypel.equals("LinesandShapes"));
 else if(lineTypel.equals("Shapes"))
   {
   // Create a stroke that is impossible to render in order to suppress rendering of strokes.
   Stroke impossibleStroke = new BasicStroke(0.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,0.0f, new float[] {0.0f, 1e10f}, 1.0f );
   r.setSeriesStroke(series, impossibleStroke);
   }
 else 
   {
   logger.e("Invalid line type of " + lineTypel + " passed to DislayChart.setLineType.  Valid values are \"Lines\", \"Shapes\", \"LinesandShapes\"");
   }
  //r.setSeriesVisible(0, false);  // none of these make series 0 invisible
  //r.setSeriesVisible(0,false, true);
  return r;
  }  // end setLineType



/**
 * @param width
 * @param height
 * @return hw[0] width, hw[1] height
 */
private Dimension setWidthHeight(int width, int height)
  {
  int[] hw= new int[2];
  if(width==0 || height==0)
    {
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    // the screen height
    height=(int)(0.90 * screenSize.getHeight());
    // the screen width
    // hw[1]=hw[1]/2;
    width=(int)(0.99 * screenSize.getWidth());
    // hw[0]=hw[0]/2;
    }
  // Make it square because you usually want a polar chart to be round.
  if(width > height) width=height;
  else if(height > width) height=width;
  hw[0]= width; hw[1]=height;
  Dimension hwd = new Dimension(hw[0], hw[1]);
  return hwd;
  }


} // end class
