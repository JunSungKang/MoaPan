import basic from './i18n-en.js';
import advertising from './i18n-advertising.js';
import pages from './i18n-pages.js';
import statuses from './i18n-status.js';
const newsletter={"트렌드 뉴스레터":"Trend newsletter","뉴스레터 관리":"Newsletter management","오늘의 지면":"Today’s edition","새 원고":"New manuscript","제목":"Title","소개 문장":"Introduction","주제":"Topic","문단":"Paragraph","소제목":"Heading","강조문":"Pull quote","참고 뉴스·논문":"References","그림 추가":"Add image","이미지 대체 설명":"Image description","그림 설명·출처":"Caption and attribution","내용":"Content","초안 저장":"Save draft","발간":"Publish","발간 취소":"Withdraw","편집":"Edit","발간물 목록":"All issues","아직 발간된 뉴스레터가 없습니다.":"No newsletter has been published yet.","본문은 작성 언어로 제공됩니다.":"The manuscript is provided in its written language.","참고 자료 검색":"Find references","뉴스·논문 찾기":"Find news and papers"};
const ai={"자동 번역·분류 안내":"About automatic translation and topics","자동 번역과 주제 분류는 수집한 기사 정보를 대상으로 합니다. 계정 정보나 개인 관심사 설정을 사용하지 않습니다.":"Automatic translation and topic classification process collected article information. They do not use account information or personal interests.","번역이 뜻을 바꾸거나 주제가 잘못 분류될 수 있습니다. 중요한 내용은 원문과 근거를 함께 확인해 주세요. 자동 처리만으로 정확성이나 공정성을 보장하지 않습니다.":"Translations may change meaning and topics may be misclassified. Check important information against its original sources and evidence. Automatic processing alone does not guarantee accuracy or fairness.","관리자는 번역과 주제를 수정할 수 있습니다. 뉴스레터의 전망은 편집자가 근거를 검토해 작성하며, 확인된 사실과 앞으로 검증할 가설을 구분합니다.":"Administrators can correct translations and topics. Editors review evidence when writing newsletter scenarios and distinguish findings from hypotheses to test."};
const messages={...ai,...newsletter,...basic,...pages,...statuses,...advertising};
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
