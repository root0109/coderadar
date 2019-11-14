package io.reflectoring.coderadar.analyzer.service;

import io.reflectoring.coderadar.analyzer.MisconfigurationException;
import io.reflectoring.coderadar.analyzer.domain.AnalyzerConfiguration;
import io.reflectoring.coderadar.analyzer.domain.MetricValue;
import io.reflectoring.coderadar.analyzer.port.driven.StartAnalyzingPort;
import io.reflectoring.coderadar.analyzer.port.driven.StopAnalyzingPort;
import io.reflectoring.coderadar.analyzer.port.driver.StartAnalyzingCommand;
import io.reflectoring.coderadar.analyzer.port.driver.StartAnalyzingUseCase;
import io.reflectoring.coderadar.plugin.api.SourceCodeFileAnalyzerPlugin;
import io.reflectoring.coderadar.projectadministration.domain.Commit;
import io.reflectoring.coderadar.projectadministration.domain.FilePattern;
import io.reflectoring.coderadar.projectadministration.domain.Project;
import io.reflectoring.coderadar.projectadministration.port.driven.analyzer.SaveCommitPort;
import io.reflectoring.coderadar.projectadministration.port.driven.analyzer.SaveMetricPort;
import io.reflectoring.coderadar.projectadministration.port.driven.analyzerconfig.GetAnalyzerConfigurationsFromProjectPort;
import io.reflectoring.coderadar.projectadministration.port.driven.filepattern.ListFilePatternsOfProjectPort;
import io.reflectoring.coderadar.projectadministration.port.driven.project.GetProjectPort;
import io.reflectoring.coderadar.projectadministration.service.ProcessProjectService;
import io.reflectoring.coderadar.projectadministration.service.filepattern.FilePatternMatcher;
import io.reflectoring.coderadar.query.port.driven.GetCommitsInProjectPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;

@Service
public class StartAnalyzingService implements StartAnalyzingUseCase {
  private final GetProjectPort getProjectPort;
  private final AnalyzeCommitService analyzeCommitService;
  private final AnalyzerPluginService analyzerPluginService;
  private final GetAnalyzerConfigurationsFromProjectPort getAnalyzerConfigurationsFromProjectPort;
  private final ListFilePatternsOfProjectPort listFilePatternsOfProjectPort;
  private final GetCommitsInProjectPort getCommitsInProjectPort;
  private final SaveMetricPort saveMetricPort;
  private final SaveCommitPort saveCommitPort;
  private final ProcessProjectService processProjectService;
  private final StartAnalyzingPort startAnalyzingPort;
  private final StopAnalyzingPort stopAnalyzingPort;
  private final AsyncListenableTaskExecutor taskExecutor;

  private final Logger logger = LoggerFactory.getLogger(StartAnalyzingService.class);

  public StartAnalyzingService(
      GetProjectPort getProjectPort,
      AnalyzeCommitService analyzeCommitService,
      AnalyzerPluginService analyzerPluginService,
      GetAnalyzerConfigurationsFromProjectPort getAnalyzerConfigurationsFromProjectPort,
      ListFilePatternsOfProjectPort listFilePatternsOfProjectPort,
      GetCommitsInProjectPort getCommitsInProjectPort,
      SaveMetricPort saveMetricPort,
      SaveCommitPort saveCommitPort,
      ProcessProjectService processProjectService,
      StartAnalyzingPort startAnalyzingPort,
      StopAnalyzingPort stopAnalyzingPort,
      AsyncListenableTaskExecutor taskExecutor) {
    this.getProjectPort = getProjectPort;
    this.analyzeCommitService = analyzeCommitService;
    this.analyzerPluginService = analyzerPluginService;
    this.getAnalyzerConfigurationsFromProjectPort = getAnalyzerConfigurationsFromProjectPort;
    this.listFilePatternsOfProjectPort = listFilePatternsOfProjectPort;
    this.getCommitsInProjectPort = getCommitsInProjectPort;
    this.saveMetricPort = saveMetricPort;
    this.saveCommitPort = saveCommitPort;
    this.processProjectService = processProjectService;
    this.startAnalyzingPort = startAnalyzingPort;
    this.stopAnalyzingPort = stopAnalyzingPort;
    this.taskExecutor = taskExecutor;
  }

  @Override
  public void start(StartAnalyzingCommand command, Long projectId) {
    List<FilePattern> filePatterns = listFilePatternsOfProjectPort.listFilePatterns(projectId);
    List<SourceCodeFileAnalyzerPlugin> sourceCodeFileAnalyzerPlugins =
        getAnalyzersForProject(projectId);

    if (filePatterns.isEmpty()) {
      throw new MisconfigurationException("Cannot analyze project without file patterns");
    } else if (sourceCodeFileAnalyzerPlugins.isEmpty()) {
      throw new MisconfigurationException("Cannot analyze project without analyzers");
    }
    processProjectService.executeTask(
        () -> {
          Project project = getProjectPort.get(projectId);
          List<Commit> commitsToBeAnalyzed =
              getCommitsInProjectPort.getSortedByTimestampAsc(projectId);
          List<MetricValue> metricValues = new ArrayList<>();

          FilePatternMatcher filePatternMatcher = new FilePatternMatcher(filePatterns);

          startAnalyzingPort.start(command, projectId);

          Long counter = 0L;
          List<Long> commitIds = new ArrayList<>();
          commitsToBeAnalyzed.forEach(commit -> commitIds.add(commit.getId()));
          ListenableFuture<?> saveTask = null;
          for (Commit commit : commitsToBeAnalyzed) {
            if (!commit.isAnalyzed()) {
              metricValues.addAll(
                  analyzeCommitService.analyzeCommit(
                      commit, project, sourceCodeFileAnalyzerPlugins, filePatternMatcher));
              ++counter;
              logger.info(
                  "Analyzed commit: {} {}, total analyzed: {}",
                  commit.getComment(),
                  commit.getName(),
                  counter);
            }
            if (metricValues.size() > 200) {
              List<MetricValue> metricValuesCopy = new ArrayList<>(metricValues);
              if (saveTask != null) {
                waitForTask(saveTask);
              }
              saveTask =
                  taskExecutor.submitListenable(
                      () -> saveMetricPort.saveMetricValues(metricValuesCopy, projectId));
              metricValues.clear();
            }
          }
          saveMetricPort.saveMetricValues(metricValues, projectId);
          if (!metricValues.isEmpty()) {
            saveCommitPort.setCommitsWithIDsAsAnalyzed(commitIds);
          }
          stopAnalyzingPort.stop(projectId);
          logger.info("Saved analysis results for project {}", project.getName());
        },
        projectId);
  }

  private void waitForTask(ListenableFuture<?> saveTask) {
    try {
      saveTask.get();
    } catch (InterruptedException | ExecutionException ignored) {
    }
  }

  private List<SourceCodeFileAnalyzerPlugin> getAnalyzersForProject(Long projectId) {
    List<SourceCodeFileAnalyzerPlugin> analyzers = new ArrayList<>();
    Collection<AnalyzerConfiguration> configs =
        getAnalyzerConfigurationsFromProjectPort.get(projectId);
    for (AnalyzerConfiguration config : configs) {
      if (config.getEnabled()) {
        analyzers.add(analyzerPluginService.createAnalyzer(config.getAnalyzerName()));
      }
    }
    return analyzers;
  }
}
