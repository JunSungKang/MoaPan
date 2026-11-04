// One-shot panel lifecycle; works for mouse, keyboard, history and direct hash navigation.
export function whenRevealed(panel,initialize){
 let started=false;
 const observer=new MutationObserver(check);
 function check(){if(started||!panel.isConnected||panel.closest('[hidden]'))return;started=true;observer.disconnect();initialize();}
 for(let node=panel;node;node=node.parentElement)observer.observe(node,{attributes:true,attributeFilter:['hidden']});
 check();
}
