import assert from 'node:assert/strict';

const roles=['web','worker','translator','classifier'];
const legacyJvm='-XX:MaxRAMPercentage=70 -Djava.io.tmpdir=/tmp -Duser.timezone=UTC';
export const containerEnvironment=c=>Object.fromEntries(c.Config.Env.map(e=>[e.slice(0,e.indexOf('=')),e.slice(e.indexOf('=')+1)]));

/** Snapshot only this application's resolved services, preserving all actual ENV values. */
export function frozenConfigurations(configuration,containers,override) {
    const before={name:'platform',services:{},networks:{},volumes:{},secrets:{}};
    for(const role of roles) {
        const name='damgox2-'+role,c=containers[role];
        const service=structuredClone(configuration.services[name]);
        assert(service&&c);
        service.image=c.Image;
        service.environment=containerEnvironment(c);
        // Explicit orchestration already verifies the existing DB and starts AI before worker.
        delete service.depends_on;delete service.profiles;delete service.build;
        for(const n of Object.keys(service.networks||{}))before.networks[n]={external:true,name:configuration.networks[n].name};
        for(const v of service.volumes||[])if(v.type==='volume')before.volumes[v.source]={external:true,name:configuration.volumes[v.source].name};
        for(const s of service.secrets||[])before.secrets[s.source]=structuredClone(configuration.secrets[s.source]);
        if(service.configs?.length)throw Error('File configs require explicit recovery support');
        before.services[name]=service;
    }
    const after=structuredClone(before);
    for(const role of roles) {
        const name='damgox2-'+role,changes=override.services[name],service=after.services[name];
        service.image=changes.image;
        const customJvm=service.environment.JAVA_TOOL_OPTIONS;
        service.environment={...service.environment,...changes.environment};
        if(['web','worker'].includes(role)&&customJvm&&customJvm!==legacyJvm)service.environment.JAVA_TOOL_OPTIONS=customJvm;
    }
    return {before,after};
}

/** Compose config JSON escapes dollar signs on output. Raw runtime ENV must be escaped once. */
export function serializeConfiguration(configuration) {
    const copy=structuredClone(configuration);
    for(const service of Object.values(copy.services))for(const [key,value]of Object.entries(service.environment))service.environment[key]=String(value).split('$').join('$$');
    return JSON.stringify(copy);
}
export function renderedEnvironment(service) {
    return Object.fromEntries(Object.entries(service.environment||{}).map(([k,v])=>[k,String(v).split('$$').join('$')]));
}
function freeze(value){if(value&&typeof value==='object'){Object.values(value).forEach(freeze);Object.freeze(value);}return value;}

/** Recovery never reads shared files or the current interpolation environment. */
export class FrozenDeployment {
    #before;#after;#readHash;#expected;#execute;
    constructor({before,after,readHash,expectedHash,execute}) {
        this.#before=freeze(structuredClone(before));this.#after=freeze(structuredClone(after));
        this.#readHash=readHash;this.#expected=expectedHash;this.#execute=execute;
    }
    apply(role) {
        assert(roles.includes(role));
        if(this.#readHash()!==this.#expected)throw Error('Shared Compose changed');
        return this.#execute(this.#after,role);
    }
    restore(role) {
        assert(roles.includes(role));
        return this.#execute(this.#before,role);
    }
}
