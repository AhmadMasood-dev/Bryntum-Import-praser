package bryntum.gantt.projectreader;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONObject;

import org.mpxj.DayType;
import org.mpxj.LocalDateRange;
import org.mpxj.LocalTimeRange;
import org.mpxj.ProjectCalendar;
import org.mpxj.ProjectCalendarContainer;
import org.mpxj.ProjectCalendarDays;
import org.mpxj.ProjectCalendarException;
import org.mpxj.ProjectCalendarHours;
import org.mpxj.ProjectCalendarWeek;
import org.mpxj.ProjectFile;
import org.mpxj.RecurrenceType;
import org.mpxj.RecurringData;

public class VanillaCalendarsJSONBuilder implements JSONBuilder<JSONObject> {

    Properties properties;
    DateTimeFormatter dateFormat;
    DateTimeFormatter dateTimeFormat;
    DateTimeFormatter timeFormat;

    String typeProperty;
    String nameProperty;
    String priorityProperty;
    String startProperty;
    String finishProperty;
    String isWorkingProperty;
    String recurrentStartDateProperty;
    String recurrentEndDateProperty;

    public VanillaCalendarsJSONBuilder(Properties properties, DateTimeFormatter dateFormat, DateTimeFormatter timeFormat, DateTimeFormatter dateTimeFormat) {
        this.properties = properties;
        this.dateFormat = dateFormat;
        this.dateTimeFormat = dateTimeFormat;
        this.timeFormat = timeFormat;

        // read JSON field names
        this.typeProperty               = properties.getProperty("calendarDay.TYPE");
        this.nameProperty               = properties.getProperty("calendarDay.NAME");
        this.priorityProperty           = properties.getProperty("calendarDay.PRIORITY");
        this.startProperty              = properties.getProperty("calendarDay.START");
        this.finishProperty             = properties.getProperty("calendarDay.FINISH");
        this.isWorkingProperty          = properties.getProperty("calendarDay.IS_WORKING_DAY");
        this.recurrentStartDateProperty = properties.getProperty("calendarDay.RECURRENT_START_DATE");
        this.recurrentEndDateProperty   = properties.getProperty("calendarDay.RECURRENT_END_DATE");
    }

    /**
     * Indicates if the provided hours collections are identical.
     * @param hours1
     * @param hours2
     * @return
     */
    private boolean isHoursEqual(ProjectCalendarHours hours1, ProjectCalendarHours hours2) {
        if ((hours1 == null && hours2 != null) || (hours1 != null && hours2 == null)) {
            return false;
        }

        if (hours1.size() != hours2.size()) {
            return false;
        }

        for (int i = 1; i <= hours1.size(); i++) {
            // if any ranges do not match
            if (!hours1.get(i).equals(hours2.get(i))) {
                return false;
            }
        }

        return true;
    }

    private String addDateRangeInfo(JSONObject intervalJSON, LocalDateRange range) {
        String rule = "";

        if (range != null) {
            LocalDate startDate = range.getStart();
            LocalDate endDate = range.getEnd();

            if (startDate != null) {
                intervalJSON.put(this.startProperty, this.dateFormat.format(startDate));
            }

            if (endDate != null) {
                // End date extracted by MPXJ is -1 day.
                // Adjusting it here the way the Gantt expects it.
                intervalJSON.put(this.finishProperty, this.dateFormat.format(
                    endDate.plus(1, ChronoUnit.DAYS)
                ));
            }
        }

        return rule;
    }

    private void addDayIntervals(JSONArray intervalsJSON, List<DayOfWeek> days, ProjectCalendarHours hours, LocalDateRange dateRange, Map<String, Object> props) {
        JSONObject intervalJSON;

        // If the days are non working we represent them as "1st day 00:00 till last day + 1 00:00" solid range
        boolean useSolidRange = hours.size() == 0;

        // We also use the same approach if each day is working for 24hrs
        if (hours.size() == 1) {
            LocalTimeRange hoursRange = hours.get(0);
            LocalTime hoursRangeStart = hoursRange.getStart();
            LocalTime hoursRangeEnd = hoursRange.getEnd();

            useSolidRange = hoursRangeStart.getHour() == 0 &&
                hoursRangeStart.getMinute() == 0 &&
                hoursRangeStart.getSecond() == 0 &&
                hoursRangeEnd.getHour() == 0 &&
                hoursRangeEnd.getMinute() == 0 &&
                hoursRangeEnd.getSecond() == 0;
        }

        // If we have a solid working or non working range taking N days
        if (useSolidRange) {
            intervalJSON = new JSONObject();

            for (String key : props.keySet()) {
                intervalJSON.put(key, props.get(key));
            }

            intervalJSON.put(this.isWorkingProperty, hours.size() > 0);
            intervalJSON.put(this.recurrentStartDateProperty, "on " + days.get(0).toString());
            intervalJSON.put(this.recurrentEndDateProperty, "on " + days.get(days.size() - 1).plus(1).toString());
            addDateRangeInfo(intervalJSON, dateRange);

            intervalsJSON.put(intervalJSON);
        }
        // otherwise us "on Mon,Tue,Wed at XX:XX" rules
        else {
            for (LocalTimeRange hoursRange : hours) {
                intervalJSON = new JSONObject();

                for (String key : props.keySet()) {
                    intervalJSON.put(key, props.get(key));
                }

                String daysText = "", delimiter = "";

                for (DayOfWeek day : days) {
                    daysText += delimiter + day.toString();
                    delimiter = ",";
                }

                intervalJSON.put(this.isWorkingProperty, true);
                intervalJSON.put(this.recurrentStartDateProperty, "on " + daysText + " at " + timeFormat.format(hoursRange.getStart()));
                intervalJSON.put(this.recurrentEndDateProperty, "on " + daysText + " at " + timeFormat.format(hoursRange.getEnd()));
                addDateRangeInfo(intervalJSON, dateRange);

                intervalsJSON.put(intervalJSON);
            }
        }
    }

    private void fillWeekOverrideIntervalsJSON(JSONArray intervalsJSON, ProjectCalendar calendar, ProjectCalendarWeek week) {
        Map<String, Object> commonProps = new HashMap<String, Object>();

        commonProps.put(this.priorityProperty, 25);

        fillWeekIntervalsJSON(intervalsJSON, calendar, week, week.getDateRange(), commonProps);
    }


    private void fillDefaultWeekIntervalsJSON(JSONArray intervalsJSON, ProjectCalendar calendar) {
        fillWeekIntervalsJSON(intervalsJSON, calendar, calendar, null, null);
    }

    private void fillWeekIntervalsJSON(JSONArray intervalsJSON, ProjectCalendar calendar, ProjectCalendarDays week, LocalDateRange dateRange, Map<String, Object> commonProps) {

        if (commonProps == null) {
            commonProps = new HashMap<String, Object>();
        }

        if (commonProps.get(this.typeProperty) == null) {
            commonProps.put(this.typeProperty, "Week");
            commonProps.put(this.nameProperty, week.getName());
        }

        ProjectCalendar parentCalendar = calendar.getParent();

        DayOfWeek day = DayOfWeek.MONDAY;
        ProjectCalendarHours prevDayHours = null;
        Integer daysProcessed = 0;

        List<DayOfWeek> days = new ArrayList<DayOfWeek>();
        DayType dayType = week.getCalendarDayType(day);

        // If that's NOT a default working time inherited from parent calendar or default week
        // or it's marked as default but we have nowhere to inherit it from
        if (dayType != DayType.DEFAULT || (parentCalendar == null && dateRange == null)) {
            // if that's default working time - calendar should report its ranges, otherwise it's set on the week
            prevDayHours = dayType == DayType.DEFAULT ? calendar.getCalendarHours(day) : week.getCalendarHours(day);
            days.add(day);
        }

        // start iterating from Tue
        day = DayOfWeek.TUESDAY;

        // iterate over the week
        while (daysProcessed < 6) {
            dayType = week.getCalendarDayType(day);

            ProjectCalendarHours dayHours = dayType == DayType.DEFAULT ? calendar.getCalendarHours(day) : week.getCalendarHours(day);

            // if the day setting are inherited from parent
            if ((parentCalendar != null || dateRange != null) && dayType == DayType.DEFAULT) {
                // finish open "days" sequence
                if (prevDayHours != null) {
                    addDayIntervals(intervalsJSON, days, prevDayHours, dateRange, commonProps);
                }
                days.clear();
                prevDayHours = null;
            }
            else {

                if (prevDayHours != null && isHoursEqual(dayHours, prevDayHours)) {
                    days.add(day);
                }
                else {
                    if (prevDayHours != null) {
                        addDayIntervals(intervalsJSON, days, prevDayHours, dateRange, commonProps);
                    }

                    // start new days sequence
                    days.clear();
                    days.add(day);
                }

                prevDayHours = dayHours;
            }

            day = day.plus(1);
            daysProcessed++;
        }

        if (days.size() > 0) {
            addDayIntervals(intervalsJSON, days, prevDayHours, dateRange, commonProps);
        }
    }

    /**
     * Build recurrence rule in Later JS format.
     * @param recurringData
     * @return Recurrence rule.
     */
    public String getRecurrenceRule(RecurringData recurringData) {
        Integer frequency = recurringData.getFrequency() != null ? recurringData.getFrequency() : 1;

        String recurrenceRule = "every " + frequency;

        RecurrenceType recurrenceType = recurringData.getRecurrenceType();

        Integer dayNumber;
        boolean relative;

        String trailingPart = " after " + dateFormat.format(recurringData.getStartDate());

        // stop condition
//        if (recurringData.getUseEndDate()) {
            trailingPart += " before " + dateFormat.format(recurringData.getFinishDate());
// TODO add support of limiting by occurrences number (commented since causes an exception
//        }
//        else {
//            trailingPart += " before " + recurringData.getOccurrences();
//        }

        switch (recurrenceType) {
            case DAILY :
                recurrenceRule += " day ";
                break;

            case WEEKLY :
                recurrenceRule += " week on ";

                for (int i = 1; i <= 7; i++) {

                    String delimiter = "";
                    DayOfWeek day = DayOfWeek.of(i);

                    if (recurringData.getWeeklyDay(day)) {
                        recurrenceRule += delimiter + day;
                        delimiter = ", ";
                    }
                }

                recurrenceRule += " ";

                break;

            case MONTHLY :
                recurrenceRule += " month ";

                dayNumber = recurringData.getDayNumber();
                relative = recurringData.getRelative();

                if (relative) {
                    // every 1 month on Mon on the 3 day instance
                    recurrenceRule += "on " + recurringData.getDayOfWeek() + " on the " + (dayNumber == 5 ? "last" : dayNumber) + " day instance ";
                } else {
                    recurrenceRule += "on the " + dayNumber + " day ";
                }

                break;

            case YEARLY :
                recurrenceRule += " year ";

                dayNumber = recurringData.getDayNumber();
                relative = recurringData.getRelative();
                Month month = Month.of(recurringData.getMonthNumber());

                if (relative) {
                    // every 1 year on Mon on the 3 day instance of March; 7
                    recurrenceRule += "on " + recurringData.getDayOfWeek() + " on the " + (dayNumber == 5 ? "last" : dayNumber) + " day instance of " + month;
                } else {
                    recurrenceRule += "on the " + dayNumber + " day of " + month;
                }

                break;
        }

        return recurrenceRule + trailingPart;
    }


    public void fillCalendarExceptionIntervalsJSON(JSONArray intervalsJSON, ProjectCalendarException exception) {

        // get exception data
        String name                 = exception.getName();
        String start                = dateFormat.format(exception.getFromDate());

        // End date extracted by MPXJ is -1 day.
        // Adjusting it here the way the Gantt expects it.
        LocalDate toDate            = exception.getToDate().plus(1, ChronoUnit.DAYS);

        String finish               = dateFormat.format(toDate);
        boolean isWorking           = exception.getWorking();
        RecurringData recurringData = exception.getRecurring();

        JSONObject exceptionJSON;

        // Daily recurrence of non-working time ends up as a simple fixed startDate and endDate interval

        if (recurringData == null || recurringData.getRecurrenceType() == RecurrenceType.DAILY && !isWorking &&
        	(recurringData.getFrequency() == 1 || recurringData.getFrequency() == null)
    	) {
            if (exception.size() > 0) {
            	for (LocalTimeRange range : exception) {
                    exceptionJSON = new JSONObject();

                    exceptionJSON.put(this.typeProperty, "Exception");
                    exceptionJSON.put(this.nameProperty, name);
                    exceptionJSON.put(this.isWorkingProperty, isWorking);
                    exceptionJSON.put(this.recurrentStartDateProperty, "at " + timeFormat.format(range.getStart()));
                    exceptionJSON.put(this.recurrentEndDateProperty, "at " + timeFormat.format(range.getEnd()));
                    exceptionJSON.put(this.startProperty, start);
                    exceptionJSON.put(this.finishProperty, finish);

                    intervalsJSON.put(exceptionJSON);
            	}
            }
            else {
                exceptionJSON = new JSONObject();

                exceptionJSON.put(this.nameProperty, name);
                exceptionJSON.put(this.startProperty, start);
                exceptionJSON.put(this.finishProperty, finish);
                exceptionJSON.put(this.isWorkingProperty, isWorking);
                exceptionJSON.put(this.typeProperty, "Exception");

                intervalsJSON.put(exceptionJSON);
            }
        }
        else {
            // build Later JS recurrence rule
            String recurrenceRule = getRecurrenceRule(recurringData);

            if (exception.size() > 0) {
                for (LocalTimeRange range : exception) {
                    exceptionJSON = new JSONObject();

                    exceptionJSON.put(this.typeProperty, "Exception");
                    exceptionJSON.put(this.nameProperty, name);
                    exceptionJSON.put(this.isWorkingProperty, isWorking);
                    exceptionJSON.put(this.recurrentStartDateProperty, recurrenceRule + " at " + timeFormat.format(range.getStart()));
                    exceptionJSON.put(this.recurrentEndDateProperty, recurrenceRule + " at " + timeFormat.format(range.getEnd()));

                    intervalsJSON.put(exceptionJSON);
                }
            }
            else {
                exceptionJSON = new JSONObject();

                exceptionJSON.put(this.typeProperty, "Exception");
                exceptionJSON.put(this.nameProperty, name);
                exceptionJSON.put(this.isWorkingProperty, isWorking);
                exceptionJSON.put(this.recurrentStartDateProperty, recurrenceRule);

                intervalsJSON.put(exceptionJSON);
            }
        }
    }


    public JSONObject getCalendarJSON(ProjectCalendar calendar) {
        JSONObject calendarJSON = new JSONObject();

        calendarJSON.put(properties.getProperty("calendar.UNIQUE_ID"), calendar.getUniqueID());
        calendarJSON.put(properties.getProperty("calendar.NAME"), calendar.getName());

        ProjectCalendar parentCalendar = calendar.getParent();

        if (parentCalendar != null) {
            calendarJSON.put(properties.getProperty("calendar.PARENT_ID"), parentCalendar.getUniqueID());
        }

        JSONArray intervalsJSON = new JSONArray();

        // collect default weekdays availability
        fillDefaultWeekIntervalsJSON(intervalsJSON, calendar);

        // collect week overrides
        for (ProjectCalendarWeek week : calendar.getWorkWeeks()) {
            fillWeekOverrideIntervalsJSON(intervalsJSON, calendar, week);
        }

        // collect calendar exceptions
        for (ProjectCalendarException exception : calendar.getCalendarExceptions()) {
            fillCalendarExceptionIntervalsJSON(intervalsJSON, exception);
        }

        calendarJSON.put(properties.getProperty("calendar.DAYS"), intervalsJSON);
        calendarJSON.put(properties.getProperty("calendar.UNSPECIFIED_TIME_IS_WORKING"), false);

        // let's build JSON for the children
        JSONArray childrenJSON = new JSONArray();

        for (ProjectCalendar child : calendar.getDerivedCalendars()) {
            childrenJSON.put(getCalendarJSON(child));
        }

        // if the calendar has children
        if (childrenJSON.length() > 0) {
            calendarJSON.put(properties.getProperty("calendar.CHILDREN"), childrenJSON);
            calendarJSON.put(properties.getProperty("calendar.EXPANDED"), true);
        }

        return calendarJSON;
    }

    @Override
    public JSONObject buildJSON(ProjectFile projectFile) {
        JSONObject calendarsJSON = new JSONObject();

        ProjectCalendarContainer calendars = projectFile.getCalendars();

        // now let's loop and start building JSON from root nodes
        JSONArray calendarListJSON = new JSONArray();
        for (ProjectCalendar calendar : calendars) {
            // if it's a root node
            if (!calendar.isDerived()) {
                calendarListJSON.put(getCalendarJSON(calendar));
            }
        }

        calendarsJSON.put(properties.getProperty("calendar.CHILDREN"), calendarListJSON);

        return calendarsJSON;

    }

}
