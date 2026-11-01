const PAGE_SIZE=7,EDITION_LIMIT=42;
export function slideWindow(items,index=0){
 const selected=items.slice(0,EDITION_LIMIT);
 const pages=Math.ceil(selected.length/PAGE_SIZE),page=Math.max(0,Math.min(Math.floor(Number.isFinite(index)?index:0),Math.max(0,pages-1)));
 return {items:selected.slice(page*PAGE_SIZE,(page+1)*PAGE_SIZE),page,pages,total:selected.length};
}
