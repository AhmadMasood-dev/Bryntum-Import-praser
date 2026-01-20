package bryntum.gantt.projectreader;

import org.mpxj.ProjectFile;

public interface JSONBuilder<T> {

    public T buildJSON(ProjectFile projectFile);

}
