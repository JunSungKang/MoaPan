import basic from './i18n-en.js';
import advertising from './i18n-advertising.js';
import pages from './i18n-pages.js';
import statuses from './i18n-status.js';
const messages={...basic,...pages,...statuses,...advertising};
const reverse=new Map(Object.entries(messages).map(([ko,en])=>[en,ko]));
export let language='ko';
try{language=localStorage.getItem('moapan-language')==='en'?'en':'ko';}catch{}
export function t(text){if(typeof text!=='string')return text;const original=reverse.get(text)||text;return language==='en'?(messages[original]||text):original;}
const originals=new WeakMap();
function translateText(node){const value=node.nodeValue,trimmed=value.trim();if(!trimmed)return;
 const previous=originals.get(node);const source=previous&&(value===previous.rendered)?previous.source:(reverse.get(trimmed)||trimmed);
 const rendered=value.replace(trimmed,language==='en'?(messages[source]||source):source);
 originals.set(node,{source,rendered});if(value!==rendered)node.nodeValue=rendered;
}
export function translatePage(){
 document.documentElement.lang=language;
 const walk=document.createTreeWalker(document.documentElement,NodeFilter.SHOW_TEXT,{acceptNode:n=>n.parentElement?.closest('script,style,[data-original-content],[data-brand-en],textarea,pre')?NodeFilter.FILTER_REJECT:NodeFilter.FILTER_ACCEPT});
 while(walk.nextNode())translateText(walk.currentNode);
 for(const node of document.querySelectorAll('[placeholder],[aria-label],[title]')){
  if(node.closest('[data-original-content]'))continue;
  for(const attr of ['placeholder','aria-label','title'])if(node.hasAttribute(attr)){const value=node.getAttribute(attr),updated=t(value);if(value!==updated)node.setAttribute(attr,updated);}
 }
 for(const select of document.querySelectorAll('[data-language-select]'))select.value=language;
}
export function setLanguage(value){if(!['ko','en'].includes(value))return;language=value;try{localStorage.setItem('moapan-language',value);}catch{}translatePage();window.dispatchEvent(new CustomEvent('moapan-language',{detail:value}));}
export function languagePicker(){const label=document.createElement('label');label.className='language-picker';const select=document.createElement('select');select.dataset.languageSelect='';select.setAttribute('aria-label','Language / 언어');for(const [value,text] of [['ko','한국어'],['en','English']]){const option=document.createElement('option');option.value=value;option.textContent=text;select.append(option);}select.value=language;select.addEventListener('change',()=>setLanguage(select.value));label.append(select);return label;}
let scheduled=false;new MutationObserver(()=>{if(scheduled)return;scheduled=true;queueMicrotask(()=>{scheduled=false;translatePage();});}).observe(document.documentElement,{subtree:true,childList:true,characterData:true});
translatePage();
