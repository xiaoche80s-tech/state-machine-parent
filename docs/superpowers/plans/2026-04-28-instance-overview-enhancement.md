# 实例概览增强实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实例概览页面增加业务ID和流程实例ID筛选，并将卡片布局改为表格平铺展示。

**Architecture:** 后端 Repository 新增动态 SQL 拼接方法支持多条件过滤，Controller 扩展参数接收；前端增加输入框筛选 + table 布局替代 div 卡片。

**Tech Stack:** Java 17, Spring JDBC, Vue3, CSS

---

### Task 1: InstanceRepository 新增多条件查询方法

**Files:**
- Modify: `state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/persistence/InstanceRepository.java:71-82`
- Test: `state-machine-boot-starter/src/test/java/cn/chedejun/statemachine/persistence/InstanceRepositoryTest.java`

在现有 `findByMachineNameAndStatus` 和 `countByMachineNameAndStatus` 之后新增两个方法，使用 StringBuilder 动态拼接 WHERE 条件。

- [ ] **Step 1: 写失败的测试**

```java
@Test void findByMachineNameWithFilters_filtersByBusinessId() {
    repository.create(new InstanceRepository.CreateInstanceParams("def-1", "order-process", "v1", "s1", "ORD-100"));
    repository.create(new InstanceRepository.CreateInstanceParams("def-2", "order-process", "v1", "s1", "ORD-200"));
    var results = repository.findByMachineNameWithFilters("order-process", null, "ORD-100", null, 0, 10);
    assertEquals(1, results.size());
    assertEquals("ORD-100", results.get(0).businessId());
}

@Test void findByMachineNameWithFilters_filtersByInstanceId() {
    repository.create(new InstanceRepository.CreateInstanceParams("def-1", "order-process", "v1", "s1", "ORD-100"));
    repository.create(new InstanceRepository.CreateInstanceParams("def-2", "order-process", "v1", "s1", "ORD-200"));
    var all = repository.findByMachineName("order-process", 0, 10);
    String id = all.get(0).id();
    var results = repository.findByMachineNameWithFilters("order-process", null, null, id, 0, 10);
    assertEquals(1, results.size());
    assertEquals(id, results.get(0).id());
}

@Test void findByMachineNameWithFilters_filtersByStatusAndBusinessId() {
    String id1 = repository.create(new InstanceRepository.CreateInstanceParams("def-1", "order-process", "v1", "s1", "ORD-100"));
    repository.create(new InstanceRepository.CreateInstanceParams("def-2", "order-process", "v1", "s1", "ORD-200"));
    repository.updateState(id1, "s2", "COMPLETED", null);
    var results = repository.findByMachineNameWithFilters("order-process", "COMPLETED", "ORD-100", null, 0, 10);
    assertEquals(1, results.size());
}

@Test void countByMachineNameWithFilters_countsCorrectly() {
    repository.create(new InstanceRepository.CreateInstanceParams("def-1", "order-process", "v1", "s1", "ORD-100"));
    repository.create(new InstanceRepository.CreateInstanceParams("def-2", "order-process", "v1", "s1", "ORD-100"));
    repository.create(new InstanceRepository.CreateInstanceParams("def-3", "order-process", "v1", "s1", "ORD-200"));
    assertEquals(3L, repository.countByMachineNameWithFilters("order-process", null, null, null));
    assertEquals(2L, repository.countByMachineNameWithFilters("order-process", null, "ORD-100", null));
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd state-machine-boot-starter && mvn test -Dtest=InstanceRepositoryTest -v`
Expected: FAIL with "找不到符号" (方法未定义)

- [ ] **Step 3: 实现 `findByMachineNameWithFilters` 方法**

在 `InstanceRepository.java` 中，在 `findByMachineNameAndStatus` 方法后（第 82 行后）添加：

```java
public List<InstanceRecord> findByMachineNameWithFilters(String machineName, String status,
        String businessId, String instanceId, int offset, int limit) {
    StringBuilder sql = new StringBuilder("SELECT * FROM state_machine_instances WHERE machine_name = ?");
    List<Object> params = new ArrayList<>();
    params.add(machineName);
    if (status != null && !status.isEmpty()) {
        sql.append(" AND status = ?");
        params.add(status);
    }
    if (businessId != null && !businessId.isEmpty()) {
        sql.append(" AND business_id = ?");
        params.add(businessId);
    }
    if (instanceId != null && !instanceId.isEmpty()) {
        sql.append(" AND id = ?");
        params.add(instanceId);
    }
    sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
    params.add(limit);
    params.add(offset);
    return jdbcTemplate.query(sql.toString(), rowMapper(), params.toArray());
}
```

- [ ] **Step 4: 实现 `countByMachineNameWithFilters` 方法**

紧接上一个方法之后添加：

```java
public long countByMachineNameWithFilters(String machineName, String status,
        String businessId, String instanceId) {
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM state_machine_instances WHERE machine_name = ?");
    List<Object> params = new ArrayList<>();
    params.add(machineName);
    if (status != null && !status.isEmpty()) {
        sql.append(" AND status = ?");
        params.add(status);
    }
    if (businessId != null && !businessId.isEmpty()) {
        sql.append(" AND business_id = ?");
        params.add(businessId);
    }
    if (instanceId != null && !instanceId.isEmpty()) {
        sql.append(" AND id = ?");
        params.add(instanceId);
    }
    return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
}
```

- [ ] **Step 5: 添加 import**

在文件顶部 `import` 区添加 `java.util.ArrayList`（如果尚未导入）。

现有 import 已有 `java.util.List`、`java.util.Optional`、`java.util.UUID`，需加 `java.util.ArrayList`。

- [ ] **Step 6: 运行测试验证通过**

Run: `cd state-machine-boot-starter && mvn test -Dtest=InstanceRepositoryTest -v`
Expected: PASS (all tests)

- [ ] **Step 7: Commit**

```bash
git add state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/persistence/InstanceRepository.java
git add state-machine-boot-starter/src/test/java/cn/chedejun/statemachine/persistence/InstanceRepositoryTest.java
git commit -m "feat: InstanceRepository 支持多条件过滤查询

新增 findByMachineNameWithFilters 和 countByMachineNameWithFilters 方法，
支持按 status、businessId、instanceId 动态组合过滤。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 2: ConsoleController 接收新增参数

**Files:**
- Modify: `state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/management/ConsoleController.java:63-77`

- [ ] **Step 1: 修改 `getInstances` 方法签名**

将第 64-67 行修改为：

```java
@GetMapping("/api/machines/{name}/instances") @ResponseBody
public Map<String, Object> getInstances(@PathVariable String name,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String businessId,
        @RequestParam(required = false) String instanceId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
```

- [ ] **Step 2: 修改查询逻辑**

将第 68-76 行替换为：

```java
boolean hasFilters = status != null || businessId != null || instanceId != null;
var records = hasFilters
    ? instanceRepository.findByMachineNameWithFilters(name, status, businessId, instanceId, page * size, size)
    : instanceRepository.findByMachineName(name, page * size, size);
long total = hasFilters
    ? instanceRepository.countByMachineNameWithFilters(name, status, businessId, instanceId)
    : instanceRepository.countByMachineNameAndStatus(name, null);
var dtos = records.stream().map(r -> new InstanceDTO(r.id(), r.machineName(), r.definitionVersion(),
    r.currentState(), r.status(), r.businessId(), r.retryCount(), r.errorMessage(), r.createdAt(), r.updatedAt())).toList();
return Map.of("instances", dtos, "total", total, "page", page, "size", size);
```

- [ ] **Step 3: 构建验证**

Run: `cd state-machine-boot-starter && mvn compile -q`
Expected: BUILD SUCCESS, no compilation errors

- [ ] **Step 4: Commit**

```bash
git add state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/management/ConsoleController.java
git commit -m "feat: ConsoleController 支持 businessId 和 instanceId 筛选参数

getInstances 接口新增可选参数，根据是否有 filter 参数调用不同的
Repository 方法。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: 前端 API 层传递筛选参数

**Files:**
- Modify: `state-machine-boot-starter/src/main/resources/static/statemachine/js/api.js:4-8`

- [ ] **Step 1: 修改 `getInstances` 方法**

将 `api.js` 第 4-8 行替换为：

```javascript
async getInstances(name, status = '', businessId = '', instanceId = '', page = 0, size = 20) {
    const p = new URLSearchParams({ page, size });
    if (status) p.set('status', status);
    if (businessId) p.set('businessId', businessId);
    if (instanceId) p.set('instanceId', instanceId);
    return (await fetch(`/statemachine/api/machines/${name}/instances?${p}`)).json();
},
```

- [ ] **Step 2: Commit**

```bash
git add state-machine-boot-starter/src/main/resources/static/statemachine/js/api.js
git commit -m "feat: API.getInstances 支持 businessId 和 instanceId 参数

通过 URLSearchParams 追加可选筛选参数。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 4: 前端筛选区增加输入框

**Files:**
- Modify: `state-machine-boot-starter/src/main/resources/static/statemachine/index.html` (filter-group area, around line 230-238)
- Modify: `state-machine-boot-starter/src/main/resources/static/statemachine/js/app.js` (state variables + loadMachineInstances)
- Modify: `state-machine-boot-starter/src/main/resources/static/statemachine/css/style.css` (filter-input styles)

- [ ] **Step 1: 在 filter-group 后添加输入框（HTML）**

在 `index.html` 第 237 行 `</div>`（filter-chip 的最后一个 button 后）后面、`</div>`（filter-group 的闭合标签）之前，添加：

```html
                            <button class="filter-chip filter-chip--blue" :class="{active: machineFilterStatus==='SUSPENDED'}" @click="machineFilterStatus='SUSPENDED'; machinePage=0; loadMachineInstances()">已挂起</button>
                            <input type="text" class="filter-input" placeholder="业务ID" v-model="machineFilterBusinessId" @keyup.enter="machinePage=0; loadMachineInstances()" />
                            <input type="text" class="filter-input" placeholder="流程实例ID" v-model="machineFilterInstanceId" @keyup.enter="machinePage=0; loadMachineInstances()" />
```

同时为两个输入框右侧各加一个清除按钮（当有值时显示 ×）：

```html
                            <input type="text" class="filter-input" placeholder="业务ID" v-model="machineFilterBusinessId" @keyup.enter="machinePage=0; loadMachineInstances()" />
                            <button v-if="machineFilterBusinessId" class="filter-clear" @click="machineFilterBusinessId=''; machinePage=0; loadMachineInstances()">✕</button>
                            <input type="text" class="filter-input" placeholder="流程实例ID" v-model="machineFilterInstanceId" @keyup.enter="machinePage=0; loadMachineInstances()" />
                            <button v-if="machineFilterInstanceId" class="filter-clear" @click="machineFilterInstanceId=''; machinePage=0; loadMachineInstances()">✕</button>
```

- [ ] **Step 2: 新增状态变量（app.js）**

在 `app.js` 第 40 行 `const machineFilterStatus = ref('');` 后面添加：

```javascript
        const machineFilterBusinessId = ref('');
        const machineFilterInstanceId = ref('');
```

- [ ] **Step 3: 修改 `loadMachineInstances` 传递参数（app.js）**

将 `app.js` 第 424-429 行的 `loadMachineInstances` 函数替换为：

```javascript
async function loadMachineInstances() {
    const resp = await API.getInstances(machineName.value, machineFilterStatus.value,
        machineFilterBusinessId.value, machineFilterInstanceId.value,
        machinePage.value, machinePageSize.value);
    machineInstances.value = resp.instances;
    machineTotalPages.value = Math.ceil(resp.total / machinePageSize.value);
    machineTotalElements.value = resp.total;
}
```

- [ ] **Step 4: 将新变量导出到 return（app.js）**

在 `app.js` 第 804 行 return 中，在 `machineFilterStatus` 后添加：

```javascript
            machinePage, machinePageSize, machineTotalPages, machineTotalElements,
            machineFilterBusinessId, machineFilterInstanceId,
```

- [ ] **Step 5: 添加 CSS 样式（style.css）**

在 `style.css` 第 368 行（`.filter-chip--red.active` 后面）添加：

```css

/* ===== Filter Input ===== */
.filter-input {
    font-size: 0.78rem;
    padding: 4px 10px;
    border-radius: 20px;
    border: 1px solid var(--gray-200);
    background: var(--surface);
    color: var(--gray-700);
    font-family: var(--mono);
    width: 140px;
    transition: all var(--transition);
}
.filter-input:focus {
    outline: none;
    border-color: var(--primary);
    box-shadow: 0 0 0 2px var(--primary-bg);
}
.filter-input::placeholder {
    color: var(--gray-400);
    font-family: var(--sans);
}
.filter-clear {
    font-size: 0.72rem;
    padding: 4px 8px;
    border-radius: 20px;
    border: 1px solid var(--gray-200);
    background: var(--surface);
    color: var(--gray-400);
    cursor: pointer;
    transition: all var(--transition);
    line-height: 1;
}
.filter-clear:hover {
    border-color: var(--red);
    color: var(--red);
}
```

- [ ] **Step 6: 启动 demo 验证前端效果**

Run: `cd state-machine-demo && mvn spring-boot:run`
在浏览器打开 `http://localhost:8080/statemachine/`，导航到某个机器的实例列表，验证：
1. 筛选区出现两个输入框
2. 输入值后回车触发查询
3. 输入框有值时显示 × 清除按钮

- [ ] **Step 7: Commit**

```bash
git add state-machine-boot-starter/src/main/resources/static/statemachine/index.html
git add state-machine-boot-starter/src/main/resources/static/statemachine/js/app.js
git add state-machine-boot-starter/src/main/resources/static/statemachine/css/style.css
git commit -m "feat: 筛选区增加业务ID和流程实例ID输入框

在状态过滤 chips 后新增两个文本输入框，支持回车搜索和一键清除。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 5: 实例列表改为表格平铺布局

**Files:**
- Modify: `state-machine-boot-starter/src/main/resources/static/statemachine/index.html` (instance-list area, lines 240-266)
- Modify: `state-machine-boot-starter/src/main/resources/static/statemachine/css/style.css` (table styles)

- [ ] **Step 1: 替换 instance-list 为 table（HTML）**

将 `index.html` 第 240-266 行的 `.instance-list` div 及其内部内容替换为：

```html
                    <div class="table-wrap">
                        <table class="instance-table">
                            <thead>
                                <tr>
                                    <th>实例 ID</th>
                                    <th>状态机</th>
                                    <th>业务 ID</th>
                                    <th>重试</th>
                                    <th>状态</th>
                                    <th>错误信息</th>
                                    <th>时间</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr v-for="i in machineInstances" :key="i.id" @click="openDrawer(i.id)" class="instance-row">
                                    <td class="td-mono">{{ i.id }}</td>
                                    <td>{{ i.machineName }}</td>
                                    <td class="td-mono">{{ i.businessId || '-' }}</td>
                                    <td>{{ i.retryCount }}</td>
                                    <td><span class="status-pill" :class="'status-' + i.status">{{ statusLabel(i.status) }}</span></td>
                                    <td v-if="i.errorMessage" class="td-error">{{ i.errorMessage }}</td>
                                    <td v-else>—</td>
                                    <td>{{ relativeTime(i.createdAt) }}</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
```

注意：保留原有的 `v-if="!machineInstances.length"` 空状态检查，将其放在 `</div><!-- .table-wrap -->` 之后、`<div class="pagination">` 之前：

```html
                    </div>
                    <div v-if="!machineInstances.length" class="empty-state-sm">
                        <p>{{ machineFilterStatus || machineFilterBusinessId || machineFilterInstanceId ? '没有符合条件的实例' : '暂无实例' }}</p>
                    </div>
```

- [ ] **Step 2: 添加表格 CSS（style.css）**

在 `style.css` 文件末尾（第 1139 行之后）添加：

```css

/* ===== Instance Table ===== */
.table-wrap {
    overflow-x: auto;
}

.instance-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.82rem;
}

.instance-table thead {
    background: var(--gray-50);
    position: sticky;
    top: 0;
}

.instance-table th {
    padding: 8px 12px;
    text-align: left;
    font-weight: 600;
    color: var(--gray-500);
    font-size: 0.72rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    border-bottom: 2px solid var(--gray-200);
    white-space: nowrap;
}

.instance-table td {
    padding: 8px 12px;
    border-bottom: 1px solid var(--gray-100);
    vertical-align: middle;
}

.instance-row {
    cursor: pointer;
    transition: background var(--transition);
}
.instance-row:hover {
    background: var(--gray-50);
}

.td-mono {
    font-family: var(--mono);
    font-size: 0.8rem;
}

.td-error {
    color: var(--red);
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
```

- [ ] **Step 3: 启动 demo 验证表格效果**

Run: `cd state-machine-demo && mvn spring-boot:run`
在浏览器验证：
1. 实例列表以表格形式展示
2. 7 列完整显示
3. 行 hover 高亮
4. 点击行打开详情抽屉
5. 列过多时支持水平滚动
6. 无数据时显示空状态

- [ ] **Step 4: Commit**

```bash
git add state-machine-boot-starter/src/main/resources/static/statemachine/index.html
git add state-machine-boot-starter/src/main/resources/static/statemachine/css/style.css
git commit -m "feat: 实例列表改为表格平铺布局

将 div 卡片行式布局替换为标准 HTML table，支持水平滚动和 hover 高亮。
7 列：实例 ID、状态机、业务 ID、重试、状态、错误信息、时间。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Spec Coverage Check

| Spec Requirement | Task |
|-----------------|------|
| API 新增 businessId/instanceId 参数 | Task 2 |
| Repository 动态 SQL 拼接 | Task 1 |
| 筛选区两个输入框 | Task 4 |
| 表格 7 列布局 | Task 5 |
| 水平滚动 | Task 5 (CSS .table-wrap overflow-x) |
| 空状态处理 | Task 5 (v-if 空检查) |
| API 参数传递 | Task 3 |
| 不影响 #/instances 路由 | 各 Task 仅改机器详情页，未动 global instances view |
| 详情抽屉不受影响 | 各 Task 未改 drawer 相关代码 |

## Placeholder Scan

- 无 TBD/TODO
- 无 "handle edge cases" 等模糊描述
- 所有步骤包含完整代码
- 类型/方法名前后一致

## Type Consistency

- `machineFilterBusinessId` / `machineFilterInstanceId` 在 HTML (v-model)、JS (ref)、JS (return)、API (params) 中名称一致
- Repository 方法签名 `findByMachineNameWithFilters(String, String, String, String, int, int)` 在 Task 1 定义，Task 2 调用一致
- `countByMachineNameWithFilters(String, String, String, String)` 同上
