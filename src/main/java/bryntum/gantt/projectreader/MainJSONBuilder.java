package bryntum.gantt.projectreader;

import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.json.JSONObject;

import org.mpxj.ProjectFile;
import org.mpxj.ProjectProperties;
import org.mpxj.RelationshipLagCalendar;
import org.mpxj.ScheduleFrom;

public class MainJSONBuilder implements JSONBuilder<JSONObject> {

    Properties properties;
    DateTimeFormatter dateFormat;
    DateTimeFormatter dateTimeFormat;
    DateTimeFormatter timeFormat;
    boolean isMpx;

    public MainJSONBuilder(Properties properties, DateTimeFormatter dateFormat, DateTimeFormatter timeFormat, DateTimeFormatter dateTimeFormat, boolean isMpx) {
        this.properties = properties;
        this.dateFormat = dateFormat;
        this.dateTimeFormat = dateTimeFormat;
        this.timeFormat = timeFormat;
        this.isMpx      = isMpx;
    }

    /**
     * Extracts the provided MPP file contents into a JSON object.
     *
     * @param projectFile MPP file to process
     * @return A JSON object containing the project data (tasks, dependencies,
     *         resources, assignments).
     */
    @Override
    public JSONObject buildJSON(ProjectFile projectFile) {
        ProjectProperties projectProperties = projectFile.getProjectProperties();

        // put all the data into a single object
        JSONObject result = new JSONObject();

        // if we need to provide project model fields into a separate container
        if (Boolean.parseBoolean(properties.getProperty("use.project.container"))) {
            JSONObject projectJSON = new JSONObject();

            // calculate unit conversion ratios
            projectJSON.put(properties.getProperty("project.DAYS_PER_WEEK"), projectProperties.getMinutesPerWeek().intValue() / projectProperties.getMinutesPerDay().intValue());
            projectJSON.put(properties.getProperty("project.DAYS_PER_MONTH"), projectProperties.getMinutesPerMonth().intValue() / projectProperties.getMinutesPerDay().intValue());
            projectJSON.put(properties.getProperty("project.HOURS_PER_DAY"), projectProperties.getMinutesPerDay().intValue() / 60);

            projectJSON.put(properties.getProperty("project.CALENDAR"), projectFile.getDefaultCalendar().getUniqueID());

            RelationshipLagCalendar lagCalendar = projectProperties.getRelationshipLagCalendar();
            String dependencyCalendar = "";

            // Map which calendar should be used for dependency lags
            switch (lagCalendar) {
                case PREDECESSOR: dependencyCalendar = "FromEvent"; break;
                case SUCCESSOR: dependencyCalendar = "ToEvent"; break;
                case PROJECT_DEFAULT: dependencyCalendar = "Project"; break;
                case TWENTY_FOUR_HOUR: dependencyCalendar = "AllWorking"; break;
            }

            projectJSON.put(properties.getProperty("project.DEPENDENCY_CALENDAR"), dependencyCalendar);

            // depending on the project scheduling direction return either project start or end date
            if (projectProperties.getScheduleFrom() == ScheduleFrom.START) {
                projectJSON.put(properties.getProperty("project.START"), dateTimeFormat.format(projectProperties.getStartDate()));
                projectJSON.put(properties.getProperty("project.DIRECTION"), "Forward");
            } else {
                projectJSON.put(properties.getProperty("project.FINISH"), dateTimeFormat.format(projectProperties.getFinishDate()));
                projectJSON.put(properties.getProperty("project.DIRECTION"), "Backward");
            }

            result.put("project", projectJSON);
        }

        JSONBuilder<JSONObject> calendarsJSONBuilder;

        if (Boolean.parseBoolean(properties.getProperty("use.vanilla.calendars"))) {
            calendarsJSONBuilder = new VanillaCalendarsJSONBuilder(properties, dateFormat, timeFormat, dateTimeFormat);
        }
        else {
            calendarsJSONBuilder = new ExtCalendarsJSONBuilder(properties, dateFormat, timeFormat, dateTimeFormat);
        }

        result.put("calendars", calendarsJSONBuilder.buildJSON(projectFile));

        result.put("tasks", new TasksJSONBuilder(properties, dateTimeFormat, isMpx).buildJSON(projectFile));
        result.put("dependencies", new DependenciesJSONBuilder(properties).buildJSON(projectFile));
        result.put("assignments", new AssignmentsJSONBuilder(properties).buildJSON(projectFile));
        result.put("resources", new ResourcesJSONBuilder(properties).buildJSON(projectFile));
        result.put("columns", new ColumnsJSONBuilder(properties).buildJSON(projectFile));

        return result;
    }

}
