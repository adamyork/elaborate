# elaborate

generate deep call graphs for java resources


## why

occasionally, the need to support very complex, deeply nested code arises. a visual representation can help quickly
identify interactions between source. for example, if one wanted to understand what outbound http calls are made when a 
command is invoked, elaborate can generate a visual, filtered representation of the code, with minimal configuration.


## what 

download the elaborate jar


## how

run the jar from the command line with the following options
````
java -jar elaborate.jar -c "<path to config file>.json" -v
````
* -c --config "<path to config file>.json"  configuration file **required**
* -v --verbose enabled verbose logging **optional**


### configuration consists of

* input the path to the input source. can be a jar or war **required**
* entryClass fully qualified class name ; root node **required**
* entryMethod starting method; root invocation **required**
* output the output path and file. txt and svg supported.if omitted output will be to console. **optional**
* includes any additional jars contained within the input source. **optional**
* excludes any classes to exclude from processing **optional**
* implicitMethod in the case of new object instantiation, these methods will be added to the call chain. **optional**


````json
{
    "input": "/path/to/someFile.war",
    "entryClass": "com.some.package.SomeClass",
    "entryMethod": "someMethod",
    "output": "/path/to/someOutput.svg",
    "includes": [
        "name-of-additional-jar"
    ],
    "excludes": [
        "java.util.*",
        "java.lang.*",
        "org.slf4j.*",
        "com.google.*",
        "org.apache.*",
        "org.jooq.*",
        "java.net.*",
        "org.springframework.*"
    ],
    "implicitMethods": [
        "execute",
        "apply",
        "call"
    ]
}
````


### sample output


text



svg