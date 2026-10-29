// Stored translations only; language switching never sends article or user data.
export function articleText(article,language){
 const source=article.source.language?.split('-')[0]||'und';
 const original={title:article.title,summaryBullets:article.summaryBullets,language:source};
 if(language===source)return original;
 const candidate=article.translations?.[language];
 if(!candidate||typeof candidate.title!=='string'||!candidate.title.trim()||!Array.isArray(candidate.summaryBullets)||candidate.summaryBullets.length!==article.summaryBullets.length||candidate.summaryBullets.some(s=>typeof s!=='string'||!s.trim()))return original;
 return {title:candidate.title,summaryBullets:candidate.summaryBullets,language};
}
