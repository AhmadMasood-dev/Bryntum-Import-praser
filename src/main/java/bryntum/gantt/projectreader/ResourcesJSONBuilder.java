package bryntum.gantt.projectreader;

import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONObject;

import org.mpxj.AccrueType;
import org.mpxj.ProjectFile;
import org.mpxj.Resource;
import org.mpxj.ResourceType;

/**
 * Class implementing extraction a MS Project file resource data
 * into a JSONArray.
*/
public class ResourcesJSONBuilder implements JSONBuilder<JSONArray> {

    Properties properties;

    // field names
    String idField;
    String nameField;
    String calendarIdField;
    String typeField;
    String accrueAtField;
    String materialLabelField;
    String maxUnitsField;

    public ResourcesJSONBuilder(Properties properties) {
        this.properties = properties;

        // load field names from properties
        loadProperties(properties);
    }

    public void loadProperties(Properties properties) {
        idField = properties.getProperty("resource.UNIQUE_ID");
        nameField = properties.getProperty("resource.NAME");
        calendarIdField = properties.getProperty("resource.BASE_CALENDAR");
        typeField = properties.getProperty("resource.TYPE");
        accrueAtField = properties.getProperty("resource.ACCRUE_AT");
        materialLabelField = properties.getProperty("resource.MATERIAL_LABEL");
        maxUnitsField = properties.getProperty("resource.MAX_UNITS");
    }

    private String getResourceTypeValue(ResourceType type) {
        String result = properties.getProperty("resourceType." + type);

        if (result == null) {
        	result = type.toString().toLowerCase();
        }

        return result;
    }

    private String getAccrueAtValue(AccrueType type) {
        String result = properties.getProperty("accrueAtType." + type);

        if (result == null && type != null) {
        	switch (type) {
	    		case PRORATED: result = "prorated"; break;
	    		case START: result = "start"; break;
	    		case END: result = "end"; break;
        	}
        }

        return result;
    }

    /**
     * Extracts the provided resource data into a JSONObject.
     *
     * @param resource Resource to extract
     * @return JSON object keeping the extracted resource data
     */
    public JSONObject getResourceJSON(Resource resource) {
        JSONObject result = new JSONObject();

        result.put(idField, resource.getUniqueID());
        result.put(nameField, (resource.getName() != null ? resource.getName() : "New resource"));
        result.put(calendarIdField, resource.getBaseCalendar());
        result.put(typeField, getResourceTypeValue(resource.getType()));
        result.put(materialLabelField, resource.getMaterialLabel());
        result.put(maxUnitsField, resource.getMaxUnits());

        AccrueType accrueAt = resource.getAccrueAt();

        if (accrueAt != null) {
        	result.put(accrueAtField, getAccrueAtValue(accrueAt));
        }

//        for (Integer i = 0; i < 5; i++) {
//        	  CostRateTable costRateTable = resource.getCostRateTable(i);
//
//            for (CostRateTableEntry entry : costRateTable)
//            {
//                entry.getStartDate();
//                entry.getEndDate();
//                entry.getStandardRate();
//                entry.getCostPerUse();
////                entry.getR
//            }
//        }

        return result;
    }

    @Override
	public JSONArray buildJSON(ProjectFile projectFile) {
        JSONArray result = new JSONArray();

        // extract all the resources
        for (Resource resource : projectFile.getResources()) {
            result.put(getResourceJSON(resource));
        }

        return result;
    }
}
