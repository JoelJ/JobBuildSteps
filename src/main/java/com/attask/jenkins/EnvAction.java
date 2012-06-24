package com.attask.jenkins;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;

/**
 * User: Joel Johnson
 * Date: 6/23/12
 * Time: 3:55 PM
 */
public class EnvAction implements EnvironmentContributingAction {
	private final String name;
	private final String value;
	public EnvAction(String name, String value) {
		this.name = name;
		this.value = value;
	}

	public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
		env.put(name, value);
	}

	public String getIconFileName() {return null;}
	public String getDisplayName() {return null;}
	public String getUrlName() {return null;}
}