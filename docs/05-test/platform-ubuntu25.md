# Ubuntu 26.04·Java 25 전환 검증서

문서번호: MP-DOC-050 · 최종 실행: 2026-11-09 14:27 KST · 상태: 구현·격리 검증·운영 교체 PASS. Git 결과는 최종 인계의 실제 원격 SHA로 확인한다. 독립 PR 리뷰·병합은 요청하지 않아 수행하지 않았다.

| 검증 | 실제 결과 |
|---|---|
| JDK/런타임 | Temurin25.0.5+8-LTS, JDK 빌드·JRE 실행, Ubuntu26.04 ARM64 |
| 단위/통합/E2E | 197 /158 /90 PASS, 합계445, 실패·오류·skip0 |
| sourceCheck/platform | Node24와 Compose 구조·shared PG override·실제 flock 경쟁3건 PASS |
| core·DB 분리 | 임시 PG의 SID/비밀번호 파일, 비관리자 migration, Flyway 쓰기 차단, API/CSRF PASS |
| worker | 격리 종료/재시작 PASS, 실운영672ms 종료·동일 컨테이너 재시작 후 heartbeat PASS |
| AI 정책 | 정확한14개 class old/new 지문 대조, 기존 결과 read/claim·idempotency·역매핑·unknown 격리 통합시험 PASS |
| 모델 실제 실행 | 같은 glibc AI 이미지, 번역2985ms/분류9812ms, 올바른 한국어·분류 JSON, 미인증401 |
| SMTP | 외부 MailBox STARTTLS/TLS1.3, CA/hostname 검증, AUTH235 PASS; 메시지 미발송 |
| CA | 최종 JRE117roots, 공식4개 root 부재, 정상HTTPS PASS, 악성/폐기14chain 모두 TLS_REJECTED; OS도 동일 정책 |
| JOSE | 정식5개 패키지 시험, 미패치5개 공격 재현, 수정본 통과, 프록시27경계·바이너리/소스 지문 PASS |
| securityCheck | JAR102패키지·SBOM107·소스·렌더 Helm·도구 자체 검사가 인식·성공; 미해결HIGH/CRITICAL0 |
| 이미지 | 최종3개 정확한 ID 검사, HIGH/CRITICAL0, secret0; Ubuntu26.04 패키지 인식 |
| DB·백업 | 기존 PG 중지/변경 없음, 암호화 백업을 tmpfs PG에 복원해39개 테이블 동일 |
| 실제 정책 전환 | 번역282건·작업 각544건 매핑, 정책 외33개 테이블 frozen 지문 동일 |
| 실제 HTTPS/API | 소개/지면/CSRF200, 비회원 계정401·구독 페이지404, HSTS/CSP PASS |
| 다른 운영 서비스 | MailBox4개·onlyplus2개·movie1개·coin PG1개, 동일ID/시작시각·실행 유지 |
| 커밋 대상 독립 빌드 | Git index 소스를 workspace 내부 별도 사본으로 추출, Java25 check/integrationTest/bootJar PASS; worktree 생성 안 함 |

최종 산출물과 시각은 [정제 증빙](evidence/platform-ubuntu25-20261109.json), 설정·전환·복구는 [인계서](../06-transition/platform-ubuntu25.md)를 따른다. 원시 보고서·DB 지문·자격증명·복구 키는 .local에만 보존한다. DB 스캔 동기화는2026-11-09T04:47:10.113223Z이며 검사/운영 반영 모두24시간 안이다. 공급자 DB 생성 시각과 동기화 시각을 별도로 확인했고 검사 오류를0건으로 처리하지 않았다.

중간 실패도 해결 이력으로 남긴다. Ubuntu GNU cp의 호스트 마운트 메타데이터 복원 실패는 해시 대조형 regular-file 복사기로 교체했다. source scan의 DS-0002는 기반 이미지까지 비루트 기본 USER를 두고 빌드 시에만 root 권한을 쓰도록 수정했다. 공식 release 다운로드의504는 동일 공식 URL의 동등한 plus 인코딩과 체크섬 검증된 workspace archive 주입으로 해결했으며 TLS/체크섬 검증은 완화하지 않았다. 변경된 최종 이미지를 다시 검사한 뒤 그 ID만 운영에 사용했다.

미실행 범위: AMD64·별도 Linux 호스트·GPU·공통 PG 실제 데이터 이관·수신함 배달 시험·실패 유발 운영 복구 훈련. 자동 복구 명령 경로는 제공하지만 실운영에 고의 장애를 유발하지 않았다. 이 범위를 PASS에 합산하지 않으며 미공개 취약점이 없다고 보장하지 않는다.
