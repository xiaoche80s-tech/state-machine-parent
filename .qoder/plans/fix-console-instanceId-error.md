# 修复 Web 控制台 instanceId 渲染错误

## Context

Vue 控制台在渲染时报 `TypeError: Cannot read properties of undefined (reading 'instanceId')`。同时发现 `confirmResume` 和 `confirmAdvance` 函数中存在逻辑 Bug：关闭 modal 后 form 被重置，导致后续用空 instanceId 调用 API。

## Task 1: 修复模板中的 instanceId 访问安全性

**文件**: `state-machine-boot-starter/src/main/resources/console/index.html`

- 第 714 行: `{{ resumeForm.instanceId }}` → `{{ resumeForm?.instanceId }}`
- 第 775 行: `{{ advanceForm.instanceId }}` → `{{ advanceForm?.instanceId }}`

同样检查 `resumeForm.currentState`、`advanceForm.currentState` 等访问是否也需要加可选链。

## Task 2: 修复 confirmResume 中 instanceId 丢失 Bug

**文件**: `state-machine-boot-starter/src/main/resources/console/js/app.js`

`confirmResume` 函数（约第 344 行）：`closeResumeModal()` 会重置 `resumeForm.value`，导致后续 `resumeForm.value.instanceId` 为空字符串。

修复：在调用 `closeResumeModal()` 前先保存 instanceId。

```js
// 修复前
closeResumeModal();
if (currentView.value === 'instance') {
    const detail = await API.getInstanceDetail(resumeForm.value.instanceId); // 空字符串!

// 修复后
const savedInstanceId = resumeForm.value.instanceId;
closeResumeModal();
if (currentView.value === 'instance') {
    const detail = await API.getInstanceDetail(savedInstanceId);
```

## Task 3: 修复 confirmAdvance 中 instanceId 丢失 Bug

**文件**: `state-machine-boot-starter/src/main/resources/console/js/app.js`

`confirmAdvance` 函数（约第 587 行）：同样的问题。

```js
// 修复前
closeAdvanceModal();
if (currentView.value === 'instance') {
    const detail = await API.getInstanceDetail(advanceForm.value.instanceId); // 空字符串!

// 修复后
const savedInstanceId = advanceForm.value.instanceId;
closeAdvanceModal();
if (currentView.value === 'instance') {
    const detail = await API.getInstanceDetail(savedInstanceId);
```

## Task 4: 同步 target 目录并验证

运行 `mvn compile` 确保 target/classes 中的资源文件同步更新。

## 验证

1. 启动 demo 应用
2. 打开 Web 控制台，确认不再报 `instanceId` 错误
3. 测试恢复（resume）功能：恢复挂起实例后，确认实例详情页能正确刷新
4. 测试推进（advance）功能：推进失败实例后，确认实例详情页能正确刷新
