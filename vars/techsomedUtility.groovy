import java.io.*
import groovy.io.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic 
import jenkins.model.Jenkins

class A{
    String name;
    String age;
}

def A funcHello(){
	return new A(name:"moshe",age:"22");
}

@NonCPS
def readConfigFile(String jsonText){
    def jsonSlurper = new JsonSlurperClassic()
    return new HashMap<>(jsonSlurper.parseText(jsonText))
}

@NonCPS
def createFile(file_path){
    writeFile(file: file_path, text: "done")
}

@NonCPS
def blancStage(text){
         stage("${text}"){}
}

@NonCPS
def loadDependencies(dep_list){
	for (name in dep_list ) {
        	def repoTest = load "${env.WORKSPACE}/${name}/Jenkins/main.groovy"
        	repoTest.main()
    }
}

@NonCPS
def void finalize(repo_name){
    writeFile(file: "${env.WORKSPACE}/${repo_name}/Jenkins/ready_${env.BUILD_NUMBER}.txt", text: "package was tested")
}


def prepareBuildStages(repos, branches) {
  def buildStagesList = []
  for (i=1; i<2; i++) {
    def buildParallelMap = [:]
    for (pair in [repos, branches].transpose() ) {
      def name = "${pair[0]}"
      def branch = "${pair[1]}"
      println("WEEELOOO ${name} ${branch}")
	  buildParallelMap.put(name, prepareOneBuildStage(name,branch))
    }
    buildStagesList.add(buildParallelMap)
  }
  return buildStagesList
}


def prepareOneBuildStage(String name, String branch) {
  return {
    stage("Build stage:${name}") {
        dir ("${name}"){
           git branch: "${branch}", credentialsId: '30bac85c-db0f-430c-9cd0-6bd25f2eb01a', url: "http://shai@rds:7990/scm/btng/${name}.git"
        }
    }
  }
}

def loadGitRepos(repos, branches){
        def runParallel = true
		buildStages = prepareBuildStages(repos,branches)
        for (builds in buildStages) {
            if (runParallel) {
              parallel(builds)
            } else {
              // run serially (nb. Map is unordered! )
              for (build in builds.values()) {
                build.call()
              }
            }
        }
}



def String getJobName(){
	return "${env.JOB_NAME}"
}


/**
 * Change param value during build
 *
 * @param paramName new or existing param name
 * @param paramValue param value
 * @return nothing
 */
def setParam(String paramName, String paramValue) {
	List<ParameterValue> newParams = new ArrayList<>();
	newParams.add(new StringParameterValue(paramName, paramValue))
	try {
		$build().addOrReplaceAction($build().getAction(ParametersAction.class).createUpdated(newParams))
	} catch (err) {
		$build().addOrReplaceAction(new ParametersAction(newParams))
	}
}

/**
 * Add a new option to choice parameter for the current job
 *
 * @param paramName parameter name
 * @param optionValue option value
 * @return nothing
 */
def addChoice(String paramName, String optionValue) {
	addChoice($build().getParent(), paramName, optionValue)
}

/**
 * Add a new option to choice parameter to the given job
 *
 * @param paramName parameter name
 * @param optionValue option value
 * @return nothing
 */
def addChoice(String jobName, String paramName, String optionValue) {
	List jobNames = jobName.tokenize("/")
	Job job = Jenkins.instance.getJob("daily_pipeline")

	addChoice(job, paramName, optionValue)
}

/**
 * Add a new option to choice parameter to the given job
 * Will be added as the first (default) choice
 * @param job job object
 * @param paramName parameter name
 * @param optionValue option value
 * @return
 */
def addChoice(Job job, String paramName, String optionValue) {
	ParametersDefinitionProperty paramsJobProperty = job.getProperty(ParametersDefinitionProperty.class);
	ChoiceParameterDefinition oldChoiceParam = (ChoiceParameterDefinition)paramsJobProperty.getParameterDefinition(paramName);
	List<ParameterDefinition> oldJobParams = paramsJobProperty.getParameterDefinitions();
	List<ParameterDefinition> newJobParams = new ArrayList<>();

	for (ParameterDefinition p: oldJobParams) {
		if (!p.getName().equals(paramName)) {
			newJobParams.add(0,p);
		}
	}

	List<String> choices = new ArrayList(oldChoiceParam.getChoices());


	choices.add(0,optionValue);

	ChoiceParameterDefinition newChoiceParam = new ChoiceParameterDefinition(paramName, choices, oldChoiceParam.getDefaultParameterValue().getValue(), oldChoiceParam.getDescription());
	newJobParams.add(newChoiceParam);

	ParametersDefinitionProperty newParamsJobProperty = new ParametersDefinitionProperty(newJobParams);
	job.removeProperty(paramsJobProperty);
	job.addProperty(newParamsJobProperty);
}