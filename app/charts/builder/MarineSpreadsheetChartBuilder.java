package charts.builder;

import static charts.builder.ChartBuilder.getFloat;
import static charts.builder.ChartBuilder.renderPNG;
import static charts.builder.ChartBuilder.renderSVG;

import java.util.Map;

import charts.BeerCoaster;
import charts.representations.Format;
import charts.representations.Representation;
import charts.spreadsheet.DataSource;
import charts.BeerCoaster.Category;
import charts.BeerCoaster.Condition;
import charts.BeerCoaster.Indicator;

import com.google.common.collect.ImmutableMap;

public class MarineSpreadsheetChartBuilder extends DefaultSpreadsheetChartBuilder {

    private static final ImmutableMap<Region, Integer> OFFSETS =
        new ImmutableMap.Builder<Region, Integer>()
          .put(Region.CAPE_YORK, 0)
          .put(Region.WET_TROPICS, 1)
          .put(Region.BURDEKIN, 2)
          .put(Region.MACKAY_WHITSUNDAYS, 3)
          .put(Region.FITZROY, 4)
          .put(Region.BURNETT_MARY, 5)
          .put(Region.GBR, 6)
          .build();

    public MarineSpreadsheetChartBuilder() {
      super(ChartType.MARINE);
    }

    private boolean isMarineSpreadsheet(DataSource datasource) {
      try {
        return "MARINE SUMMARY".equalsIgnoreCase(
            datasource.select("Summary!B18").format("value"));
      } catch(Exception e) {
        return false;
      }
    }

    @Override
    Chart build(DataSource datasource, final Region region,
        final Map<String, String[]> query) {
      final BeerCoaster beercoaster = getDrawable(datasource, region);
      if(beercoaster != null) {
        return new Chart() {
          @Override
          public ChartDescription getDescription() {
            return new ChartDescription(ChartType.MARINE, region);
          }
          @Override
          public Representation outputAs(Format format)
              throws UnsupportedFormatException {
            switch (format) {
            case CSV:
              // TODO: Replace with real implementation
              return format.createRepresentation("");
            case SVG:
              return format.createRepresentation(
                  renderSVG(beercoaster));
            case PNG:
              return format.createRepresentation(
                  renderPNG(beercoaster,
                      getFloat(query.get("width")),
                      getFloat(query.get("height"))));
            }
            throw new Chart.UnsupportedFormatException();
          }
        };
      } else {
        return null;
      }
    }

    private BeerCoaster getDrawable(DataSource datasource, Region region) {
      try {
        Integer offset = OFFSETS.get(region);
        if(offset == null) {
            throw new Exception("unknown region "+region);
        }
        Double wa = getValue(datasource, "E", 9, offset);
        Double coral = getValue(datasource, "P", 9, offset);
        Double seag = getValue(datasource, "J", 9, offset);
        Double chla = getValue(datasource, "C", 9, offset);
        Double tss = getValue(datasource, "D", 9, offset);
        Double cs = getValue(datasource, "M", 9, offset);
        Double juv = getValue(datasource, "O", 9, offset);
        Double alg = getValue(datasource, "N", 9, offset);
        Double cover = getValue(datasource, "L", 9, offset);
        Double abu = getValue(datasource, "G", 9, offset);
        Double rep = getValue(datasource, "H", 9, offset);
        Double nut = getValue(datasource, "I", 9, offset);
        Double mc = getValue(datasource, "F", 20, offset);
        BeerCoaster bc = configureInternal(
            wa, coral, seag, chla, tss, cs, juv, alg, cover, abu, rep, nut, mc);
        return bc;
      } catch(Exception e) {
        throw new RuntimeException(e);
      }
    }

    private Condition determineCondition(Double index) {
      if (index == null) {
        return null;
      } else if(index >= 80) {
        return Condition.VERY_GOOD;
      } else if(index >= 60) {
        return Condition.GOOD;
      } else if(index >= 40) {
        return Condition.MODERATE;
      } else if(index >= 20) {
        return Condition.POOR;
      } else {
        return Condition.VERY_POOR;
      }
    }

    private BeerCoaster configureInternal(Double wa, Double coral, Double seag, Double chla,
            Double tss, Double cs, Double juv, Double alg, Double cover, Double abu,
            Double rep, Double nut, Double mc) {
      BeerCoaster chart = new BeerCoaster();
      chart.setCondition(Category.WATER_QUALITY, determineCondition(wa));
      chart.setCondition(Category.CORAL, determineCondition(coral));
      chart.setCondition(Category.SEAGRASS, determineCondition(seag));
      chart.setCondition(Indicator.CHLOROPHYLL_A, determineCondition(chla));
      chart.setCondition(Indicator.TOTAL_SUSPENDED_SOLIDS, determineCondition(tss));
      chart.setCondition(Indicator.SETTLEMENT, determineCondition(cs));
      chart.setCondition(Indicator.JUVENILE, determineCondition(juv));
      chart.setCondition(Indicator.ALGAE, determineCondition(alg));
      chart.setCondition(Indicator.COVER, determineCondition(cover));
      chart.setCondition(Indicator.ABUNDANCE, determineCondition(abu));
      chart.setCondition(Indicator.REPRODUCTION, determineCondition(rep));
      chart.setCondition(Indicator.NUTRIENT_STATUS, determineCondition(nut));
      chart.setOverallCondition(determineCondition(mc));
      return chart;
    }

    private Double getValue(DataSource ds, String column, int row, int rowOffset) throws Exception {
      String str = ds.select("Summary!"+column+(row+rowOffset)).format("value");
      try {
        return Double.parseDouble(str);
      } catch(NumberFormatException e) {
        return null;
      }
    }

    @Override
    boolean canHandle(DataSource datasource) {
      return isMarineSpreadsheet(datasource);
    }

}
