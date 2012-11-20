package com.attask.jenkins;

import hudson.console.HyperlinkNote;
import hudson.model.Cause;
import hudson.model.Run;

/**
 * User: Joel Johnson
 * Date: 11/15/12
 * Time: 8:32 PM
 */
public class RetriedCause extends Cause.UpstreamCause {
	private final String externalizableId;
	private final String url;
	private final String buildName;

	public RetriedCause(Run run) {
		super(run);
		externalizableId = run.getExternalizableId();
		url = run.getUrl();
		buildName = run.getDisplayName();
	}

	public String getExternalizableId() {
		return externalizableId;
	}

	public String getUrl() {
		return url;
	}

	public String getBuildName() {
		return buildName;
	}

	@Override
	public String getShortDescription() {
		return "Retry of " + HyperlinkNote.encodeTo("../../../" + getUrl(), getBuildName());
	}
}
