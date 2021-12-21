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
import java.nio.file.Paths

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

def joinPaths(a,b){
	return Paths.get(a,b)
}
@NonCPS
def readConfigFile(String jsonText){
    def jsonSlurper = new JsonSlurperClassic()
    return new ArrayList<>(jsonSlurper.parseText(jsonText))
}


def cleanIt(paramMAp, dependencies){
	def output = bat returnStdout: true, script: "dir \"${paramMAp.WORKSPACE}\" /b /A:D"
	foldersList = output.tokenize('\n').collect() { it.trim() }
	foldersList = foldersList.drop(2)
	def cleanDependencies = (0..dependencies.size()-1).findAll(
                       { !foldersList.contains(dependencies[it])}).collect { dependencies[it] }
    println(cleanDependencies)
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
	  buildParallelMap.put(name, prepareOneBuildStage(name,branch))
    }
    buildStagesList.add(buildParallelMap)
  }
  return buildStagesList
}


def prepareOneBuildStage(String name, String branch) {
  return {
    stage("Build stage:${name} test") {
checkout([
                            $class: 'GitSCM',
                            branches: [[name: "${branch}"]],
                            browser: [$class: 'BitbucketWeb', repoUrl: 'http://rds:7990'],
                            doGenerateSubmoduleConfigurations: false,
                            extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${name}"], [$class: 'CleanBeforeCheckout']],
                            submoduleCfg: [],
                            userRemoteConfigs: [[credentialsId: '30bac85c-db0f-430c-9cd0-6bd25f2eb01a', url: "http://rds:7990/scm/btng/${name}.git"]]])
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

def tagRepo(paramMAp, repo_name, package_type){
	// assert (fileExists(file: "${paramMAp.WORKSPACE}/pycommon/.venv") && fileExists(file: "${env.WORKSPACE}/pycommon/misc/version_util.py"))
	withCredentials([gitUsernamePassword(credentialsId: '30bac85c-db0f-430c-9cd0-6bd25f2eb01a', gitToolName: 'git-tool')]) {
        bat "python ${paramMAp.WORKSPACE}/pycommon/misc/version_util.py --command 3 --repo_path ${paramMAp.WORKSPACE}/${repo_name} --type ${package_type} --jenkins_id ${currentBuild.number}"
    }
	// bat "python ${paramMAp.WORKSPACE}/${repo_name}/misc/version_util.py --command 3 --repo_path ${paramMAp.WORKSPACE}/${repo_name} --type ${package_type} --jenkins_id ${currentBuild.number}"
}



