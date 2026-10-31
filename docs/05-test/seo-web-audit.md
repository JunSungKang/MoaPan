# SEO·웹 표준 점검 결과서

> 문서번호: MP-DOC-027 · 기준: 2026-11-01 현재 변경 · 상태: 유지관리 · 작성: 개발 담당 · 독립 SEO 감사: seo_audit 에이전트 · 코드 검토: 독립 리뷰 담당

## 1. 범위와 판정 원칙

전체 정적 HTML11개와 뉴스레터 상세를 점검했다. 별도 SEO 에이전트가 코드·실제 승인 HTTPS·공식 Google/네이버/Bing/W3C 지침을 읽기 전용으로 조사했다. 개발 담당은 브라우저·Nu·HTTP 재현 명령으로 보완했다. 검색 기술 준비도와 실제 검색엔진 색인·순위는 다르다. 임의 가중치의 SEO100점이나 검색 노출 확률을 만들지 않는다.

현재 공개 지면과 소개·정책은 색인 허용, 로그인·가입은 noindex, 뉴스레터·관심사·계정은 로그인, 관리는 ADMIN이 필요하다. 보호 페이지가 검색되지 않는 것을 결함으로 세지 않는다. robots/noindex는 인증을 대체하지 않는다. SEO 정책/크롤링 구조 변경은 이번 작업에서 구현하지 않았다.

## 2. 디자인·웹 표준 조치

| 문제 | 반영 | 확인 |
|---|---|---|
| 메인의 메뉴가 붙고 다른 글꼴로 보임 | 뉴스레터 전용 메뉴 규칙을 공통 site.css로 이동 | 모든 메뉴 system-ui/14px/21px, 링크 간격 실측 최소16px |
| 보조 페이지의 헤더/언어/계정/푸터가 제각각 | 동일 compact header·공유 토큰·링크 순서 | 비인증 폼 제외8개 보조 페이지 구조 통일 |
| 제목 의미/건너뛰기 | 페이지 H1·H2·skip link 정리 | Nu11페이지 메시지0, 각 상태 visible H1 1개 |
| 모바일 영문 검색 문구 줄바꿈 | 문구는 한 줄, 공간 부족 시 컨트롤 단위로 다음 줄 이동 | 브라우저 Range의 실제 한 줄 검증 |
| 영문 상세 복귀 링크에 한글 잔존 | 공통 사전에 누락 번역 추가 | 같은 UI의 한국어/영어 전환 |
| 소개의 로그인 필수 안내가 실제 정책과 모순 | 누구나 대표 지면, 회원은 개인 관심사 적용으로 교정 | 현재 코드·공개 정책 일치 |

11페이지+상세1개 × 한국어/영어2 ×390/768/1440px3 = **72상태**를 검사한다. 메뉴 경계·실제 계산 글꼴·가로 넘침·제목·푸터·JavaScript 오류를 점검하고 guest/member/admin·키보드·인쇄를 별도로 검사한다. 자동 링크 간격 거부 하한은12px이며16px은 이번 화면의 실측 최솟값이다. 인증 폼은 계속 중앙 정렬이며 신문 본문·잡지 본문·편집 작업 영역의 용도별 차이는 유지한다. 접근성 전체 인증을 수행했다는 의미는 아니다.

## 3. 페이지별 익명 접근·색인 정책

| 페이지 | 실제 익명 HTTP | 의도한 색인 | 제목·설명·lang·viewport | 최초 HTML 절대 canonical |
|---|---|---|---|---|
| / 및 /index.html | 200 | 허용 | 있음 | 상대경로 · 보완 대상 |
| /about.html | 200 | 허용 | 있음 | 상대경로 · 보완 대상 |
| /privacy.html | 200 | 허용 | 있음 | 상대경로 · 보완 대상 |
| /terms.html | 200 | 허용 | 있음 | 상대경로 · 보완 대상 |
| /login.html | 200 | noindex | 있음 | 불필요 |
| /signup.html | 200 | noindex | 있음 | 불필요 |
| /settings.html | 302 → login | noindex | 소스에 있음 | 불필요 |
| /account.html | 302 → login | noindex | 소스에 있음 | 불필요 |
| /newsletter.html | 302 → login | noindex | 소스에 있음 | 불필요 |
| /admin.html | 302 → login | noindex·ADMIN | 소스에 있음 | 불필요 |
| /newsletter-editor.html | 302 → login | noindex·ADMIN | 소스에 있음 | 불필요 |

| 확인 항목 | 결과 |
|---|---|
| 기본 문서 메타정보 | 11/11 존재·고유 title |
| 의도한 접근·색인 정책 | 11/11 일치 |
| Nu 정적 HTML | 11/11 검사, 오류0·경고0 |
| 공개 페이지 접근 | 4/4 정상HTTPS200 |
| 공개 HTTP canonical Link | 4/4 승인 origin의 절대 주소 |
| 원본 HTML 절대 canonical | **0/4**, 상대경로 사용 |
| sitemap | 200 XML, 공개4개만 포함, 가짜 lastmod 없음 |
| robots | 200, sitemap 절대주소·public/assets 허용·internal/admin API 차단 |
| 없는 페이지 | 404+noindex |
| 보호 API | 뉴스레터·관리 익명401 |
| 초기 기사 표시 | 원본 HTML 기사0, JavaScript 실행 후 첫7개 |
| 포털 실제 색인 | Google/네이버/Bing 각각 **미확인** |

User-Agent를 Googlebot/Yeti/bingbot 문자열로 바꾼 요청도 home/robots/edition에200이었다. 이는 실제 엔진 IP에서의 접근 증명이 아니다. 공개 edition은 한 번에 최대42개를 반환하지만 candidateCount는 더 넓은 공통 후보 수일 수 있다. 두 값을 혼동하지 않는다.

## 4. 검색 발견성 보완 과제

### P2 — 원본 HTML의 대표 URL

공개4페이지의 canonical href는 상대경로다. JavaScript가 절대화하고 HTTP Link도 절대주소를 제공하므로 Google의 canonical 전체 실패라고 단정하지 않는다. 다만 [네이버 지침](https://searchadvisor.naver.com/guide/markup-structure)은 절대 URL을 요구하며 [Google도 절대 URL을 권장](https://developers.google.com/search/docs/crawling-indexing/consolidate-duplicate-urls)한다. 승인 origin과 일치하는 최초 응답의 canonical 동기화를 별도 승인 후 검토해야 한다. /와/index.html은 현재 둘 다200이며 대표 주소 리다이렉트도 후속 검토 사항이다.

### P2 — 뒷면 기사·링크의 발견성

현재7개씩6면을 버튼·키보드·스와이프로 교체하고 개별 기사의 MOAPAN 고유 URL은 없다. [Google은 일반적으로 버튼을 클릭하지 않으므로](https://developers.google.com/search/docs/specialty/ecommerce/pagination-and-incremental-page-loading) 2~6면까지 발견된다고 보장할 수 없다. [Google의 JS 렌더링](https://developers.google.com/search/docs/crawling-indexing/javascript/javascript-seo-basics)과 [네이버의 JS 안내](https://searchadvisor.naver.com/guide/seo-advanced-javascript)를 함께 고려해야 한다. 공개 공통 지면에 한정한 크롤 가능한 URL/href를 검토하되, 기존 정적 HTML+REST 원칙·기사42개 제한·권리 조건을 유지해야 한다. 회원 본문 공개, 전체 DB dump, 원문 복제, 검색어 GET URL화·기록은 해결책으로 추가하지 않는다.

### P3 — 공유 표현·언어 메타데이터

공개페이지에 og:url/og:image/WebSite 구조화 데이터가 없다. 기본 색인의 필수 조건은 아니며 [Google 사이트명 지침](https://developers.google.com/search/docs/appearance/site-names)에 따라 실제 브랜드·승인 주소·자체 이미지로 보강할 수 있다. 존재하지 않는 운영자·도메인·기사URL·발행정보를 만들지 않는다.

루트 HTML은lang=ko지만 HTTP Content-Language는 요청 협상에 따라 en-US 또는 und 등으로 관찰됐다. [W3C 설명](https://www.w3.org/International/questions/qa-http-and-lang)처럼 HTTP의 audience 정보와 본문 언어는 구분해야 한다. 명확화 대상이며 이를 색인 차단으로 분류하지 않는다. 별도 영문URL 없이 브라우저 저장 언어로 전환하므로 영문 개별 색인/hreflang도 약속하지 않는다.

## 5. 실제 검색 표본과 확인 한계

독립 에이전트가2026-11-01 07:04 KST에 공개 검색을 조회했다. 일치 기준은 결과URL hostname이175.195.62.165인 경우다.

| 검색어 | 반환 표본 | 자체 URL 일치 |
|---|---|---:|
| "모아판" "MoA-PaN" | 결과 없음 | 0 |
| 모아판 MoA-PaN | 다른 서비스18건 | 0 |
| "175.195.62.165" | IP 대역 안내1건 | 0 |
| "https://175.195.62.165/" | 결과 없음 | 0 |
| site:175.195.62.165 | 결과 없음 | 0 |
| site:175.195.62.165 "모아판" | 결과 없음 | 0 |

이 도구의 검색 공급자·지역 SERP·전체 결과 수·순위는 명시되지 않았다. 세 포털을 각각 직접 검사한 결과가 아니며 결과 부재를 미색인 확정으로 바꾸지 않는다. [Google도 site: 결과가 전체 색인이 아니라고 설명](https://developers.google.com/search/docs/monitor-debug/search-operators/all-search-site)한다. IP라서 검색이 불가능하다는 근거도 확인하지 못했다.

실제 검색 상태는 소유권이 확인된 포털의 URL 검사와 sitemap 처리 결과가 필요하다. 이번 도구 목록에는 전용 Search Console/Search Advisor/Webmaster 검사 연결이 없으며 계정·소유확인 증빙도 사용하지 않았다. [네이버 소유 확인](https://searchadvisor.naver.com/guide/faq-start-register), [네이버 URL 검사](https://searchadvisor.naver.com/guide/url-inspection), [Bing URL 검사](https://www.bing.com/webmasters/help/URL-Inspection-55a30305)를 후속 운영 항목으로 남긴다. 등록 부재만으로 미색인을 확정하지 않는다. 사용자 승인 없이 외부 가입·제출·도메인 구매·DNS 변경·추적 SDK를 추가하지 않았다. 필드 Core Web Vitals/순위는 미검사다.

## 6. 재현·근거 관리

- UI: sh scripts/build/gradle-local.sh --no-daemon e2eTest, SiteDesignAuditTest의72상태 및 BrowserTest의 기존 기능 회귀.
- HTML: JAVA_HOME을 고정 JDK로 지정하고 sh scripts/test/html-check.sh. Nu26.10.12(0a70d65), JAR SHA25619f89e204cb881e2d88c5d0cd451df6c1ee8334e7fe626f6694b64a129f6a813을 실행 전 검증한다. 원격 latest가 바뀌면 검토 없이 바꾸지 않는다.
- HTTP: MOAPAN_AUDIT_BASE_URL=https://175.195.62.165 node scripts/test/site-http-audit.mjs. 본문·쿠키·계정 자격·검색어를 보고서에 보관하지 않는다.
- 실제 최종 결과·JAR/이미지 digest·보안 DB 시각·화면 해시는 [실행 증빙](evidence/design-seo-20261101.json)과 [시험 결과서](test-report.md)에 기록한다. 실패·미실행은 PASS로 합산하지 않는다. 화면·포털 검색 표본과 운영 HTTP 검사의 시각을 구분한다.

기존의9페이지/sitemap3개/index로그인필수 기록은 당시 결과이며, 현재 공개 정책·11페이지 감사로 대체한다.
