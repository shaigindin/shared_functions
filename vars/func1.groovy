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

def void readConfigFile(String filePath){
//    def jsonSlurper = new JsonSlurper()
//    File f = new File(filePath)
//    return jsonSlurper.parseText(f.text)
    print(filePath)
}


