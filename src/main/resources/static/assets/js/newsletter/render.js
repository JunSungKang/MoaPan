export const manuscriptLanguage=manuscript=>['ko','en'].includes(manuscript.language)?manuscript.language:'und';
function textBlock(block,doc){
 const node=doc.createElement({heading:'h2',quote:'blockquote',paragraph:'p'}[block.type]||'p');node.textContent=block.text;return node;
}
function imageBlock(block,doc){
 if(!/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(block.url))return null;
 const figure=doc.createElement('figure'),image=doc.createElement('img'),caption=doc.createElement('figcaption');
 image.src=block.url;image.alt=block.text;image.decoding='async';
 if(Number.isInteger(block.width)&&Number.isInteger(block.height)&&block.width>0&&block.height>0&&block.width<=2000&&block.height<=2000){image.width=block.width;image.height=block.height;}
 caption.textContent=block.caption;figure.append(image,caption);return figure;
}
function referenceBlock(block,doc){
 let url;try{url=new URL(block.url);if(url.protocol!=='https:'||url.username||url.password)return null;}catch{return null;}
 const node=doc.createElement('p'),link=doc.createElement('a');link.href=url.href;link.textContent=block.text;link.target='_blank';link.rel='noopener noreferrer';node.append(link);return node;
}
const strategies=Object.freeze({image:imageBlock,reference:referenceBlock,heading:textBlock,quote:textBlock,paragraph:textBlock});
// One renderer for preview, reader and exported HTML; build off-document and replace once.
export function render(parent,manuscript){
 const doc=parent.ownerDocument,fragment=doc.createDocumentFragment();
 parent.lang=manuscriptLanguage(manuscript);parent.dataset.originalContent='';
 for(const block of manuscript.blocks||[]){const strategy=Object.hasOwn(strategies,block.type)?strategies[block.type]:textBlock;const node=strategy(block,doc);if(node)fragment.append(node);}
 parent.replaceChildren(fragment);
}
