import {request} from '../shared/api.js';

// Independent small entry starts the same bounded requests while the presentation graph loads.
// This promise is consumed once by news.js; there is no cache or additional news download.
export function loadInitialEdition(){return Promise.all([
 request('/api/v1/public/tags'),request('/api/v1/public/interests'),request('/api/v1/public/news/edition')
]);}
export const initialEdition=loadInitialEdition();
// Mark early failures as handled until the presentation entry reports the original rejection.
initialEdition.catch(()=>{});
