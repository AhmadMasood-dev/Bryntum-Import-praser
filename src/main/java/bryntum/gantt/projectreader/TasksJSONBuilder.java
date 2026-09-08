package bryntum.gantt.projectreader;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONObject;

import org.mpxj.ChildTaskContainer;
import org.mpxj.ConstraintType;
import org.mpxj.Duration;
import org.mpxj.LocalDateTimeRange;
import org.mpxj.ProjectCalendar;
import org.mpxj.ProjectFile;
import org.mpxj.ProjectProperties;
import org.mpxj.ScheduleFrom;
import org.mpxj.Task;
import org.mpxj.TaskMode;
import org.mpxj.TaskType;
import org.mpxj.TimeUnit;

/**
 * Class implementing extraction a MS Project file tasks data
 * into a JSONObject.
*/
public class TasksJSONBuilder implements JSONBuilder<JSONObject> {

    static JSONObject EMPTY_JSON_OBJECT = new JSONObject();

    Properties properties;
    DateTimeFormatter dateTimeFormat;
    boolean isMpx;

    // field names
    String idField;
    String indexField;
    String nameField;
    String notesField;
    String startField;
    String finishField;
    String durationField;
    String durationUnitField;
    String workField;
    String workUnitField;
    String calendarIdField;
    String percentCompleteField;
    String milestoneField;
    String rollupField;
    String modeField;
    String typeField;
    String ignoreResourceCalendarField;
    String constraintDateField;
    String constraintTypeField;
    String baselineStartField;
    String baselineFinishField;
    String baselineDurationField;
    String baselinesField;
    String segmentsField;
    String childrenField;
    String expandedField;
    String leafField;

    String summaryTaskType;
    String dynamicAssignmentsType;
    Boolean useSegmentsForSuspended;

    public TasksJSONBuilder(Properties properties, DateTimeFormatter dateTimeFormat, boolean isMpx) {
        this.properties = properties;
        this.dateTimeFormat = dateTimeFormat;
        this.isMpx      = isMpx;

        // load related properties
        loadProperties(properties);
    }

    public void loadProperties(Properties properties) {
        // load field names
        idField = properties.getProperty("task.UNIQUE_ID");
        indexField = properties.getProperty("task.INDEX");
        nameField = properties.getProperty("task.NAME");
        notesField = properties.getProperty("task.NOTES");
        startField = properties.getProperty("task.START");
        finishField = properties.getProperty("task.FINISH");
        durationField = properties.getProperty("task.DURATION");
        durationUnitField = properties.getProperty("task.DURATION_UNIT");
        workField = properties.getProperty("task.WORK");
        workUnitField = properties.getProperty("task.WORK_UNIT");
        calendarIdField = properties.getProperty("task.CALENDAR");
        percentCompleteField = properties.getProperty("task.PERCENT_COMPLETE");
        milestoneField = properties.getProperty("task.MILESTONE");
        rollupField = properties.getProperty("task.ROLLUP");
        modeField = properties.getProperty("task.MODE");
        typeField = properties.getProperty("task.TYPE");
        ignoreResourceCalendarField = properties.getProperty("task.IGNORE_RESOURCE_CALENDAR");
        constraintDateField = properties.getProperty("task.CONSTRAINT_DATE");
        constraintTypeField = properties.getProperty("task.CONSTRAINT_TYPE");
        baselineStartField = properties.getProperty("task.BASELINE_START");
        baselineFinishField = properties.getProperty("task.BASELINE_FINISHES");
        baselineDurationField = properties.getProperty("task.BASELINE_DURATION");
        baselinesField = properties.getProperty("task.BASELINES");
        segmentsField = properties.getProperty("task.SEGMENTS");
        childrenField = properties.getProperty("task.CHILDREN");
        expandedField = properties.getProperty("task.EXPANDED");
        leafField = properties.getProperty("task.LEAF");

        // we might force summary tasks to have a specific scheduling mode
        if (properties.getProperty("force.summaryTask.type") != null && !properties.getProperty("force.summaryTask.type").equals("")) {
            summaryTaskType = properties.getProperty("force.summaryTask.type");
        }

        // ExtGantt might use "DynamicAssignment" scheduling mode
        if (properties.getProperty("taskType.DYNAMIC_ASSIGNMENTS") != null && !properties.getProperty("taskType.DYNAMIC_ASSIGNMENTS").equals("")) {
            dynamicAssignmentsType = properties.getProperty("taskType.DYNAMIC_ASSIGNMENTS");
        }

        useSegmentsForSuspended = Boolean.parseBoolean(properties.getProperty("use.segments.for.suspended"));
    }

    private String getConstraintType(ConstraintType constraintType) {
        String result = null;

        if (constraintType != null) {
            result = properties.getProperty("constraintType." + constraintType);
        }

        return result;
    }

    String getUnitByTimeUnit(TimeUnit timeUnit) {
        String unitName = null;

        if (timeUnit != null) {
            unitName = timeUnit.getName();

            return properties.getProperty("timeUnit." + unitName, unitName);
        }

        return unitName;
    }

    private String getTaskType(Task task) {
        String result = properties.getProperty("taskType." + task.getType());

        // force parent tasks to have the scheduling mode defined w/ "force.summaryTask.type" option
        if (task.hasChildTasks() && summaryTaskType != null) {
            result = summaryTaskType;
        }
        else if (result == null) {
            // ExtGantt has not one-to-one mapping to MS Project task types
            if (dynamicAssignmentsType != null && task.getType() == TaskType.FIXED_DURATION && task.getEffortDriven()) {
                result = dynamicAssignmentsType;
            }
        }

        return result;
    }

    JSONObject getBaselineJSON(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null || endDate != null) {
            JSONObject baselineJSON = new JSONObject();

            if (startDate != null) {
                baselineJSON.put(startField, dateTimeFormat.format(startDate));
            }
            if (endDate != null) {
                baselineJSON.put(finishField, dateTimeFormat.format(endDate));
            }

            return baselineJSON;
        }

        return TasksJSONBuilder.EMPTY_JSON_OBJECT;
    }

    /**
     * Extracts the provided task data into JSON object.
     *
     * @param task Task to extract
     * @return JSON object keeping the extracted task data
     */
    public JSONObject getTaskJSON(Task task) {
        return getTaskJSON(task, null);
    }

    private Integer getTaskIndexFromParent(Task task) {
        Task parent = task.getParentTask();

        if (parent == null) {
            return null;
        }

        int index = 0;
        for (Task sibling : parent.getChildTasks()) {
            if (sibling == task) {
                return index;
            }
            index++;
        }

        return null;
    }

    private JSONObject getTaskJSON(Task task, Integer index) {
        JSONObject taskJSON = new JSONObject();

        LocalDateTime suspendDate = task.getSuspendDate();
        LocalDateTime resumeDate = task.getResume();
        LocalDateTime startDate = task.getStart();
    	LocalDateTime endDate = task.getFinish();

        taskJSON.put(idField, task.getUniqueID());
        if (indexField != null) {
            Integer taskIndex = index != null ? index : getTaskIndexFromParent(task);
            if (taskIndex != null) {
                taskJSON.put(indexField, taskIndex);
            }
        }
        taskJSON.put(nameField, task.getName());

        String notes = task.getNotes();
        if (notesField != null && notes != null && !notes.isEmpty()) {
            taskJSON.put(notesField, notes);
        }

        if (startDate != null) {
            taskJSON.put(startField, dateTimeFormat.format(task.getStart()));
        }
        if (endDate != null) {
            taskJSON.put(finishField, dateTimeFormat.format(task.getFinish()));
        }

        Duration duration = task.getDuration();
        if (duration != null) {
            taskJSON.put(durationField, duration != null ? duration.getDuration() : null);
            taskJSON.put(durationUnitField, getUnitByTimeUnit(duration.getUnits()));
        }

        Duration work = task.getWork();
        if (work != null) {
            taskJSON.put(workField, work != null ? work.getDuration() : null);
            taskJSON.put(workUnitField, getUnitByTimeUnit(work.getUnits()));
        }

        ProjectCalendar calendar = task.getCalendar();
        if (calendar != null) {
            taskJSON.put(calendarIdField, calendar.getUniqueID());
        }

        taskJSON.put(percentCompleteField, task.getPercentageComplete());
        taskJSON.put(milestoneField, task.getMilestone());
        taskJSON.put(rollupField, task.getRollup());
        taskJSON.put(modeField, task.getTaskMode() == TaskMode.MANUALLY_SCHEDULED);
        taskJSON.put(typeField, getTaskType(task));

        if (ignoreResourceCalendarField != null) {
            taskJSON.put(ignoreResourceCalendarField, task.getIgnoreResourceCalendar());
        }

        LocalDateTime constraintDate = task.getConstraintDate();
        if (constraintDate != null) {
            taskJSON.put(constraintDateField, dateTimeFormat.format(constraintDate));
        }

        ConstraintType constraintType = task.getConstraintType();
        if (constraintType != null) {
            taskJSON.put(constraintTypeField, getConstraintType(constraintType));
        }

        // If baselines field is configured - extract baselines as an array
        if (baselinesField != null) {
            // array for the extracted baselines of the task
            JSONArray baselines = new JSONArray();

            int lastBaselineIndex = -1;

            JSONObject baselineJSON = getBaselineJSON(task.getBaselineStart(), task.getBaselineFinish());

            if (baselineJSON != TasksJSONBuilder.EMPTY_JSON_OBJECT) {
                baselines.put(baselineJSON);
                lastBaselineIndex = 0;
            }

            for (int i = 1; i <= 10; i++) {
                baselineJSON = getBaselineJSON(task.getBaselineStart(i), task.getBaselineFinish(i));

                if (baselineJSON != TasksJSONBuilder.EMPTY_JSON_OBJECT) {
                    // if there is a gap between previous baseline index and this one - fill it with empty objects
                    for (int j = lastBaselineIndex + 1; j < i; j++) {
                        baselines.put(TasksJSONBuilder.EMPTY_JSON_OBJECT);
                    }

                    baselines.put(baselineJSON);
                    // remember the last found baseline index
                    lastBaselineIndex = i;
                }
            }

            // Provide baselines field if has at least one baseline provided
            if (lastBaselineIndex > -1) {
                taskJSON.put(baselinesField, baselines);
            }

        } else  {
            if (baselineStartField != null && task.getBaselineStart() != null) {
                taskJSON.put(baselineStartField, dateTimeFormat.format(task.getBaselineStart()));
            }
            if (baselineFinishField != null && task.getBaselineFinish() != null) {
                taskJSON.put(baselineFinishField, dateTimeFormat.format(task.getBaselineFinish()));
            }
            // TODO: BaselineDuration is not supported by the Gantt Task model at the
            // moment, so this code doesn't work really
            if (baselineDurationField != null && task.getBaselineDuration() != null) {
                taskJSON.put(baselineDurationField, task.getBaselineDuration());
            }
        }

        List<LocalDateTimeRange> taskSplits = task.getSplits();

        // If the task is suspended and resumed then we can use "segments" to represent that state
        if (taskSplits == null && useSegmentsForSuspended && suspendDate != null && resumeDate != null) {
            taskSplits = new ArrayList<LocalDateTimeRange>();

            taskSplits.add(new LocalDateTimeRange(startDate, suspendDate));
            taskSplits.add(new LocalDateTimeRange(resumeDate, endDate));
        }

        // Build segments based on task splits
        if (taskSplits != null) {
            JSONArray segments = new JSONArray();
            LocalDateTime segmentStart = startDate;
            JSONObject segmentJSON;

            // Surprisingly getSplits() does not return splits but the ranges task performs
            for (LocalDateTimeRange split : taskSplits) {
                segmentJSON = new JSONObject();

                segmentJSON.put(startField, dateTimeFormat.format(split.getStart()));
                segmentJSON.put(finishField, dateTimeFormat.format(split.getEnd()));

                segments.put(segmentJSON);
            }

            taskJSON.put(segmentsField, segments);
        }

        // retrieve the task children info
        JSONArray children = new JSONArray();

        int childIndex = 0;
        for (Task child : task.getChildTasks()) {
            children.put(getTaskJSON(child, childIndex));
            childIndex++;
        }

        if (children.length() > 0) {
            taskJSON.put(childrenField, children);
            taskJSON.put(expandedField, task.getExpanded());
            taskJSON.put(leafField, false);
        } else {
            taskJSON.put(leafField, true);
        }

        return taskJSON;
    }

    @Override
    public JSONObject buildJSON(ProjectFile projectFile) {
        JSONObject result = new JSONObject();

        ProjectProperties projectProperties = projectFile.getProjectProperties();

        JSONArray taskListJSON = new JSONArray();

        ChildTaskContainer taskSource = projectFile;

        // If that's an MS Project file w/ "Show Project Summary Task" option disabled - skip root level task which represents the project summary
        if (!this.isMpx && projectProperties.getFileApplication() == "Microsoft" && !projectProperties.getShowProjectSummaryTask()) {
            taskSource = projectFile.getChildTasks().get(0);
        }

        for (Task task : taskSource.getChildTasks()) {
            taskListJSON.put(getTaskJSON(task, taskListJSON.length()));
        }

        result.put(childrenField, taskListJSON);
        result.put(nameField, "Root Node");

        // if we don't use a separate container for project fields
        if (!Boolean.parseBoolean(properties.getProperty("use.project.container"))) {
            JSONObject tasksMetaDataJSON = new JSONObject();

            // depending on the project scheduling direction return either project start or
            // end date
            if (projectProperties.getScheduleFrom() == ScheduleFrom.START) {
                tasksMetaDataJSON.put("projectStartDate", dateTimeFormat.format(projectProperties.getStartDate()));
            } else {
                tasksMetaDataJSON.put("projectEndDate", dateTimeFormat.format(projectProperties.getFinishDate()));
                tasksMetaDataJSON.put("scheduleBackwards", true);
            }

            tasksMetaDataJSON.put("cascadeChanges", projectProperties.getHonorConstraints());
            result.put("metaData", tasksMetaDataJSON);
        }

        return result;
    }

}
