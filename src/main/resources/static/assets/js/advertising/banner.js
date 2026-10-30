// No provider script, impression beacon, redirect or click event is installed here.
export function renderBanner(slot,config){
  if(!config?.enabled){slot.textContent='광고 문의';return;}
  const target=new URL(config.targetUrl);
  if(target.protocol!=='https:'||target.username||target.password)throw new Error('invalid_advertisement_url');
  const link=document.createElement('a');link.href=target.href;link.target='_blank';link.rel='sponsored noopener noreferrer';
  if(config.imagePath){
    if(!/^\/assets\/images\/[A-Za-z0-9_-]+\.(png|jpe?g|webp|gif)$/.test(config.imagePath))throw new Error('invalid_advertisement_image');
    const image=document.createElement('img');image.src=config.imagePath;image.alt=config.title;image.width=220;image.height=80;image.decoding='async';link.append(image);
  }else{
    const title=document.createElement('strong');title.textContent=config.title;link.append(title);
    if(config.description){const description=document.createElement('span');description.textContent=config.description;link.append(description);}
  }
  link.dataset.originalContent='';slot.replaceChildren(link);
}
