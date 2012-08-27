package net.sf.importthem.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.internal.resources.Folder;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.wizards.JavaCapabilityConfigurationPage;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.progress.UIJob;

/**
 * http://plugination.org/?page_id=44
 * http://svn.codespot.com/a/eclipselabs.org/plugination/trunk/idetools/org.plugination.featureworkingsets/org.
 * plugination.featureworkingsets/src/org/plugination/featureworkingsets/SyncFeaturesWithWorkingSetsHandler.java
 * 
 * http://eclipse.dzone.com/articles/eclipse-working-sets-explained
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class ImportHandler extends AbstractHandler {

  public ImportHandler() {}

  public Object execute(final ExecutionEvent event) throws ExecutionException {
    final IStructuredSelection selection = (IStructuredSelection)HandlerUtil.getActiveMenuSelection(event);
    UIJob job = new UIJob("Generating working sets") {
      @Override
      public IStatus runInUIThread(IProgressMonitor monitor) {
        monitor.beginTask("Generating working sets", IProgressMonitor.UNKNOWN);
        try {
          Folder[] selectedFolders = (Folder[])selection.toList().toArray(new Folder[0]);
          Map<String, Set<IFile>> workingSet2dotProjects = new HashMap<String, Set<IFile>>() {
            @Override
            public Set<IFile> get(Object key) {
              Set<IFile> value = super.get(key);
              if (value == null) {
                value = new HashSet<IFile>();
                put((String)key, value);
              }
              return value;
            }
          };
          try {
            Util.seekProjects(workingSet2dotProjects, selectedFolders);
            for (IFile file : workingSet2dotProjects.get("impl")) {
              String parentName = file.getParent().getName();
              if (parentName.endsWith("_sb")) {
                String wsName = parentName.replace("_sb", "OSB");
                workingSet2dotProjects.get(wsName).add(file);
              }
              if (parentName.endsWith("_OSB_deploy")) {
                String wsName = parentName.replace("_OSB_deploy", "OSB");
                workingSet2dotProjects.get(wsName).add(file);
              }
            }
            workingSet2dotProjects.remove("impl");
            imprtAll(workingSet2dotProjects, monitor);
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        }
        finally {
          monitor.done();
        }
        return Status.OK_STATUS;
      }
    };
    job.setUser(true);
    job.schedule();
    return null;
  }

  private void imprtAll(Map<String, Set<IFile>> workingSet2dotProjects, IProgressMonitor monitor) throws CoreException {
    if (workingSet2dotProjects == null || workingSet2dotProjects.isEmpty()) {
      return;
    }
    monitor.subTask("Importing");
    SubProgressMonitor sub = new SubProgressMonitor(monitor, workingSet2dotProjects.values().size() + 1);
    for (String workingSetname : workingSet2dotProjects.keySet()) {
      Set<IFile> dotProjects = workingSet2dotProjects.get(workingSetname);
      if (!dotProjects.isEmpty()) {
        IWorkingSet workingSet = createWorkingSet(workingSetname);
        List<IProject> projects = new ArrayList<IProject>();
        for (IFile dotProject : dotProjects) {
          projects.add(imprt(dotProject, sub));
          monitor.worked(1);
          sub.worked(1);
        }
        workingSet.setElements(concat(workingSet.getElements(), projects.toArray(new IAdaptable[projects.size()])));
      }
    }
    refreshPackageExplorer();
    sub.worked(1);
    sub.done();
  }

  public static <T> T[] concat(T[] first, T... second) {
    T[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private IWorkingSet createWorkingSet(String name) {
    IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
    IWorkingSet ws = workingSetManager.getWorkingSet(name);
    if (ws == null) {
      ws = workingSetManager.createWorkingSet(name, new IAdaptable[0]);
      ws.setId("org.eclipse.jdt.ui.JavaWorkingSetPage");
      workingSetManager.addWorkingSet(ws);
    }
    return ws;
  }

  private void refreshPackageExplorer() {
    PackageExplorerPart explorer = getActivePackageExplorer();
    if (explorer != null) {
      explorer.rootModeChanged(PackageExplorerPart.WORKING_SETS_AS_ROOTS);
      IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
      IWorkingSet[] sortedWorkingSets = workingSetManager.getAllWorkingSets();
      explorer.getWorkingSetModel().addWorkingSets(sortedWorkingSets);
      explorer.getWorkingSetModel().configured();
    }
  }

  private IProject imprt(IFile dotProject, IProgressMonitor monitor) throws CoreException {
    final IWorkspace workspace = ResourcesPlugin.getWorkspace();
    IProjectDescription projectDescription = workspace.loadProjectDescription(dotProject.getLocation());
    IProject project = workspace.getRoot().getProject(projectDescription.getName());
    JavaCapabilityConfigurationPage.createProject(project, projectDescription.getLocationURI(), monitor);
    return project;
  }

  private PackageExplorerPart getActivePackageExplorer() {
    final Object[] findView = new Object[1];
    Display.getDefault().syncExec(new Runnable() {
      @Override
      public void run() {
        findView[0] = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(JavaUI.ID_PACKAGES);
        if (findView[0] == null) {
          try {
            findView[0] = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                .showView(JavaUI.ID_PACKAGES);
          }
          catch (PartInitException e) {
            e.printStackTrace();
          }
        }
      }
    });
    return (PackageExplorerPart)findView[0];
  }
}
