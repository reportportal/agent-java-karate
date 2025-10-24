# Changelog

## [Unreleased]
### Changed
- Client version updated to [5.4.6](https://github.com/reportportal/client-java/releases/tag/5.4.6), by @HardNorth

## [5.3.2]
### Changed
- Client version updated on [5.4.4](https://github.com/reportportal/client-java/releases/tag/5.4.4), by @HardNorth
### Fixed
- Some data cleanup in `ReportPortalHook` class, by @HardNorth
- Some step order issues in rare cases, by @HardNorth

## [5.3.1]
### Added
- Attachment logging, by @HardNorth

## [5.3.0]
### Changed
- Client version updated on [5.4.3](https://github.com/reportportal/client-java/releases/tag/5.4.3), by @HardNorth
- Replace "jsr305" annotations with "jakarta.annotation-api", by @HardNorth
- Switch on use of `Instant` class instead of `Date` to get more timestamp precision, by @HardNorth
### Fixed
- Issue [#43](https://github.com/reportportal/agent-java-karate/issues/43) NullpointerException in case of `null` parameter in Examples, by @HardNorth

## [5.2.2]
### Changed
- Client version updated on [5.3.17](https://github.com/reportportal/client-java/releases/tag/5.3.17), by @HardNorth

## [5.2.1]
### Changed
- Client version updated on [5.3.16](https://github.com/reportportal/client-java/releases/tag/5.3.16), by @HardNorth

## [5.2.0]
### Added
- Code reference as feature name in case it is not specified, by @HardNorth
### Changed
- Client version updated on [5.3.14](https://github.com/reportportal/client-java/releases/tag/5.3.14), by @HardNorth

## [5.1.3]
### Changed
- Client version updated on [5.2.25](https://github.com/reportportal/client-java/releases/tag/5.2.25), by @HardNorth

## [5.1.2]
### Changed
- Client version updated on [5.2.15](https://github.com/reportportal/client-java/releases/tag/5.2.15), by @HardNorth
### Fixed
- Issue [#38](https://github.com/reportportal/agent-java-karate/issues/38): Rerun failed scenarios, by @HardNorth

## [5.1.1]
### Fixed
- Issue [#32](https://github.com/reportportal/agent-java-karate/issues/32): Handling of calling scenario in the same Feature file, by @HardNorth

## [5.1.0]
### Changed
- Client version updated on [5.2.14](https://github.com/reportportal/client-java/releases/tag/5.2.14), by @HardNorth
- Called inner Features are now Nested Steps inside base Feature, by @HardNorth
- Unify Markdown description generation with other agents, by @HardNorth

## [5.0.5]
### Changed
- Client version updated on [5.2.13](https://github.com/reportportal/client-java/releases/tag/5.2.13), by @HardNorth
### Fixed
- Issue [#30](https://github.com/reportportal/agent-java-karate/issues/30) Empty interrupted features in case of scenarios tag skip, by @HardNorth

## [5.0.4]
### Removed
- Shutdown hook register on supplied Launch, by @HardNorth
### Added
- Implemented new feature: display last error log in scenario description, by @vrymar
- Implemented unit tests for the new feature, by @vrymar
### Changed
- Improved dependencies references in build.gradle, by @vrymar

## [5.0.3]
### Fixed
- Backgrounds finish with `FAILED` status, by @HardNorth

## [5.0.2]
### Changed
- Karate dependency marked as `compileOnly` to force users specify their own version of Karate, by @HardNorth
- Client version updated on [5.2.5](https://github.com/reportportal/client-java/releases/tag/5.2.5), by @HardNorth
### Fixed
- Issue [#23](https://github.com/reportportal/agent-java-karate/issues/23) scenarios outside features in parallel execution, by @HardNorth

## [5.0.1]
### Changed
- Karate dependency marked as `compileOnly` to force users specify their own version of Karate, by @HardNorth
- Client version updated on [5.2.5](https://github.com/reportportal/client-java/releases/tag/5.2.5), by @HardNorth
### Fixed
- Issue [#23](https://github.com/reportportal/agent-java-karate/issues/23) scenarios outside features in parallel execution, by @HardNorth

## [5.0.0]
### Added
- Basic Agent functionality, by @vrymar 
### Changed
- Refactored and implemented main ReportPortal agent features, by @HardNorth

## [1.0.6]
### Changed
- Refactored and cleaned up code
### Removed
- lombok dependency
### Fixed
- Parallel executions


## [1.0.5]
### Added
- Nested steps
- GitHub workflows integration
### Changed
- Upgraded versions: Junit, agent-java, Mockito
- Moved project to Gradle
### Fixed
- Steps order
