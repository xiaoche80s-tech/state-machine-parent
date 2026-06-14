const API = {
    async getMachines() {
        return (await fetch('api/machines.json')).json();
    },
    async getVersions(name) {
        return (await fetch('api/versions.json?name=' + encodeURIComponent(name))).json();
    },
    async getInstances(name, status = '', businessId = '', instanceId = '', page = 0, size = 20) {
        const p = new URLSearchParams({ name, page, size });
        if (status) p.set('status', status);
        if (businessId) p.set('businessId', businessId);
        if (instanceId) p.set('instanceId', instanceId);
        return (await fetch('api/instances.json?' + p)).json();
    },
    async getInstanceDetail(id) {
        return (await fetch('api/instance.json?id=' + encodeURIComponent(id))).json();
    },
    async retryInstance(id) {
        return (await fetch('api/retry.json', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id })
        })).json();
    },
    async resumeInstance(id, expectedCurrentState, contextJson) {
        const body = { id, expectedCurrentState };
        if (contextJson) body.contextJson = contextJson;
        return (await fetch('api/resume.json', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        })).json();
    },
    async advanceInstance(id, contextJson) {
        const body = { id };
        if (contextJson) body.contextJson = contextJson;
        return (await fetch('api/advance.json', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        })).json();
    }
};
