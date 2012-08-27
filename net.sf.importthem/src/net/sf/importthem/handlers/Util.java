package net.sf.importthem.handlers;

import java.util.Map;
import java.util.Set;

import org.eclipse.core.internal.resources.Folder;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

public class Util {

  static void seekProjects(Map<String, Set<IFile>> workingSet2dotProjects, Folder... folders) throws Exception {
    for (Folder folder : folders) {
      addIfProject(folder, workingSet2dotProjects);
      for (IResource res : folder.members()) {
        if (res instanceof Folder) {
          seekProjects(workingSet2dotProjects, (Folder)res);
        }
      }
    }
  }

  static void addIfProject(Folder folder, Map<String, Set<IFile>> workingSet2dotProjects) {
    IFile dotProject = folder.getFile(".project");
    if (dotProject.exists()) {
      workingSet2dotProjects.get(folder.getParent().getName()).add(dotProject);
    }
  }

}
