package com.attask.jenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

/**
 * User: Joel Johnson
 * Date: 3/6/13
 * Time: 7:13 PM
 */
public class TriggerAndWaitBuildStep extends Builder {
	private final String jobNames;
	private final String parameters;

	@DataBoundConstructor
	public TriggerAndWaitBuildStep(String jobNames, String parameters) {
		this.jobNames = jobNames;
		this.parameters = parameters;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		EnvVars vars = build.getEnvironment(listener);

		LinkedList<AbstractProject> buildsToTrigger = findBuildsToTrigger();
		LinkedList<QueueTaskFuture<AbstractBuild>> scheduledBuilds = scheduleBuilds(build, listener, vars, buildsToTrigger);
		Result result = waitForBuildsToStart(build, scheduledBuilds, listener);
		result = waitForBuildsToFinish(scheduledBuilds, listener, result);

		build.setResult(result);
		return result.isBetterThan(Result.FAILURE);
	}

	private Result waitForBuildsToFinish(LinkedList<QueueTaskFuture<AbstractBuild>> scheduledBuilds, BuildListener listener, Result finalResult) throws IOException {
		PrintStream logger = listener.getLogger();

		for (QueueTaskFuture<AbstractBuild> scheduledBuild : scheduledBuilds) {
			try {
				AbstractBuild finishedBuild = scheduledBuild.get();
				Result result = finishedBuild.getResult();

				logger.print("Build finished ");
				listener.hyperlink(WaitForBuildStep.getRootUrl() + finishedBuild.getUrl(), finishedBuild.getFullDisplayName());
				logger.print(" with result: " + result + ".");
				logger.println(" completed in: " + finishedBuild.getDurationString() + ". ");

				finalResult = finalResult.combine(result);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (ExecutionException e) {
				listener.error("Error while waiting for build "+scheduledBuild.toString()+".");
				listener.error(e.getMessage());
				listener.error(ExceptionUtils.getFullStackTrace(e));
				finalResult = finalResult.combine(Result.FAILURE);
			}
		}

		return finalResult;
	}

	private Result waitForBuildsToStart(AbstractBuild<?, ?> build, LinkedList<QueueTaskFuture<AbstractBuild>> scheduledBuilds, BuildListener listener) throws InterruptedException, IOException {
		Result result = Result.SUCCESS;
		PrintStream logger = listener.getLogger();
		for (QueueTaskFuture<AbstractBuild> scheduledBuild : scheduledBuilds) {
			try {
				AbstractBuild executingBuild = scheduledBuild.waitForStart();

				logger.print("Started build ");
				listener.hyperlink(WaitForBuildStep.getRootUrl() + executingBuild.getUrl(), executingBuild.getFullDisplayName());
				logger.println();

				DownstreamBuildsAction action = build.getAction(DownstreamBuildsAction.class);
				if(action == null) {
					action = new DownstreamBuildsAction();
					build.addAction(action);
				}
				action.addDownstreamBuild(executingBuild);
			} catch (ExecutionException e) {
				listener.error("Error while waiting for build.");
				listener.error(e.getMessage());
				listener.error(ExceptionUtils.getFullStackTrace(e));
				result = result.combine(Result.FAILURE);
			}
		}
		return result;
	}

	private LinkedList<QueueTaskFuture<AbstractBuild>> scheduleBuilds(Run build, BuildListener listener, EnvVars vars, LinkedList<AbstractProject> buildsToTrigger) throws InterruptedException, IOException {
		PrintStream logger = listener.getLogger();
		LinkedList<QueueTaskFuture<AbstractBuild>> scheduledBuilds = new LinkedList<QueueTaskFuture<AbstractBuild>>();
		int iterations = 0;
		while(!buildsToTrigger.isEmpty()) {
			AbstractProject projectToSchedule = buildsToTrigger.removeFirst();
			String expandedParameters = vars.expand(this.parameters);
			Action parameterActions = TriggerJobBuildStep.getParameterActions(projectToSchedule, expandedParameters, listener, false);
			QueueTaskFuture<AbstractBuild> queueTaskFuture = projectToSchedule.scheduleBuild2(0, new Cause.UpstreamCause(build), parameterActions);
			if(queueTaskFuture != null) {
				scheduledBuilds.add(queueTaskFuture);
				logger.print("Queued project ");
				listener.hyperlink(WaitForBuildStep.getRootUrl() + projectToSchedule.getUrl(), projectToSchedule.getFullDisplayName());
				logger.println();
			} else {
				//Add it back in, it wasn't scheduled.
				buildsToTrigger.add(projectToSchedule);
			}

			iterations++;
			if(iterations >= buildsToTrigger.size()) {
				//Take a rest before retrying.
				iterations = 0;
				Thread.sleep(5000);
			}
		}
		return scheduledBuilds;
	}

	public LinkedList<AbstractProject> findBuildsToTrigger() {
		LinkedList<AbstractProject> projects = new LinkedList<AbstractProject>();
		Scanner scanner = new Scanner(getJobNames());
		while(scanner.hasNextLine()) {
			String name = scanner.nextLine();
			TopLevelItem item = Jenkins.getInstance().getItem(name);
			if(item instanceof AbstractProject) {
				projects.add((AbstractProject) item);
			}
		}
		return projects;
	}

	@Exported
	public String getJobNames() {
		return jobNames;
	}

	@Exported
	public String getParameters() {
		return parameters;
	}

	@Extension
	public static final class DescriptorImpl extends com.attask.jenkins.BuildStepDescriptor {
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		public String getDisplayName() {
			return "Trigger builds and wait for completion";
		}
	}
}
