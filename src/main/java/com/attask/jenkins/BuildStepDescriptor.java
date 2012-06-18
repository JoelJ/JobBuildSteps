package com.attask.jenkins;

import hudson.Extension;
import hudson.model.*;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.QueryParameter;

/**
 * User: joeljohnson
 * Date: 3/6/12
 * Time: 9:08 PM
 */
public abstract class BuildStepDescriptor extends Descriptor<Builder> {
	public FormValidation doCheckJobName(@QueryParameter(value = "jobName", required = true) String value) {
		if(value == null || value.isEmpty()) {
			return FormValidation.error("Job Name is a required field");
		}
		if(value.contains("$")) {
			return FormValidation.warning("It appears you are using a variable. Unable to validate the Job Name.");
		}
		if(Hudson.getInstance().getTopLevelItemNames().contains(value)) {
			return FormValidation.ok();
		}
		return FormValidation.error("You must specify a job name that exists or an environment variable.");
	}
}
