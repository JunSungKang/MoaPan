// Stored translations only; language switching never sends article or user data.
function translationText(text){
 // Some stored translator responses contain the exact source_text envelope.
 // Decode only this known shape; all output still goes through textContent.
 if(!text.trimStart().startsWith('{'))return text;
 try{
  const value=JSON.parse(text);
  if(value&&Object.keys(value).length===1&&typeof value.source_text==='string'&&value.source_text.trim())return value.source_text;
 }catch{/* Preserve ordinary text and incomplete JSON verbatim. */}
 return text;
}
export function articleText(article,language){
 const source=article.source.language?.split('-')[0]||'und';
 const original={title:article.title,summaryBullets:article.summaryBullets,language:source};
 if(language===source)return original;
 const candidate=article.translations?.[language];
 if(!candidate||typeof candidate.title!=='string'||!candidate.title.trim()||!Array.isArray(candidate.summaryBullets)||candidate.summaryBullets.length!==article.summaryBullets.length||candidate.summaryBullets.some(s=>typeof s!=='string'||!s.trim()))return original;
 return {title:translationText(candidate.title),summaryBullets:candidate.summaryBullets.map(translationText),language};
}
