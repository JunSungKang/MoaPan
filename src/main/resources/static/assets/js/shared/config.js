// 대표 문구를 교체할 때 mastheadQuote만 수정합니다. 외부 요청이나 자동 순환은 없습니다.
export const brand = Object.freeze({
  name: '모아판',
  english: 'MoA-PaN',
  mastheadQuote: '오늘의 조각을 모아,\n세상을 넓게 읽다.'
});

// 자체 호스팅 광고 이미지와 목적지 HTTPS 링크를 설정합니다. 추적 스크립트는 사용하지 않습니다.
export const advertisement = Object.freeze({
  imageUrl: '', // 예: /assets/images/sponsor.webp
  linkUrl: '', // 예: https://example.com/
  alt: '광고'
});
