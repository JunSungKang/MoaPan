import {test} from 'node:test';import assert from 'node:assert/strict';import {parseCss,dominatedDeclarations,normalizeCss,validateStylesheetLinks} from './style-structure.mjs';
test('finds overridden longhands and whole shorthands, retaining valid parts',()=>{const src='a{margin:1px;color:red;font:12px serif} a{margin:2px;color:blue;font-size:14px}';const r=dominatedDeclarations(parseCss(src));assert.deepEqual(r.map(d=>d.property).sort(),['color','margin']);});
test('keeps different media conditions, selectors and important priorities',()=>{assert.equal(dominatedDeclarations(parseCss('a{color:red!important} b{color:blue} @media print{a{color:black}} a{color:green}')).length,0);});
test('parses quoted punctuation and removes empty rules without changing remaining order',()=>{const src='a::before{content:"x;{}"} a{color:red} a{color:blue}';const out=normalizeCss(src);assert(out.css.includes('content: "x;{}"'));assert(!out.css.includes('color: red'));assert.equal(dominatedDeclarations(parseCss(out.css)).length,0);});
test('rejects malformed and unsupported grammar rather than passing an incomplete audit',()=>{assert.throws(()=>parseCss('a{color:red'));assert.throws(()=>parseCss('@unknown x{a{color:red}}'));});

 test('rejects actual repeated requests while allowing distinct domain styles',async()=>{const {validateStylesheetLinks}=await import('./style-structure.mjs');assert.throws(()=>validateStylesheetLinks('<link rel="stylesheet" href="a.css"><link rel="stylesheet" href="a.css">'));assert.deepEqual(validateStylesheetLinks('<link rel="stylesheet" href="base.css"><link rel="stylesheet" href="auth.css">'),['base.css','auth.css']);});
test('normalizes insignificant selector/media spacing but preserves quoted attribute values',()=>{
 const css='.a > b{color:red} @media (max-width: 600px){.a{color:red}} .a>b{color:blue} @media(max-width:600px){.a{color:blue}} [title="a > b"]{color:red}[title="a>b"]{color:blue}';
 assert.equal(dominatedDeclarations(parseCss(css)).length,2);
});
test('preserves descendant combinators around pseudo classes and media word boundaries',()=>{
 assert.equal(dominatedDeclarations(parseCss('.a :hover{color:red}.a:hover{color:blue}.a:not(.b) .c{color:red}.a:not(.b).c{color:blue}')).length,0);
 assert.equal(dominatedDeclarations(parseCss('@media screen and (max-width:600px){a{color:red}} @media screenand (max-width:600px){a{color:blue}}')).length,0);
});
test('detects repeated stylesheets independent of attribute order, quote style and URL dot segments',()=>{
 assert.throws(()=>validateStylesheetLinks('<link href="base.css" rel="stylesheet"><link rel=\'stylesheet\' href=\'./base.css#again\'>'));
});
