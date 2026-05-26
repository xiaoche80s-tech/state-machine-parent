## Context

当前项目是多模块 Maven 项目（parent、starter、demo），三个 pom.xml 各自独立声明 `version`、`groupId`、`properties` 和 `dependencyManagement`，没有 `<parent>` 继承关系。

发布目标：
- 仅 `state-machine-boot-starter` 发布到 Maven Central
- `state-machine-demo` 不发布，仅作为示例
- `state-machine-parent` 作为聚合 POM 不发布
- JDK 8 版本在 `jdk8` 分支维护，JDK 17 版本在 `jdk17`/`main` 分支维护，两个分支独立发布

## Goals / Non-Goals

**Goals:**
- starter 独立发布到 Maven Central，不依赖 parent pom 的发布
- demo 通过 `<parent>` 继承 root pom，复用公共属性和依赖管理
- demo 被排除在发布范围之外
- 保持本地 `mvn clean install` 行为不变

**Non-Goals:**
- 不涉及代码逻辑变更
- 不引入新依赖或插件（除 Maven Central 发布必需的 gpg/source/javadoc 插件）
- 不做版本升级
- parent pom 不发布

## Decisions

### 决策 1：starter 独立发布，不继承 parent

`state-machine-boot-starter/pom.xml` 保留自身的 `groupId:cn.chedejun`、`artifactId:state-machine-boot-starter`、`version:1.0.0-SNAPSHOT` 声明。不添加 `<parent>` 引用。

理由：用户引入 starter 时 Maven 不会尝试解析 parent pom，避免不必要的依赖下载失败。

### 决策 2：starter 自带发布元数据和插件

starter pom.xml 直接添加 Maven Central 必需的元数据（licenses、developers、scm、distributionManagement）和发布插件配置（maven-source-plugin、maven-javadoc-plugin、maven-gpg-plugin），使用 `release` profile 隔离。

### 决策 3：demo 通过 `<parent>` 继承 root pom

`state-machine-demo/pom.xml` 通过 `<parent>` 引用 root pom，继承 `java.version`、`spring-boot.version` 等公共属性和 `dependencyManagement`。添加 `<maven.deploy.skip>true</maven.deploy.skip>` 阻止发布。

### 决策 4：root pom 做聚合和依赖管理

root `pom.xml` 保留 `modules`、`properties`、`dependencyManagement`，作为本地构建的聚合 POM。不发布到 Maven Central。

### 决策 5：双分支各自独立版本

`jdk8` 分支和 `jdk17`/`main` 分支各自维护 starter pom.xml 中的 `version`。两个分支版本可以不同。

## Risks / Trade-offs

- [版本号需要在两处修改] → root pom 和 starter pom 各有一个 `version`，每次版本变更需要改两处。这是选择方案 A 的代价，但只有两处，不易遗漏。
- [javadoc 构建失败阻塞发布] → 所有 public API 必须有 javadoc，否则 `maven-javadoc-plugin` 会报错。
- [GPG 签名密钥] → 发布前需要配置 GPG 密钥，这是 Maven Central 的硬性要求。
- [demo 仍被打包] → `mvn clean install` 仍会编译 demo，只是不部署。这是预期行为。
