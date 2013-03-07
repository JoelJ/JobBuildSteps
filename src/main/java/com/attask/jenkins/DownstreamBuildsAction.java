package com.attask.jenkins;

import hudson.model.Action;
import hudson.model.BallColor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.test.AbstractTestResultAction;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: Joel Johnson
 * Date: 3/6/13
 * Time: 4:32 PM
 */
@ExportedBean
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

	@Exported
	public List<String> getDownstreamBuilds() {
		return Collections.unmodifiableList(downstreamBuilds);
	}

	public List<Run> findDownstreamBuilds() {
		List<Run> result = new ArrayList<Run>(downstreamBuilds.size());
		for (String downstreamBuildId : downstreamBuilds) {
			Run<?, ?> run = Run.fromExternalizableId(downstreamBuildId);
			if(run != null) {
				result.add(run);
			}
		}
		return result;
	}

	public String findOrb(Run run) {
		boolean building = run.isBuilding();
		if(building) {
			return BallColor.NOTBUILT_ANIME.getImageOf("16x16");
		} else {
			Result result = run.getResult();
			return result.color.getImageOf("16x16");
		}
	}

	public Integer findFailureCount(Run run) {
		Integer result = null;
		AbstractTestResultAction testAction = run.getAction(AbstractTestResultAction.class);
		if(testAction != null) {
			result = testAction.getFailCount();
		}
		return result;
	}

	public String getIconFileName() {
		return null;
	}

	public String getDisplayName() {
		return null;
	}

	public String getUrlName() {
		return "downstreamBuilds";
	}
}
