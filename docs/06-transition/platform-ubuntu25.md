# Ubuntu 26.04·Temurin 25 통합 운영 인계

문서번호: MP-DOC-049 · 방식: Sprint Delivery · 운영 교체: 2026-11-09 14:27 KST PASS · 검토: 자체 실행 검증, 독립 PR 리뷰 미실시.

## 실행 구성

현재 주소는 https://175.195.62.165/ 이다. 실제 daemon은 desktop-linux, Linux aarch64이며 호스트는 macOS다. 공통 계약·검증된 공통 이미지가 착수 시 없어서 프로젝트 전용 fallback을 제작했다. 다른 세션의 공통 태그는 변경하지 않았다. 새 최종 이미지 3개는 공식 Ubuntu26.04이며 web/worker가 앱 하나, 번역/분류가 AI 하나를 공유한다. 기존 PostgreSQL은 이번 이미지 교체 대상에서 제외하고 계속 운영한다.

| 역할 | Compose 서비스 | 포트·실행 |
|---|---|---|
| 웹 | damgox2-web | 내부8080, 점검용127.0.0.1:20100 |
| worker | damgox2-worker | HTTP 포트 없음 |
| proxy | damgox2-proxy | 기존 공인IP:80/443, 점검용127.0.0.1:20000 |
| 번역 | damgox2-translator | 내부9931, Index Translate2B Q8 |
| 분류 | damgox2-classifier | 별도 내부망9931, Qwen3.5 4B Q4 |

Compose 프로젝트는 platform이다. 실제 이름은 platform-damgox2-역할-1이다. platform에 다른 프로젝트 서비스도 있으므로 프로젝트 전체 down·orphan 삭제를 실행하지 않는다. 기존 인증서 damgox2_public_caddy_data/config와 두 모델 볼륨을 외부 볼륨으로 재사용한다. 공개80/443 연결은 유지했고 메일 클라이언트의 공개25/143/18080/18443 설정은 변경하지 않았다.

## 버전·빌드

Temurin25.0.5+8-LTS JDK로 빌드하고 같은 공급자 빌드의 JRE로 실행한다. 이는 최신 GA27 자동 선택을 대신하는 이번 사용자의 명시적25 지정이다. Node24.18.0은 빌드·검증에만 사용하며 Python 프로젝트 실행 도구는 없다. 새 Python 도구가 필요하면3.14 검증을 요구한다. 기존 com.moapan 패키지와 호환 property는 유지한다.

공식 Ubuntu index digest는 `sha256:6a06111c3b0a8600feadb270aec771dc239bf407b25625f93f44ab00e11f4d3c`이다. JDK/JRE의 OS·아키텍처별 URL·공식 SHA256은 [버전 입력](../../scripts/platform/runtime-versions.json)에 있다. 최종 이미지 전체 ID와 실제 검증 결과는 [증빙](../05-test/evidence/platform-ubuntu25-20261109.json)을 따른다. 이 ID는 로컬 이미지이며 registry에 업로드한 digest라고 주장하지 않는다.

```sh
sh scripts/platform/build.sh
# Docker 다운로드 프록시가 실패할 때도 동일한 공식 파일·체크섬을 사용한다.
node scripts/platform/prepare-java.mjs .local/platform-java-dist aarch64
JAVA_DIST_DIR="$PWD/.local/platform-java-dist" sh scripts/platform/build.sh
```

검증된 공통 이미지가 제공되면 JAVA_BUILD_IMAGE/JAVA_RUNTIME_IMAGE/NATIVE_BUILD_IMAGE/OS_RUNTIME_IMAGE/NODE_IMAGE로 주입한다. build.sh가 OS·Java·아키텍처를 검사하며, 미제공 시 Bake target으로 자체 기반 이미지를 구성한다. Ubuntu 기반의 native build에서 고정 llama.cpp를 컴파일하므로 musl 바이너리를 복사하지 않는다. 실제 검증은 ARM64 CPU다. AMD64 구성은 제공하지만 실행 검증은 하지 않았다.

## DB·비밀 설정

이번 실운영은 기존 damgox2-db-1:5432 / damgox2 / damgox2 계정과 자격을 유지한다. DB 컨테이너·볼륨·계정·스키마를 변경하거나 중지하지 않았다. 현재 스키마는V27이다. 새 Compose에 PostgreSQL 서비스는 없다.

| 외부 설정 | 의미 |
|---|---|
| DAMGOX2_DATABASE_HOST/PORT/NAME/USER | 런타임 연결 대상 |
| DAMGOX2_DB_PASSWORD_FILE | 호스트의 비공개 비밀번호 파일; 컨테이너 /run/secrets/database-password |
| DAMGOX2_DATABASE_MIGRATION_USER | 해당 DB의 migration 소유 역할 |
| DAMGOX2_DB_MIGRATION_PASSWORD_FILE | migration 전용 비밀번호 파일 |
| DAMGOX2_DATABASE_NETWORK | 기존 DB가 연결된 외부 Docker network |
| DAMGOX2_PUBLIC_HOST/BIND_ADDRESS | 인증서 대상 host와 실제 bind IP를 분리 |
| DAMGOX2_WEB_PORT/API_PORT | 예약 범위20000~20004 /20100~20104, loopback |
| DAMGOX2_SMTP_NETWORK, DAMGOX2_MAIL_* | 외부 SMTP 연결, STARTTLS와 인증 필수 |
| MAIL_DATA_KEY_FILE/MAIL_SMTP_CA_FILE/MAIL_SMTP_PASSWORD_FILE | 이메일 암호화·CA·SMTP 비밀 파일 |
| TRANSLATION_KEY_FILE/CLASSIFICATION_KEY_FILE | 각각의 내부 AI 인증 파일 |

실제 값은 .env와 .local 안의 비공개 파일로만 제공한다. 값·원시 Compose config·쿠키·DB dump는 Git에 넣지 않는다. MailBox는 mailbox_default망의 mailbox-smtp-receiver:2587로 STARTTLS 연결하며 공개25를 인증 submission 포트로 사용하지 않는다.

공통 운영 세션이 데이터 이관·검증을 끝낸 뒤 다음 override를 적용한다. 공통 PG를 발견했다는 이유만으로 연결을 바꾸지 않는다.

```sh
# 비공개 .local/platform.env에 아래 설정의 실제 값을 준비한다.
# DAMGOX2_SHARED_PG_HOST, DAMGOX2_SHARED_PG_NETWORK
# DAMGOX2_SHARED_PG_PASSWORD_FILE, DAMGOX2_SHARED_PG_MIGRATION_USER
# DAMGOX2_SHARED_PG_MIGRATION_PASSWORD_FILE
docker compose --env-file .env --env-file .local/platform.env \
  -f compose.platform.yaml -f scripts/platform/compose.shared-pg.yaml \
  --profile tools run --rm --no-deps damgox2-migrate
```

런타임은 damgox2/damgox2_sid로 고정되며 migration은 별도 DB 소유 역할을 사용한다. DB/역할 생성은 공통 운영 담당 범위다. migration 역할에는 SUPERUSER/CREATEROLE/CREATEDB가 필요하지 않으며 해당 DB/schema 소유와 DDL 권한만 필요하다. [database-grants.sql](../../scripts/platform/database-grants.sql)을 해당 DB 소유 역할로 실행해 DML·sequence·기본 권한을 부여하고 runtime의 schema CREATE와 Flyway 이력 쓰기를 차단한다. 이 SQL은 기존 운영 계정에 이번 작업에서 실행하지 않았다.

## AI 결과 호환·전환

재컴파일로 정책14개 class 바이트가 달라진다. 이전 실제 이미지의14개 지문을 먼저 대조하고, worker를 중지한 상태에서 명시적 policy-transition apply-reviewed-java25를 실행했다. 알려진 이전 provider/정책만 매핑했으며 번역282건과 번역·분류 작업 각544건을 보존했다. 요약·태그·회원·뉴스레터 내용은 덮어쓰지 않았다. 알 수 없는 이전 provider는 삭제/자동 재추론하지 않고 격리한다. 일반 worker 시작에는 전환 명령이 실행되지 않는다.

기존 CA 정책은 같은 공식 식별값으로 JDK/JRE/OS 저장소에 적용한다. TrustAll·TLS 검증 해제·이전 truststore 덮어쓰기는 사용하지 않는다.

## 잠금·실제 교체·복구

이번 일회성 전환은 `sh scripts/platform/deploy.sh`로 수행했다. 이 실행기는 macOS native flock을 실제로 보유하고, 자식의 잠금 FD와 파일 inode를 검증한다. 승인된 외부 파일은 operations/run/deploy.lock뿐이며 다른 operations 파일을 변경하지 않았다. 잠금 안에서 컨테이너·DB 연결을 재확인하고 해당 프로젝트의 정확한 기존5개 컨테이너만 중지했다. DB는 계속 실행했다.

개인 복구 자료는 `.local/platform-ubuntu25-20261109/delivery-l8xf2x`에 있다. images.json은 검사한 최종 ID를 고정하고 previous-containers.json은 이전 정확한 ID·마운트를 기록한다. 기존5개 컨테이너는 삭제하지 않고 중지 상태로 보존했다. `.local/platform-ubuntu25-20261109/rollback-compose`에 이전 Compose 파일이 있다. 암호화 백업은 `.local/backups`에 있으며 복구 키는 별도 비공개로 보존했다. 암호화 백업을 임시 tmpfs PG에 복원해39개 테이블의 지문·건수를 모두 대조했다. 평문 dump 파일은 만들지 않았다.

이 실행기는 이전 damgox2-* 컨테이너가 실행 중인 최초 전환 전제이며, 전환 완료 후 다시 실행해 중복 배포하지 않는다. 현재 서비스의 일반 조회·시작은 동일한 private env와 위 images.json을 Compose에 함께 지정하고 damgox2-* 서비스만 명시한다.

실패 시 실행기는 새5개 서비스만 중지하고 정책 매핑을 rollback-reviewed-java27로 되돌린 뒤 이전 정확한 컨테이너 ID를 시작한다. 실제 배포에서는 자동 복구 경로가 호출되지 않았다. 사후 수동 복구도 같은 운영 잠금 아래 worker 중지 → 이전/현재 정책과 알려지지 않은 provider 재검토 → 정책 역매핑 → 이전5개 컨테이너 시작 → HTTPS/worker 재검증 순서로 수행한다. DB의 파괴적 롤백이나 전체 dump 복원은 자동으로 하지 않는다. 이후 발생한 새 데이터와 보안 공지를 재검토하지 않고 과거 이미지를 재공개하지 않는다.

현재 구성·실행에는 기존 미커밋 사업/UI 변경을 보존했다. 이번 Git 전달에서는 플랫폼 변경과 V27 호환·보안 선행 코드만 포함하고 해당 기존 변경·로컬 스킬은 별도 미커밋 상태로 유지한다. 따라서 운영 이미지에는 보존된 작업본도 들어 있고, 이번 단일 커밋만으로 그 별도 변경까지 게시했다고 주장하지 않는다. 커밋 대상 자체의 독립 소스 사본에서도 Java25 bootJar 빌드를 별도로 확인했다. PR 병합과 공통 PG 데이터 이관은 이번 승인 범위가 아니다.
