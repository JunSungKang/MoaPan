현재 접속: **https://175.195.62.165/** — 로컬 Mac 실행, 휴대폰 데이터망 확인 완료. 공개 모드 실행/중지는 [배포 안내](docs/DEPLOYMENT.md)를 참고한다.

# 모아판 · MOAPAN

여러 출처의 뉴스를 관심사에 맞게 모아 종이신문처럼 읽는 개인화 뉴스 V1. Java 27 / Spring Boot 4 / Gradle / PostgreSQL / 정적 HTML·CSS·Vanilla ES Modules / Docker Compose 기반이다.

**CI·CD, GitHub Actions, 예약 workflow는 사용자 요청에 따라 구현·실행하지 않는다.** 검증은 로컬 명령으로 실행한다. 외부 HTTPS 배포와 사업 개시는 별도 승인·검토가 필요하다. 실제 인수 상태는 [진행 상태](docs/PROGRESS.md)와 [테스트 보고](docs/TEST_REPORT.md)를 확인한다.

## 로컬 실행

```sh
cp .env.example .env
# .env를 비공개로 편집: DATABASE_PASSWORD는 64자 이상의 fresh random hex,
# DATABASE_ADMIN_PASSWORD와 MOAPAN_SECRET은 각각 다른 충분한 random secret 사용.
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

접속: http://localhost:8080. 기본 loopback HTTP에서는 합성 계정만 사용한다. DB와 web 포트는 host에 공개하지 않는다. `.env`, tool cache, 검사 원본, 개인 backup은 `.local`과 함께 Git에서 제외한다. `.env.example`을 실제 운영 secret으로 사용하면 안 된다.

첫 기동은 migrate가 Flyway를 실행한 뒤 web·worker가 시작된다. web/worker는 app 전용 non-superuser DB 역할, migrate는 별도 자격으로 실행한다. app 이미지는 정확한 Temurin27.0.1.0.2+1 Linux x64 tar를 SHA256 검증해 구성한다. 선택 빌드의 Linux arm64 미공개로 현재 app 플랫폼은 linux/amd64이며 Apple Silicon에서는 emulation을 사용한다. PostgreSQL·Caddy는 digest로 고정한 공식 소스/베이스에 보안 패치를 반영해 로컬 이미지를 빌드한다. Caddy와 gosu만 Go 1.27.3으로 재빌드하며 제품 코드는 Java다.

회원가입 후 다음 명령에 기존 아이디를 stdin으로 입력해 관리자 권한을 부여한다. 비밀번호는 CLI 인자로 받지 않는다.

```sh
docker compose run --rm -e MOAPAN_ROLE=admin-cli web
```

관리자 화면: http://localhost:8080/admin.html. 사이트 DRAFT 등록 → 수집 주소 등록 → 이용조건 검토·허용 동작 저장 → 기술 점검 → 주소 사용 활성화 → 사이트 활성화 → 지금 수집. 미리보기는 기사로 저장하지 않는다. 202는 작업 접수이며 성공은 job/runs에서 확인한다.

비회원도 대표 관심사 지면(최대30개)을 볼 수 있다. 관리자 화면의 대표 관심사에서 기본 주제를 변경하며, 로그인한 회원도 개인 관심사가 없으면 대표값을 사용한다. 계정/관심사 메뉴는 로그인한 사용자에게만, 관리 메뉴는 관리자에게만 표시한다. 기사 검색은 팝업에서 적용한다. 약관의 현재 판과 이전 본문은 하단 링크에서 확인한다.

## 빌드·검증

```sh
./gradlew --no-daemon clean check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest
```

JDK는 정확한 공급자 빌드가 필요하다. 현재 macOS aarch64 workspace JDK와 cache를 사용할 때는 같은 인자를 `sh scripts/gradle-local.sh`에 전달한다. `java`, `javac`, Gradle toolchain과 런타임을 [버전 기록](docs/VERSIONS.md)과 대조한다. preview·incubator·Maven 빌드는 사용하지 않는다.

Playwright Java browser는 먼저 `sh scripts/gradle-local.sh playwrightInstall`로 workspace `.local/browsers`에 준비한다. PostgreSQL 통합 테스트에는 Docker가 필요하다. 테스트는 격리 Testcontainers DB와 합성 계정·문장을 사용한다. 실제 외부 source smoke는 `node scripts/local-smoke.mjs`로 분리한다. 이 명령은 loopback Compose에서만 실행하며 합성 관리자 계정을 검증 후 삭제한다. 운영 DB에서 실행하지 않는다.

`securityCheck`에는 공식 배포 체크섬을 검증한 Trivy가 필요하며, 미준비·검사 오류는 실패다. `.local/bin`의 검증된 도구를 우선 사용한다. 최종 이미지 검사는 별도 실행한다.

```sh
sh scripts/image-scan.sh moapan-local:1.0.0 moapan-proxy-local:2.11.7 moapan-db-local:18.6 moapan-build:1.0.0 moapan-proxy-build:2.11.7
```

Wrapper ZIP/JAR 체크섬, dependency locking, strict verification을 사용한다. checksum metadata 생성만으로 공급자 신뢰 검토가 완료됐다고 간주하지 않는다. 로컬 명령은 native file watcher를 끈다(macOS FSEvents 정지 진단 후 선택).

## 개인정보와 운영

이름·이메일·전화번호를 받지 않으며 개인 관심사·가중치는 브라우저 UUID별 IndexedDB에 저장한다. 검색 팝업의 조건은 현재 페이지 메모리에만 두고 전송하지 않는다. 비밀번호 복구와 자동 기기 동기화는 없으며, 암호화 파일로 관심사를 직접 옮길 수 있다. 같은 브라우저 프로필의 다른 접근자에 대한 암호학적 비밀성을 보장하지 않는다. 계정·세션·보안 카운터도 회원 관련 정보다.

원문은 직접 링크로 이동한다. 기사 전문, 이미지·로고, 추적 SDK와 클릭 이벤트 저장은 기본 제공하지 않는다. [개인정보 경계](docs/PRIVACY.md), [API](docs/API.md), [출처](docs/SOURCES.md), [보안 검토](docs/SECURITY_REVIEW.md), [배포·백업](docs/DEPLOYMENT.md)를 참조한다.

컨테이너를 재생성해도 DB volume은 유지한다. `docker compose down -v`는 데이터를 지우므로 일반 운영 명령으로 사용하지 않는다. 백업 도구는 `sh scripts/build-backup-tools.sh`로 보안 수정 버전을 준비한다. 백업은 `sh scripts/backup.sh <age-public-recipient>`로 암호화하고, `sh scripts/restore-check.sh <backup> <private-identity-file>`로 격리 복원을 검증한다. 실제 `.local/backups` 파일·identity를 Git이나 공개 아티팩트로 올리지 않는다. 자동 백업은 구성하지 않는다.

법률·상표·도메인·사업자·언론 관련 등록 검토가 완료됐다고 주장하지 않는다. 현재 공개 저장소의 main merge는 수행하지 않고 지정 작업 브랜치로 전달한다.

엔진/서비스 준비를 제한 시간 안에 확인하려면 이미지 빌드 후 `node scripts/local-start.mjs`를 실행한다. Engine 사전 검사10초, Compose health90초, 전체 시작120초 상한이며 실패를 성공으로 처리하지 않는다. Docker 시작 장애 기록은 docs/BUGS.md를 참고한다.

대표 문구 변경: `src/main/resources/static/assets/js/config.js`의 `brand.mastheadQuote`를 수정한다. `\n`은 의도한 줄바꿈이다. 브랜드 영문 표기는 같은 파일의 `english`에서 관리한다. 코드 변경 후 검증·이미지 재빌드·서비스 재생성이 필요하며 자동 문구 순환은 사용하지 않는다.
