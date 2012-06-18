package com.attask.jenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.Builder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;
import java.util.*;

/**
 * User: joeljohnson
 * Date: 3/6/12
 * Time: 9:07 PM
 */
@ExportedBean
public class TriggerJobBuildStep extends Builder {
	private final String jobName;
	private final String envVarName;
	private final String parameters;
	private final int waitLimitMinutes;


	@DataBoundConstructor
	public TriggerJobBuildStep(String jobName, String envVarName, String parameters, int waitLimitMinutes) {
		this.jobName = jobName;
		this.envVarName = envVarName;
		this.parameters = parameters;
		this.waitLimitMinutes = waitLimitMinutes <= 0 ? 15 : waitLimitMinutes;
	}

	@Exported
	public String getJobName() {
		return jobName;
	}

	@Exported
	public String getEnvVarName() {
		return envVarName;
	}

	@Exported
	public String getParameters() {
		return parameters;
	}

	@Exported
	public int getWaitLimitMinutes() {
		return waitLimitMinutes;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
		EnvVars envVars = build.getEnvironment(listener);
		final String variableName = envVars.expand(this.envVarName);
		String jobName = envVars.expand(this.jobName);
		TopLevelItem topLevelItem = Hudson.getInstance().getItem(jobName);
		if(topLevelItem == null || !(topLevelItem instanceof AbstractProject)) {
			listener.getLogger().println(jobName + " is not a Project {" + topLevelItem + "}");
			return false;
		}

		final AbstractProject job = (AbstractProject)topLevelItem;
		final int nextBuildNumber = triggerBuild(build, listener, job);
		if(nextBuildNumber < 0) {
			return false;
		}

		if(variableName != null && !variableName.isEmpty()) {
			build.addAction(new EnvironmentContributingAction() {
				public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
					listener.getLogger().println("setting environment variable '" + variableName + "' to '" + nextBuildNumber + "'");
					env.put(variableName, String.valueOf(nextBuildNumber));
				}

				public String getIconFileName() { return null; }
				public String getDisplayName() { return null; }
				public String getUrlName() { return null; }
			});
		}
		return true;
	}

	private int triggerBuild(Run upstreamRun, BuildListener listener, final AbstractProject jobToStart) throws IOException {
		int nextBuildNumber = jobToStart.getNextBuildNumber();
		boolean addedToQueue = jobToStart.scheduleBuild(0, new Cause.UpstreamCause(upstreamRun), getParameterActions(jobToStart, parameters));
		if(!addedToQueue) {
			listener.error("Didn't start job! Apparently the same job is queued.");
			return -1;
		}

		listener.getLogger().print("Queued job ");
		listener.hyperlink(WaitForBuildStep.getRootUrl() + jobToStart.getUrl(), jobToStart.getFullDisplayName());
		listener.getLogger().println();

		for(int attempt = 0; attempt < waitLimitMinutes * 6; attempt++) {
			for(int jobNumber = nextBuildNumber; jobNumber < jobToStart.getNextBuildNumber(); jobNumber++) {
				Run run = jobToStart.getBuildByNumber(jobNumber);

				@SuppressWarnings("unchecked")
				Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause)run.getCause(Cause.UpstreamCause.class);
				if(upstreamCause == null || !upstreamCause.pointsTo(upstreamRun)) {
					continue;
				}

				listener.getLogger().print("Started job ");
				listener.hyperlink(WaitForBuildStep.getRootUrl() + jobToStart.getUrl(), jobToStart.getFullDisplayName());
				listener.getLogger().print(" ");
				listener.hyperlink(WaitForBuildStep.getRootUrl() + run.getUrl(), run.getDisplayName());
				listener.getLogger().println();

				return run.getNumber();
			}
			try {
				listener.getLogger().println("Job hasn't started. Waiting for 10 seconds.");
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				listener.fatalError(e.getMessage());
			}
		}

		listener.error("Couldn't find started job!");
		return -2;
	}

	private Action getParameterActions(AbstractProject project, String parameters) {
		List<ParameterValue> result = new ArrayList<ParameterValue>();
		Map<String, String> propertiesMap = getPropertiesMap(parameters);

		@SuppressWarnings("unchecked")
		ParametersDefinitionProperty projectProperties = (ParametersDefinitionProperty)project.getProperty(ParametersDefinitionProperty.class);
		List<ParameterDefinition> parameterDefinitions = projectProperties.getParameterDefinitions();
		for (ParameterDefinition parameterDefinition : parameterDefinitions) {
			ParameterValue defaultParameterValue = parameterDefinition.getDefaultParameterValue();
			String propertyName = defaultParameterValue.getName();
			if(propertiesMap.containsKey(propertyName)) {
				result.add(new StringParameterValue(propertyName, propertiesMap.get(propertyName)));
			} else {
				result.add(defaultParameterValue);
			}
		}

		return new ParametersAction(result);
	}

	private Map<String, String> getPropertiesMap(String parameters) {
		String[] split = parameters.split("\n");
		Map<String, String> properties = new HashMap<String, String>();
		for(int i = 0; i < split.length; i++) {
			String lineWithoutComments = split[i].split("#", 2)[0];
			String[] property = lineWithoutComments.split("=", 2);
			if(property.length == 2) {
				properties.put(property[0].trim(), property[1].trim());
			}
		}
		return properties;
	}

	@Extension
	public static final class DescriptorImpl extends com.attask.jenkins.BuildStepDescriptor {
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		public String getDisplayName() {
			return "Trigger a build";
		}
	}
}
