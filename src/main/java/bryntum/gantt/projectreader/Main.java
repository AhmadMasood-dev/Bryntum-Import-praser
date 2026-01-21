/**
 * MS Project import example.
 * Copyright Bryntum, 2021
 */
package bryntum.gantt.projectreader;

import bryntum.gantt.projectreader.service.ProjectReaderService;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Paths;

public class Main {
    private static String errorMessage = "There was an exception raised during the operation. Exception message: ";
    private static String wrongUsageMessage = "Usage: java -jar bryntum-project-reader.jar mpp-file output-file \nNote: provide \"1\" instead of output-file path to return JSON into stdout.";

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

            Integer indentFactor = null;
            String dateFormat = null;
            String dateTimeFormat = null;

            if (args.length > 2 && args[2] != null) {
                indentFactor = Integer.valueOf(args[2]);
            }

            if (args.length > 3 && args[3] != null) {
                dateFormat = args[3];
            }

            if (args.length > 4 && args[4] != null) {
                dateTimeFormat = args[4];
            }

            ProjectReaderService readerService = new ProjectReaderService();
            String result = readerService.buildJson(Paths.get(sourceFile), indentFactor, dateFormat, dateTimeFormat, null);

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
