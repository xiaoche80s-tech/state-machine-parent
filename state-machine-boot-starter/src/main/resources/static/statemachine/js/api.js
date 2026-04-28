const API = {
    async getMachines() { return (await fetch('/statemachine/api/machines')).json(); },
    async getVersions(name) { return (await fetch(`/statemachine/api/machines/${name}/versions`)).json(); },
    async getInstances(name, status = '', businessId = '', instanceId = '', page = 0, size = 20) {
        const p = new URLSearchParams({ page, size });
        if (status) p.set('status', status);
        if (businessId) p.set('businessId', businessId);
        if (instanceId) p.set('instanceId', instanceId);
        return (await fetch(`/statemachine/api/machines/${name}/instances?${p}`)).json();
    },
    async getInstanceDetail(id) { return (await fetch(`/statemachine/api/instances/${id}`)).json(); },
    async retryInstance(id) { return (await fetch(`/statemachine/api/instances/${id}/retry`, { method: 'POST' })).json(); },
    async resumeInstance(id, expectedCurrentState, contextJson) {
        const body = { expectedCurrentState };
        if (contextJson) body.contextJson = contextJson;
        return (await fetch(`/statemachine/api/instances/${id}/resume`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        })).json();
    }
};
