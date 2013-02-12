package com.attask.jenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.*;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;

/**
 * User: Joel Johnson
 * Date: 11/15/12
 * Time: 6:59 PM
 */
public class RetryBuildWrapper extends BuildWrapper implements MatrixAggregatable {
	private final String worseThan;

	@DataBoundConstructor
	public RetryBuildWrapper(int numberRetries, String worseThan) {
		//I like storing raw values. I know, I'm weird.
		//Converting worseThan to an Enum then back again converts it to FAILURE if it's invalid.
		this.worseThan = worseThan == null || worseThan.isEmpty() ? Result.FAILURE.toString() : Result.fromString(worseThan).toString();
	}

	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		return new Environment() {
			@Override
			public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
				return build instanceof MatrixRun || RetryBuildWrapper.this.tearDown(build, listener);
			}
		};
	}

	private boolean tearDown(AbstractBuild build, BuildListener listener) {
		if(!shouldRetry(build, listener)) {
			return true;
		}

		listener.getLogger().println("retrying " + build);
		build.addAction(new RetriedAction(build));
		ParametersAction action = build.getAction(ParametersAction.class);
		build.getProject().scheduleBuild(0, new RetriedCause(build), action);

		return true;
	}

	private boolean shouldRetry(AbstractBuild build, BuildListener listener) {
		Result result = build.getResult();
		if (result != null && result.isWorseOrEqualTo(Result.fromString(worseThan))) {
			RetriedCause cause = (RetriedCause) build.getCause(RetriedCause.class);
			if(cause == null) {
				return true;
			}
			listener.error(build + " already retried. Not retrying again.");
		}
		return false;
	}

	@Exported
	public String getWorseThan() {
		return worseThan;
	}

	public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
		return new MatrixAggregator(build, launcher, listener) {
			@Override
			public boolean endBuild() throws InterruptedException, IOException {
				return tearDown(build, listener);
			}
		};
	}

	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {
		@Override
		public boolean isApplicable(AbstractProject<?, ?> item) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Auto-retry build if it fails";
		}
	}
}
