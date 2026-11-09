import test from 'node:test';
import assert from 'node:assert/strict';
import {articleText} from '../../src/main/resources/static/assets/js/article/article-text.js';

const original={title:'Original',summaryBullets:['Original summary'],source:{language:'en-US'}};
function translated(title,summary=title){return {...original,translations:{ko:{title,summaryBullets:[summary]}}};}

test('stored translation envelopes display their text without modifying the article',()=>{
 const article=translated('{"source_text":"한글 제목"}',' {"source_text":"번역 요약"} ');
 const before=JSON.stringify(article);
 assert.deepEqual(articleText(article,'ko'),{title:'한글 제목',summaryBullets:['번역 요약'],language:'ko'});
 assert.equal(JSON.stringify(article),before);
 assert.deepEqual(articleText(article,'en'),{title:original.title,summaryBullets:original.summaryBullets,language:'en'});
});

test('ordinary text, malformed JSON and other shapes remain unchanged',()=>{
 for(const text of ['plain text','{"source_text":','{}','[]','null','{"source_text":null}','{"source_text":2}',
  '{"source_text":""}','{"source_text":" "}','{"source_text":"안녕","other":1}','{"different":"안녕"}']){
  const output=articleText(translated(text),'ko');
  assert.equal(output.title,text);assert.deepEqual(output.summaryBullets,[text]);
 }
});

test('HTML-like output stays a string and original-language wrappers are untouched',()=>{
 const text='<img src=x onerror=alert(1)>';
 assert.equal(articleText(translated(JSON.stringify({source_text:text})),'ko').title,text);
 const article={...translated('한국어'),title:'{"source_text":"Original JSON"}'};
 assert.equal(articleText(article,'en').title,article.title);
});

test('incomplete translations fall back to the original article',()=>{
 for(const candidate of [null,{title:' ',summaryBullets:['번역']},{title:'번역',summaryBullets:[]},{title:'번역',summaryBullets:[' ']}]){
  assert.deepEqual(articleText({...original,translations:{ko:candidate}},'ko'),{title:original.title,summaryBullets:original.summaryBullets,language:'en'});
 }
});
