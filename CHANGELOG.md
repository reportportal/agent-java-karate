# Changelog

## [Unreleased]
### Changed
- Karate dependency marked as `compileOnly` to force users specify their own version of Karate, by @HardNorth
- Client version updated on [5.2.5](https://github.com/reportportal/client-java/releases/tag/5.2.5), by @HardNorth

## [5.1.0]
### Added
- Implemented new feature: display last error log in scenario description, by @vrymar
- Implemented unit tests for the new feature, by @vrymar
### Changed
- Improved dependencies references in build.gradle, by @vrymar

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
