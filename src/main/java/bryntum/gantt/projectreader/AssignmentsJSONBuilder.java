package bryntum.gantt.projectreader;

import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONObject;

import org.mpxj.ProjectFile;
import org.mpxj.Resource;
import org.mpxj.ResourceAssignment;
import org.mpxj.ResourceType;

/**
 * Class implementing extraction a MS Project file assignments data
 * into a JSONArray.
 */
public class AssignmentsJSONBuilder implements JSONBuilder<JSONArray> {

    Properties properties;

    // field names
    String idField;
    String resourceIdField;
    String taskIdField;
    String unitsField;
    String rateTableField;
	String quantityField;
	String costField;

    public AssignmentsJSONBuilder(Properties properties) {
        this.properties = properties;

        // load field names from properties
        loadProperties(properties);
    }

    public void loadProperties(Properties properties) {
        idField = properties.getProperty("assignment.UNIQUE_ID");
        resourceIdField = properties.getProperty("assignment.RESOURCE_UNIQUE_ID");
        taskIdField = properties.getProperty("assignment.TASK_UNIQUE_ID");
        unitsField = properties.getProperty("assignment.UNITS");
        rateTableField = properties.getProperty("assignment.RATE_TABLE");
        quantityField = properties.getProperty("assignment.QUANTITY");
		costField = properties.getProperty("assignment.COST");
    }

    /**
     * Extracts the provided assignment data into JSON object.
     *
     * @param assignment Assignment to extract
     * @return JSON object keeping the extracted assignment data
     */
    public JSONObject getAssignmentJSON(ResourceAssignment assignment) {
        JSONObject result = new JSONObject();

        result.put(idField, assignment.getUniqueID());
        result.put(resourceIdField, assignment.getResourceUniqueID());
        result.put(taskIdField, assignment.getTaskUniqueID());
        result.put(rateTableField, assignment.getCostRateTableIndex());

        Resource resource = assignment.getResource();
        if (resource == null) {
            return result;
        }

        ResourceType resourceType = resource.getType();


        switch (resourceType) {
	    	case MATERIAL:
	            result.put(quantityField, assignment.getUnits());
	            break;
	    	case COST:
	            result.put(costField, assignment.getCost());
	            break;
	    	case WORK:
	            result.put(unitsField, assignment.getUnits());
	            break;
        }

        return result;
    }

    @Override
	public JSONArray buildJSON(ProjectFile projectFile) {
        JSONArray result = new JSONArray();

        // extract all the resources
        for (Resource resource : projectFile.getResources()) {
            // corresponding resource' assignment
            for (ResourceAssignment assignment : resource.getTaskAssignments()) {
                result.put(getAssignmentJSON(assignment));
            }
        }

        return result;
    }
}
