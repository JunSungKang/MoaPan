// 대표 문구를 교체할 때 mastheadQuote만 수정합니다. 외부 요청이나 자동 순환은 없습니다.
export const brand = Object.freeze({
  name: '담고담고',
  english: 'DamGoX2',
  mastheadQuote: '여러 소식을 담고 펼치는 판'
});

// 뉴스레터 이름·영문 표기·의미는 이 설정에서 함께 관리합니다.
export const newsletter = Object.freeze({
  name: brand.name,
  english: brand.english,
  meaning: '여러 소식을 담고 펼치는 판',
  meaningEnglish: 'A page to gather and unfold stories'
});
export function newsletterText(kind, language='ko') {
  const en=language==='en',name=en?newsletter.english:newsletter.name;
  return {name,english:brand.english,meaning:en?newsletter.meaningEnglish:newsletter.meaning,
    management:en?name+' management':name+' 관리',
    'management-link':en?name+' management ↗':name+' 관리 ↗',
    editorial:en?name+' editorial team':name+' 편집부',
    description:en?brand.english+'. '+newsletter.meaningEnglish+'.':brand.name+'. '+newsletter.meaning+'.'}[kind]||name;
}
