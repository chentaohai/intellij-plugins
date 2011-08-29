package com.intellij.lang.javascript.flex.projectStructure;

import com.intellij.ProjectTopics;
import com.intellij.lang.javascript.flex.FlexModuleType;
import com.intellij.lang.javascript.flex.projectStructure.options.BuildConfigurationEntry;
import com.intellij.lang.javascript.flex.projectStructure.options.CompilerOptions;
import com.intellij.lang.javascript.flex.projectStructure.options.DependencyEntry;
import com.intellij.lang.javascript.flex.projectStructure.options.FlexIdeBuildConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;

import java.util.*;

@State(name = "FlexIdeBuildConfigurationManager", storages = {@Storage(file = "$MODULE_FILE$")})
public class FlexIdeBuildConfigurationManager implements PersistentStateComponent<FlexIdeBuildConfigurationManager.State> {

  private static final Logger LOG = Logger.getInstance(FlexIdeBuildConfigurationManager.class.getName());

  private final Module myModule;
  private FlexIdeBuildConfiguration[] myConfigurations = new FlexIdeBuildConfiguration[]{new FlexIdeBuildConfiguration()};
  private CompilerOptions myModuleLevelCompilerOptions = new CompilerOptions();

  public FlexIdeBuildConfigurationManager(final Module module) {
    myModule = module;

    myModule.getProject().getMessageBus().connect(myModule).subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void beforeModuleRemoved(Project project, Module module) {
        if (module != myModule) {
          removeDependenciesOn(module);
        }
      }
    });
  }

  private void removeDependenciesOn(Module module) {
    for (FlexIdeBuildConfiguration configuration : myConfigurations) {
      // TODO remove 'optimize for' links
      for (Iterator<DependencyEntry> i = configuration.DEPENDENCIES.getEntries().iterator(); i.hasNext(); ) {
        DependencyEntry entry = i.next();
        if (entry instanceof BuildConfigurationEntry && ((BuildConfigurationEntry)entry).getModule() == module) {
          i.remove();
        }
      }
    }
  }

  public static FlexIdeBuildConfigurationManager getInstance(final Module module) {
    assert ModuleType.get(module) == FlexModuleType.getInstance();
    return (FlexIdeBuildConfigurationManager)module.getPicoContainer()
      .getComponentInstance(FlexIdeBuildConfigurationManager.class.getName());
  }

  public FlexIdeBuildConfiguration[] getBuildConfigurations() {
    return Arrays.copyOf(myConfigurations, myConfigurations.length);
  }

  public CompilerOptions getModuleLevelCompilerOptions() {
    return myModuleLevelCompilerOptions;
  }

  void setBuildConfigurations(final FlexIdeBuildConfiguration[] configurations) {
    assert configurations.length > 0;
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myConfigurations = getValidatedConfigurations(Arrays.asList(configurations));
  }

  public State getState() {
    final State state = new State();
    Collections.addAll(state.myConfigurations, myConfigurations);
    state.myModuleLevelCompilerOptions = myModuleLevelCompilerOptions.clone();
    return state;
  }

  public void loadState(final State state) {
    if (state.myConfigurations.isEmpty()) {
      myConfigurations = new FlexIdeBuildConfiguration[]{new FlexIdeBuildConfiguration()};
    }
    else {
      myConfigurations = getValidatedConfigurations(state.myConfigurations);
      for (FlexIdeBuildConfiguration configuration : myConfigurations) {
        configuration.initialize(myModule.getProject());
      }
    }
    myModuleLevelCompilerOptions = state.myModuleLevelCompilerOptions.clone();
  }

  private static FlexIdeBuildConfiguration[] getValidatedConfigurations(Collection<FlexIdeBuildConfiguration> configurations) {
    LinkedHashMap<String, FlexIdeBuildConfiguration> name2configuration =
      new LinkedHashMap<String, FlexIdeBuildConfiguration>(configurations.size());
    for (FlexIdeBuildConfiguration configuration : configurations) {
      if (StringUtil.isEmpty(configuration.NAME)) {
        LOG.error("Empty build configuration name");
      }
      if (name2configuration.put(configuration.NAME, configuration) != null) {
        LOG.error("Duplicate build configuration name: " + configuration.NAME);
      }
    }

    return name2configuration.values().toArray(new FlexIdeBuildConfiguration[name2configuration.size()]);
  }

  public static class State {
    @AbstractCollection(elementTypes = FlexIdeBuildConfiguration.class)
    public Collection<FlexIdeBuildConfiguration> myConfigurations = new ArrayList<FlexIdeBuildConfiguration>();

    public CompilerOptions myModuleLevelCompilerOptions = new CompilerOptions();
  }
}
