package com.attask.jenkins;

import hudson.model.Action;
import hudson.model.Run;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: Joel Johnson
 * Date: 3/6/13
 * Time: 4:32 PM
 */
public class DownstreamBuildsAction implements Action {
	private final List<String> downstreamBuilds;

	public DownstreamBuildsAction() {
		this.downstreamBuilds = new ArrayList<String>();
	}

	public void addDownstreamBuild(Run run) {
		synchronized (downstreamBuilds) {
			downstreamBuilds.add(run.getExternalizableId());
		}
	}

	public List<String> getDownstreamBuilds() {
		return Collections.unmodifiableList(downstreamBuilds);
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
