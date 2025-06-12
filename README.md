# ReportPortal runtime Hook for Karate tests

Karate reporters which uploads the results to a ReportPortal server.

> **DISCLAIMER**: We use Google Analytics for sending anonymous usage information such as agent's and client's names, and their versions
> after a successful launch start. This information might help us to improve both ReportPortal backend and client sides. It is used by the
> ReportPortal team only and is not supposed for sharing with 3rd parties.

[![Maven Central](https://img.shields.io/maven-central/v/com.epam.reportportal/agent-java-karate.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.epam.reportportal/agent-java-karate)
[![CI Build](https://github.com/reportportal/agent-java-karate/actions/workflows/ci.yml/badge.svg)](https://github.com/reportportal/agent-java-karate/actions/workflows/ci.yml)
[![codecov](https://codecov.io/github/reportportal/agent-java-karate/graph/badge.svg?token=wJr9F6hZln)](https://codecov.io/github/reportportal/agent-java-karate)
[![Join Slack chat!](https://img.shields.io/badge/slack-join-brightgreen.svg)](https://slack.epmrpp.reportportal.io/)
[![stackoverflow](https://img.shields.io/badge/reportportal-stackoverflow-orange.svg?style=flat)](http://stackoverflow.com/questions/tagged/reportportal)
[![Build with Love](https://img.shields.io/badge/build%20with-❤%EF%B8%8F%E2%80%8D-lightgrey.svg)](http://reportportal.io?style=flat)

The latest version: 5.2.0. Please use `Maven Central` link above to get the agent.

## Overview: How to Add ReportPortal Logging to Your Project

To start using ReportPortal with Karate framework please do the following steps:

1. [Configuration](#configuration)
    * Create/update the `reportportal.properties` configuration file
    * Build system configuration
    * Add Listener
        * Runtime
        * Post-running
2. [Logging configuration](#logging)
    * Loggers and their types
3. [Running tests](#running-tests)
    * Build system commands

## Configuration

### 'reportportal.properties' configuration file

As the first step you need to create a file named `reportportal.properties` in your Java project in a source
folder `src/main/resources` or `src/test/resources` (depending on where your tests are located):

**reportportal.properties**

```
rp.endpoint = http://localhost:8080
rp.api.key = test_YIvQraKKSquDZqrA6JLCWCX5qwmMZBk_7tTm_fkN44AHCi18Ze0RtYqxWNYKxk5p
rp.launch = Karate Tests
rp.project = default_personal
```

**Property description**

* `rp.endpoint` - the URL for the ReportPortal server (actual link).
* `rp.api.key` - an access token for ReportPortal which is used for user identification. It can be found on your report
  portal user profile page.
* `rp.project` - a project ID on which the agent will report test launches. Must be set to one of your assigned
  projects.
* `rp.launch` - a user-selected identifier of test launches.

The full list of supported properties is located here in client-java library documentation (a common library for all
Java agents): https://github.com/reportportal/client-java

## Build system configuration

### Maven

If your project is Maven-based you need to add dependencies to `pom.xml` file:

```xml

<project>
    <!-- project declaration omitted -->

    <dependency>
        <groupId>com.epam.reportportal</groupId>
        <artifactId>agent-java-karate</artifactId>
        <version>5.2.0</version>
        <scope>test</scope>
    </dependency>

    <!-- build config omitted -->
</project>
```

You are free to use you own version of Karate, but not earlier than 1.0.0. If you leave just Agent dependency it will
be still OK, it will use transitive Karate version.

### Gradle

For Gradle-based projects please update dependencies section in `build.gradle` file:

```groovy
dependencies {
    testImplementation 'com.epam.reportportal:agent-java-karate:5.2.0'
}
```

## Listener configuration

### Runtime

Runtime publisher uploads Karate tests on ReportPortal during the test execution, providing real-time monitoring capabilities. To publish
test results in this case, the test project should use by `ReportPortalHook` class, an instance of which you should pass to Karate runner.
E.G.:

```java
import com.epam.reportportal.karate.ReportPortalHook;
import com.intuit.karate.Runner;

class ScenarioRunnerTest {
	@Test
	void testParallel() {
		return Runner
                .path("classpath:examples")
                .hook(new ReportPortalHook())
                .outputCucumberJson(true)
                .tags("~@ignore")
                .parallel(1);
	}
}
```

### Post-running

Post-running publisher uploads Karate tests on ReportPortal after the test execution. It uses Karate result object to get data about tests.
It might be useful if your tests make heavy load both on ReportPortal server or on the running node. To publish test results in this case,
the test project should run by `KarateReportPortalRunner` instead of Karate runner.
E.G.:

```java
import com.epam.reportportal.karate.KarateReportPortalRunner;

class ScenarioRunnerTest {
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

## Logging

Karate uses `slf4j` as Logging library, so you are free to choose any Logging Framework.

ReportPortal provides its own logger implementations for major logging frameworks like *Log4j* and *Logback*. It also
provides additional formatting features for popular client and test libraries like: *Selenide*, *Apache HttpComponents*,
*Rest Assured*, etc.

Here is the list of supported loggers and setup documentation links.

**Logging frameworks:**

| **Library name** | **Documentation link**                              |
|------------------|-----------------------------------------------------|
| Log4j            | https://github.com/reportportal/logger-java-log4j   |
| Logback          | https://github.com/reportportal/logger-java-logback |

**HTTP clients:**

| **Library name**      | **Documentation link**                                     |
|-----------------------|------------------------------------------------------------|
| OkHttp3               | https://github.com/reportportal/logger-java-okhttp3        |
| Apache HttpComponents | https://github.com/reportportal/logger-java-httpcomponents |

## Running tests

We are set. To run tests we just need to execute corresponding command in our build system.

#### Maven

`mvn test` or `mvnw test` if you are using Maven wrapper

#### Gradle

`gradle test` or `gradlew test` if you are using Gradle wrapper
