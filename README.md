# agent-java-karate

- [Description](#description)
- [Agent Configuration](#agent-configuration)
- [Properties File Configuration](#properties-file-configuration)
- [Execution](#execution)


## Description
ReportPortal Java agent for Karate testing tool.

## Agent Configuration
Until the agent-java-karate project is published to Maven repository,
the following configuration can be used:

* Add jitpack repository to get agent project from GitHub:
    * Maven pom.xml
    ```xml
        <repositories>
            <repository>
                <id>jitpack.io</id>
                <url>https://jitpack.io</url>
            </repository>
        </repositories>
    ```

    * Gradle build.gradle
    ```groovy
         repositories {                
            maven { url 'https://jitpack.io' }
         }
    ```

* Add dependency for the agent project:
    * Maven pom.xml
    ```xml
        <dependency>
            <groupId>com.github.vrymar</groupId>
            <artifactId>agent-java-karate</artifactId>
            <version>v1.0.0</version>
        </dependency>
    ```

    * Gradle build.gradle
    ```groovy
        implementation 'com.github.vrymar:agent-java-karate:v1.0.0'
    ```
  
**Note**: When the agent is approved by ReportPortal, 
the agent repository can be taken from `reportportal`. E.g.: 
* Maven pom.xml
    ```xml
        <dependency>
            <groupId>com.github.reportportal</groupId>
            <artifactId>agent-java-karate</artifactId>
            <version>Tag_or_Version</version>
        </dependency>
    ```

* Gradle build.gradle
   ```groovy
       implementation 'com.github.reportportal:agent-java-karate:Tag_or_Version'
   ```

## Properties File Configuration
* Create `reportportal.properties` file in `src\main\resources` directory.
* Add the following parameters:
```
rp.endpoint = <REPORTPORTAL_URL_ADDRESS>  
rp.uuid = <REPORTPORTAL_PERSONAL_UUID>  
rp.launch = <REPORTPORTAL_LAUNCH_NAME>  
rp.project = <REPORTPORTAL_PROJECT_NAME>  

OPTIONAL PARAMETERS  
rp.reporting.async=true  
rp.reporting.callback=true  
rp.enable=true  
rp.description=My awesome launch  
rp.attributes=key:value;value  
rp.rerun=true  
rp.rerun.of=ae586912-841c-48de-9391-50720c35dd5a  
rp.convertimage=true  
rp.mode=DEFAULT  
rp.skipped.issue=true  
rp.batch.size.logs=20  
rp.keystore.resource=<PATH_TO_YOUR_KEYSTORE>  
rp.keystore.password=<PASSWORD_OF_YOUR_KEYSTORE>  
```

## Execution
To publish test results to ReportPortal, the test project should run by `KarateReportPortalRunner` instead of Karate runner.
E.g.:  

  ```java
    class scenarioRunnerTest {
    
        @Test
        void testParallel() {
            KarateReportPortalRunner
                    .path("classpath:examples")
                    .outputCucumberJson(true)
                    .tags("~@ignore")
                    .parallel(1);
        }
    }
  ```
