package com.attask.jenkins;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;

import java.util.Map;

/**
 * User: Joel Johnson
 * Date: 1/7/13
 * Time: 3:31 PM
 */
public class EnvMapAction implements EnvironmentContributingAction {
	private final Map<String, String> inject;

	public EnvMapAction(Map<String, String> inject) {
		this.inject = inject;
	}

	public Map<String, String> getInject() {
		return inject;
	}

	public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
		env.putAll(inject);
	}

	public String getIconFileName() {
		return null;
	}

	public String getDisplayName() {
		return null;
	}

	public String getUrlName() {
		return null;
	}
}
