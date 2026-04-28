## 1. 主时间线添加 ROUTE 快照渲染

- [x] 1.1 在 `index.html` 主时间线（`v-for="instanceDetail.snapshots"`）中添加 `v-if` / `v-else` 分支，区分 NODE 和 ROUTE 快照
- [x] 1.2 为 ROUTE 快照创建紧凑渲染模板，显示 `stateName → toState` 箭头，不展示 IN/OUT JSON 块
- [x] 1.3 在 `app.js` 中添加 `parseRouteOutput(output)` 辅助函数，解析 JSON 包裹的目标状态名，解析失败回退原始值

## 2. Drawer 时间线添加 ROUTE 快照渲染

- [x] 2.1 在 `index.html` Drawer 时间线（`v-for="drawerSnapshots"`）中添加与主时间线相同的 NODE/ROUTE 分支逻辑
- [x] 2.2 为 Drawer 中 ROUTE 快照创建紧凑渲染模板

## 3. CSS 样式

- [x] 3.1 为 ROUTE 快照添加独立样式类（如 `.timeline-item.route-type`），包括不同的 marker 颜色/图标和紧凑布局
- [x] 3.2 添加状态转换箭头样式（如 `→` 符号的 CSS 样式）

## 4. 验证

- [x] 4.1 启动 demo 应用或运行集成测试，确认 ROUTE 快照在时间线中正确显示
- [x] 4.2 验证 NODE 快照展示不受影响
- [x] 4.3 验证 Drawer 中 ROUTE 快照显示正确
