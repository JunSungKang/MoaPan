# 수동 실행·배포·백업

외부 승인 서버·도메인이 없다는 사용자 답변에 따라 외부 HTTPS는 BLOCKED/미검증이다. 로컬 HTTP 성공을 외부 배포 성공으로 표시하지 않는다. CI/CD·공개 터널·유료 자원 생성은 없다.

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
sh scripts/backup.sh <age-public-recipient>
sh scripts/restore-check.sh <workspace-encrypted-backup> <workspace-private-identity-file>
```

`sh scripts/build-backup-tools.sh`로 age 1.3.2+moapan-security.1을 준비한다. 검증한 Go 1.27.3과 고정 모듈로 보안 수정이 반영된 도구를 빌드하며 버전·검사 근거는 VERSIONS.md에 있다. pg_dump를 FIFO에서 즉시 암호화하며 평문 dump를 파일에 남기지 않는다. dump와 암호화 각각 실패를 확인한다. workspace .local/backups만 허용하고 umask077을 적용한다. identity의 값은 CLI 인자로 전달하지 않는다. 백업·키를 Git이나 공개 보고서에 포함하지 않는다. 키는 백업과 분리한 승인된 비밀 보관소에 보관해야 한다.

격리 복원은 별도 DB를 생성해 복호화·SQL을 검증하고 모든 복원 계정을 비활성화·세션을 제거한 뒤 그 격리 DB만 삭제한다. 운영 복원 전 공개를 중지하고 이 정책을 적용하여 탈퇴 계정이 자동 부활하지 않게 한다. 재활성화는 별도 운영 확인을 거친다. 실제 검증에서 articles=20, active_accounts=0, sessions=0이었다. 검증용 키·암호화 백업은 검증 후 정리한다. 수동 백업 보관기간은7일이며 자동 백업은 구성하지 않았다.

앱 rollback은 이전 앱과 현재 schema의 호환성을 확인한 경우에만 수행한다. 파괴적 DB rollback이나 취약한 이미지로의 무조건 복귀는 하지 않는다. 보안 수정 실패 시 외부 공개 중지·격리를 우선한다. 외부 배포·백업 저장소 접근통제·사업자 로그 정책은 실제 운영 환경이 정해진 뒤 확인해야 한다.
