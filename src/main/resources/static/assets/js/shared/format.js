import {language} from './i18n.js';
const formatters=new Map();
const options={timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'};
function formatter(kind){
 const key=kind+':'+(language==='en'?'en':'ko');
 if(!formatters.has(key)){
  let locale,settings;
  if(kind==='long'){locale=language==='en'?'en-US':'ko-KR';settings={...options,month:'long'};}
  else if(kind==='article'){locale=language==='en'?'en-GB':'ko-KR';settings=options;}
  else if(kind==='compact'){locale='sv-SE';settings={...options,hour12:false};}
  else if(kind==='year'){locale='en';settings={timeZone:'Asia/Seoul',year:'numeric'};}
  else throw new Error('Unknown date format');
  formatters.set(key,new Intl.DateTimeFormat(locale,settings));
 }
 return formatters.get(key);
}
export const date=value=>formatter('long').format(new Date(value));
export const articleDate=value=>formatter('article').format(new Date(value));
export const compactDate=value=>formatter('compact').format(new Date(value));
export const issueYear=value=>formatter('year').format(new Date(value));
