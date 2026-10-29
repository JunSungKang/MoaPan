export function effectivePreferences(preferences, representative){return {...preferences,enabled:true,weights:preferences.enabled&&Object.keys(preferences.weights).length?preferences.weights:{...representative}};}
export function rank(candidates, preferences, now = Date.now()) {
  const weights=preferences.enabled?preferences.weights:{};const total=Object.values(weights).reduce((a,b)=>a+b,0);
  const filtered=candidates.filter(a=>!preferences.excludedSources.includes(a.source.id)&&!a.tags.some(t=>preferences.excludedTags.includes(t.id)));
  const rows=filtered.map(article=>{const time=Date.parse(article.publishedAt||article.collectedAt);const valid=Number.isFinite(time)&&time<=now+300000;const age=Math.max(0,(now-time)/3600000);const matched=article.tags.filter(t=>weights[t.id]);const match=total?matched.reduce((sum,t)=>sum+weights[t.id]*t.confidence,0)/total:0;return {...article,matchedTags:matched,rankingTime:valid?time:0,score:total?0.8*match+0.2*(valid?2**(-age/48):0):0};});
  rows.sort((a,b)=>(total?b.score-a.score:0)||b.rankingTime-a.rankingTime||a.id.localeCompare(b.id));
  if(!total)return rows;
  const top=[],deferred=[],sources=new Map();
  for(const article of rows){const n=sources.get(article.source.id)||0;if(top.length<12&&n<3){top.push(article);sources.set(article.source.id,n+1);}else deferred.push(article);}
  if(top.length<12)top.push(...deferred.splice(0,12-top.length));return top.concat(deferred);
}
