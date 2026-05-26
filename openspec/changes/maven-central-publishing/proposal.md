## Why

当前三个 pom.xml 各自独立声明 `version` 和 `properties`，版本号分散在多处。发布到 Maven Central 时需要手动同步，且缺乏发布必需的元数据（scm、licenses、developers 等）。

## What Changes

- `state-machine-parent`（root pom）作为聚合 POM，添加 Maven Central 发布所需的配置（scm、licenses、developers、distributionManagement 等），但**不发布**到 Maven Central
- `state-machine-boot-starter` **不继承** parent，保留独立的 `groupId`、`version` 声明，自身添加发布插件配置
- `state-machine-demo` 作为子模块通过 `<parent>` 继承 root pom，设置 `maven.deploy.skip=true`（不发布）
- 公共属性（`java.version`、`spring-boot.version` 等）保留在 parent pom 中，demo 通过继承获取，starter 自行声明

## Capabilities

### New Capabilities

- `maven-publishing`: Maven Central 发布基础设施（starter 独立发布配置、demo 跳过部署）
- `version-management`: 通过 Maven 父子 POM 聚合管理 demo 的构建属性，starter 独立管理自身版本

### Modified Capabilities

<!-- 无现有规格变更 -->

## Impact

- starter pom.xml 新增发布插件配置（maven-source-plugin、maven-javadoc-plugin、maven-gpg-plugin）
- starter pom.xml 新增 Maven Central 元数据（licenses、developers、scm、distributionManagement）
- demo pom.xml 改为 `<parent>` 引用 root pom
- root pom.xml 新增 distributionManagement 和 release profile
- 不影响编译产物、运行时行为或 API
