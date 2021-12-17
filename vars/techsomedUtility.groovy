import java.io.*
import groovy.io.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic 
import jenkins.model.Jenkins
import hudson.EnvVars;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;


def createGlobalEnvironmentVariables(String key, String value){ 
       Jenkins instance = Jenkins.getInstance();
       DescribableList<NodeProperty<?>, NodePropertyDescriptor> globalNodeProperties = instance.getGlobalNodeProperties();
       List<EnvironmentVariablesNodeProperty> envVarsNodePropertyList = globalNodeProperties.getAll(EnvironmentVariablesNodeProperty.class);
       EnvironmentVariablesNodeProperty newEnvVarsNodeProperty = null;
       EnvVars envVars = null;
       if ( envVarsNodePropertyList == null || envVarsNodePropertyList.size() == 0 ) {
           newEnvVarsNodeProperty = new hudson.slaves.EnvironmentVariablesNodeProperty();
           globalNodeProperties.add(newEnvVarsNodeProperty);
           envVars = newEnvVarsNodeProperty.getEnvVars();
       } else {
           envVars = envVarsNodePropertyList.get(0).getEnvVars();
       }
       envVars.put(key, value)
       instance.save()
}

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
           git branch: "${branch}", credentialsId: '30bac85c-db0f-430c-9cd0-6bd25f2eb01a', url: "http://rds:7990/scm/btng/${name}.git"
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

def tagRepo(repo_name){
	assert (fileExists(file: "${env.WORKSPACE}/pycommon/.venv") && fileExists(file: "${env.WORKSPACE}/pycommon/misc/version_util.py"))
	bat "${env.WORKSPACE}/pycommon/.venv/Scripts/activate && python ${env.WORKSPACE}/pycommon/misc/version_util.py --command 3 --repo_path ${env.WORKSPACE}/${repo_name} --type python --jenkins_id ${currentBuild.number}"
}



