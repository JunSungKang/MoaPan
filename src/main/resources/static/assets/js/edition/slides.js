export const PAGE_SIZE=7,EDITION_LIMIT=42,SEARCH_LIMIT=140;
export function newest(a,b){return new Date(b.publishedAt||b.collectedAt)-new Date(a.publishedAt||a.collectedAt)||a.id.localeCompare(b.id);}
export function slideWindow(items,index=0,search=false){
 const selected=(search?[...items].sort(newest):items).slice(0,search?SEARCH_LIMIT:EDITION_LIMIT);
 const pages=Math.ceil(selected.length/PAGE_SIZE),page=Math.max(0,Math.min(Math.floor(Number.isFinite(index)?index:0),Math.max(0,pages-1)));
 return {items:selected.slice(page*PAGE_SIZE,(page+1)*PAGE_SIZE),page,pages,total:selected.length};
}
