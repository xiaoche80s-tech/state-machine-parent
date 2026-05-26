## ADDED Requirements

### Requirement: Starter is independently publishable to Maven Central
`state-machine-boot-starter` SHALL contain all metadata and plugins required for Maven Central publication, without inheriting from a parent POM.

#### Scenario: Starter POM has required metadata
- **WHEN** checking `state-machine-boot-starter/pom.xml`
- **THEN** it contains `licenses`, `developers`, `scm`, and `distributionManagement` sections

#### Scenario: Starter artifact can be deployed
- **WHEN** running `mvn deploy -P release` in the `state-machine-boot-starter` directory
- **THEN** the starter jar is uploaded to Maven Central staging repository

### Requirement: Parent POM is not published
`state-machine-parent` (root pom) SHALL NOT be deployed to Maven Central. It serves only as a local build aggregator.

#### Scenario: Parent is skipped during deploy
- **WHEN** running `mvn deploy -P release` from project root
- **THEN** parent pom is not uploaded to any remote repository

#### Scenario: Demo module is excluded from publishing
- **WHEN** running `mvn deploy -P release` from project root
- **THEN** demo module is not uploaded (maven.deploy.skip=true)

### Requirement: Release plugins configured in starter under profile
Source jar, javadoc jar, and GPG signing SHALL be configured in the starter POM under a `release` profile that is not activated by default.

#### Scenario: Release profile not active by default
- **WHEN** running `mvn clean install`
- **THEN** GPG signing is not executed

#### Scenario: Release profile produces publishable artifacts
- **WHEN** running `mvn deploy -P release` in starter directory
- **THEN** source jars, javadoc jars, and GPG signatures are generated and deployed
