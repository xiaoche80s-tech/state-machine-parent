const { createApp, ref, computed, onMounted, nextTick } = Vue;

createApp({
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
            const map = { RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败', SUCCESS: '成功' };
            return map[s] || s;
        }
        function shortJson(s) { return s.length > 60 ? s.substring(0, 60) + '\u2026' : s; }
        function formatJson(s) { try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; } }
        function toggle(k) { expanded.value[k] = !expanded.value[k]; }

        async function copyText(text) {
            try {
                await navigator.clipboard.writeText(text);
            } catch (e) {
                const ta = document.createElement('textarea');
                ta.value = text;
                document.body.appendChild(ta);
                ta.select();
                document.execCommand('copy');
                document.body.removeChild(ta);
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
            const vers = await API.getVersions(inst.machineName);
            if (!vers.length) return;

            const stateStatus = {};
            drawerSnapshots.value.forEach(s => {
                const prev = stateStatus[s.stateName];
                if (prev === 'red') return;
                stateStatus[s.stateName] = s.status === 'SUCCESS' ? 'green' : 'red';
            });

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
            try {
                await mermaid.run({ nodes: [el] });
                await new Promise(r => requestAnimationFrame(r));
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
                const styleEl = document.createElementNS('http://www.w3.org/2000/svg', 'style');
                styleEl.textContent = css;
                svg.insertBefore(styleEl, svg.firstChild);
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
            const vers = await API.getVersions(inst.machineName);
            if (!vers.length) return;

            // Collect snapshot statuses per state
            const stateStatus = {};
            instanceDetail.value.snapshots.forEach(s => {
                const prev = stateStatus[s.stateName];
                if (prev === 'red') return; // already failed, stays red
                stateStatus[s.stateName] = s.status === 'SUCCESS' ? 'green' : 'red';
            });

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
            try {
                await mermaid.run({ nodes: [el] });
                await new Promise(r => requestAnimationFrame(r));
                // Inject CSS to color nodes
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
                const styleEl = document.createElementNS('http://www.w3.org/2000/svg', 'style');
                styleEl.textContent = css;
                svg.insertBefore(styleEl, svg.firstChild);
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
            drawerVisible, drawerInstance, drawerSnapshots, openDrawer, closeDrawer
        };
    }
}).mount('#app');
