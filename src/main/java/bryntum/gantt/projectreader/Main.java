/**
 * MS Project import example.
 * Copyright Bryntum, 2021
 */
package bryntum.gantt.projectreader;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.mpxj.ProjectFile;
import org.mpxj.mpx.MPXReader;
import org.mpxj.reader.ProjectReader;
import org.mpxj.reader.UniversalProjectReader;

public class Main {
    private static String errorMessage = "There was an exception raised during the operation. Exception message: ";
    private static String wrongUsageMessage = "Usage: java -jar bryntum-project-reader.jar mpp-file output-file \nNote: provide \"1\" instead of output-file path to return JSON into stdout.";

    static Properties defaultProperties = new Properties();
    static Properties properties = new Properties(defaultProperties);

    static {
        try {
            defaultProperties.load(Main.class.getResourceAsStream("/META-INF/projectreader.default.properties"));

            // if properties file overriding defaults exists - load it
            if (Main.class.getResource("/META-INF/projectreader.properties") != null) {
                properties.load(Main.class.getResourceAsStream("/META-INF/projectreader.properties"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(properties.getProperty("date.format", "yyyy-MM-dd"));
    static DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern(properties.getProperty("dateTime.format", "yyyy-MM-dd'T'HH:mm:ss"));
    static DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern(properties.getProperty("time.format", "HH:mm"));

    static int indentFactor = Integer.parseInt(properties.getProperty("indent.size", "4"));

    public static void main(String[] args) {
        String sourceFile, targetFile;
        Boolean printResult;

        try {
            if (args.length < 2) {
                System.out.println(wrongUsageMessage);
                System.exit(0);
            }

            sourceFile = args[0];
            targetFile = args[1];
            printResult = targetFile.equals("1");

            // optional indent size for resulting JSON string
            if (args.length > 2 && args[2] != null) {
                indentFactor = Integer.parseInt(args[2]);
            }

            if (args.length > 3 && args[3] != null) {
                dateFormat = DateTimeFormatter.ofPattern(args[3]);
            }

            if (args.length > 4 && args[4] != null) {
                dateTimeFormat = DateTimeFormatter.ofPattern(args[4]);
            }

            UniversalProjectReader universalReader = new UniversalProjectReader();

            UniversalProjectReader.ProjectReaderProxy proxy = universalReader.getProjectReaderProxy(sourceFile);

            // retrieve the effective reader class
            ProjectReader projectReader = proxy.getProjectReader();

            // we have a special twist for mpx-file tasks
            boolean isMpx = projectReader instanceof MPXReader;

            ProjectFile projectFile = projectReader.read(sourceFile);

            String result = new MainJSONBuilder(properties, dateFormat, timeFormat, dateTimeFormat, isMpx).buildJSON(projectFile).toString(indentFactor);

            BufferedWriter out = new BufferedWriter(printResult ? new OutputStreamWriter(System.out) : new FileWriter(targetFile));

            out.write(result);
            out.close();

        } catch (Exception e) {
            System.out.println(errorMessage);
            e.printStackTrace();
            System.exit(0);
        }
    }
}
