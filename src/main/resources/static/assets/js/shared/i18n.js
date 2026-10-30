import basic from './i18n-en.js';
import advertising from './i18n-advertising.js';
import pages from './i18n-pages.js';
import statuses from './i18n-status.js';
const newsletter={"트렌드 뉴스레터":"Trend newsletter","뉴스레터 관리":"Newsletter management","오늘의 지면":"Today’s edition","새 원고":"New manuscript","제목":"Title","소개 문장":"Introduction","주제":"Topic","문단":"Paragraph","소제목":"Heading","강조문":"Pull quote","참고 뉴스·논문":"References","그림 추가":"Add image","이미지 대체 설명":"Image description","그림 설명·출처":"Caption and attribution","내용":"Content","초안 저장":"Save draft","발간":"Publish","발간 취소":"Withdraw","편집":"Edit","발간물 목록":"All issues","아직 발간된 뉴스레터가 없습니다.":"No newsletter has been published yet.","본문은 작성 언어로 제공됩니다.":"The manuscript is provided in its written language.","참고 자료 검색":"Find references","뉴스·논문 찾기":"Find news and papers"};
const messages={...newsletter,...{"원고 삭제":"Delete manuscript","이 원고와 그림, 발간된 글을 모두 삭제할까요? 삭제하면 되돌릴 수 없습니다.":"Delete this manuscript, its images and the published issue? This cannot be undone.","원고를 삭제했습니다.":"Manuscript deleted."},...{"＋ 새 원고":"+ New draft","＋ 문단":"+ Paragraph","원문 링크":"Source link","원고 목록":"Manuscripts","작업 화면":"Workspace view","주제를 선택하거나 입력하세요":"Choose or enter a topic","어떤 미래를 이야기할까요?":"What comes next?","독자가 이 글을 읽어야 할 이유를 짧게 적어주세요.":"Tell readers why this story matters.","키워드로 수집된 자료 찾기":"Find collected sources by keyword","발간 화면 미리보기":"Publication preview","원고 제목":"Manuscript title"},...{"다음의 흐름을 읽다.":"Read what comes next.","오늘의 뉴스에서 발견한 신호를 엮어,":"Connecting signals from today’s news,","다가올 변화를 깊이 읽는 트렌드 뉴스레터.":"a closer look at the changes ahead.","발간물":"Published issues","모든 뉴스레터":"All newsletters","모아판 편집부":"MoA-PaN editorial team","발간물 목록으로 돌아가기 →":"Back to all issues →","다음 이야기를 준비하고 있습니다.":"The next story is taking shape.","새로운 관점과 함께 곧 찾아뵙겠습니다.":"We will be back with a new perspective.","읽어보기 ↗":"Read the issue ↗","내 원고":"My manuscripts","발간물 보기 ↗":"View issues ↗","작성":"Write","미리보기":"Preview","원고 작성":"Write a manuscript","발간하기 ↗":"Publish ↗","분류":"Category","원고 제목을 입력하세요":"Enter your headline","이 글을 소개하는 짧은 문장을 적어 주세요.":"Introduce this story in a few sentences.","내용 추가":"Add content","본문":"Body","인용":"Quote","참고 자료":"Reference","그림":"Image","참고할 뉴스·논문 찾기":"Find news and papers to reference","검색어":"Search term","링크 제목":"Link title","발간됨":"Published","초안":"Draft","아직 작성한 원고가 없습니다. 새 원고로 첫 이야기를 시작하세요.":"No manuscripts yet. Start your first story with a new draft.","원고를 불러왔습니다.":"Manuscript loaded."},...basic,...pages,...statuses,...advertising};
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
