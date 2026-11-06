현재 접속: **https://175.195.62.165/** — 로컬 Mac 실행, 휴대폰 데이터망 확인 완료. 공개 모드 실행/중지는 [배포 안내](docs/06-transition/deployment.md)를 참고한다.

# 담고담고 · DamGoX2

여러 출처의 뉴스를 관심사에 맞게 모아 종이신문처럼 읽는 개인화 뉴스 V1. Java 27 / Spring Boot 4 / Gradle / PostgreSQL / 정적 HTML·CSS·Vanilla ES Modules / Docker Compose 기반이다.

**CI·CD, GitHub Actions, 예약 workflow는 사용자 요청에 따라 구현·실행하지 않는다.** 검증은 로컬 명령으로 실행한다. 외부 HTTPS 배포와 사업 개시는 별도 승인·검토가 필요하다. 실제 인수 상태는 [진행 상태](docs/01-management/progress.md)와 [테스트 보고](docs/05-test/test-report.md)를 확인한다.

웹 품질·패턴 재검토: [최종 점검서](docs/05-test/web-quality.md). 재현 명령은 `sh scripts/build/gradle-local.sh --no-daemon webQualityAudit`이며, Node/npm은 잠금된 Lighthouse·axe 감사 도구에 사용한다. 감사는 폐기 PostgreSQL·합성 자료를 사용하고 종료 시 테스트 서버를 정리한다.

## 로컬 실행

```sh
cp .env.example .env
# .env를 비공개로 편집: DATABASE_PASSWORD는 64자 이상의 fresh random hex,
# DATABASE_ADMIN_PASSWORD와 DAMGOX2_SECRET은 각각 다른 충분한 random secret 사용.
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

접속: http://localhost:8080. 기본 loopback HTTP에서는 합성 계정만 사용한다. DB와 web 포트는 host에 공개하지 않는다. `.env`, tool cache, 검사 원본, 개인 backup은 `.local`과 함께 Git에서 제외한다. `.env.example`을 실제 운영 secret으로 사용하면 안 된다.

첫 기동은 migrate가 Flyway를 실행한 뒤 web·worker가 시작된다. web/worker는 app 전용 non-superuser DB 역할, migrate는 별도 자격으로 실행한다. app 이미지는 Alpine3.24.3과 Temurin27.0.1.0.2+1 공식 musl Linux amd64/arm64 배포 파일을 각각 SHA256 검증해 구성한다. 현재 실행 검증은 Linux ARM64 컨테이너에서 완료했으며 별도 Linux 서버·AMD64·GPU 실행은 미검증이다. PostgreSQL·Caddy는 digest로 고정한 공식 소스/베이스에 보안 패치를 반영해 로컬 이미지를 빌드한다. Caddy와 gosu만 Go 1.27.3으로 재빌드하며 제품 코드는 Java다.

회원가입 후 다음 명령에 기존 아이디를 stdin으로 입력해 관리자 권한을 부여한다. 비밀번호는 CLI 인자로 받지 않는다.

```sh
docker compose run --rm -e DAMGOX2_ROLE=admin-cli web
```

관리자 화면: http://localhost:8080/admin.html. 사이트 DRAFT 등록 → 수집 주소 등록 → 이용조건 검토·허용 동작 저장 → 기술 점검 → 주소 사용 활성화 → 사이트 활성화 → 지금 수집. 미리보기는 기사로 저장하지 않는다. 202는 작업 접수이며 성공은 job/runs에서 확인한다.

비회원도 대표 관심사 지면(7개씩 최대6슬라이드·42개)을 볼 수 있다. 관리자 화면의 대표 관심사에서 기본 주제를 변경하며, 로그인한 회원도 개인 관심사가 없으면 대표값을 사용한다. 뉴스레터 목록·본문은 로그인한 활성 계정에 유효한 구독 권한이 있어야 이용하며, 권한이 없으면 메뉴를 숨기고 직접 HTML/API도404로 응답한다. 계정/관심사 기능은 로그인이 필요하다. 관리 기능은 ADMIN만 허용한다. 공개 뉴스는 조회량·공개 권리 제한을 적용하고 회원 전용 API는 계속 인증을 요구한다. 기사 검색은 팝업에서 실행하며 수집된 공개자료의 최신순 최대140개를 7개씩20슬라이드로 조회한다. 현재 약관은 최초 제1판이며 이전 이력은 없다.

## 빌드·검증

```sh
./gradlew --no-daemon clean check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest
```

JDK는 정확한 공급자 빌드가 필요하다. 현재 macOS aarch64 workspace JDK와 cache를 사용할 때는 같은 인자를 `sh scripts/build/gradle-local.sh`에 전달한다. `java`, `javac`, Gradle toolchain과 런타임을 [버전 기록](docs/06-transition/versions.md)과 대조한다. preview·incubator·Maven 빌드는 사용하지 않는다.

Playwright Java browser는 먼저 `sh scripts/build/gradle-local.sh playwrightInstall`로 workspace `.local/browsers`에 준비한다. PostgreSQL 통합 테스트에는 Docker가 필요하다. 테스트는 격리 Testcontainers DB와 합성 계정·문장을 사용한다. 현재 서비스는 `DAMGOX2_AUDIT_BASE_URL=https://175.195.62.165 node scripts/test/site-http-audit.mjs`로 읽기 전용 점검한다. 실제 주제 출처 등록·수집 시험은 `scripts/test/topic-source-smoke.mjs`로 분리하며, 이 명령은 DB를 변경하므로 승인된 격리 검증 환경에서만 실행한다. 과거 NASA/NOAA를 재활성화하는 구형 local-smoke 실행기는 폐기했다.

`securityCheck`에는 공식 배포 체크섬을 검증한 Trivy가 필요하며, 미준비·검사 오류는 실패다. `.local/bin`의 검증된 도구를 우선 사용한다. 최종 이미지 검사는 별도 실행한다.

```sh
sh scripts/security/image-scan.sh damgox2-local:1.0.0 damgox2-proxy-local:2.11.7 damgox2-db-local:18.6 damgox2-build:1.0.0 damgox2-proxy-build:2.11.7
```

Wrapper ZIP/JAR 체크섬, dependency locking, strict verification을 사용한다. checksum metadata 생성만으로 공급자 신뢰 검토가 완료됐다고 간주하지 않는다. 로컬 명령은 native file watcher를 끈다(macOS FSEvents 정지 진단 후 선택).

## 개인정보와 운영

이름·전화번호는 받지 않는다. 필수 수신 이메일은 가입 인증·비밀번호 복구·결제 및 구독 관련 안내에만 사용하며 마케팅·광고·다른 목적으로 사용하지 않는다. 이메일은 별도 키로 암호화하고 개인 관심사·가중치는 브라우저 UUID별 IndexedDB에 저장한다. 검색 조건은 실행과 결과 페이지 이동 때 같은 origin 서버에 POST로 보내 일시 처리한다. URL·DB·로그·회원 검색 이력에 저장하거나 외부로 전송하지 않는다. 입력 중에는 전송하지 않는다. 미리 인증한 이메일로 비밀번호를 재설정할 수 있다. 자동 기기 동기화는 없으며, 암호화 파일로 관심사를 직접 옮길 수 있다. 같은 브라우저 프로필의 다른 접근자에 대한 암호학적 비밀성을 보장하지 않는다. 계정·세션·보안 카운터도 회원 관련 정보다.

원문은 직접 링크로 이동한다. 기사 전문, 이미지·로고, 추적 SDK와 클릭 이벤트 저장은 기본 제공하지 않는다. [개인정보 경계](docs/07-operation/privacy.md), [API](docs/03-design/api.md), [출처](docs/07-operation/sources.md), [보안 검토](docs/07-operation/security-review.md), [배포·백업](docs/06-transition/deployment.md)를 참조한다.

컨테이너를 재생성해도 DB volume은 유지한다. `docker compose down -v`는 데이터를 지우므로 일반 운영 명령으로 사용하지 않는다. 백업 도구는 `sh scripts/backup/build-backup-tools.sh`로 보안 수정 버전을 준비한다. 백업은 `sh scripts/backup/backup.sh <age-public-recipient>`로 암호화하고, `sh scripts/backup/restore-check.sh <backup> <private-identity-file>`로 격리 복원을 검증한다. 실제 `.local/backups` 파일·identity를 Git이나 공개 아티팩트로 올리지 않는다. 자동 백업은 구성하지 않는다.

법률·상표·도메인·사업자·언론 관련 등록 검토가 완료됐다고 주장하지 않는다. 현재 공개 저장소의 main merge는 수행하지 않고 지정 작업 브랜치로 전달한다.

엔진/서비스 준비를 제한 시간 안에 확인하려면 이미지 빌드 후 `node scripts/deploy/local-start.mjs`를 실행한다. Engine 사전 검사10초, Compose health90초, 전체 시작120초 상한이며 실패를 성공으로 처리하지 않는다. Docker 시작 장애 기록은 docs/05-test/bugs.md를 참고한다.

대표 문구 변경: `src/main/resources/static/assets/js/config.js`의 `brand.mastheadQuote`를 수정한다. `\n`은 의도한 줄바꿈이다. 브랜드 영문 표기는 같은 파일의 `english`에서 관리한다. 코드 변경 후 검증·이미지 재빌드·서비스 재생성이 필요하며 자동 문구 순환은 사용하지 않는다.

언어 선택은 화면 안내와 기사 제목·요약을 한국어/영어로 전환한다. Index Translate 2B Q8_0 CPU 컨테이너가 허용된 제목·요약을 번역한다. 자동 검증·보정 재시도 후 통과한 결과만 제공하며 실패한 기사는 원문을 유지한다. 모든 의미 오류를 검출한다고 보장하지 않는다.

## 전체 서비스 실행 · 현재 구성

처음에는 비공개 환경 설정 후 `sh scripts/translation/translation-prepare.sh`로 번역 키와 모델을 준비한다. 현재 Mac 공개 주소에서는 다음 조합을 사용한다. DB·모델·인증서 볼륨을 유지하며 초기화 서비스는 성공 후 종료한다.

```sh
docker compose -f compose.yaml -f scripts/deploy/compose-public.yaml -f scripts/deploy/compose-translation.yaml -f scripts/deploy/compose-classification.yaml up -d --build
docker compose -f compose.yaml -f scripts/deploy/compose-public.yaml -f scripts/deploy/compose-translation.yaml -f scripts/deploy/compose-classification.yaml ps -a
```

Linux 서버에서는 `compose-public.yaml` 대신 `scripts/deploy/compose-server.yaml`을 사용한다. 공개 주소·방화벽·Docker 자동 시작 설정은 해당 서버에 맞춰 준비한다. [Linux 이관 안내](docs/06-transition/linux-translation.md)를 따른다. 단순 재시작에는 `--build`를 생략한다.

## 디렉토리 안내

| 위치 | 책임 |
|---|---|
| 루트 build.gradle·gradle/·gradlew | Gradle 빌드·버전 검증·의존성 잠금 |
| 루트 Dockerfile·compose.yaml·Caddyfile | 기본 컨테이너 빌드·로컬 실행 진입점 |
| src/main/java/com/moapan | auth/security/site/source/news/jobs 기능별 서버 |
| src/main/resources | 설정·Flyway 마이그레이션·정적 웹·번역 용어표 |
| src/test·integrationTest·e2eTest | 단위·PostgreSQL 통합·브라우저 테스트 |
| scripts/build | 로컬 JDK/Gradle 실행 |
| scripts/deploy | 환경별 Compose·proxy·DB 초기화·시작 점검 |
| scripts/translation | Linux 모델 초기화·CPU 실행·이미지 |
| scripts/backup | 암호화 백업 도구·격리 복원 검사 |
| scripts/security | 소스·산출물·이미지·truststore 보안 검사 |
| scripts/test | 실제 수집/운영 시나리오 smoke 도구 |
| scripts/governance | 저장소 보호 초기 설정 도구와 테스트 |
| scripts/experiments | 과거 Mac 번역 비교용 실행기; 운영 미사용 |
| docs | [문서 색인](docs/README.md)·설계·운영·인수 기록 |
| docs/05-test/evidence | 비밀값 없는 검증 증빙 |
| .local·build·.gradle | 비공개 설정/캐시/실행 산출물, Git 제외 |

작업은 develop 기준의 `codex/` 브랜치와 PR로 전달하며 main 병합은 별도다. 기존 경로를 참조하는 외부 명령은 위 분류에 맞춰 변경한다.

## 자동 준비와 Kubernetes 전환

새 환경에서 비공개 .env를 준비한 뒤 `sh scripts/deploy/start.sh server` 한 명령으로 모델 준비(Hugging Face 다운로드·SHA256 검증)와 전체 Compose 빌드/실행을 연결한다. 현재 Mac 공개 환경은 `public`, loopback은 `local`이다. 기존 모델은 재다운로드하지 않는다.

사용 모델은 **IndexTeam/Index-Translate-2B-GGUF · Q8_0**, 고정 revision `3a0eca6ce931d88d00614d427ab3ed8ff0743902`다. 약2.08GB 모델 파일은 Git/이미지에 넣지 않고 실행 시 전용 볼륨에 저장한다. [다운로드 구현](scripts/translation/model-init.sh).

[Helm 차트](scripts/deploy/helm/damgox2/README.md)는 전체 이미지 역할을 Kubernetes로 배치한다. Secret·registry digest·Ingress·StorageClass는 실제 클러스터 값으로 설정해야 한다. lint/template 검증과 실제 클러스터 실행은 구분하며 현재 클러스터 실행은 미검증이다.

## Qwen 기사 주제 분류
`sh scripts/deploy/start.sh local|public|server`는 기존 번역과 별개로 Qwen3.5-4B Q4_K_M도 Hugging Face에서 고정 revision으로 다운로드·SHA256 검증 후 내부 Compose 서비스로 실행한다. classifier는 외부 포트를 열지 않으며 Java worker가 저장된 제목·요약을 비동기 분류한다. 관리자 화면 **기사 주제 분류**에서 1~8개 태그와 포함/제외 기준을 편집할 수 있다. 초기 5개 주제는 시작용 기본값이다. 수동 잠금은 유지하며 기존 기사는 순차 처리한다.

구성·정책·자원·실패 처리: [docs/03-design/classification.md](docs/03-design/classification.md). 수동 Compose 명령에도 `-f scripts/deploy/compose-classification.yaml`을 포함한다. Helm은 classifier 이미지 digest·전용 PVC·Secret·내부 Service를 미리 준비했으며 실제 Kubernetes 설치는 수행하지 않았다.

광고는 관리자 → 광고 관리에서 직접 링크 배너를 저장하면 지면 재조회 시 반영됩니다. 애드센스는 승인/식별자 미보유 상태에 맞춰 연결 정보 등록만 지원하며 실제 송출은 연결되지 않았습니다. 상세 절차는 [배포·운영 안내](docs/06-transition/deployment.md#광고-등록-방법)를 참조하세요.

서비스명은 담고담고(DamGoX2)이며 “여러 소식을 담고 펼치는 판”을 뜻합니다. 이름·영문 표기는 [config.js](src/main/resources/static/assets/js/shared/config.js)의 brand, 뉴스레터 표시·의미는 newsletter 설정에서 관리합니다. 유효한 구독 권한이 있는 계정으로 로그인하면 콘텐츠 메뉴에서 목록/본문을 읽습니다. 관리자 계정 메뉴 → 담고담고 관리에서 새 원고·글/그림·참고 자료를 편집하고 초안 저장/미리보기/발간/발간 취소를 할 수 있습니다. 원고 수정은 재발간 전까지 기존 발간본에 영향을 주지 않습니다. 뉴스레터 자체의 이메일 발송은 제공하지 않습니다. 계정 인증·복구 메일과 구분합니다.

## 뉴스레터 연구·업무 활용
관리자는 담고담고 관리에서 발간 주의 수집 자료와 전망/실험/중단 조건을 연결하고 발간 전 점검을 실행한다. 발간본은 최초호수/시각과 개정내용을 구분한다. 회원은 권리 확인된 원고를 출처 포함 복사·HTML로 내보내고 회의 요약·연구 우선순위·실험 양식으로 활용할 수 있다. 수집 기사 전문을 내보내지 않는다.
제품 재평가는 서비스 밖의 분석 문서로만 관리합니다. 실제 고객 조사는 아직 수행하지 않았습니다. 개인/기업 유료 구독의 결제 연동은 준비 중이며 현재는 관리자가 계정별 이용 기간을 부여하는 방식입니다. 신규·기존 계정은 기본 미구독입니다. 가격·실결제·자동청구·조직 SaaS는 아직 없습니다. 내부 진단과 외부 검증의 차이는 [사업화·실무 검증 계획서](docs/02-analysis/business-validation.md)를 참고하세요.

보안·모델 관리: [AI 가드레일·윤리·라이선스](docs/07-operation/ai-security-and-licenses.md), [제3자 고지와 소스 제공 조건](THIRD_PARTY_NOTICES.md). `check`에는 실제 의존성·보존 고지 SHA를 대조하는 `licenseCheck`가 포함됩니다.

### AI 가드레일 후속 운영
현재 Index-Translate-2B 번역과 Qwen3.5-4B 분류를 유지한다. 입력 보류, 실제4096토큰 예산, SSE 취소, 출처별 일일 집계 한도와 유한 재시도, 관리자 번역 수정·잠금을 추가했다. 상세 범위·한계·운영 지침은 [AI 보안](docs/07-operation/ai-security-and-licenses.md), [검증 증빙](docs/05-test/evidence/ai-guardrail-followup-20261031.json)을 따른다. Helm은 모델 다운로드 Job과 실행 Pod의 외부 통신 차단을 분리하며 실클러스터 설치는 별도다.

## 현재 보안 검증

2026-10-31 최종256회귀와 실제 경량모델·HTTPS·8개 이미지 검증을 완료했다. Ubuntu 잔여 구성요소를 제거하고 JOSE 공식 수정을 별도 백포트했다. source-version 검사에 사용하는 백포트 근거는2026-11-07까지 유효하며 이후 공급자 공지·패치/빌드 증빙을 재검토해야 한다. 비밀번호 길이는 사용자 요청으로6~128을 유지하므로 단독 인증 권고와의 차이를 완료 처리하지 않는다. [현재 증빙](docs/05-test/evidence/runtime-security-followup-20261031.json)과 [고정20항목](docs/07-operation/security-review.md)을 확인한다.

## 공통 조회 캐시
브라우저 공통 ES Module/Intl/DOM 재사용과 정적 assets의 내용 ETag·압축을 적용했다. 서버는 내장 Caffeine으로 태그·공통 대표 관심사·광고 표시·요청한 공통 기사 카드·발간 원고를 최대60분 재사용한다. DB 수정 시 저장된 작은 해시를 비교해 변경을 즉시 반영하며 권한·quota·현재 공개 조건은 매 요청 검사한다. 회원 개인 관심사·검색·세션/계정 응답은 캐시하지 않는다. web16MiB/worker8MiB는 논리 가중치 예산이다. V17~22와 Helm schema22, 실제330회귀/HTTPS/데이터 보존 증거는 [서버 캐시 검증서](docs/05-test/server-cache.md)를 따른다.


### PageSpeed·웹 표준 검증
2026-11-02 06:40 KST 실제 외부 PageSpeed 측정에서 모바일·데스크톱 성능/접근성/권장사항/SEO가 모두100점, CLS0으로 확인됐다. 실험값이며 검색 순위나 모든 이용 환경의 점수 보장은 아니다. [측정·336시험·보안·서비스·최종 코드 점검 근거](docs/05-test/pagespeed-desktop.md)를 참조한다. 현재6개 Compose서비스와 기존DB/모델을 보존한다.

구독 권한 관리: 관리자 → 구독 권한 → 아이디 검색/계정 선택 → 한국 시간 기준 이용 시작·종료 → 기간 저장·권한 부여 또는 회수. 이 조작은 결제나 자동 청구를 수행하지 않습니다. API/404/만료와보안경계는 [설계서](docs/03-design/subscription-access.md)·[검증서](docs/05-test/subscription-access.md)를 참고하세요. 구독모드는 managed만 지원합니다.

### 필수 수신 이메일·비밀번호 찾기

신규 가입은 수신 이메일 인증이 필요하다. 로그인 페이지의 **비밀번호 찾기**에서 사전 인증한 이메일의 인증번호로 새 비밀번호를 설정한다. 복구·이메일 변경은 기존 모든 로그인 세션을 종료한다. 기존 회원은 계정을 유지하며 계정 관리에서 현재 비밀번호와 새 이메일 인증으로 등록한다. 이메일은 인증·비밀번호 복구·결제/구독 안내에만 사용하고 마케팅·광고·다른 목적으로 사용하지 않는다. 현재 결제·청구와 뉴스레터 이메일 발송은 미구현이다.

### 외부 MailBox 연결과 책임

담고담고는 SMTP 클라이언트와 암호화 발송 대기만 관리한다. MailBox/Postfix/IMAP/Webmail 서버를 복제하거나 빌드·시작하지 않는다. 운영자가 별도로 관리하는 SMTP에 `scripts/deploy/compose-mail-external.yaml`로 연결한다. SMTP 인증이 필요하면 그 뒤에 `scripts/deploy/compose-mail-auth.yaml`을 추가하고 MAIL_SMTP_USERNAME과 MAIL_SMTP_PASSWORD_FILE을 지정한다. 비밀번호 값은 환경변수에 넣지 않고 파일 secret으로 읽는다. worker만 지정한 기존 SMTP Docker 네트워크에 연결하고 web은 기존 backend를 유지한다.

최초 준비는 저장소 안에서 다음 명령을 실행한다. 기존 이메일 암호화 키를 덮어쓰거나 재생성하지 않으며, 서버 인증서나 개인키도 만들지 않는다. 기존 키가 잘못됐으면 교체하지 않고 실패한다.

```sh
node scripts/deploy/prepare-email-key.mjs
node scripts/test/mail-deployment-check.mjs
```

별도32-byte 이메일 키는 `.local/mail/data.key`에 보관하며 암호화 DB와 함께 백업·접근통제한다. 호스트 보관 디렉터리는0700이며 파일은 비루트 앱UID10001이 파일 secret으로 읽을 수 있는 읽기 전용 권한을 사용한다. 키를 분실하거나 다른 키로 바꾸면 기존 이메일 데이터를 복구할 수 없다. 실제 키와 .env는 Git에 넣지 않는다.

.env.example의 MAIL_FROM/MAIL_SMTP_HOST/MAIL_SMTP_PORT/MAIL_SMTP_NETWORK/MAIL_SMTP_CA_FILE을 운영자가 관리하는 값으로 설정한다. 인증서 SAN에 있는 호스트 이름과 확인된 CA 공개 인증서만 지정한다. 공개 CA 파일은 파일 secret의 앱UID10001이 읽을 수 있게 준비한다. CA 개인키·서버 개인키는 앱에 전달하지 않으며 전역 truststore·호스트 검증을 변경하지 않는다. Compose는 STARTTLS 필수, Helm은 STARTTLS 또는 SMTPS를 지원한다.

보안 검사·백업·운영 승인이 끝난 뒤 실제 사용하는 local/public/server와 두 모델 overlay 뒤에 `-f scripts/deploy/compose-mail-external.yaml`을 추가한다. 기존 ingress/볼륨과 검증한 앱 digest를 유지한다. 이전 내장 SMTP의 compose-mail.yaml 및 Helm mail.server 옵션은 퇴역했으며 재사용하지 않는다. 메일 미설정 시 신규 인증·가입·복구는503으로 닫히고 기존 계정 로그인은 유지된다.

이 저장소의 검사 통과는 외부 MailBox의 보안 문제 해결을 뜻하지 않는다. 외부 이미지·중계 권한·TLS·실제 수신함 전달을 별도로 검증한다. 2026-11-04 현재 새 MailBox 이미지의 검사, 인증 STARTTLS와 운영자 실제 받은편지함 수신을 확인해 같은 검증 앱 이미지로 V25 서비스를 전환했다. 재시작 시 필요한 모든 overlay/비공개 파일 경로는 [현재 운영 전환](docs/06-transition/deployment.md#현재-운영-전환--2026-11-06-damgox2-명칭-이관)을 따른다. Gmail 등 별도 인터넷 수신자 전달과 MailBox 운영을 보장한다는 의미는 아니다. 현재 실제 결과는 [이메일 검증서](docs/05-test/email-identity.md), 외부 담당 작업용 프롬프트는 [MailBox 결함 조치 요청서](docs/07-operation/mailbox-security-handoff.md)를 따른다.

## Docker/Helm 명칭 이관
배포 프로젝트와 이미지·리소스는 damgox2 이름을 사용한다. 기존 환경에서는 새 이름으로 up만 실행하면 빈 볼륨이 생기므로 먼저 [데이터 보존 이관 절차](docs/03-design/infrastructure-naming.md)를 따른다. 실제 전환 결과는 [검증서](docs/05-test/infrastructure-naming.md)를 참조한다. Java 내부 패키지·세션·브라우저 저장 식별자와 불변 SQL 이력은 호환성을 위해 유지한다.
