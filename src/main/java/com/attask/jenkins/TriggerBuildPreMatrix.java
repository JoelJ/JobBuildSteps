package com.attask.jenkins;

import hudson.Extension;
import hudson.FilePath;
import hudson.matrix.*;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * User: Joel Johnson
 * Date: 1/7/13
 * Time: 1:23 PM
 */
public class TriggerBuildPreMatrix extends DefaultMatrixExecutionStrategyImpl {
	private String jobName;
	private String parameters;
	private String resultsFileToInject;

	@DataBoundConstructor
	public TriggerBuildPreMatrix(Boolean runSequentially, boolean hasTouchStoneCombinationFilter, String touchStoneCombinationFilter, Result touchStoneResultCondition, MatrixConfigurationSorter sorter,
								 String jobName,
								 String parameters,
								 String resultsFileToInject) {
		super(runSequentially, hasTouchStoneCombinationFilter, touchStoneCombinationFilter, touchStoneResultCondition, sorter);

		this.jobName = jobName;
		this.parameters = parameters;
		this.resultsFileToInject = resultsFileToInject;
	}

	@Override
	public Result run(MatrixBuild build, List<MatrixAggregator> aggregators, BuildListener listener) throws InterruptedException, IOException {
		String uuid = "__MATRIX_"+UUID.randomUUID().toString().replaceAll("-", "");
		TriggerJobBuildStep triggerJobBuildStep = new TriggerJobBuildStep(jobName, uuid, parameters, 0, null);
		boolean perform = triggerJobBuildStep.perform(build, null, listener);
		if(!perform) {
			listener.error("There was an error triggering downstream job before matrix jobs could start.");
			return Result.FAILURE;
		}

		String buildNumber = null;
		List<EnvAction> actions = build.getActions(EnvAction.class);
		for (EnvAction action : actions) {
			if(uuid.equals(action.getName())) {
				buildNumber = action.getValue();
			}
		}
		if(buildNumber == null) {
			throw new NullPointerException("buildNumber was null. This is very strange.");
		}

		listener.getLogger().println("Successfully triggered: " + jobName + " #" +buildNumber);

		WaitForBuildStep waitForBuildStep = new WaitForBuildStep(jobName, buildNumber, 0, 0, this.resultsFileToInject, true, true, 500, null, null, 0, resultsFileToInject);
		perform = waitForBuildStep.perform(build, null, listener);
		if(!perform) {
			listener.error("An error occurred while waiting for downstream job to finish before matrix jobs could start.");
			return Result.FAILURE;
		}

		return super.run(build, aggregators, listener);
	}

	public String getJobName() {
		return jobName;
	}

	public String getParameters() {
		return parameters;
	}

	public String getResultsFileToInject() {
		return resultsFileToInject;
	}

	@Extension
	public static class DescriptorImpl extends MatrixExecutionStrategyDescriptor {
		@Override
		public String getDisplayName() {
			return "Trigger Pre-Job Execution Strategy";
		}
	}
}
