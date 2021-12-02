import java.io.*
import groovy.io.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput


class A{
    String name;
    String age;
}

def A funcHello(){
	return new A(name:"moshe",age:"22");
}

def readConfigFile(String jsonText){
    def jsonSlurper = new JsonSlurper()
    return jsonSlurper.parseText(jsonText)


}
