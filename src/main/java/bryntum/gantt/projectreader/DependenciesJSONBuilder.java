package bryntum.gantt.projectreader;

import java.util.List;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mpxj.Duration;
import org.mpxj.ProjectFile;
import org.mpxj.Relation;
import org.mpxj.Task;
import org.mpxj.TimeUnit;

/**
 * Class implementing extraction a MS Project file task relations data
 * into a JSONArray.
*/
public class DependenciesJSONBuilder implements JSONBuilder<JSONArray> {

    Properties properties;

    // field names
    String idField;
    String successorIdField;
    String predecessorIdField;
    String lagField;
    String lagUnitField;
    String typeField;

    String typeOfDependencyType;

    public DependenciesJSONBuilder(Properties properties) {
        this.properties = properties;

        // load related properties
        loadProperties(properties);
    }

    public void loadProperties(Properties properties) {
        idField = properties.getProperty("dependency.UNIQUE_ID");
        successorIdField = properties.getProperty("dependency.SUCCESSOR_UNIQUE_ID");
        predecessorIdField = properties.getProperty("dependency.PREDECESSOR_UNIQUE_ID");
        lagField = properties.getProperty("dependency.LAG");
        lagUnitField = properties.getProperty("dependency.LAG_UNIT");
        typeField = properties.getProperty("dependency.TYPE");

        typeOfDependencyType = properties.getProperty("force.typeof.dependencyType");
    }

    String getUnitByTimeUnit(TimeUnit timeUnit) {
        String unitName = null;

        if (timeUnit != null) {
            unitName = timeUnit.getName();

            return properties.getProperty("timeUnit." + unitName, unitName);
        }

        return unitName;
    }

    private void setDependencyType(JSONObject json, Relation dependency) {
        String value = properties.getProperty("dependencyType." + dependency.getType());

        json.put(typeField, typeOfDependencyType != null && typeOfDependencyType.equals("int") ? Integer.parseInt(value) : value);
    }

    /**
     * Extracts the provided dependency data into JSON object.
     *
     * @param dependency Dependency to extract
     * @return JSON object keeping the extracted dependency data
     */
    public JSONObject getDependencyJSON(Relation dependency, int dependencyId) {
        JSONObject result = new JSONObject();

        result.put(idField, dependencyId);
        result.put(successorIdField, dependency.getSuccessorTask().getUniqueID());
        result.put(predecessorIdField, dependency.getPredecessorTask().getUniqueID());

        double lag = dependency.getLag().getDuration();
        TimeUnit lagUnit = dependency.getLag().getUnits();

        // if lag is provided as %-value - replace it with a fixed value
        if (lagUnit == TimeUnit.PERCENT && Boolean.parseBoolean(properties.getProperty("fix.lag.percentage"))) {
            Duration predecessorDuration = dependency.getPredecessorTask().getDuration();

            if (predecessorDuration != null) {
                lagUnit = predecessorDuration.getUnits();
                lag     = predecessorDuration.getDuration() * lag * .01;
            }
        }

        result.put(lagField, lag);
        result.put(lagUnitField, getUnitByTimeUnit(lagUnit));
        setDependencyType(result, dependency);

        return result;
    }

    @Override
	public JSONArray buildJSON(ProjectFile projectFile) {
        JSONArray result = new JSONArray();

        int dependencyId = 0;

        // extract all the dependencies
        for (Task task : projectFile.getTasks()) {

            List<Relation> predecessors = task.getPredecessors();

            if (predecessors != null && predecessors.isEmpty() == false) {
                for (Relation relation : predecessors) {
                    result.put(getDependencyJSON(relation, ++dependencyId));
                }
            }
        }

        return result;
    }

}
