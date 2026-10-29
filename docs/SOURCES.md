# 현재 수집 범위 — 2026-10-29

2026-10-10(한국시간, 날짜 포함) 이후 보안 취약점·LLM 성능·RAG·데이터베이스·검색 효율 자료를 수집한다. GitHub Advisory30건과 arXiv 논문100건이 현재 공개 대상이다. NIST 사이버보안 RSS는 등록됐으나 해당 피드의 대상자료0건. 수집주소·검색식: [등록 정의](topic-sources.json), 최신 실행: [증빙](evidence/topic-collection.json). 기존55건은 비공개 보존한다. 아래 10월28일 기록은 과거 상태다.

## 2026-10-28 실험용 출처 기록

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

아래는 이전 검증 절차이며 현재 출처 재구성 후 실행하면 NASA/NOAA를 재활성화하므로 실행하지 않는다. 이전 재검증은 로컬 합성 계정만 사용하는 `node scripts/test/local-smoke.mjs`로 실행한다. 점검·미리보기 최대5개와 실제 worker job 성공을 구분하고, 실행 후 합성 계정·세션을 삭제한다. 실제 운영 DB에서 테스트용 권한 부여를 실행하지 않는다.

## 2026-10-29 분야별 수집과 논문
현재 등록 정의는 [topic-sources.json](topic-sources.json), 실제 결과는 evidence/topic-collection.json을 확인한다. 2026-10-10 00:00 Asia/Seoul(2026-10-09T15:00Z) 이상 발행 자료만 허용하며 날짜 불명/미래로 무효화된 자료는 저장하지 않는다. 기존55건은 삭제하지 않고 비공개 보존했고 이전 넓은 범위 출처를 중지했다.

- NIST 사이버보안 RSS: 기관 작성 텍스트, 기존 공식 이용조건 적용.
- GitHub Advisory API: reviewed·미철회 공지, published 필터와 저장 전 날짜 검사. CC BY4.0 출처·원문·라이선스와 추출/축약 표시를 소개 페이지에 제공. 원문코드/실행지시는 실행하지 않는다.
- arXiv: 공식 API https://info.arxiv.org/help/api/user-manual.html 의 검색식으로 보안(cs.CR), LLM 성능/평가, RAG, DB(cs.DB), 검색 효율(cs.IR)5개 주소. 앞선 주제 검색식을 ANDNOT으로 제외하여 주소 간 중복을 줄인다. https://info.arxiv.org/help/api/tou.html 은 제목·초록 등 descriptive metadata의 CC0 저장·변환·공유를 명시적으로 허용한다. PDF/전문은 수집하지 않는다. export.arxiv.org robots의 일반 크롤러 금지와 공식 API 사용 허가를 구분하며 HTML crawling은 하지 않는다. PostgreSQL 공유 admission lock으로 모든 web preview/worker의 arXiv 동시 요청1개와3초 간격을 강제한다. API 검색은 제출일, 저장 검증은 Atom published이며 updated를 최초 발행일로 사용하지 않는다.

각 arXiv 주소 최신20건, 보안 API 최신30건,6시간 주기. 기간 전체를 빠짐없이 backfill하거나 새 자료가20건을 넘을 때 모두 수집하는 계약은 아니다. 요청된 주제와 관련된 메타데이터 검색이며 논문 타당성·동료심사·성능 재현을 보증하지 않는다. 영어 원문 유지.

대안 검토: OpenAlex는 키 없는 기본 조회 가능하지만 대규모/커서 페이지는 인증키 필요(https://help.openalex.org/api/authentication/); 이번에는 키와 추가 어댑터 없이 적용 가능한 arXiv 선택. Crossref는 제목·DOI 검색 가능하지만 일부 초록은 저작권 제한이 있어 무조건 요약할 수 없다(https://www.crossref.org/documentation/retrieve-metadata/rest-api/). Semantic Scholar 공식 API 안내는403으로 이번 검증 미완료. Elastic은 약관상 자동 수집/재게시 제한(https://www.elastic.co/legal/terms-of-use), Hugging Face blog 저장·요약의 명시적 재이용 허락은 확인하지 못해 활성화하지 않는다. PostgreSQL 소프트웨어 라이선스를 모든 커뮤니티 뉴스의 재이용 허락으로 확대 해석하지 않는다.

최종 결과: 보안 공지30건 + 보안/LLM평가/RAG/DB/검색효율 논문 각20건 =130건. NIST 주제RSS0건(비정상 개수 채우기 없음). 공개 URL 중복0, 기준일 위반0. 전체 기간 전수 수집이 아닌 최신 표본이다. 실행 명령: MOAPAN_SMOKE_BASE_URL=https://175.195.62.165 node scripts/test/topic-source-smoke.mjs (관리자 CLI를 사용할 수 있는 승인된 로컬에서만). 개별 수집 실패가 있으면 스크립트도 실패 종료한다.

### 카드 메타데이터 갱신
2026-10-29 KST: 기존 승인 출처 재수집으로 arXiv 논문 100건에 공개 저자 메타데이터를 저장했다. GitHub 공지에는 출판 저자가 제공되지 않아 임의의 제보자/기관으로 대체하지 않는다. 요약은 연구 방법·평가·한계 및 취약점 영향/대응 관련 원문 문장 우선 추출로 변경했다. 최신 피드에서 벗어난 기록은 기존 허용 요약 안에서만 재선정한다. 수집 권리·날짜 조건·외부 본문 접근 범위는 변경하지 않았다.

## 로컬 번역 권리 재검토 (2026-10-29)
arXiv API descriptive metadata의 CC0 및 GitHub Advisory Database CC BY4.0의 변형 허용을 공식 약관/라이선스에서 확인했다. 이 두 출처의 기존 저장 제목·요약에 한해 allow_translation을 명시적으로 켜고 로컬 모델로 처리한다. 본문/PDF·이미지·외부 AI·회원정보 처리 권한은 추가하지 않는다. 소개 페이지에서 자동 번역을 포함한 변경 사실과 원문/라이선스를 안내한다. 다른 출처는 번역 비활성 유지.
