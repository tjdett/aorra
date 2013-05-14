package controllers;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.batik.dom.svg.SAXSVGDocumentFactory;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.jcrom.Jcrom;
import org.w3c.dom.Document;

import play.libs.F;
import play.libs.Json;
import play.mvc.Result;
import providers.CacheableUserProvider;
import service.JcrSessionFactory;
import service.filestore.FileStore;
import au.edu.uq.aorra.charts.ChartRenderer;
import be.objectify.deadbolt.java.actions.SubjectPresent;

import com.google.common.collect.Lists;
import com.google.inject.Inject;

import ereefs.charts.ChartDescription;
import ereefs.charts.ChartFactory;
import ereefs.charts.ChartType;
import ereefs.spreadsheet.DataSource;
import ereefs.spreadsheet.XlsxDataSource;

public class Chart extends SessionAwareController {

    private final FileStore fileStore;

    @Inject
    public Chart(final JcrSessionFactory sessionFactory,
            final Jcrom jcrom,
            final CacheableUserProvider sessionHandler,
            final FileStore fileStore) {
      super(sessionFactory, jcrom, sessionHandler);
      this.fileStore = fileStore;
    }

    private String buildUrl(ereefs.charts.Chart chart, String format, String path) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder("charts/");
        result.append(chart.getDescription().getType().toString().toLowerCase());
        result.append(".");
        if(StringUtils.isNotBlank(format)) {
            result.append(format);
        } else {
            result.append("png");
        }
        result.append("?");
        Iterator<Map.Entry<String, String>> meIter = 
                chart.getDescription().getProperties().entrySet().iterator();
        while(meIter.hasNext()) {
            Map.Entry<String, String> me = meIter.next();
            result.append(String.format("%s=%s", URLEncoder.encode(me.getKey(), "UTF-8"),
                    URLEncoder.encode(me.getValue(), "UTF-8")));
            if(meIter.hasNext()) {
                result.append("&");
            }
        }
        result.append("&path="+URLEncoder.encode(path, "UTF-8"));
        return result.toString();
    }

    private String getParameter(String key) {
        String[] values = request().queryString().get(key);
        return ((values != null) && (values.length > 0))?values[0]:null;
    }

    private List<DataSource> getDatasources(Session session, String[] paths) throws Exception {
        List<DataSource> result = Lists.newArrayList();
        if(paths == null) {
            return null;
        }
        final FileStore.Manager fm = fileStore.getManager(session);
        for(String path : paths) {
            FileStore.FileOrFolder fof = fm.getFileOrFolder("/"+path);
            if (fof instanceof FileStore.File) {
                FileStore.File file = (FileStore.File) fof;
                // Check this is an OpenXML document (no chance otherwise)
                if (!file.getMimeType().equals(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
                } else {
                    result.add(new XlsxDataSource(file.getData()));
                }
            }
        }
        return result;
    }

    @SubjectPresent
    public Result charts(final String chart) {
        return inUserSession(new F.Function<Session, Result>() {
            @Override
            public final Result apply(Session session) throws RepositoryException {
                try {
                    List<DataSource> datasources = getDatasources(
                            session, request().queryString().get("path"));
                    ChartFactory f = new ChartFactory(datasources);
                    if(StringUtils.isBlank(chart)) {
                        String format = getParameter("format");
                        if(StringUtils.isBlank(format)) {
                            format = "png";
                        }
                        List<ereefs.charts.Chart> charts = f.getCharts(request().queryString());
                        final ObjectNode json = Json.newObject();
                        final ArrayNode aNode = json.putArray("charts");
                        for(ereefs.charts.Chart chart : charts) {
                            ChartDescription desc = chart.getDescription();
                            final ObjectNode chartNode = Json.newObject();
                            chartNode.put("type", desc.getType().toString());
                            for(Map.Entry<String, String> me : desc.getProperties().entrySet()) {
                                chartNode.put(me.getKey(), me.getValue());
                            }
                            chartNode.put("url", buildUrl(chart, format, request().queryString().get("path")[0]));
                            aNode.add(chartNode);
                        }
                        return ok(json).as("application/json");
                    } else {
                        ChartType type = ChartType.getChartType(FilenameUtils.removeExtension(chart));
                        if(type == null) {
                            return notFound("unknown chart type "+chart);
                        }
                        String format = FilenameUtils.getExtension(chart);
                        List<ereefs.charts.Chart> charts = f.getCharts(type, request().queryString());
                        if(charts.isEmpty()) {
                            return notFound();
                        } else {
                            ChartRenderer renderer = new ChartRenderer(charts.get(0).getChart());
                            String svg = renderer.render();
                            return toFormat(svg, format);
                        }
                    }
                    
                    /*
                    ChartFactory f = new ChartFactory();
                    if(StringUtils.isBlank(chart)) {
                        String format = getParameter("format");
                        List<ereefs.charts.Chart> charts = f.getCharts(request().queryString());
                        final ObjectNode json = Json.newObject();
                        final ArrayNode aNode = json.putArray("charts");
                        for(ereefs.charts.Chart chart : charts) {
                            ChartDescription desc = chart.getDescription();
                            final ObjectNode chartNode = Json.newObject();
                            chartNode.put("type", desc.getType().toString());
                            for(Map.Entry<String, String> me : desc.getProperties().entrySet()) {
                                chartNode.put(me.getKey(), me.getValue());
                            }
                            chartNode.put("url", buildUrl(chart, format));
                            aNode.add(chartNode);
                        }
                        return ok(json).as("application/json");
                    } else {
                        ChartType type = ChartType.getChartType(FilenameUtils.removeExtension(chart));
                        if(type == null) {
                            return notFound("unknown chart type "+chart);
                        }
                        String format = FilenameUtils.getExtension(chart);
                        List<ereefs.charts.Chart> charts = f.getCharts(type, request().queryString());
                        if(charts.isEmpty()) {
                            return notFound();
                        } else {
                            ChartRenderer renderer = new ChartRenderer(charts.get(0).getChart());
                            String svg = renderer.render();
                            return toFormat(svg, format);
                        }
                    }
                    */
                    
                    /*
                    String format = getParameter("format");
                    if(StringUtils.isBlank(format)) {
                        format = "png";
                    }
                    String path = getParameter("path");
                    final FileStore.Manager fm = fileStore.getManager(session);
                    FileStore.FileOrFolder fof = fm.getFileOrFolder("/"+path);
                    if (fof instanceof FileStore.File) {
                        FileStore.File file = (FileStore.File) fof;
                        // Check this is an OpenXML document (no chance otherwise)
                        if (!file.getMimeType().equals(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
                          return notFound();
                        }
                        Spreadsheet spreadsheet = new Spreadsheet(file.getData(), file.getMimeType());
                        List<ereefs.charts.Chart> charts = spreadsheet.getCharts(request().queryString());
                        if(charts.isEmpty()) {
                            return notFound();
                        } else {
                            ChartRenderer renderer = new ChartRenderer(charts.get(0).getChart());
                            String svg = renderer.render();
                            return toFormat(svg, format);
                        }
                    } else {
                        return notFound();
                    }
                    */
                    
                } catch(Exception e) {
                    throw new RepositoryException(e);
                }
            }
        });
    }

    private Result toFormat(String svg, String format) throws Exception {
        if("png".equals(format)) {
            Document doc = toDocument(svg);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            new PNGTranscoder().transcode(new TranscoderInput(doc), new TranscoderOutput(os));
            return ok(os.toByteArray()).as("image/png");
        } else {
            return ok(svg).as("image/svg+xml");
        }
    }

    private Document toDocument(String svg) throws Exception {
        // Turn back into DOM
        String parserName = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parserName);
        Document doc = f.createDocument("file:///test.svg",
          new CharArrayReader(svg.toCharArray()));
        String h = doc.getDocumentElement().getAttribute("height");
        String w = doc.getDocumentElement().getAttribute("width");
        doc.getDocumentElement().setAttributeNS(null, "viewbox", String.format("0 0 %s %s", w, h));
//        if (relativeDimensions) {
//          doc.getDocumentElement().setAttribute("height", "100%")
//          doc.getDocumentElement().setAttribute("width", "100%")
//        }
        return doc;
    }
}
