# 实例概览增强设计

## 概述

实例概览页面（机器详情页的实例列表）当前使用 div 卡片行式布局，筛选仅支持按状态过滤。本次增强增加业务ID和流程实例ID筛选，并将布局改为标准表格平铺展示。

## 涉及范围

- 后端：`ConsoleController` 和 `InstanceRepository`
- 前端：`index.html`、`app.js`、`style.css`
- 数据库：无 DDL 变更（`business_id` 字段已存在）

## 后端设计

### API 接口扩展

`GET /api/machines/{name}/instances` 新增两个可选查询参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| businessId | String | 否 | 按业务ID精确匹配 |
| instanceId | String | 否 | 按流程实例ID精确匹配 |

**查询模式**：所有新增参数采用 `(col=? OR ? IS NULL)` 模式，不传参时不限制结果。与现有 `status` 参数可任意组合。

### ConsoleController

在 `getInstances` 方法中新增参数接收：
```java
@RequestParam(required = false) String businessId,
@RequestParam(required = false) String instanceId
```

根据是否有 filter 参数调用不同的 Repository 方法。

### InstanceRepository

新增两个方法：
- `findByMachineNameWithFilters(machineName, status, businessId, instanceId, offset, limit)`
- `countByMachineNameWithFilters(machineName, status, businessId, instanceId)`

使用 `StringBuilder` 动态拼接 `WHERE` 条件，参数按出现顺序收集到 `List<Object>`，通过 `jdbcTemplate.query()` / `queryForObject()` 执行。

### 查询逻辑

SQL 动态条件构建规则：
- `WHERE machine_name = ?`（固定条件）
- 如果 `status` 非空：`AND status = ?`
- 如果 `businessId` 非空：`AND business_id = ?`
- 如果 `instanceId` 非空：`AND id = ?`
- `ORDER BY created_at DESC`
- `LIMIT ? OFFSET ?`（仅查询方法）

## 前端设计

### 筛选区

在机器详情页实例列表的 filter-group 区域（状态 chips 后）新增两个文本输入框：

```html
<input type="text" placeholder="业务ID" class="filter-input" v-model="machineFilterBusinessId" @keyup.enter="machinePage=0; loadMachineInstances()" />
<input type="text" placeholder="流程实例ID" class="filter-input" v-model="machineFilterInstanceId" @keyup.enter="machinePage=0; loadMachineInstances()" />
```

**交互**：
- 回车触发查询并重置分页
- 改变筛选值后自动触发查询（或提供搜索按钮）

### 表格布局

将 `.instance-list` 中的 div 卡片替换为 `<table>`：

**列定义**（7列）：

| 列 | 字段 | 备注 |
|----|------|------|
| 实例 ID | `i.id` | 等宽字体 |
| 状态机 | `i.machineName` | - |
| 业务 ID | `i.businessId` | 空显示 `-` |
| 重试 | `i.retryCount` | - |
| 状态 | `i.status` | status-pill 样式 |
| 错误信息 | `i.errorMessage` | 红色文字，无错误显示 `—` |
| 时间 | `i.createdAt` | relativeTime 格式 |

**交互**：
- 点击整行打开详情抽屉（`@click="openDrawer(i.id)"`）
- hover 高亮行
- `.table-wrap` 设置 `overflow-x: auto` 支持水平滚动

### API 调用

`loadMachineInstances()` 将新增的两个 filter 变量传递给 `API.getInstances()`。

`api.js` 的 `getInstances` 方法通过 `URLSearchParams` 追加 `businessId` 和 `instanceId` 参数。

## 样式

新增 CSS 类：
- `.table-wrap`：容器，支持水平滚动
- `.instance-table`：表格基础样式，border-collapse
- `.instance-table thead`：固定表头背景色
- `.instance-table tbody tr:hover`：hover 高亮
- `.instance-table td.mono`：等宽字体（ID 列）
- `.instance-table td.error`：红色错误文本

其余颜色、状态 pill、间距变量复用现有 design tokens。

## 空状态

当 `machineInstances.length === 0` 时显示空状态提示，保持现有 `.empty-state-sm` 样式。

## 回归注意

- `Instances View`（`#/instances` 路由）不受影响
- `getInstance` API（不带机器名过滤的全局实例查询）不受影响
- 详情抽屉（drawer）不受影响
