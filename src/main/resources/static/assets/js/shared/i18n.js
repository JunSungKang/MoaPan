import basic from './i18n-en.js';
const admin=location.pathname.endsWith('/admin.html');
const policy=admin||/\/(about|privacy|terms)\.html$/.test(location.pathname);
const edition=location.pathname==='/'||location.pathname.endsWith('/index.html');
const [adminNavigation,pages,advertising,product]=await Promise.all([
 admin?import('./i18n-admin-navigation.js').then(m=>m.default):{},
 policy?import('./i18n-pages.js').then(m=>m.default):{},
 admin?import('./i18n-advertising.js').then(m=>m.default):{},
 edition?{}:import('./i18n-features.js').then(m=>m.default)
]);
const messages={...adminNavigation,...product,...basic,...pages,...advertising};
const reverse=new Map(Object.entries(messages).map(([ko,en])=>[en,ko]));
let policyMessages;
export function loadPolicyMessages(){
 if(policy)return Promise.resolve();
 return policyMessages??=import('./i18n-pages.js').then(({default:pages})=>{
  Object.assign(messages,pages);for(const [ko,en] of Object.entries(pages))reverse.set(en,ko);translatePage();
 }).catch(error=>{policyMessages=undefined;throw error;});
}
export let language='ko';
try{language=localStorage.getItem('moapan-language')==='en'?'en':'ko';}catch{}
function originalOf(text){return Object.hasOwn(messages,text)?text:(reverse.get(text)||text);}
function englishOf(source){return Object.hasOwn(messages,source)?messages[source]:source;}
export function t(text){if(typeof text!=='string')return text;const source=originalOf(text);return language==='en'?englishOf(source):source;}
const originals=new WeakMap();
// Keep the known source key before insertion; identical English labels must not lose their identity.
export function translatedText(value){const raw=value==null?'':String(value),trimmed=raw.trim(),source=originalOf(trimmed),node=document.createTextNode(raw.replace(trimmed,t(trimmed)));originals.set(node,{source,rendered:node.nodeValue});return node;}
function translateText(node){const value=node.nodeValue,trimmed=value.trim();if(!trimmed)return;
 const previous=originals.get(node);const source=previous&&(value===previous.rendered)?previous.source:originalOf(trimmed);
 const rendered=value.replace(trimmed,language==='en'?englishOf(source):source);
 if(!previous||previous.source!==source||previous.rendered!==rendered)originals.set(node,{source,rendered});if(value!==rendered)node.nodeValue=rendered;
}
const attributeOriginals=new WeakMap();
function attributes(root){
 const selector='[placeholder],[aria-label],[title],[data-language-select]';
 const nodes=[...(root.matches?.(selector)?[root]:[]),...(root.querySelectorAll?.(selector)||[])];
 for(const node of nodes){if(node.closest('[data-original-content]'))continue;
  for(const attr of ['placeholder','aria-label','title'])if(node.hasAttribute(attr)){
   let saved=attributeOriginals.get(node);if(!saved){saved=new Map();attributeOriginals.set(node,saved);}
   const value=node.getAttribute(attr),previous=saved.get(attr),source=previous&&value===previous.rendered?previous.source:originalOf(value),updated=t(source);
   saved.set(attr,{source,rendered:updated});if(value!==updated)node.setAttribute(attr,updated);
  }
  if(node.hasAttribute('data-language-select'))node.value=language;
 }
}
const excluded='script,style,[data-original-content],[data-brand-en],textarea,pre';
function translateRegion(root){
 if(!root.isConnected)return;
 if(root.nodeType===Node.TEXT_NODE){if(!root.parentElement?.closest(excluded))translateText(root);return;}
 if(root.nodeType!==Node.ELEMENT_NODE||root.closest('[data-original-content]'))return;
 const walk=document.createTreeWalker(root,NodeFilter.SHOW_ELEMENT|NodeFilter.SHOW_TEXT,{acceptNode:n=>n.nodeType===Node.ELEMENT_NODE?(n.matches(excluded)?NodeFilter.FILTER_REJECT:NodeFilter.FILTER_SKIP):NodeFilter.FILTER_ACCEPT});
 if(!root.closest(excluded))while(walk.nextNode())translateText(walk.currentNode);
 attributes(root);
}
export function translatePage(){document.documentElement.lang=language;translateRegion(document.documentElement);}
export function setLanguage(value){if(!['ko','en'].includes(value))return;language=value;try{localStorage.setItem('moapan-language',value);}catch{}translatePage();window.dispatchEvent(new CustomEvent('moapan-language',{detail:value}));}
export function languagePicker(){const label=document.createElement('label');label.className='language-picker';const select=document.createElement('select');select.dataset.languageSelect='';select.setAttribute('aria-label','Language / 언어');for(const [value,text] of [['ko','한국어'],['en','English']]){const option=document.createElement('option');option.value=value;option.textContent=text;select.append(option);}select.value=language;select.addEventListener('change',()=>setLanguage(select.value));label.append(select);return label;}
// Mutation records already batch synchronous changes. Do not retain removed subtrees or rescan the document.
new MutationObserver(records=>{
 const roots=new Set();
 for(const record of records){if(record.type==='childList')for(const node of record.addedNodes)roots.add(node);else roots.add(record.target);}
 for(const node of roots){if(!node.isConnected)continue;let parent=node.parentNode,covered=false;while(parent){if(roots.has(parent)){covered=true;break;}parent=parent.parentNode;}if(!covered)translateRegion(node);}
}).observe(document.documentElement,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['placeholder','aria-label','title']});
translatePage();
