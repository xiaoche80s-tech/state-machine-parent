## Why

执行快照时间线（Snapshot Timeline）的当前布局存在视觉混乱问题：
- IN/OUT JSON 块占据大量垂直空间，且默认展开显示截断的 JSON 内容
- 路由类型（ROUTE）快照与节点类型（NODE）快照的视觉层级不统一
- 错误信息展示位置不够醒目，用户难以快速定位失败原因
- 连接线在 ROUTE 类型快照处断开，时间线视觉连贯性被破坏

## What Changes

- **重构 IN/OUT 块布局**：默认折叠显示，仅展示一行摘要（字段数量/类型），点击后展开完整 JSON
- **统一路由与节点快照的视觉层级**：ROUTE 类型快照不再使用全宽背景高亮，改为与 NODE 类型一致的紧凑行内展示
- **优化错误信息展示**：错误信息前置到 timeline-header 区域，红色边框高亮，无需滚动即可看到
- **修复 ROUTE 快照的连接线断裂**：确保所有类型快照的时间线连接点位置一致
- **简化 IN/OUT 块头部**：移除冗余的"预览/复制"按钮，改为行内小图标

## Capabilities

### New Capabilities
<!-- 无新增功能，纯 UI 布局调整 -->

### Modified Capabilities
<!-- 不涉及需求层面的变更，仅前端展示层优化 -->

## Impact

- **前端模板**：`state-machine-boot-starter/src/main/resources/static/statemachine/index.html`（时间线 HTML 结构）
- **前端样式**：`state-machine-boot-starter/src/main/resources/static/statemachine/css/style.css`（timeline/CSS 约 120 行相关样式）
- **前端逻辑**：`state-machine-boot-starter/src/main/resources/static/statemachine/js/app.js`（IO 块展开/折叠状态管理）
- **无后端变更**：数据模型和 API 不变
- **无破坏性变更**：仅视觉层调整
