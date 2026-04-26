const { createApp, ref, computed, onMounted, nextTick } = Vue;

// TreeNode component for editable JSON tree
const TreeNode = {
    name: 'TreeNode',
    props: { node: Object, depth: Number },
    emits: ['update:modelValue'],
    template: `
        <div>
            <div class="tree-node" :class="{ 'tree-node--nested': depth > 0 }">
                <button v-if="node.children" class="tree-toggle" :class="{ expanded: expanded }" @click="expanded = !expanded">▶</button>
                <span v-else style="width:12px;display:inline-block"></span>
                <span class="tree-key">{{ node.key }}</span>
                <span class="tree-type">{{ node.type }}</span>
                <template v-if="node.children">
                    <span class="tree-type">({{ node.children.length }})</span>
                </template>
                <input v-else class="tree-input" v-model="node.value" :type="node.type === 'number' ? 'number' : 'text'" />
            </div>
            <template v-if="node.children && expanded">
                <tree-node v-for="(child, i) in node.children" :key="i" :node="child" :depth="depth + 1" />
            </template>
        </div>
    `,
    data() {
        return { expanded: true };
    }
};

createApp({
    components: { TreeNode },
    setup() {
        const currentView = ref('list');
        const machines = ref([]);
        const machineName = ref('');
        const versions = ref([]);
        const instances = ref([]);
        const filterStatus = ref('');
        const machineInstances = ref([]);
        const machineFilterStatus = ref('');
        const machinePage = ref(0);
        const machinePageSize = ref(10);
        const machineTotalPages = ref(0);
        const machineTotalElements = ref(0);
        const instanceDetail = ref({ instance: null, snapshots: [] });
        const expanded = ref({});
        const loading = ref(false);
        const drawerVisible = ref(false);
        const drawerInstance = ref(null);
        const drawerSnapshots = ref([]);

        // Stats
        const totalMachines = computed(() => machines.value.length);
        const totalRunning = computed(() => machines.value.reduce((s, m) => s + m.runningInstances, 0));
        const totalFailed = computed(() => machines.value.reduce((s, m) => s + m.failedInstances, 0));
        const currentMachineStats = computed(() => machines.value.find(m => m.name === machineName.value));

        function shortId(id) { return id ? id.substring(0, 8) + '\u2026' : ''; }
        function statusClass(s) { return s ? 'status-' + s : ''; }
        function statusLabel(s) {
            const map = { RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败', SUCCESS: '成功', SUSPENDED: '已挂起' };
            return map[s] || s;
        }
        function shortJson(s) { return s.length > 60 ? s.substring(0, 60) + '\u2026' : s; }
        function formatJson(s) { try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; } }
        function toggle(k) { expanded.value[k] = !expanded.value[k]; }

        const toastMsg = ref('');
        const toastVisible = ref(false);

        function showToast(msg) {
            toastMsg.value = msg;
            toastVisible.value = true;
            setTimeout(() => { toastVisible.value = false; }, 1500);
        }

        // Resume modal
        const resumeModalVisible = ref(false);
        const resumeForm = ref({ instanceId: '', currentState: '', expectedState: '', contextJson: '' });
        const resumeLoading = ref(false);
        const resumeContextMode = ref('tree'); // 'text' or 'tree'
        const resumeContextTree = ref([]);
        const resumeContextError = ref('');

        function parseJsonTree(obj, path = '') {
            if (obj === null || obj === undefined) return [{ key: path || '(root)', type: 'null', value: 'null', editable: false }];
            const nodes = [];
            if (Array.isArray(obj)) {
                for (let i = 0; i < obj.length; i++) {
                    const childPath = path ? `${path}[${i}]` : `[${i}]`;
                    if (typeof obj[i] === 'object' && obj[i] !== null) {
                        nodes.push({ key: `[${i}]`, type: Array.isArray(obj[i]) ? 'array' : 'object', children: parseJsonTree(obj[i], childPath), editable: true });
                    } else {
                        nodes.push({ key: `[${i}]`, type: typeof obj[i], value: obj[i], path: childPath, editable: true });
                    }
                }
            } else if (typeof obj === 'object') {
                for (const k of Object.keys(obj)) {
                    const childPath = path ? `${path}.${k}` : k;
                    if (typeof obj[k] === 'object' && obj[k] !== null) {
                        nodes.push({ key: k, type: Array.isArray(obj[k]) ? 'array' : 'object', children: parseJsonTree(obj[k], childPath), editable: true });
                    } else {
                        nodes.push({ key: k, type: typeof obj[k], value: obj[k], path: childPath, editable: true });
                    }
                }
            } else {
                nodes.push({ key: path || '(root)', type: typeof obj, value: obj, editable: true });
            }
            return nodes;
        }

        function jsonTreeToObj(nodes) {
            const result = {};
            for (const node of nodes) {
                if (node.children) {
                    result[node.key] = jsonTreeToObj(node.children);
                } else {
                    let val = node.value;
                    if (node.type === 'number') val = Number(val);
                    else if (node.type === 'boolean') val = val === 'true' || val === true;
                    else if (node.type === 'null') val = null;
                    result[node.key] = val;
                }
            }
            // If all keys are numeric indices, return array
            const allNumeric = Object.keys(result).every(k => /^\d+$/.test(k));
            return allNumeric ? Object.values(result) : result;
        }

        function getTreeJson() {
            return JSON.stringify(jsonTreeToObj(resumeContextTree.value));
        }

        function onTreeNodeUpdate(idx, value) {
            resumeContextTree.value[idx].value = value;
        }

        function syncTreeToText() {
            try {
                resumeForm.value.contextJson = getTreeJson();
            } catch (e) { /* ignore */ }
        }

        async function openResumeModal(instanceId, currentState) {
            resumeForm.value = { instanceId, currentState, expectedState: currentState, contextJson: '' };
            resumeContextMode.value = 'tree';
            resumeContextError.value = '';
            resumeModalVisible.value = true;
            // Fetch instance detail to get last snapshot outputJson
            try {
                const detail = await API.getInstanceDetail(instanceId);
                if (detail.snapshots && detail.snapshots.length > 0) {
                    const lastSnap = detail.snapshots[detail.snapshots.length - 1];
                    const jsonStr = lastSnap.output || lastSnap.input || '{}';
                    resumeForm.value.contextJson = JSON.stringify(JSON.parse(jsonStr), null, 2);
                    resumeContextTree.value = parseJsonTree(JSON.parse(jsonStr));
                } else {
                    resumeForm.value.contextJson = '{}';
                    resumeContextTree.value = [];
                }
            } catch (e) {
                resumeForm.value.contextJson = '{}';
                resumeContextTree.value = [];
            }
        }

        function closeResumeModal() {
            resumeModalVisible.value = false;
            resumeForm.value = { instanceId: '', currentState: '', expectedState: '', contextJson: '' };
            resumeContextTree.value = [];
            resumeContextError.value = '';
        }

        async function confirmResume() {
            if (!resumeForm.value.expectedState) return;
            let contextJson = null;
            if (resumeContextMode.value === 'text') {
                try {
                    contextJson = JSON.stringify(JSON.parse(resumeForm.value.contextJson));
                } catch (e) {
                    resumeContextError.value = 'JSON 格式错误: ' + e.message;
                    return;
                }
            } else {
                try {
                    contextJson = getTreeJson();
                } catch (e) {
                    resumeContextError.value = '树形数据格式错误: ' + e.message;
                    return;
                }
            }
            resumeContextError.value = '';
            resumeLoading.value = true;
            try {
                const result = await API.resumeInstance(resumeForm.value.instanceId, resumeForm.value.expectedState, contextJson);
                if (result.success) {
                    showToast('已恢复执行');
                    closeResumeModal();
                    if (currentView.value === 'instance') {
                        const detail = await API.getInstanceDetail(resumeForm.value.instanceId);
                        if (detail.instance) {
                            instanceDetail.value = detail;
                            await nextTick();
                            const el = await waitForElement('#instance-mermaid');
                            if (el) renderInstanceMermaid();
                        }
                    } else if (currentView.value === 'machine') {
                        await loadMachineInstances();
                    }
                    if (drawerVisible.value && drawerInstance.value && drawerInstance.value.id) {
                        const data = await API.getInstanceDetail(drawerInstance.value.id);
                        if (data.instance) {
                            drawerInstance.value = data.instance;
                            drawerSnapshots.value = data.snapshots;
                            await nextTick();
                            const el = await waitForElement('#drawer-graph');
                            if (el) renderDrawerMermaid();
                        }
                    }
                } else {
                    showToast('恢复失败: ' + (result.error || '未知错误'));
                }
            } finally {
                resumeLoading.value = false;
            }
        }

        async function copyText(text) {
            try {
                await navigator.clipboard.writeText(text);
                showToast('已复制');
            } catch (e) {
                const ta = document.createElement('textarea');
                ta.value = text;
                document.body.appendChild(ta);
                ta.select();
                document.execCommand('copy');
                document.body.removeChild(ta);
                showToast('已复制');
            }
        }

        function relativeTime(dateStr) {
            const d = new Date(dateStr);
            const now = new Date();
            const diff = (now - d) / 1000;
            if (diff < 60) return '刚刚';
            if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前';
            if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前';
            return Math.floor(diff / 86400) + ' 天前';
        }

        function formatTime(dateStr) {
            if (!dateStr) return '';
            const d = new Date(dateStr);
            return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
        }

        function formatTimeShort(dateStr) {
            if (!dateStr) return '';
            const d = new Date(dateStr);
            return d.toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        }

        function getMachineHealth(m) {
            if (m.failedInstances > 0) return 'red';
            if (m.runningInstances > 0) return 'amber';
            return 'green';
        }

        function isMachineActive(name) {
            return machineName.value === name && (currentView.value === 'machine' || currentView.value === 'instances');
        }

        function goInstance(id) {
            location.hash = '/instance/' + id;
        }

        function getStateType(stateName, transitions) {
            const outs = transitions.filter(t => t.from === stateName);
            const ins = transitions.filter(t => t.to === stateName);
            if (!outs.length && ins.length) return '终态';
            if (!ins.length && outs.length) return '起始';
            if (!outs.length && !ins.length) return '独立';
            return '中间';
        }

        function getTransitionsFrom(stateName, transitions) {
            return transitions.filter(t => t.from === stateName);
        }

        async function loadMachines() { machines.value = await API.getMachines(); }

        async function waitForElement(selector, timeout = 2000) {
            const start = Date.now();
            while (Date.now() - start < timeout) {
                const el = document.querySelector(selector);
                if (el) return el;
                await new Promise(r => requestAnimationFrame(r));
            }
            return document.querySelector(selector);
        }

        async function loadInstanceDetail(id) {
            currentView.value = 'instance';
            instanceDetail.value = await API.getInstanceDetail(id);
            await nextTick();
            const el = await waitForElement('#instance-mermaid');
            if (el) renderInstanceMermaid();
        }

        async function loadMachineInstances() {
            const resp = await API.getInstances(machineName.value, machineFilterStatus.value, machinePage.value, machinePageSize.value);
            machineInstances.value = resp.instances;
            machineTotalPages.value = Math.ceil(resp.total / machinePageSize.value);
            machineTotalElements.value = resp.total;
        }

        async function loadMachineDetail(name) {
            machineName.value = name;
            currentView.value = 'machine';
            machineFilterStatus.value = '';
            machinePage.value = 0;
            if (!machines.value.length) await loadMachines();
            versions.value = await API.getVersions(name);
            await loadMachineInstances();
            await nextTick();
            const el = await waitForElement('.mermaid-wrap');
            if (el) renderMermaid();
        }

        async function loadInstances() {
            machineName.value = getMachineFromHash();
            currentView.value = 'instances';
            loading.value = true;
            try {
                instances.value = (await API.getInstances(machineName.value, filterStatus.value)).instances;
            } finally {
                loading.value = false;
            }
        }

        async function retryInstance(id) {
            await API.retryInstance(id);
            instanceDetail.value = await API.getInstanceDetail(id);
        }

        function closeDrawer() {
            drawerVisible.value = false;
            drawerInstance.value = null;
            drawerSnapshots.value = [];
        }

        async function openDrawer(instanceId) {
            drawerVisible.value = true;
            const data = await API.getInstanceDetail(instanceId);
            drawerInstance.value = data.instance;
            drawerSnapshots.value = data.snapshots;
            await nextTick();
            const el = await waitForElement('#drawer-graph');
            if (el) renderDrawerMermaid();
        }

        async function renderDrawerMermaid() {
            const el = document.getElementById('drawer-graph');
            if (!el || !drawerInstance.value) return;

            const inst = drawerInstance.value;
            if (!inst.machineName) return;
            const vers = await API.getVersions(inst.machineName);
            if (!vers.length || !vers[0].transitions) return;

            const stateStatus = {};
            const stateHasSuccess = {}; // track if a state ever succeeded
            drawerSnapshots.value.forEach(s => {
                // Always track success
                if (s.status === 'SUCCESS') stateHasSuccess[s.stateName] = true;
                // State status: once red, stays red (unless later succeeded)
                const prev = stateStatus[s.stateName];
                if (prev === 'red' && s.status !== 'SUCCESS') return;
                stateStatus[s.stateName] = s.status === 'SUCCESS' ? 'green' : 'red';
            });

            // Determine which transitions were actually traversed
            // Compress snapshots to unique consecutive states, then check edges
            const traversedEdges = new Set();
            const uniqueStates = [];
            let lastState = null;
            drawerSnapshots.value.forEach(s => {
                if (s.stateName !== lastState) {
                    uniqueStates.push(s.stateName);
                    lastState = s.stateName;
                }
            });
            for (let i = 0; i < uniqueStates.length - 1; i++) {
                const from = uniqueStates[i];
                const to = uniqueStates[i + 1];
                if (stateHasSuccess[from]) {
                    traversedEdges.add(from + '-->' + to);
                }
            }
            // For COMPLETED instances, also mark the last state's outgoing edge (drawer)
            if (inst.status === 'COMPLETED' && uniqueStates.length > 0) {
                const lastSt = uniqueStates[uniqueStates.length - 1];
                const matchedTransition = vers[0].transitions.find(t => t.from === lastSt);
                if (matchedTransition && stateHasSuccess[lastSt]) {
                    traversedEdges.add(lastSt + '-->' + matchedTransition.to);
                }
            }

            const suspendedMap = {};
            if (vers[0].states) vers[0].states.forEach(s => { suspendedMap[s.name] = s.suspended; });

            mermaid.initialize({
                startOnLoad: false,
                theme: 'base',
                themeVariables: {
                    primaryColor: '#eef2ff',
                    primaryTextColor: '#1e293b',
                    primaryBorderColor: '#6366f1',
                    lineColor: '#6366f1',
                    secondaryColor: '#f8fafc',
                    tertiaryColor: '#f1f5f9',
                    fontSize: '12px',
                    fontFamily: 'Inter, -apple-system, sans-serif'
                },
                flowchart: { curve: 'basis', padding: 8, nodeSpacing: 30, rankSpacing: 20 }
            });

            let def = 'graph TD\n';
            vers[0].transitions.forEach(t => {
                const fromLabel = suspendedMap[t.from] ? `⏸ ${t.from}` : t.from;
                const toLabel = suspendedMap[t.to] ? `⏸ ${t.to}` : t.to;
                def += `    ${t.from}([${fromLabel}]) --> ${t.to}([${toLabel}])\n`;
            });

            el.textContent = def;
            // Force mermaid to re-render by giving a unique id each time
            el.removeAttribute('data-processed');
            el.id = 'drawer-graph-' + Date.now();
            try {
                await mermaid.run({ nodes: [el] });
                await new Promise(r => requestAnimationFrame(r));
                const svg = el.querySelector('svg');
                if (!svg) return;
                // Inject CSS to color nodes and edges
                let css = '';
                for (const [stateName, status] of Object.entries(stateStatus)) {
                    const nodeId = 'flowchart-' + stateName.toLowerCase();
                    const colors = status === 'green'
                        ? { fill: '#ecfdf5', stroke: '#10b981', text: '#065f46' }
                        : { fill: '#fef2f2', stroke: '#ef4444', text: '#991b1b' };
                    css += `*[id^="${nodeId}"] rect, *[id^="${nodeId}"] circle, *[id^="${nodeId}"] path { fill:${colors.fill}!important; stroke:${colors.stroke}!important; }\n`;
                    css += `*[id^="${nodeId}"] text, *[id^="${nodeId}"] tspan { fill:${colors.text}!important; }\n`;
                }
                // Color traversed edges green (mermaid uses class: flowchart-link LS-{from} LE-{to})
                for (const edge of traversedEdges) {
                    const [from, to] = edge.split('-->');
                    css += `.flowchart-link.LS-${from}.LE-${to} { stroke:#10b981!important; stroke-width:2.5!important; }\n`;
                }
                const styleEl = document.createElementNS('http://www.w3.org/2000/svg', 'style');
                styleEl.textContent = css;
                svg.insertBefore(styleEl, svg.firstChild);
                // Direct DOM manipulation as SVG <style> !important can be overridden by later rules
                for (const edge of traversedEdges) {
                    const [from, to] = edge.split('-->');
                    const sel = `.flowchart-link.LS-${from}.LE-${to}`;
                    svg.querySelectorAll(sel).forEach(p => {
                        p.style.stroke = '#10b981';
                        p.style.strokeWidth = '2.5';
                    });
                }
                // Color arrow markers green for traversed edges
                const origMarker = svg.querySelector('marker[id$="_flowchart-pointEnd"]');
                if (origMarker) {
                    const greenMarker = origMarker.cloneNode(true);
                    greenMarker.id = origMarker.id.replace('pointEnd', 'pointEnd-green');
                    greenMarker.querySelectorAll('path').forEach(p => {
                        p.style.fill = '#10b981';
                        p.style.stroke = '#10b981';
                    });
                    origMarker.parentElement.appendChild(greenMarker);
                    const greenRef = `url(#${greenMarker.id})`;
                    for (const edge of traversedEdges) {
                        const [from, to] = edge.split('-->');
                        const sel = `.flowchart-link.LS-${from}.LE-${to}`;
                        svg.querySelectorAll(sel).forEach(p => {
                            p.setAttribute('marker-end', greenRef);
                        });
                    }
                }
            } catch (e) {
                console.error('Mermaid render failed:', e);
            }
        }

        async function renderMermaid() {
            mermaid.initialize({
                startOnLoad: false,
                theme: 'base',
                themeVariables: {
                    primaryColor: '#eef2ff',
                    primaryTextColor: '#1e293b',
                    primaryBorderColor: '#6366f1',
                    lineColor: '#6366f1',
                    secondaryColor: '#f8fafc',
                    tertiaryColor: '#f1f5f9',
                    fontSize: '13px',
                    fontFamily: 'Inter, -apple-system, sans-serif'
                },
                flowchart: { curve: 'basis', padding: 16 }
            });
            for (const v of versions.value) {
                const el = document.getElementById('mermaid-' + v.id);
                if (!el || !v.transitions) continue;
                const suspendedMap = {};
                if (v.states) v.states.forEach(s => { suspendedMap[s.name] = s.suspended; });
                let def = 'graph LR\n';
                v.transitions.forEach(t => {
                    const fromLabel = suspendedMap[t.from] ? `⏸ ${t.from}` : t.from;
                    const toLabel = suspendedMap[t.to] ? `⏸ ${t.to}` : t.to;
                    def += `    ${t.from}([${fromLabel}]) --> ${t.to}([${toLabel}])\n`;
                });
                el.textContent = def;
            }
            await mermaid.run();
        }

        async function renderInstanceMermaid() {
            const el = document.getElementById('instance-mermaid');
            if (!el || !instanceDetail.value.instance) return;

            const inst = instanceDetail.value.instance;
            if (!inst.machineName) return;
            const vers = await API.getVersions(inst.machineName);
            if (!vers.length || !vers[0].transitions) return;

            // Collect snapshot statuses per state, and build ordered state sequence
            const stateStatus = {};
            const stateHasSuccess = {};
            instanceDetail.value.snapshots.forEach(s => {
                // Always track success
                if (s.status === 'SUCCESS') stateHasSuccess[s.stateName] = true;
                // State status: once red, stays red (unless later succeeded)
                const prev = stateStatus[s.stateName];
                if (prev === 'red' && s.status !== 'SUCCESS') return;
                stateStatus[s.stateName] = s.status === 'SUCCESS' ? 'green' : 'red';
            });

            // Determine which transitions were actually traversed
            // Compress snapshots to unique consecutive states, then check edges
            const traversedEdges = new Set();
            const uniqueStates = [];
            let lastState = null;
            instanceDetail.value.snapshots.forEach(s => {
                if (s.stateName !== lastState) {
                    uniqueStates.push(s.stateName);
                    lastState = s.stateName;
                }
            });
            for (let i = 0; i < uniqueStates.length - 1; i++) {
                const from = uniqueStates[i];
                const to = uniqueStates[i + 1];
                if (stateHasSuccess[from]) {
                    traversedEdges.add(from + '-->' + to);
                }
            }
            // For COMPLETED instances, also mark the last state's outgoing edge
            if (inst.status === 'COMPLETED' && uniqueStates.length > 0) {
                const lastSt = uniqueStates[uniqueStates.length - 1];
                const matchedTransition = vers[0].transitions.find(t => t.from === lastSt);
                if (matchedTransition && stateHasSuccess[lastSt]) {
                    traversedEdges.add(lastSt + '-->' + matchedTransition.to);
                }
            }

            // Build suspended map from definition states
            const suspendedMap = {};
            if (vers[0].states) vers[0].states.forEach(s => { suspendedMap[s.name] = s.suspended; });

            mermaid.initialize({
                startOnLoad: false,
                theme: 'base',
                themeVariables: {
                    primaryColor: '#eef2ff',
                    primaryTextColor: '#1e293b',
                    primaryBorderColor: '#6366f1',
                    lineColor: '#6366f1',
                    secondaryColor: '#f8fafc',
                    tertiaryColor: '#f1f5f9',
                    fontSize: '12px',
                    fontFamily: 'Inter, -apple-system, sans-serif'
                },
                flowchart: { curve: 'basis', padding: 8, nodeSpacing: 30, rankSpacing: 20 }
            });

            let def = 'graph TD\n';

            vers[0].transitions.forEach(t => {
                const fromLabel = suspendedMap[t.from] ? `⏸ ${t.from}` : t.from;
                const toLabel = suspendedMap[t.to] ? `⏸ ${t.to}` : t.to;
                def += `    ${t.from}([${fromLabel}]) --> ${t.to}([${toLabel}])\n`;
            });

            el.textContent = def;
            el.removeAttribute('data-processed');
            el.id = 'instance-mermaid-' + Date.now();
            try {
                await mermaid.run({ nodes: [el] });
                await new Promise(r => requestAnimationFrame(r));
                // Inject CSS to color nodes and edges
                const svg = el.querySelector('svg');
                if (!svg) return;
                let css = '';
                for (const [stateName, status] of Object.entries(stateStatus)) {
                    const nodeId = 'flowchart-' + stateName.toLowerCase();
                    const colors = status === 'green'
                        ? { fill: '#ecfdf5', stroke: '#10b981', text: '#065f46' }
                        : { fill: '#fef2f2', stroke: '#ef4444', text: '#991b1b' };
                    css += `*[id^="${nodeId}"] rect, *[id^="${nodeId}"] circle, *[id^="${nodeId}"] path { fill:${colors.fill}!important; stroke:${colors.stroke}!important; }\n`;
                    css += `*[id^="${nodeId}"] text, *[id^="${nodeId}"] tspan { fill:${colors.text}!important; }\n`;
                }
                // Color traversed edges green (mermaid uses class: flowchart-link LS-{from} LE-{to})
                for (const edge of traversedEdges) {
                    const [from, to] = edge.split('-->');
                    css += `.flowchart-link.LS-${from}.LE-${to} { stroke:#10b981!important; stroke-width:2.5!important; }\n`;
                }
                const styleEl = document.createElementNS('http://www.w3.org/2000/svg', 'style');
                styleEl.textContent = css;
                svg.insertBefore(styleEl, svg.firstChild);
                // Direct DOM manipulation as SVG <style> !important can be overridden by later rules
                for (const edge of traversedEdges) {
                    const [from, to] = edge.split('-->');
                    const sel = `.flowchart-link.LS-${from}.LE-${to}`;
                    svg.querySelectorAll(sel).forEach(p => {
                        p.style.stroke = '#10b981';
                        p.style.strokeWidth = '2.5';
                    });
                }
                // Color arrow markers green for traversed edges
                const origMarker = svg.querySelector('marker[id$="_flowchart-pointEnd"]');
                if (origMarker) {
                    const greenMarker = origMarker.cloneNode(true);
                    greenMarker.id = origMarker.id.replace('pointEnd', 'pointEnd-green');
                    greenMarker.querySelectorAll('path').forEach(p => {
                        p.style.fill = '#10b981';
                        p.style.stroke = '#10b981';
                    });
                    origMarker.parentElement.appendChild(greenMarker);
                    const greenRef = `url(#${greenMarker.id})`;
                    for (const edge of traversedEdges) {
                        const [from, to] = edge.split('-->');
                        const sel = `.flowchart-link.LS-${from}.LE-${to}`;
                        svg.querySelectorAll(sel).forEach(p => {
                            p.setAttribute('marker-end', greenRef);
                        });
                    }
                }
            } catch (e) {
                console.error('Mermaid render failed:', e);
            }
        }

        function getMachineFromHash() {
            const m = location.hash.match(/machine\/([^\/]+)/);
            return m ? m[1] : '';
        }

        function handleRoute() {
            const h = location.hash;
            if (h.includes('/instances')) loadInstances();
            else if (h.startsWith('#/machine/')) loadMachineDetail(getMachineFromHash());
            else if (h.startsWith('#/instance/')) loadInstanceDetail(h.replace('#/instance/', ''));
            else { currentView.value = 'list'; loadMachines(); }
        }

        onMounted(() => { handleRoute(); window.addEventListener('hashchange', handleRoute); });

        return {
            currentView, machines, machineName, versions, instances, filterStatus, instanceDetail, expanded, loading,
            totalMachines, totalRunning, totalFailed, currentMachineStats, machineInstances, machineFilterStatus,
            machinePage, machinePageSize, machineTotalPages, machineTotalElements,
            shortId, statusClass, statusLabel, shortJson, formatJson, toggle,
            relativeTime, formatTime, formatTimeShort,
            getMachineHealth, isMachineActive, goInstance,
            getStateType, getTransitionsFrom,
            loadMachines, loadMachineDetail, loadInstances, loadInstanceDetail, retryInstance, loadMachineInstances,
            copyText,
            drawerVisible, drawerInstance, drawerSnapshots, openDrawer, closeDrawer,
            toastMsg, toastVisible, showToast,
            resumeModalVisible, resumeForm, resumeLoading, resumeContextMode, resumeContextTree, resumeContextError,
            openResumeModal, closeResumeModal, confirmResume, getTreeJson, syncTreeToText, onTreeNodeUpdate
        };
    }
}).mount('#app');
