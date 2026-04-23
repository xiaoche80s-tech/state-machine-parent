const API = {
    async getMachines() { return (await fetch('/statemachine/api/machines')).json(); },
    async getVersions(name) { return (await fetch(`/statemachine/api/machines/${name}/versions`)).json(); },
    async getInstances(name, status = '', page = 0, size = 20) {
        const p = new URLSearchParams({ page, size });
        if (status) p.set('status', status);
        return (await fetch(`/statemachine/api/machines/${name}/instances?${p}`)).json();
    },
    async getInstanceDetail(id) { return (await fetch(`/statemachine/api/instances/${id}`)).json(); },
    async retryInstance(id) { return (await fetch(`/statemachine/api/instances/${id}/retry`, { method: 'POST' })).json(); }
};
