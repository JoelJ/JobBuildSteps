package com.attask.jenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.IOUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.FileScanner;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.*;

/**
 * User: joeljohnson
 * Date: 3/6/12
 * Time: 4:52 PM
 */
public class WaitForBuildStep extends Builder {
	public final String jobName;
	public final String buildNumber;
	public final int retries;
	public final int delay;
	public final String filesToCopy;
	public final boolean copyBuildResult = true;

    @DataBoundConstructor
    public WaitForBuildStep(String jobName, String buildNumber, int retries, int delay, String filesToCopy) throws FormValidation {
        this.jobName = jobName;
		this.buildNumber = buildNumber;
		this.retries = retries < 0 ? 0 : retries;
		this.delay = delay < 5000 ? 5000 : delay;
		this.filesToCopy = filesToCopy;
    }

	@Override
	public boolean perform(final AbstractBuild build, Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
		final PrintStream logger = listener.getLogger();
		EnvVars envVars = build.getEnvironment(listener);
		Hudson jenkins = Hudson.getInstance();
		
		String jobName = envVars.expand(this.jobName);
		TopLevelItem topLevelItem = jenkins.getItem(jobName);

		if(topLevelItem == null || !(topLevelItem instanceof Job)) {
			listener.getLogger().println(jobName + " is not a Job {" + topLevelItem + "}");
			return false;
		}

		int buildNumber = Integer.parseInt(envVars.expand(this.buildNumber));

		Job job = (Job) topLevelItem;
		final Run buildToWaitFor = job.getBuildByNumber(buildNumber);
		Waiter wait = new Waiter(retries, delay);
		boolean waitResult = wait.retryUntil(new Waiter.Predicate() {
			public boolean call() {
				try {
					logger.print("Checking status of build ");
					listener.hyperlink(getRootUrl() + buildToWaitFor.getUrl(), buildToWaitFor.getFullDisplayName());
					if (buildToWaitFor.isBuilding()) {
						logger.print(" (building)");
						logger.println();
						return false;
					} else {
						logger.print(" (complete)");
						logger.println();
						return true;
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
		if(!waitResult) {
			listener.hyperlink(getRootUrl() + buildToWaitFor.getUrl(), buildToWaitFor.getFullDisplayName());
			logger.println(" didn't finish");
		} else {
			build.setResult(buildToWaitFor.getResult());
			copyArtifacts(filesToCopy, buildToWaitFor, new File(build.getWorkspace().getRemote()), listener);
		}
		return waitResult;
	}

	public static String getRootUrl() {
		String rootUrl = Hudson.getInstance().getRootUrl();
		return rootUrl == null ? "/" : rootUrl;
	}

	private void copyArtifacts(String filesToCopy, Run build, File workspace, BuildListener listener) {
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setIncludes(new String[] {filesToCopy});
		scanner.setBasedir(build.getArtifactsDir());
		scanner.scan();
		String[] includedFiles = scanner.getIncludedFiles();

		for (String includedFile : includedFiles) {
			listener.getLogger().println("Copying artifact: '" + includedFile + "'");
			File sourceFile = new File(build.getArtifactsDir(), includedFile);

			File destDir = new File(workspace, build.getParent().getName());
			if(!destDir.exists()) {
				listener.getLogger().println("'" + destDir.getAbsolutePath() + "' doesn't exist. Creating.");
				if(!destDir.mkdirs()) {
					listener.error("Couldn't create directory '" + destDir.getAbsolutePath() + "'. Attempting to continue.");
				}
			}
			File destFile = new File(destDir, sourceFile.getName());
			try {
				OutputStream stream = new FileOutputStream(destFile);
				try {
					IOUtils.copy(sourceFile, stream);
				} finally {
					try {
						stream.close();
					} catch (IOException e) {
						listener.error("unable to close stream for file copy '" + includedFile + "' to " + destFile.getAbsolutePath() + ". " + e.getMessage());
					}
				}
			} catch (FileNotFoundException e) {
				listener.error(e.getMessage());
			} catch (IOException e) {
				listener.error("unable to copy file '" + includedFile + "' to " + destFile.getAbsolutePath() + ". " + e.getMessage());
			}
			listener.getLogger().println("Copied artifact: '" + includedFile + "'");
		}
	}

	@Extension
	public static final class DescriptorImpl extends com.attask.jenkins.BuildStepDescriptor {
		public FormValidation doCheckBuildNumber(@QueryParameter(value = "buildNumber", required = true) String value,
												 @QueryParameter(value = "jobName", required = true) String jobName
		) {
			try {
				if(value == null || value.isEmpty()) {
					return FormValidation.error("Build Number is a required field");
				}
				if(value.contains("$")) {
					return FormValidation.warning("It appears you are using a variable. Unable to validate the Build Number.");
				}
				if(jobName.contains("$")) {
					return FormValidation.warning("It appears you are using a variable for Job Name. Unable to validate the Build Number.");
				}

				int buildNumber = Integer.parseInt(value);
				if(buildNumber <= 0) {
					return FormValidation.error("Build Number must be a valid positive/non-zero number or an environment variable.");
				}
				TopLevelItem topLevelItem = Hudson.getInstance().getItem(jobName);
				if(!(topLevelItem instanceof Job)) {
					return FormValidation.warning("Cannot validate Build Number without a valid Job Name.");
				}

				Job job = (Job)topLevelItem;
				if(job.getBuildByNumber(buildNumber) == null) {
					return FormValidation.error("Build with given number does not exist for the given Job name.");
				}
			} catch(NumberFormatException e) {
				return FormValidation.error("Build Number must be a valid positive/non-zero number or an environment variable.");
			}

			return FormValidation.ok();
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		public String getDisplayName() {
			return "Wait for build to finish";
		}
	}
}
