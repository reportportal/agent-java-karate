# Changelog

## [Unreleased]
### Removed
- Shutdown hook register on supplied Launch, by @HardNorth

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
