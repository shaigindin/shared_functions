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

def getFoldersFromPath(path){
	def output = bat returnStdout: true, script: "dir \"${path}\" /b /A:D"
	foldersList = output.tokenize('\n').collect() { it.trim() }
	return foldersList.drop(2) 
}


def filterLoadRepos(paramMAp, dependencies){
	def repos = []
	for (i=0; i < dependencies.size(); i++){
		println("${paramMAp.WORKSPACE}/${dependencies[i]}/Jenkins/ready_${paramMAp.BUILD_NUMBER}.txt")
		if(!fileExists(file: "${paramMAp.WORKSPACE}/${dependencies[i]}/Jenkins/ready_${paramMAp.BUILD_NUMBER}.txt")){
			repos.add(dependencies[i])
		}
	}
	return repos
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
	for (i=0; i<repos.size(); i++) {
		def name = repos[i]
		def branch = branches["${name}"]
		buildParallelMap.put("Git Check: ${name} test", prepareOneBuildStage(name,branch))
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



def tagRepo(paramMAp, repo_name, package_type){
	// assert (fileExists(file: "${paramMAp.WORKSPACE}/pycommon/.venv") && fileExists(file: "${env.WORKSPACE}/pycommon/misc/version_util.py"))
	withCredentials([gitUsernamePassword(credentialsId: '30bac85c-db0f-430c-9cd0-6bd25f2eb01a', gitToolName: 'git-tool')]) {
        bat(script: "python ${paramMAp.WORKSPACE}/pycommon/misc/version_util.py --command 3 --repo_path ${paramMAp.WORKSPACE}/${repo_name} --type ${package_type} --jenkins_id ${currentBuild.number}", label : "tag ${repo_name} repository")
    }
}


def createVenvCpp(paramMAp, repoName){
    stage('CppTDM: Prepare Env'){
        println("YAAAAYYY:")
		print(paramMAp)
		if (paramMAp.SET_VERSION_BUILD_NUMBER){
			print("param baby")
		}
		else{
			print("not param baby")
		}
		buildParam = (paramMAp.SET_VERSION_BUILD_NUMBER) ? "--version_build_number ${currentBuild.number}" : " "
        bat "python ${paramMAp.WORKSPACE}/pycommon/misc/version_util.py " +
        "--command 2 --repo_path ${paramMAp.WORKSPACE}/${repoName} --type cpp ${buildParam}"
        bat "cd ${paramMAp.WORKSPACE}/${repoName} && python cmake_generate.py --delete --andbuild"
     }
}

def createVenvPy(paramMAp, repoName){
    stage('PyTDM: Prepare Env'){
        if(paramMAp.SET_VERSION_BUILD_NUMBER){
            bat "python ${paramMAp.WORKSPACE}/pycommon/misc/version_util.py --command 2 --repo_path ${paramMAp.WORKSPACE}/${repoName} --type python --version_build_number ${currentBuild.number}"
        }
        bat "python ${paramMAp.WORKSPACE}/pycommon/misc/venv_creator.py -s ${paramMAp.WORKSPACE}/${repoName}"
    }
}

def is_repo_built(paramMAp, repo_name){
	return fileExists(file: "${paramMAp.WORKSPACE}/${repo_name}/Jenkins/ready_${paramMAp.BUILD_NUMBER}.txt")
}

