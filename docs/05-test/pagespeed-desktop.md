# PageSpeed·웹 표준 및 최종 코드 정리 검증서

- 산출물 ID: MP-DOC-033
- 상태: 구현·서비스 반영 검증 완료
- 대상: 현재 공개 HTTPS 지면, 정적11페이지, Java67파일, 브라우저38모듈, CSS4파일, scripts84파일.
- 방법론: Sprint Delivery. 승인 범위: 성능·표준·SEO 개선, 입증된 죽은 코드의 무백업 삭제, 서비스 반영 및 develop 병합. CI/CD와 main은 제외.

## 1. 기준 측정

[사용자 제공 보고서](https://pagespeed.web.dev/analysis/https-175-195-62-165/hzwkh9jxig?form_factor=desktop): 2026-11-01 12:27 KST 데스크톱 성능88·접근성100·권장사항96·SEO100, CLS0.244, LCP0.4초, TBT0.

[착수 시 재측정](https://pagespeed.web.dev/analysis/https-175-195-62-165/sk4krz712f?form_factor=desktop): 2026-11-02 06:15 KST 데스크톱88/100/96/100, CLS0.244, LCP0.3초. 같은 실행의 모바일77/100/96/100, CLS0.677, LCP1.4초. 단일 실험값이며 사용자 실측(CrUX)은 없다. 과거 모바일 점수와 최신값은 구분한다.

## 2. 원인과 구현

- footer가 기사·동적 메뉴/상태 요소 뒤에서 이동했다. 콘텐츠 메뉴는 정적 HTML로 제공하고 날짜·언어/검색·상태 영역과 비동기 지면의 최소 읽기 공간을 확보했다.
- 정상 비회원 확인을 계정API401에 의존해 콘솔 오류가 발생했다. GET /api/v1/auth/session을 익명204/현재 유효계정200으로 분리했다. 보호된 account401·권한·CSRF·no-store·전역 한도는 유지한다.
- 실제 사용하는 공통 site.css 규칙을 newspaper.css로 합쳐 첫 CSS 요청을2개에서1개로 줄였다. 원래 파일과 HTML 참조를 제거했다. 로그인 언어 메뉴의 중복 font 선언은 공통 규칙으로 통일했다.
- 지면에서 실제 사용하는 ES Module만 modulepreload로 병렬 발견한다. 번들러나 외부 런타임은 추가하지 않았다.
- 공개 정적 문서/발견용 robots·sitemap과 assets만 압축한다. 토큰/계정 등 동적 API와 보호 HTML은 압축하지 않는다.
- COOP same-origin, Trusted Types 강제 sink 차단을 적용했다. HSTS 헤더는1년/includeSubDomains이나 IP literal에는 실제 HSTS가 적용되지 않는다. 도메인 preload 등록을 수행하거나 가능하다고 주장하지 않는다.
- 공개4페이지 메타데이터·canonical/sitemap과 보호 페이지 noindex를 보존했다. 독립 SEO 소스 검토에서 신규 색인/권한 회귀는 발견되지 않았다. 점수100은 실제 색인·노출 순위 보장이 아니다.

## 3. 최종 죽은 코드 점검

Java는 JDK compiler symbol 분석(parse/analyze, 코드 생성 없음)으로67파일/723선언을 조회하고 메서드 참조·Spring/JPA/JSON/레코드 진입점을 구분했다. 브라우저는 Acorn8.16.0 import/dynamic-import 그래프에서38/38모듈이11 HTML의 실제 진입점으로 연결됨을 확인했다. CSS selector는 HTML과 JS DOM/문자열을 함께 확인하며 동적 합성 이름을 별도 검토했다. scripts84파일에서 shell21개와 Node19개의 문법을 검사했다. 이는 동적 경로의 모든 실행 가능성 증명이 아니며 전체 회귀/운영 검증을 함께 사용한다.

6슬라이드/42개 제한 도입 뒤 항상 hidden으로만 남던 더 불러오기 버튼과 도달 불가능한 cursor 추가요청 분기·상태·이벤트·스타일을 삭제했다. 서버 cursor API는 실제 계약/보안 회귀에서 사용하므로 보존한다. 초기 슬라이드 버튼은 자료가 준비되기 전 비활성으로 두어 조기 클릭 오류도 방지했다. 제거 코드를 별도 파일·archive·stash로 백업하지 않았다. 원문/회원자료·DB/모델은 변경하지 않는다.

## 4. 실제 검증 결과

[최종 외부 PageSpeed 보고서](https://pagespeed.web.dev/analysis/https-175-195-62-165/aofq5k9r8s?form_factor=desktop), 2026-11-02 06:40 KST, Lighthouse13.6.0 / Chrome153.0.8010.36. 모바일/데스크톱 에뮬레이션이며 실제 이용자 CrUX 자료는 없다.

| 항목 | 수정 전 데스크톱 | 최종 데스크톱 | 최종 모바일 |
|---|---:|---:|---:|
| 성능 | 88 | 100 | 100 |
| 접근성 | 100 | 100 | 100 |
| 권장사항 | 96 | 100 | 100 |
| SEO | 100 | 100 | 100 |
| CLS | 0.244 | 0 | 0 |
| LCP | 0.321초 | 0.339초 | 1.386초 |
| TBT | 0 | 0 | 0 |

- 같은 시점 수정 전 모바일77→100, CLS0.677→0. 점수는 측정 조건에 따라 달라지며 검색 순위/실사용자 성능 보장이 아니다.
- 최종336시험 PASS(단위164, PostgreSQL111, Playwright61), 실패/오류/skip0. 지연1.2초 합성 뉴스에서390/768/1440px 모두CLS0·콘솔 오류0. 72상태/한영/키보드·검색·인쇄·메뉴·자료분리 회귀와 실제 Spring Trusted Types 차단/안전 원고 내보내기 검증 포함.
- Nu HTML validator26.10.12:11페이지 오류0/경고0. 독립 SEO 검토의 로그인 언어 font 회귀를 수정했다. 합성 HttpServer 출력 파싱 테스트와 실제 Spring CSP 실행 검증을 구분했다.
- 초기 API 모의 응답의204 본문/옛 account 경로 때문에 생긴 UI 실패는 fixture를 실제 계약에 맞춰 수정했다. source 컴파일의 메서드명 누락도 수정했다. 테스트를 삭제하거나 판정 기준/timeout을 완화하지 않았다. 최종 전체 명령은 재실행하여 통과했다.
- 실제 HTTPS 검사에서 HEAD의 보안/캐시 헤더 누락도 발견했다. HeaderWriterFilter를 응답 본문 쓰기 전 실행하게 변경하고 HEAD·정적 ETag·보호 API 회귀를 추가했다. 공개 프록시의 잘못된 Content-Language 기본값과 외부에 연결되지 않는 h3:8443 광고도 제거했다. h1/h2·HTTPS·기존 포트/인증서는 유지했다.
- 최종 JAR/SBOM/source/Helm·세 이미지 검사 gate PASS. 이미지 비밀값0, runtime/builder raw취약점0. proxy의 기존 GO-2026-5932 UNKNOWN은 기존 정확한 코드 비노출 근거로 판정했으며 신규 ignore/예외는 없다. DB 동기화2026-11-01T20:52:15.050556Z(검사 시24시간 이내), Trivy0.76.1. 상세 적용 판단은 운영 보안 검토서를 따른다.
- 최종 runtime sha256:fba510fc15d13e3a078a6a27185d763a9046badb2aa7294e0790450e117db7f2, builder sha256:d4741202ca5253fb0a7e6a8c2d1ff14e3b061032fdd0eb76c92da33c8f63d679, proxy sha256:453e34b953b74d07e425de9383bf2d7e3190deba1fe828eb34b036c69874074c. JAR SHA-256 3c1877697b477650eb30e2dd84199b3612b3e0390daddce817c1d7da0fab5e9e. 호스트/이미지/web/worker JAR 일치.
- JDK Temurin27.0.1.0.2+1, truststore117roots, 공식4개 제거 대상 부재, 정상HTTPS 성공/14대상 TLS 거부. 첫 offline trust 실행은 외부 연결을 할 수 없어 검증 실패였으며 네트워크를 허용한 동일 최종 이미지로 재검증했다. 우회 인증서 검증은 사용하지 않았다.
- 기존 DB 기사300·계정2·뉴스레터1, schemaV22·대상 ID 집합 해시 보존. web/worker/proxy/db/번역/분류6컨테이너 정상, worker heartbeat 확인. 모델 정책10클래스 바이트 동일. 실제 HTTPS 합성 계정 가입/로그인/권한 거부/탈퇴/폐기 세션 검증 후 테스트 계정 삭제.
- CSS 초기2요청→1, 공개 HTML gzip과 조건부 ETag304 확인. API/보호 HTML은 비압축/no-store 유지, 삭제 site.css는404. guest session204/계정401/관리401, 일반 회원관리403 확인. 비밀값·원문·회원설정·쿠키는 증빙에 기록하지 않았다.

남은 Lighthouse의 critical CSS·dependency tree·LCP breakdown은 점수 감점 실패가 아닌 진단이다. 내용이 잠깐 무스타일로 보이게 하거나 CSP를 약화해 진단을 숨기지 않았다. HSTS preload 지시는 IP literal에서 사용할 수 없어 추가하지 않았다. 수동 보조공학 전체 검증/실제 포털 색인 등록은 수행하지 않았다. GitHub 최종 병합 상태와 cleanup은 해당 PR과 작업 완료 보고가 정본이다.

## 5. 근거

- [Google CLS 최적화](https://web.dev/articles/optimize-cls)
- [Chrome 콘솔 오류 검사](https://developer.chrome.com/docs/lighthouse/best-practices/errors-in-console)
- [Google canonical 지침](https://developers.google.com/search/docs/crawling-indexing/consolidate-duplicate-urls)
- [HSTS 표준](https://www.rfc-editor.org/rfc/rfc6797#section-8.1.1)
