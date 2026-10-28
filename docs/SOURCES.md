# 현재 실험용 뉴스 출처

2026-10-28 23:06 KST 확인. 기존 기사23건과 연결 지면·기사 태그·작업·수집 이력을 삭제했다. NASA/NOAA 사이트는 PAUSED, 주소는 비활성으로 보존했다. 회원·관리자·태그 사전·대표 관심사는 변경하지 않았다. 현재 실제 저장55건, 최근7일 지면3건. 정확한 시각·건수: [실행 증빙](evidence/news-reseed.json). 이전 verification.json의 NASA/NOAA 건수는 당시 검증 결과이며 현재 데이터가 아니다.

|출처|RSS|실험 가치|저장/최근7일|공식 이용조건|
|---|---|---|---|---|
|NIST News|https://www.nist.gov/news-events/news/rss.xml|양자내성 암호 전환, 반도체·측정 기술, 온라인 신원 보안|40 / 1|https://www.nist.gov/copyrights-disclaimers|
|NSF News|https://www.nsf.gov/rss/rss_www_news.xml|차세대 전자기술, AI·양자·과학 연구 동향|15 / 2|https://www.nsf.gov/policies/digital|

공식 피드 안내: https://www.nist.gov/coo/nist-rss-feeds 및 https://www.nsf.gov/rss . 각 호스트 robots.txt를 확인했고 해당 RSS 경로의 접근 금지는 없었다. NSF에서 금지한 /news/releases HTML은 수집하지 않는다. 현재 피드 응답에서 별도 copyright/rights reserved 표시는 발견하지 못했다. 이 점검을 모든 향후 자료에 대한 권리 보증으로 취급하지 않는다.

기관 작성 텍스트의 공개 이용 지침에 따라 제목·피드 설명 기반 추출 요약·원문 링크만 처리한다. 출처를 표시하고 제휴를 암시하지 않는다. 별도 저작권 표시 자료, 이미지·로고·전문·번역·외부 AI는 제외한다. rightsStatus=RESTRICTED, metadata/display/summary 허용, 나머지 권한 비활성. 영어 원문이며 번역되지 않았다. 수집 주기1시간. 등록·권리 설정·점검·미리보기·활성화·즉시 수집을 실제 관리자 API와 Java worker로 실행했다. 임시 합성 관리자 계정은 삭제했다.

뉴스의 기술·연구 주제를 추천·태그·요약 실험에 활용할 수 있다는 의미이며 조회수나 인기 순위를 검증한 '실시간 인기 트렌드' 데이터는 아니다. 55건 전체를 최신7일 기사로 위장하지 않으며 공개 지면은 기존 기간 정책을 유지한다.

## 검토했지만 현재 수집하지 않는 후보

- FTC: https://www.ftc.gov/feeds/press-release.xml — AI 서비스·디지털 광고·사기 대응 사례가 유용하다. https://www.ftc.gov/policy-notices/website-policy 에서 정부 작성 자료 이용 조건 확인. 일반 HTTPS 조회200과 달리 실제 Java 점검은502 source_http_rejected. 원인 확정 전 FAILED/비활성으로 유지했고 우회하지 않았다. 검증 요청 사이6초 대기하여 robots crawl-delay5초 준수.
- ScienceDaily: https://www.sciencedaily.com/newsfeeds.htm — RSS 활용 안내가 있지만 https://www.sciencedaily.com/terms.htm 의 데이터베이스 구축 제한과 저장 범위가 불명확하여 제외.
- 대한민국 정책브리핑: https://www.korea.kr/ — 한국어 산업·AI 정책과 기사별 공공누리 조건이 유용하지만 사이트에서 RSS 중단 안내 확인. 현재 지원하는 RSS/Atom/JSON 입력으로 검증되지 않아 활성화하지 않았다. HTML 수집기를 새로 만들지는 않았다.
- CISA: 공식 안내 조회403 등 접근 문제가 있어 이번 출처에서 제외.

사업 개시 전 콘텐츠 이용·법률 검토는 별도다. 아래 기록은 과거 실행 이력이다.

# 이전 초기 출처 — 현재 수집 중지

2026-10-28 공식 이용조건을 읽고 두 출처를 DB에 등록·기술 점검·활성화하여 실제 수집했다. 정확한 최종 성공 시각과 건수는 `evidence/verification.json`에 기록한다. 초기 수집은 NASA 10건, NOAA 10건이고 최근 7일 edition에는 해당 기간의 기사만 포함한다. 통합·UI fixture와 실제 데이터는 별도 DB/실행으로 분리했다.

|사이트|실제 수집 주소|권리 근거|허용 범위|
|---|---|---|---|
|NASA News Releases|https://www.nasa.gov/news-release/feed/|[NASA 사용 지침](https://www.nasa.gov/nasa-brand-center/images-and-media/)|기관 작성 뉴스의 제목·피드 설명·출처·원문 링크 및 허용 설명문의 추출형 요약. 출처 표시, 정보 목적, 제휴·추천 오인 금지. 제3자 저작물·이미지·로고 제외|
|NOAA News|https://www.noaa.gov/rss.xml|[NOAA 이용 안내](https://oceanservice.noaa.gov/about/faq.html)|별도 제한이 표시되지 않은 기관 작성 뉴스의 메타데이터·설명·추출형 요약. NOAA 표시, 정부 공식 편집물·제휴로 오인시키지 않음. 제3자 콘텐츠·이미지 제외|

두 주소 모두 권리 상태 RESTRICTED, metadata/display/summary만 허용했다. 본문 접근·보관·번역·이미지·외부 AI 처리는 비활성이다. 원문 언어는 영어이며 한국어 번역으로 표시하지 않는다. RSS 전체 응답은 처리 중 메모리에서만 읽고 DB에는 전문을 저장하지 않는다. 원문 HTML을 추가 방문하지 않는다.

RSS 제공이나 robots.txt를 이용허락으로 간주하지 않는다. 항목별 제3자 권리 표시나 조건 변경이 확인되면 해당 기사를 숨기고 출처의 허용 범위를 재검토한다. 이 근거는 전체 웹사이트의 모든 콘텐츠를 허가받았다는 뜻이 아니다. 한국어 출처는 명확한 권리 근거를 확보하지 못해 초기 출처에 넣지 않았다. 사업 개시 전 별도 콘텐츠·법률 검토가 필요하다.

아래는 이전 검증 절차이며 현재 출처 재구성 후 실행하면 NASA/NOAA를 재활성화하므로 실행하지 않는다. 이전 재검증은 로컬 합성 계정만 사용하는 `node scripts/local-smoke.mjs`로 실행한다. 점검·미리보기 최대5개와 실제 worker job 성공을 구분하고, 실행 후 합성 계정·세션을 삭제한다. 실제 운영 DB에서 테스트용 권한 부여를 실행하지 않는다.
