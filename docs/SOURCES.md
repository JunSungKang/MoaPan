# 실제 초기 출처

2026-10-28 공식 이용조건을 읽고 두 출처를 DB에 등록·기술 점검·활성화하여 실제 수집했다. 정확한 최종 성공 시각과 건수는 `evidence/verification.json`에 기록한다. 초기 수집은 NASA 10건, NOAA 10건이고 최근 7일 edition에는 해당 기간의 기사만 포함한다. 통합·UI fixture와 실제 데이터는 별도 DB/실행으로 분리했다.

|사이트|실제 수집 주소|권리 근거|허용 범위|
|---|---|---|---|
|NASA News Releases|https://www.nasa.gov/news-release/feed/|[NASA 사용 지침](https://www.nasa.gov/nasa-brand-center/images-and-media/)|기관 작성 뉴스의 제목·피드 설명·출처·원문 링크 및 허용 설명문의 추출형 요약. 출처 표시, 정보 목적, 제휴·추천 오인 금지. 제3자 저작물·이미지·로고 제외|
|NOAA News|https://www.noaa.gov/rss.xml|[NOAA 이용 안내](https://oceanservice.noaa.gov/about/faq.html)|별도 제한이 표시되지 않은 기관 작성 뉴스의 메타데이터·설명·추출형 요약. NOAA 표시, 정부 공식 편집물·제휴로 오인시키지 않음. 제3자 콘텐츠·이미지 제외|

두 주소 모두 권리 상태 RESTRICTED, metadata/display/summary만 허용했다. 본문 접근·보관·번역·이미지·외부 AI 처리는 비활성이다. 원문 언어는 영어이며 한국어 번역으로 표시하지 않는다. RSS 전체 응답은 처리 중 메모리에서만 읽고 DB에는 전문을 저장하지 않는다. 원문 HTML을 추가 방문하지 않는다.

RSS 제공이나 robots.txt를 이용허락으로 간주하지 않는다. 항목별 제3자 권리 표시나 조건 변경이 확인되면 해당 기사를 숨기고 출처의 허용 범위를 재검토한다. 이 근거는 전체 웹사이트의 모든 콘텐츠를 허가받았다는 뜻이 아니다. 한국어 출처는 명확한 권리 근거를 확보하지 못해 초기 출처에 넣지 않았다. 사업 개시 전 별도 콘텐츠·법률 검토가 필요하다.

재검증은 로컬 합성 계정만 사용하는 `node scripts/local-smoke.mjs`로 실행한다. 점검·미리보기 최대5개와 실제 worker job 성공을 구분하고, 실행 후 합성 계정·세션을 삭제한다. 실제 운영 DB에서 테스트용 권한 부여를 실행하지 않는다.
