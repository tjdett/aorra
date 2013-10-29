package charts.builder;

import static java.util.Arrays.asList;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.awt.Dimension;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import charts.Chart.UnsupportedFormatException;
import charts.ChartType;
import charts.Region;
import charts.builder.spreadsheet.XlsDataSource;
import charts.builder.spreadsheet.XlsxDataSource;
import charts.representations.Format;

import com.google.common.collect.Lists;

@RunWith(Parameterized.class)
public class ChartBuilderTest {

  private final static ChartBuilder chartBuilder = new ChartBuilder();
  private final ChartType chartType;
  private final Format format;

  public ChartBuilderTest(ChartType ct, Format f) {
    this.chartType = ct;
    this.format = f;
  }

  @Parameters(name = "{1} - {0}")
  public static Collection<Object[]> data() {
    final List<Object[]> l = Lists.newLinkedList();
    for (final ChartType ct : ChartType.values()) {
      l.add(new Object[] { ct,  Format.CSV});
      l.add(new Object[] { ct,  Format.DOCX});
      l.add(new Object[] { ct,  Format.EMF});
    }
    return l;
  }

  @Test
  public void format() {
    final List<charts.Chart> charts = chartBuilder.getCharts(
        asList(getDatasource(chartType)),
        asList(getDefaultTestingRegion(chartType)),
        new Dimension(0, 0));
    assertThat(charts).as("No chart generated for "+chartType).isNotEmpty();
    final charts.Chart chart = charts.get(0);
    try {
      chart.outputAs(format);
    } catch (UnsupportedFormatException ufe) {
      fail(chartType+" should support "+format+" output.");
    }
  }

  public static DataSource getDatasource(ChartType t) {
    try {
      String filename = getChartTypeFile(t);
      if(filename.endsWith(".xlsx")) {
          return new XlsxDataSource(new FileInputStream(getChartTypeFile(t)));
      } else {
          return new XlsDataSource(new FileInputStream(getChartTypeFile(t)));
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Region getDefaultTestingRegion(ChartType t) {
    switch (t) {
    case TSA:
      return Region.BURDEKIN;
      //$CASES-OMITTED$
    default:
      return Region.GBR;
    }
  }

  public static String getChartTypeFile(ChartType t) {
    switch (t) {
    case COTS_OUTBREAK:
      return "test/cots_outbreak.xlsx";
    case ANNUAL_RAINFALL:
      return "test/annual_rainfall.xlsx";
    case GRAZING_PS:
      return "test/grazing_practice_systems.xlsx";
    case GRAINS_PS:
    case HORTICULTURE_PS:
    case SUGARCANE_PS:
      return "test/land_practice_systems.xlsx";
    case MARINE:
    case MARINE_CT:
    case MARINE_ST:
    case MARINE_WQT:
      return "test/marine.xls";
    case PROGRESS_TABLE_REGION:
    case PROGRESS_TABLE:
      return "test/progress_table.xlsx";
    case TSA:
      return "test/seagrass_cover.xls";
    case TTT_CANE_AND_HORT:
    case TTT_GRAZING:
    case TTT_NITRO_AND_PEST:
    case TTT_SEDIMENT:
      return "test/tracking_towards_targets.xlsx";
    case GROUNDCOVER:
      return "test/groundcover.xlsx";
    case GROUNDCOVER_BELOW_50:
      return "test/groundcover_below_50.xlsx";
    case LOADS:
    case LOADS_DIN:
    case LOADS_PSII:
    case LOADS_TN:
    case LOADS_TSS:
      return "test/loads.xlsx";
    case CORAL_HCC:
    case CORAL_SCC:
    case CORAL_MA:
    case CORAL_JUV:
      return "test/coral.xls";
    case PSII_MAX_HEQ:
      return "test/PSII.xlsx";
    case PSII_TRENDS:
      return "test/Max conc.xlsx";
    default:
      throw new RuntimeException("Unknown chart type: "+t);
    }
  }

}
