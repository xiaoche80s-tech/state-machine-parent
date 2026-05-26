## ADDED Requirements

### Requirement: Demo module inherits version and properties from parent
`state-machine-demo` SHALL declare `<parent>` pointing to `state-machine-parent` and inherit `version`, `groupId`, and build properties through Maven inheritance.

#### Scenario: Demo inherits version from parent
- **WHEN** running `mvn help:evaluate -Dexpression=project.version -q -DforceStdout` in demo directory
- **THEN** the output matches the version declared in root `pom.xml`

#### Scenario: Demo inherits shared properties
- **WHEN** checking demo `pom.xml`
- **THEN** it does not declare `java.version`, `spring-boot.version`, or `project.build.sourceEncoding`

### Requirement: Starter manages its own version independently
`state-machine-boot-starter` SHALL declare its own `groupId`, `artifactId`, and `version` without a `<parent>` declaration.

#### Scenario: Starter has independent version
- **WHEN** checking `state-machine-boot-starter/pom.xml`
- **THEN** it contains its own `<version>` element and no `<parent>` element

#### Scenario: Starter defines its own properties
- **WHEN** checking `state-machine-boot-starter/pom.xml`
- **THEN** it declares its own `java.version` and `spring-boot.version` properties

### Requirement: DependencyManagement in parent inherited by demo
Spring Boot BOM `dependencyManagement` SHALL be declared in the root `pom.xml`. Demo inherits dependency versions; starter manages its own.

#### Scenario: Spring Boot BOM in parent
- **WHEN** checking root `pom.xml`
- **THEN** `spring-boot-dependencies` BOM is declared in `<dependencyManagement>`

#### Scenario: Demo does not repeat dependencyManagement
- **WHEN** checking demo `pom.xml`
- **THEN** it does not declare its own `<dependencyManagement>` block

### Requirement: Full build succeeds with unified version management
The project SHALL compile, test, and package successfully with the parent-child structure (demo inherits, starter independent).

#### Scenario: Full build passes
- **WHEN** running `mvn clean install` from project root
- **THEN** all modules build without errors
