# 수동 실행·배포·백업

> 문서번호: MP-DOC-016 · 기준: 저장소 현재 리비전 · 상태: 유지관리 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

사용자 최신 완료 기준: 로컬 Mac에서 실행하고 휴대폰 데이터망에서 접속 성공. https://175.195.62.165/ 에서 사용자 확인을 받았다. 별도 호스팅 배포는 범위에서 제외. CI/CD·공개 터널·유료 자원 생성은 없다.

공개 실행: .env에 PUBLIC_BASE_URL=https://175.195.62.165, MOAPAN_PUBLIC_HOST=175.195.62.165, COOKIE_SECURE=true를 설정한다. 비밀값은 기존 .env에 유지한다. `docker compose -f compose.yaml -f scripts/deploy/compose-public.yaml up -d --no-build --wait --wait-timeout 90`으로 실행한다. 기본 Compose만 실행하면 로컬 모드 설정이 적용되므로 공개 모드에서는 반드시 두 파일을 지정한다. Docker의 외부 privileged port forwarding은 사용자 승인 후 활성화했다.

공개 proxy는 80→8081(HTTPS redirect/ACME),443→8443(TLS),127.0.0.1:8080(health only)만 노출한다. DB와 web은 호스트 포트를 열지 않는다. Let’s Encrypt shortlived IP 인증서를 Caddy가 named volume에 보관·자동 갱신한다. Mac과 Docker가 실행 중이고 해당 IP와 포트가 유지돼야 접속·갱신 가능하다. 현재 인증서 만료2026-11-03T21:11:17Z; IP가 바뀌면 .env·주소·인증서 재검증이 필요하다.

공개 접속 중지: `docker compose -f compose.yaml -f scripts/deploy/compose-public.yaml stop proxy`. 볼륨 삭제는 필요 없다. 다시 로컬 HTTP로 전환하려면 PUBLIC_BASE_URL=http://localhost:8080, COOKIE_SECURE=false 후 기본 Compose로 재생성한다.

검증: `COMPOSE_FILE=compose.yaml:scripts/deploy/compose-public.yaml MOAPAN_SMOKE_BASE_URL=https://175.195.62.165 node scripts/test/local-smoke.mjs`. 합성 계정만 생성하며 완료 후 삭제한다. TLS 검증 우회는 하지 않는다.

```sh
cp .env.example .env
# 비공개 편집기로 독립 random secret을 설정하고 .env 권한을 0600으로 제한
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

접속은 http://localhost:8080. 합성 계정만 사용한다. migrate exit0 후 web·worker가 시작하며 worker/migrate/admin-cli는 HTTP 포트를 열지 않는다. proxy만127.0.0.1:8080을 공개한다. DB·web은 내부 네트워크에서만 연결한다. app는 linux/amd64, DB/proxy는 지원되는 native 플랫폼이다. 선택한 최신 Temurin 패치의 Linux arm64 바이너리가 아직 없어 Apple Silicon app는 emulation으로 실행한다.

관리자 부여: `docker compose run --rm -e MOAPAN_ROLE=admin-cli web`에서 기존 아이디를 stdin으로 입력한다. 초기 관리자 비밀번호는 없다. DB runtime 역할 moapan은 superuser/createdb/createrole 권한이 없으며 migration 자격은 분리한다.

최종 검증 이미지 식별값은 `evidence/verification.json`에 기록한다. 이미지 tag를 다른 빌드로 덮으면 재검사해야 한다. 외부 배포 시 보안 DB를24시간 내 다시 동기화하고 같은 digest만 수동 승격한다. 현재 로컬 Caddyfile은 HTTP 전용이다. 승인 도메인 Caddy HTTPS·HSTS·PUBLIC_BASE_URL·Secure 쿠키·신뢰한 프록시 전달 정책을 함께 설정·검증하기 전 외부에 공개하지 않는다.

## 영속성과 복원

`database_v1` named volume을 유지한다. 일반 재시작에 `docker compose down -v`를 사용하지 않는다. Debian→Alpine 전환은 libc/locale 차이 때문에 구 볼륨을 직접 연결하지 않고 age 암호화 SQL을 새 볼륨에 복원했다. 구 볼륨은 자동 삭제하지 않았다. 실제 기사20건과 FK·Flyway 이력이 유지됨을 확인했다. 새 DB 초기화는 C.UTF-8이다.

```sh
sh scripts/backup/backup.sh <age-public-recipient>
sh scripts/backup/restore-check.sh <workspace-encrypted-backup> <workspace-private-identity-file>
```

`sh scripts/backup/build-backup-tools.sh`로 age 1.3.2+moapan-security.1을 준비한다. 검증한 Go 1.27.3과 고정 모듈로 보안 수정이 반영된 도구를 빌드하며 버전·검사 근거는 VERSIONS.md에 있다. pg_dump를 FIFO에서 즉시 암호화하며 평문 dump를 파일에 남기지 않는다. dump와 암호화 각각 실패를 확인한다. workspace .local/backups만 허용하고 umask077을 적용한다. identity의 값은 CLI 인자로 전달하지 않는다. 백업·키를 Git이나 공개 보고서에 포함하지 않는다. 키는 백업과 분리한 승인된 비밀 보관소에 보관해야 한다.

격리 복원은 별도 DB를 생성해 복호화·SQL을 검증하고 모든 복원 계정을 비활성화·세션을 제거한 뒤 그 격리 DB만 삭제한다. 운영 복원 전 공개를 중지하고 이 정책을 적용하여 탈퇴 계정이 자동 부활하지 않게 한다. 재활성화는 별도 운영 확인을 거친다. 실제 검증에서 articles=20, active_accounts=0, sessions=0이었다. 검증용 키·암호화 백업은 검증 후 정리한다. 수동 백업 보관기간은7일이며 자동 백업은 구성하지 않았다.

앱 rollback은 이전 앱과 현재 schema의 호환성을 확인한 경우에만 수행한다. 파괴적 DB rollback이나 취약한 이미지로의 무조건 복귀는 하지 않는다. 보안 수정 실패 시 외부 공개 중지·격리를 우선한다. 외부 배포·백업 저장소 접근통제·사업자 로그 정책은 실제 운영 환경이 정해진 뒤 확인해야 한다.

엔진/서비스 준비를 제한 시간 안에 확인하려면 이미지 빌드 후 `node scripts/deploy/local-start.mjs`를 실행한다. Engine 사전 검사10초, Compose health90초, 전체 시작120초 상한이며 실패를 성공으로 처리하지 않는다. Docker 시작 장애 기록은 docs/05-test/bugs.md를 참고한다.

Caddyfile과 scripts/deploy/db-init.sh는 이미지에 포함된다. 수정 후 proxy/db 이미지를 재빌드하고 다시 보안 검사한다. 기본 Compose는 호스트 파일 bind mount를 사용하지 않으며 기존 named volume을 유지한다.

## 선택적 무료 로컬 번역 (macOS arm64)
현재 운영 번역은 Linux CPU 컨테이너 방식으로 전환했다. 준비·실행·서버 이관·자동 복구 절차는 [LINUX_TRANSLATION.md](linux-translation.md)를 따른다. host.docker.internal이나 Mac native 모델 시작은 필요하지 않다.

선택 모델 Index Translate 2B Q8_0와 glossary-v1 유지. scripts/experiments/translation-local.sh는 과거 비교 실험용이며 현재 운영 경로가 아니다. Docker daemon이 시작돼야 restart 정책이 동작한다.

## 광고 등록 방법
1. 관리자 계정으로 로그인하고 관리 → 광고 관리에서 직접 링크 배너를 선택한다.
2. 광고 문구와 광고주가 제공한 HTTPS 목적지 링크를 입력한다. 이미 서버에 준비한 이미지가 있을 때만 이미지 경로를 입력한다. 이미지 업로드 기능은 없으며 문구만으로도 배너가 표시된다.
3. 미리보기로 확인하고 지면에 광고 표시를 선택해 저장한다. 지면을 다시 열면 우측 상단에 반영된다. 표시 해제 후 저장하면 광고 문의 자리로 돌아간다.
4. CPC 계약 링크도 같은 목적지 필드에 입력한다. 모아판은 클릭을 기록하지 않으므로 집계·수익·정산은 계약한 공급자에서 확인한다.
5. 애드센스는 연결 정보로 전환해 ca-pub-로 시작하는 공개 게시자 ID와 광고 단위 ID를 저장한다. 현재는 준비 상태이며 실제 광고 요청과 수익은 발생하지 않는다. 승인 계정·사이트·광고 단위와 동의 관리/CSP 설계를 확보한 뒤 별도 송출 연결이 필요하다.
공식 근거(2026-10-30 확인): [광고 단위 생성](https://support.google.com/adsense/answer/9274025), [strict CSP 통합](https://support.google.com/adsense/answer/16283098), [동의 관리](https://support.google.com/adsense/answer/7670013). 계정 승인/게시자 ID가 없어 실광고 송출·수익 검증은 하지 않았다. V12 마이그레이션 추가 및 Helm migrationVersion=12.

뉴스레터: /newsletter.html은 회원용 목록·본문, /newsletter-editor.html은 관리자 원고 편집이다. 관리자 계정 메뉴의 뉴스레터 관리에서 제목·소개·주제를 입력하고 문단/소제목/강조문/그림/참고 자료를 조합한다. 뉴스·논문 찾기로 기존 뉴스의 제목과 원문 링크만 참고 자료에 연결할 수 있다. PNG/JPEG/WebP 5MB 이하 파일을 브라우저에서 최대1200px JPEG로 압축하며 그림당 약220KiB, 원고 API 전체1MiB를 제한한다. 초안 저장은 발간본에 영향을 주지 않으며 발간 버튼과 확인 후에만 회원에게 보인다. 발간 취소 시 초안은 유지한다. 그림 대체 설명과 출처는 관리자가 작성하고 이용 권리를 확인한다. 자동 이메일 발송·이메일 수집·AI 원고 생성은 없다. UI 번역과 원고 번역은 구분하며 원고는 작성 언어 그대로다. V13 및 Helm migrationVersion13을 적용한다.

V14 전환: newsletters에 nullable dossier와최초발간/개정필드를추가하며기존본문/그림/회원/기사삭제없음. migrate역할을먼저한번실행한후동일검증이미지web/worker를재생성한다. 이전V13앱은추가컬럼을사용하지않으므로스키마를파괴적으로되돌리지않는다. 기존발간본은구조화근거·권리재검토전에는재게재도구가없고본문열람은유지한다. Helm migrationVersion=14이며실클러스터배포는검증범위밖이다. 백업은기존DB전체dump/암호화/접근통제절차로새컬럼도포함한다. 실제고객평가/가격/결제를운영중이라고표시하지않는다.
