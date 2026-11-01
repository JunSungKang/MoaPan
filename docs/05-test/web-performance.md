# 공통 코드·브라우저 성능 검증서

- 산출물 ID: MP-DOC-030
- 기준: f19d5e539f84da72cb21a7908efd583b889a747f → 이번 성능 PR의 검증 소스
- 방법론: Sprint Delivery
- 판정: 구현·로컬 검증 PASS. 이번 PR의 병합 및 운영 배포는 별도 승인 대기.
- [실측 원본·테스트·이미지 증빙](evidence/web-performance-20261101.json), [전체 자원 목록·해시](evidence/web-asset-inventory-20261101.json)

## 1. 전수 조사와 적용 범위

11개 HTML, 기존37개 JS,5개 CSS를 전수 목록화하고 각 진입점과 import, 국제화, 포맷터, API·이벤트 수명을 검토했다. DOM/포맷터 공통 모듈2개를 추가했다. 백엔드 Java62개도 정적 목록·JSON mapper/정규식/HTTP 캐시 반복 패턴을 조사했다. 이를 백엔드 전체의 보안·성능 감사 완료로 표현하지 않는다.

| 영역 | 확인한 낭비·경계 | 적용 또는 유지 근거 |
|---|---|---|
| 전체 화면 공통 | el/date를 포함한 페이지 초기화 모듈을 leaf UI까지 import | dom.js/format.js로 분리. 인증·메뉴·광고 초기화 없이 공통 함수만 재사용. 기존 common.js export 호환 유지 |
| 로그인·지면 | 관리자·약관·광고 번역 사전을 모두 적재 | admin은 관리자·광고 사전, about/privacy/terms/admin은 해당 페이지 사전만 적재. 일반 화면은 불필요39KB 가까운 사전 전송 제외 |
| 국제화 DOM | 작은 변경마다 전체 문서 텍스트·속성 순회 | 추가·변경된 연결 노드만 처리, 조상 범위 중복 제거. WeakMap으로 원문 연결, 삭제한 DOM을 별도 보관하지 않음 |
| 공통 날짜 | 날짜마다 Intl.DateTimeFormat 생성 | 서울 시간의4개 고정 형식×2개 언어, 최대8개 인스턴스 재사용 |
| 오늘의 지면 | 계정→태그→대표 관심사 조회 직렬화 | 서로 독립인 최초 조회를 Promise.all로 묶음. 제한된 기사 한 번 로드 및 검색 정책 유지 |
| 광고·발간물 | 사용 전부터 선택 기능 코드를 적재 | 활성 광고가 있을 때 banner, 복사·파일 버튼 사용 시 publication-output 적재. native ES Module/HTTP 캐시 재사용 |
| CSRF | 동시 쓰기가 토큰을 각각 조회 | 동일 진행 중 조회만 공유. 로그인/로그아웃/401 경계 이전 결과를 복원하지 않음. 기사·API 응답 캐시는 추가하지 않음 |
| 관리자 기사 | 방문한 깨끗한 편집기 DOM이 계속 남음 | 수정하지 않은 과거 편집기는 dispose. 미저장·저장 중 편집기는 보호. 저장 성공 자료를 갱신해 재이동 시 옛 내용 복원 방지 |
| 관리자 주제 | 인증 상실 후 사전 캐시·느린 요청이 남을 수 있음 | 문서 메모리 사전을 비우고 이전 응답의 재등록 차단 |
| 정적 HTTP | 기본 no-store로 JS/CSS 반복 전체 전송 | assets에만 public,no-cache 및 내용 SHA-256 weak ETag. 일치 시304, 다르면200. 이름이 고정돼 immutable 장기 캐시를 쓰지 않음 |
| 프록시3종 | 정적 텍스트 압축 누락 | Compose·외부 Caddy·Helm에 assets 전용 zstd/gzip. API/CSRF 압축 제외, Vary 확인 |
| CSS·원문 렌더·URL 검증 | 현재 공통 site.css를 전 화면이 재사용. 원문과 UI, 허용 URL 정책은 서로 다름 | 레이아웃을 다시 쓰거나 보안 정책이 다른 함수를 억지로 합치지 않음. 원문 렌더는 UI 자동 번역과 분리 유지 |
| 백엔드 JSON·정규식 | 도메인별 JSON 검증, 기사 fingerprint, SSRF·LLM guard의 목적이 서로 다름 | 이번 병목은 브라우저 적재/순회/정적 전송이다. 인증·검증 경계가 다른 backend 함수를 무리하게 묶거나 API 결과를 캐시하지 않음 |
| IndexedDB·암호화 파일·다운로드 | 계정별 분리·작업 완료 시 닫기/URL 해제가 이미 있음 | 현재 수명 관리 유지. 비밀번호·기사·클릭을 새 영속 캐시에 넣지 않음 |

## 2. 전후 측정

실제 Spring HTTP·일회용 PostgreSQL·headless Chromium을 사용했다. 브라우저 라우팅 가로채기는 HTTP 캐시를 꺼버리므로 사용하지 않았다. 로그인·지면 각각 신규 컨텍스트3회, 최초 접속 후 같은 컨텍스트 reload를 비교한 중앙값이다. assets만 집계하며 API/HTML은 제외했다. 서버 압축 없는 조건이므로 아래 수치에는 gzip 효과가 중복 합산되지 않았다.

| 측정 | 변경 전(byte) | 변경 후(byte) | 감소 |
|---|---:|---:|---:|
| 로그인 초기 JS 본문 | 95764 | 58304 | 39.1% |
| 지면 초기 JS 본문 | 103603 | 66034 | 36.3% |
| 로그인 최초 assets 전송 | 126123 | 88063 | 30.2% |
| 지면 최초 assets 전송 | 131505 | 93336 | 29.0% |
| 로그인 재접속 assets 전송 | 126123 | 4200 | 96.7% |
| 지면 재접속 assets 전송 | 131505 | 5100 | 96.1% |

- 텍스트2,000개 배치 후20회 작은 변경: TreeWalker가 반환한 텍스트 수 40780 → 0. 변경된 텍스트의 직접 처리는 계속 수행하므로 번역 작업이0이라는 뜻은 아니다.
- 날짜1,000회 표시: 포맷터 1000 → 1개 생성. 이 배치 시간은 54.1 → 12.9ms(한 배치 참고값).
- 기사30개 순회 후 깨끗한 편집기 DOM1개 유지, 미저장 초안은 별도 보존하는 브라우저 회귀 통과. 절대 RAM 감소량을 측정했다고 주장하지 않는다.
- cold loadEventEnd 중앙값: 로그인 59.1 → 51.3ms, 지면 80.0 → 70.5ms. 로컬3표본이며 통계적 유의성·실제 인터넷 전체 개선률을 보장하지 않는다.
- 격리된 실제 Caddy 이미지의 공통 JS: 7439 → 2603byte(65.0% 감소), 압축 해제 동일성·Vary·API 비압축 확인.

## 3. 기능·보안 검증

- 단위155 / PostgreSQL 통합68 / 브라우저59 =282 PASS, 실패·오류·건너뜀0.
- 전체11페이지·두 언어·390/768/1440px 기존 회귀, 동적 번역 왕복·원문/textarea/pre 보존, 동시 CSRF 및 인증 변경 경합, 초안·저장 자료 보존 추가 검증.
- 실제 HTTP 재접속 전송 감소 및 PostgreSQL MockMvc304/변경 validator/오류 no-store/보호 화면·API 인증 확인. API quota와 인증 필터를 건너뛰지 않음.
- Nu HTML0건, 소스/JAR/SBOM 및 새 web/proxy 이미지 검사 게이트 PASS. 기존 보안 검토의 예외나 정책 차이를 이번 작업으로 해소했다고 주장하지 않음.
- proxy scanner의 GO-2026-5932 UNKNOWN1건은 기존 검토에서 OpenPGP 하위 패키지 미포함으로 NOT_AFFECTED다. 새 Caddy 실행 파일 SHA-256이 기존 검증본과 같음을 재확인했다. scanner 결과를 숨기거나 새 ignore를 추가하지 않았다.
- 운영 컨테이너6개의 ID 일치. 운영 DB·서비스 재배포 없음. 새 이미지는 격리된 검증 태그이며 운영 교체는 미실행.

## 4. 재검증 명령

```sh
sh scripts/build/gradle-local.sh --no-daemon check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest
sh scripts/test/html-check.sh
# 실제 HTTP 측정만 별도 실행
sh scripts/build/gradle-local.sh --no-daemon e2eTest --tests com.moapan.WebPerformanceTest
```

측정 결과는 build/reports/performance/after.json, 이미지 검사는 build/reports/security에 생성된다. 기준 측정은 제품 수정 전 동일 테스트의 PERFORMANCE_STAGE=baseline으로 실행했다. 해당 플래그는 측정 모드이며 기본 실행의 회귀 조건을 낮추지 않는다. 압축 확인은 격리 Caddy에서 수행했으며 실제 Spring 캐시 검증과 구분한다.

## 5. 공식 근거

- [Spring 정적 리소스·조건부 요청](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/static-resources.html)
- [Spring ETag 설정 API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/config/annotation/ResourceHandlerRegistration.html)
- [Caddy encode](https://caddyserver.com/docs/caddyfile/directives/encode)
- [HTTP 캐시](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Caching)
