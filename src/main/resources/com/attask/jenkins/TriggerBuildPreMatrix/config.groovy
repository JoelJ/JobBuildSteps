package com.attask.jenkins.TriggerBuildPreMatrix

import hudson.matrix.MatrixConfigurationSorterDescriptor
import hudson.model.Result;

def f = namespace(lib.FormTagLib)

f.entry(title:"Job to Execute (Pre)", field:"jobName", description: "Job to trigger before any of the matrix jobs run. Leave empty to skip.") {
	f.textbox()
}
f.entry(title:"Parameters (Pre)", field:"parameters") {
	f.textarea()
}

f.entry(title:"Results File To Inject", field:"resultsFileToInject") {
	f.textbox()
}

f.entry(title:"Job to Execute (Post)", field:"postJobName", description: "Job to trigger after all the matrix jobs run. Leave empty to skip.") {
	f.textbox()
}

f.entry(title:"Parameters (Post)", field:"postParameters") {
	f.textarea()
}

f.optionalBlock (field:"runSequentially", title:_("Run each configuration sequentially"), inline:true) {
	if (MatrixConfigurationSorterDescriptor.all().size()>1) {
		f.dropdownDescriptorSelector(title:_("Execution order of builds"), field:"sorter")
	}
}

f.optionalBlock (field:"hasTouchStoneCombinationFilter", title:_("Execute touchstone builds first"), inline:true) {
	f.entry(title:_("Filter"), field:"touchStoneCombinationFilter") {
		f.textbox()
	}

	f.entry(title:_("Required result"), field:"touchStoneResultCondition", description:_("required.result.description")) {
		select(name:"touchStoneResultCondition") {
			f.option(value:"SUCCESS",  selected:my.touchStoneResultCondition==Result.SUCCESS,  _("Stable"))
			f.option(value:"UNSTABLE", selected:my.touchStoneResultCondition==Result.UNSTABLE, _("Unstable"))
		}
	}
}
