import {translatedText} from './i18n.js';
// Presentation primitives have no authentication, API calls or page bootstrap.
export function el(tag,text,className){const node=document.createElement(tag);if(text!==undefined)node.append(translatedText(text));if(className)node.className=className;return node;}
export function originalEl(tag,text,className,language='und'){const node=el(tag,undefined,className);node.dataset.originalContent='';node.lang=language;if(text!==undefined)node.textContent=text;return node;}
export function report(error){const node=document.querySelector('[role="status"]');if(node)node.textContent=error.message||String(error);}
