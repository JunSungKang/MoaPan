# 전체 미사용 코드 점검 결과서

> 문서번호: MP-DOC-032 · 기준: 저장소 현재 리비전 · 상태: 서비스 반영 검증 완료 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

## 범위와 판정 방법

운영 Java, HTML 11개, CSS 5개, 브라우저 ES Module, 테스트, Shell/Node/Go 운영 도구, Compose·Helm·Gradle·Spring 설정을 점검했다. Java는 실제 의존성 classpath로 JDK compiler의 symbol을 해석했고, 브라우저는 HTML 진입점·정적/동적 import·DOM 생성 코드를 추적했다. Node 도구는 명시적인 CLI·테스트 진입점으로 별도 분석했다.

단순 이름 검색이나 화면 한 번의 coverage만으로 삭제하지 않았다. Spring HTTP·스케줄·Bean, JPA·JSON record, 인터페이스 구현, 보안 회귀시험, 운영 명령의 호출을 구분했다. 최종 수치와 실행 산출물은 [증빙](evidence/dead-code-audit-20261102.json)에 기록한다.

## 제거 내역

| 영역 | 제거 내용 | 근거 |
|---|---|---|
| 지면 | `NewsService.tags()` | 현재 태그 API는 `TagDictionary`를 사용하며 옛 중복 조회의 소비자가 없음 |
| 기사 관리 | 인수 없는 `EditorialService.list()` | controller는 수집일·정렬 인수를 받는 현재 조회를 사용 |
| 수집 | 내부 `Raw.contentType`과 불필요한 헤더 읽기 | private 전송용 record의 필드를 읽는 코드·직렬화 소비자가 없음. 응답 제한·파서·TLS·리다이렉트 검사는 유지 |
| 부트스트랩 | 중복 migrate 분기, `launchCommand` 래퍼, 사용되지 않는 Flyway Bean | migrate는 main 앞에서 직접 실행 후 반환. 뒤 분기의 Bean 소비자와 활성 자동설정이 없음. 실제 migrate·권한 마감과 관리자 context 종료는 유지 |
| Java import | 미사용 import 14개 | 파일별 compiler symbol 참조로 확인. 모델 클래스 2개의 빈 줄은 유지해 debug line table까지 같게 보존 |
| 기사 화면 | `article-language.js` | 현재 어느 HTML/import 경로에서도 연결되지 않으며 옛 단일 translation DTO·기사별 버튼을 사용. 현재 `articleText` 및 전체 언어 선택으로 대체됨 |
| 검색·공통 JS | 로컬 검색 정렬·상한 분기, 쓰지 않는 import 4개·지역값 2개 | 현재 검색은 서버 POST 결과로 페이지를 구성. 지면 분할 함수는 지면만 처리 |
| CSS | 옛 스크랩·상세표·헤더 등 16개 토큰의 선택자 분기 37개 | 현재 HTML·DOM 생성에 없는 토큰만 제거. 동적 생성되는 `letter-teaser`·`latest-letter`는 보존 |
| 과거 도구 | Mac native 번역 실행기, 일회성 과거 증빙 조립기 | 현재 Compose/Helm/보안 검증 경로의 호출자가 없으며 현행 운영을 재현하지 못하는 이전 실행 경로 |

## 보존한 경로

- `ModelJson.content` 등 보안 parser 계약, 모델 인터페이스 구현과 package-private 캐시 검사 메서드는 실제 보안·단위 시험에서 사용한다.
- 운영·설치·백업·복원·source/AI smoke·권한 bootstrap 도구와 Go의 모듈 등록은 수동 명령·컨테이너·프레임워크 진입점이다.
- V1~V22 SQL, 기존 발간물·회원·기사, 라이선스와 과거 측정 증거는 제거하지 않는다.
- 모델 정책 관련 클래스 10개의 바이트가 이전 JAR와 일치한다. 정책 해시 때문에 기존 번역·분류가 불필요하게 재처리되는 변경을 피했다.

## 검증 상태

최종 330건 PASS(단위164/PostgreSQL107/브라우저59), failure/error/skip0. Nu HTML 메시지0. 390·768·1440px와 두 언어의72개 합성 화면 행렬, 모바일 지면·데스크톱 편집 화면도 확인했다. 소스/JAR/SBOM/Helm과 최종 앱·빌드 이미지 gate를 통과했고, 새 ignore는 추가하지 않았다. 기존 비밀번호 정책 S07과 공급자 백포트·코드 부재 판단은 해결됐다고 다시 표시하지 않는다.

중간 재실행에서 관리자 시험2건이 초기DOM의 enabled 상태를 준비 완료로 오인해 실패했다. 실제 옵션·가시성 또는 실패 안내를 기다리도록 시험의 준비 조건을 강화했으며 원래 assertion·30초 상한은 유지했다. 해당5건과 최종 전체330건을 재검증했다. 초기 실패 기록은 workspace .local/dead-code-20261102에 보존했다. 옛 번역 UI 시험은 현재 helper의 정상·누락·불완전 번역·원문 복귀·무전송 시험으로 옮겼고, 검색140개·20페이지·최신순 기준은 실제 서버/브라우저 시험에서 유지한다.

검사한 앱 sha256:ec89cdad8d05eef1a5bf22956b45f234810359034bcc68898204c1f1f22cb772를 실제 web/worker에 반영했다. 호스트·이미지·web·worker의 JAR SHA-256은4ece479b47d3285dece0f58b14f40cd7af96f99ae15bae854f21cca03d5a1cfd로 같다. 실제 migrate/admin-cli/web/worker 역할, HTTPS 공개/보호경계·캐시·정적304·삭제모듈404를 검증했다. 암호화 백업·격리복원·300기사/2계정/1발간물 ID집합보존·합성계정삭제를 확인했다. DB/모델/proxy는 교체하지 않았다.

독립 사전 검토에서 차단 결함은 없었다. 이 commit의 current-head 승인·develop 병합 결과는 해당 GitHub PR의 실제 상태를 기준으로 확인한다. 현재 코드·명시한 진입점과 시험의 판정이며 미래 확장·미확인 외부 소비자까지 보증하지 않는다.


## 2026-11-04 서버·데몬 후속 정리


최종 이 프로젝트 결과: **clean check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest PASS**, 19 tasks 실행. JUnit 단위184/PG147/UI77 =408 PASS, FAIL/ERROR/SKIP0. 키 경계/보존7건, Compose3모드/12누락 거부, Helm STARTTLS·SMTPS와4메일설정 거부·기본3거부, Nu 메시지0을 확인했다. 첫 clean은 새 시험의 build 디렉터리 준비 누락으로 sourceCheck 실패했고 이를 고친 뒤 동일 clean 전체 명령을 재실행해 통과했다. 판정 기준·assertion·timeout을 낮추지 않았다.

소스 DS-0002 HIGH는 실제 서버 구성 퇴역 후0건이며 source/JAR/SBOM/두 Helm 시나리오 및 최종 앱·빌드 이미지 gate PASS다. source에는 JOSE3건과 x/crypto UNKNOWN2건(중복)이 원시 검출됐으며, JOSE에는 종전의 검증된 백포트/영향 근거를, UNKNOWN에는 기존 OpenPGP 코드 부재 판단을 유지했고 새로운 ignore/예외나 scanner 제외를 추가하지 않았다. JAR/SBOM·최종 이미지 취약점/secret은0건이다. JAR684f13607aaeae9216f8ac373d35f82fff48ea7968e182208390c6978daef6fb, runtime058f45121034986b64c7f64f4e20b26654f494421a7cb00deddd3d5065e4945e, builder23bd0e0bcc555c0186f1a4baeb43809e0a22fcb9ffb4e0cb5067709a89e80fc9를 대조했다.

추가 사용자 요청에 따라 미사용 서버/데몬·시작 설정을 조사했다. 체크섬이 기존 소유 소스와 같은 생성 SMTP context7파일과 미사용 내장 SMTP 이미지1개를 제거했다. 컨테이너 마운트/열린 파일/현재 시작 참조가 없는 이전 Mac Ollama·llama/RPC 런타임2곳116파일과 불필요한 검증된 소스 압축파일1개, 현재 컨테이너/소스/최종 검증 이미지가 참조하지 않는 과거 프로젝트 이미지 태그36개(실제 이미지ID34개)를 제거했다. 전역 prune/force/볼륨 삭제·타 프로젝트 변경은 하지 않았다.

현재 Java85파일의 compiler symbol(비프레임워크 private264/무주석 type113) 참조 후보0·오류0, HTML13개 진입점의 JS42개 모두 연결/누락0을 확인했다. record 자동 필드·프레임워크 진입점은 삭제 근거로 삼지 않는다. 현재6서비스와 정상 종료된 필수 migration/model-init3개를 보존했다. host Node2개는 ChatGPT CUA 도구이고 TCP listener가 없어 앱 데몬으로 취급하지 않았다. 기존 DB V24와 앱24fc8045a50c·HTTPS200 유지, 실제 회원/키/모델/볼륨은 미변경이다. 외부 MailBox의 고위험과 root 미승인은 미해결이며 본 프로젝트 gate PASS를 외부 해결/운영 전환으로 확대하지 않는다.

[최종 기계 증빙](evidence/smtp-boundary-cleanup-20261104.json). 실행 로그는 .local/smtp-boundary-fix-20261104의 clean-final-retry.log/final-image-scans.log/helm-check.log이며 최초 실패도 보존했다. 임시 분석 Java 코드는 제거했고 삭제 코드·실행파일의 별도 백업은 만들지 않았다.
