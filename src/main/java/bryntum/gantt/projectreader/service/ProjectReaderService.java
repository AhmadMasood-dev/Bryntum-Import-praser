package bryntum.gantt.projectreader.service;

import bryntum.gantt.projectreader.MainJSONBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import org.mpxj.ProjectFile;
import org.mpxj.mpx.MPXReader;
import org.mpxj.reader.ProjectReader;
import org.mpxj.reader.UniversalProjectReader;
import org.springframework.stereotype.Service;

@Service
public class ProjectReaderService {
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    private static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String DEFAULT_TIME_FORMAT = "HH:mm";
    private static final String DEFAULT_INDENT = "4";

    private static final Properties DEFAULT_PROPERTIES = new Properties();
    private static final Properties PROPERTIES = new Properties(DEFAULT_PROPERTIES);

    static {
        try {
            DEFAULT_PROPERTIES.load(ProjectReaderService.class.getResourceAsStream("/META-INF/projectreader.default.properties"));

            if (ProjectReaderService.class.getResource("/META-INF/projectreader.properties") != null) {
                PROPERTIES.load(ProjectReaderService.class.getResourceAsStream("/META-INF/projectreader.properties"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String buildJson(Path sourceFile, Integer indentOverride, String dateFormatOverride, String dateTimeFormatOverride, String timeFormatOverride) throws Exception {
        int indentFactor = resolveIndent(indentOverride);
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(resolvePattern("date.format", dateFormatOverride, DEFAULT_DATE_FORMAT));
        DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern(resolvePattern("dateTime.format", dateTimeFormatOverride, DEFAULT_DATE_TIME_FORMAT));
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern(resolvePattern("time.format", timeFormatOverride, DEFAULT_TIME_FORMAT));

        UniversalProjectReader universalReader = new UniversalProjectReader();
        UniversalProjectReader.ProjectReaderProxy proxy = universalReader.getProjectReaderProxy(sourceFile.toString());

        ProjectReader projectReader = proxy.getProjectReader();
        boolean isMpx = projectReader instanceof MPXReader;

        ProjectFile projectFile = projectReader.read(sourceFile.toString());

        return new MainJSONBuilder(PROPERTIES, dateFormat, timeFormat, dateTimeFormat, isMpx)
            .buildJSON(projectFile)
            .toString(indentFactor);
    }

    private int resolveIndent(Integer indentOverride) {
        if (indentOverride != null) {
            return indentOverride.intValue();
        }
        String indentValue = PROPERTIES.getProperty("indent.size", DEFAULT_INDENT);
        return Integer.parseInt(indentValue);
    }

    private String resolvePattern(String key, String override, String fallback) {
        if (override != null && !override.trim().isEmpty()) {
            return override;
        }
        return PROPERTIES.getProperty(key, fallback);
    }
}
