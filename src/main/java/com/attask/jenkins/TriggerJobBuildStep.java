package com.attask.jenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Builder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ExecutionException;

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
	private final String runOnCondition;

	@DataBoundConstructor
	public TriggerJobBuildStep(String jobName, String envVarName, String parameters, int waitLimitMinutes, String runOnCondition) {
		this.jobName = jobName;
		this.envVarName = envVarName;
		this.parameters = parameters;
		this.waitLimitMinutes = waitLimitMinutes <= 0 ? 15 : waitLimitMinutes;
		this.runOnCondition = runOnCondition;
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

	@Exported
	public String getRunOnCondition() {
		return runOnCondition;
	}

	public boolean checkTriggerOnly() {
		return envVarName == null || envVarName.trim().isEmpty();
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
		String runOnConditionExpanded = build.getEnvironment(listener).expand(this.runOnCondition);
        if (!shouldRun(runOnConditionExpanded)) {
            listener.getLogger().println("Not triggering job '" + jobName + "' since 'Only run if this value is true' is '" + runOnConditionExpanded + "'");
            return true;
        }

        EnvVars envVars = build.getEnvironment(listener);
		final String variableName = envVars.expand(this.envVarName);
		String jobName = envVars.expand(this.jobName);
		TopLevelItem topLevelItem = Hudson.getInstance().getItem(jobName);
		if(topLevelItem == null || !(topLevelItem instanceof AbstractProject)) {
			listener.getLogger().println(jobName + " is not a Project {" + topLevelItem + "}");
			return false;
		}

		final AbstractProject job = (AbstractProject)topLevelItem;
		boolean triggerOnly = checkTriggerOnly();
		final Run nextBuild = triggerBuild(build, listener, job, build.getEnvironment(listener), triggerOnly);
		if(nextBuild == null) {
			if(triggerOnly) {
				listener.error("Couldn't start the build.");
				return false;
			} else {
				return true;
			}
		}

		DownstreamBuildsAction action = build.getAction(DownstreamBuildsAction.class);
		if(action == null) {
			action = new DownstreamBuildsAction();
			build.addAction(action);
		}
		action.addDownstreamBuild(nextBuild);

		if(variableName != null && !variableName.isEmpty()) {
			listener.getLogger().println("setting environment variable '" + variableName + "' to '" + nextBuild.getNumber() + "'");
			build.addAction(new EnvAction(variableName, String.valueOf(nextBuild.getNumber())));
		}
		return true;
	}

    public static boolean shouldRun(String runOnConditionExpanded) {
        if(runOnConditionExpanded != null && !runOnConditionExpanded.isEmpty() && !isAffirmativeWord(runOnConditionExpanded)) {
            return false;
        }
        return true;
    }

    /**
	 * Checks if the given word is either "true" or "yes".
	 * The check is case-insensitive, and any white space is trimmed from the start and end of the given string.
	 * @return False if the given word is null or is not "true" or "yes".
	 */
	private static boolean isAffirmativeWord(String word) {
        if (word == null) {
            return false;
        }
        boolean inverse = false;
        if (word.startsWith("!")) {
            inverse = true;
            word = word.substring(1);
        }
        boolean result = Boolean.parseBoolean(word);

		//XOR to invert it if there's a '!'
        return result ^ inverse;
    }

	private Run triggerBuild(Run upstreamRun, BuildListener listener, final AbstractProject jobToStart, EnvVars vars, boolean triggerOnly) throws IOException {
		Action parameterActions = getParameterActions(jobToStart, vars.expand(parameters), listener);
		QueueTaskFuture<Build> queueTaskFuture = null;

		int retries = 0;
		while(retries < 5) {
			++retries;
			queueTaskFuture = jobToStart.scheduleBuild2(0, new Cause.UpstreamCause(upstreamRun), Arrays.asList(parameterActions));
			if(queueTaskFuture == null) {
				try {
					listener.error("Unable to queue job. Trying again in 5 seconds. (try: " + retries + "/5)");
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					throw new IOException(e);
				}
			} else {
				break;
			}
		}
		if(queueTaskFuture == null) {
			listener.error("Didn't start job! Apparently the same job is queued.");
			return null;
		}

		if(triggerOnly) {
			listener.getLogger().println("Only triggering the build. Not waiting to get a build number.");
			return null;
		}

		listener.getLogger().print("Queued job ");
		listener.hyperlink(WaitForBuildStep.getRootUrl() + jobToStart.getUrl(), jobToStart.getFullDisplayName());
		listener.getLogger().println();

		try {
			Build build = queueTaskFuture.waitForStart();
			listener.getLogger().print("Run started: ");
			listener.hyperlink(WaitForBuildStep.getRootUrl() + build.getUrl(), build.getFullDisplayName());
			listener.getLogger().println();
			return build;
		} catch (InterruptedException e) {
			throw new IOException(e);
		} catch (ExecutionException e) {
			throw new IOException(e);
		}
	}

	public static Action getParameterActions(AbstractProject project, String parameters, BuildListener listener) {
		return getParameterActions(project, parameters, listener, true);
	}

	public static Action getParameterActions(AbstractProject project, String parameters, BuildListener listener, boolean echoParameters) {
		PrintStream logger = listener.getLogger();
		List<ParameterValue> result = new ArrayList<ParameterValue>();
		Map<String, String> propertiesMap = getPropertiesMap(parameters);

		@SuppressWarnings("unchecked")
		ParametersDefinitionProperty projectProperties = (ParametersDefinitionProperty)project.getProperty(ParametersDefinitionProperty.class);
		if(projectProperties != null) {
			List<ParameterDefinition> parameterDefinitions = projectProperties.getParameterDefinitions();
			if(parameterDefinitions != null) {
				for (ParameterDefinition parameterDefinition : parameterDefinitions) {
					String propertyName = parameterDefinition.getName();
					if(propertiesMap.containsKey(propertyName)) {
						String value = propertiesMap.get(propertyName);
						if(echoParameters) {
							logger.println("using variable: '" + propertyName + "' -> '" + value + "'");
						}
						result.add(new StringParameterValue(propertyName, value));
					} else {
						ParameterValue defaultParameterValue = parameterDefinition.getDefaultParameterValue();
						if(echoParameters) {
							logger.println("using default for: '" + defaultParameterValue.getName() + "' -> '" + defaultParameterValue.toString() + "'");
						}
						result.add(defaultParameterValue);
					}
				}
			}
		}

		return new ParametersAction(result);
	}

	private static Map<String, String> getPropertiesMap(String parameters) {
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
