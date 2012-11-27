package com.attask.jenkins;

import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.model.Run;

/**
 * User: Joel Johnson
 * Date: 11/15/12
 * Time: 3:56 PM
 */
public class RetriedAction implements Action {
	private final int buildNumber;
	private final String externalizableId;

	public RetriedAction(Run run) {
		if(run == null) {
			throw new IllegalArgumentException("run cannot be null");
		}
		buildNumber = run.getNumber();
		externalizableId = run.getExternalizableId();
	}

	public Run findBuild(BuildListener listener, int numberRetries) {
		Run<?, ?> run = Run.fromExternalizableId(getExternalizableId());
		Job<?,?> project = run.getParent();
		int newerBuildNumber = buildNumber;
		int numberTries = 0;
		while(true) {
			while(newerBuildNumber < project.getNextBuildNumber()) {
				newerBuildNumber++;
				Run<?, ?> newBuild = project.getBuildByNumber(newerBuildNumber);
				RetriedCause cause = newBuild.getCause(RetriedCause.class);
				if(cause != null) {
					if(this.externalizableId.equals(cause.getExternalizableId())) {
						return newBuild;
					}
				}
			}
			try {
				listener.getLogger().println("Still haven't found the retried job. Trying again in 1 second.");
				numberTries++;
				if(numberTries > numberRetries)
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				listener.error("Interrupted. Bailing out.", e.getMessage());
				break;
			}
		}
		return null;
	}

	public int getBuildNumber() {
		return buildNumber;
	}

	public String getExternalizableId() {
		return externalizableId;
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
