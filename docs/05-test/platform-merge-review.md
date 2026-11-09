# Ubuntu25 플랫폼 병합 독립 리뷰 보완 검증서

문서번호: MP-DOC-051 · 기준일: 2026-11-09 · 상태: 보완 기능·보안·실제 운영 검증 PASS; 현재-head 재리뷰/병합은 PR 상태로 확인

PR #31의 최초 head 40a7f6f7b98d4710b3b230d766d9038635740326에 대한 독립 코드 리뷰는 CHANGES_REQUESTED였다. 이미 성공한 정상 운영 전환과 별개로 다음 실패 경로를 발견했다. 기존 성공 증빙을 이 실패 경로의 검증으로 대신하지 않았다.

| 항목 | 결함 | 보완과 검증 |
|---|---|---|
| F1 | 현재 대기 작업과 미확인 번역이 공존하면 덮어쓰기 가능; 미확인 부분 작업 초기화 | claim·heartbeat에서 unknown provider 배제, upsert의 provider 원자적 일치, 미확인 작업 자동 relabel·만료 초기화 제거. 대기 작업/부분 결과/늦은 결과 유입 PostgreSQL 회귀 |
| F2 | opaque taxonomy hash만 비교해 미확인 분류·태그를 재처리 | V28의 nullable model_policy로 검증된 모델과 taxonomy 변경 구분. 정확한 현 hash 또는 명시적 검증 전환만 provenance 부여. 미확인 태그 보존·비공개와 캐시 지문/검색 필터 검증 |
| F3 | 복원 소비자 조기 실패 시 생산자가 막혀 배포/lock 무기한 유지 | 양쪽 프로세스·pipeline·deadline·취소를 함께 감독, 오류 시 TERM/KILL·종료 회수. 조기 소비자 오류·정지·취소 회귀 |
| F4 | DB commit 후 응답 유실 시 역전환을 건너뛰고 구 worker 시작 | 외부 호출 전에 전환 시도 기록, 멱등 역전환과 실제 DB 안전 확인 후 구 worker 시작. 응답 유실/불명확한 역전환 회귀 |
| F5 | symlink 부모 아래 mkdir 후 workspace 밖 경로 거부 | 쓰기 전에 가장 가까운 기존 부모의 realpath 확인, canonical 목적지만 사용. workspace 내부에 모의 외부 영역을 만든 무부수효과 회귀 |

원본 코드에서 추가 PostgreSQL 회귀3건이 모두 실패하는 것을 확인한 뒤 수정했다. 복원/정책 응답/경로 검사8건은 성공했다. 미확인 데이터를 삭제하거나 무제한 scanner 예외를 추가하지 않았으며14개 AI 정책 class는 변경하지 않았다.

V28은 기존 article_classification_jobs에 시스템 모델 provenance 한 필드와 부분 인덱스를 추가하고, 해당 필드가 바뀌면 기사 캐시의 분류 지문도 변경되도록 한다. 기존 내용·태그·회원·작업 counters를 변경하는 backfill은 하지 않는다. runtime DML과 DB 소유 migration 역할의 분리는 유지한다. 클러스터 관리자 권한을 요구하지 않는다. 정책 CLI의 순·역전환은 model provenance도 알려진 source와 일치할 때만 함께 이행한다.

초기 정상 전환 증빙은 MP-DOC-050의 당시 기록으로 유지한다. 최종 보완 테스트·산출물·실제 반영 결과는 이 문서의 정제 증빙과 GitHub 현재-head 재리뷰로 연결한다. 강제 운영 장애를 발생시켜 rollback을 시험했다고 표시하지 않는다.

## 실제 재검증과 반영

단위197·통합163·E2E90, 합계450건 실패/오류/skip0이다. CSS 지면 시험의 언어 선택자 대기 timeout이 한 번 발생했으며 기준 완화 없이 단독 재실행과 전체90건 재실행을 통과했다. 최초 실패와 재실행 로그를 workspace 증빙으로 보존했다. securityCheck/acceptanceTest와 최종 앱·proxy·AI 이미지 검사도 통과했다.

동일 운영 flock 아래 새 암호화 백업을 격리 PG로 복원해39개 테이블 지문/건수를 대조한 뒤 V28을 적용했다. 기존36개 테이블과 model_policy를 제외한 분류 작업 내용이 그대로임을 확인했다. DB는 중지하지 않았고 proxy·모델과 다른 운영10개 컨테이너도 재시작하지 않았다. 웹·worker만 같은 검사 완료 앱 이미지로 교체하고 worker717ms 종료·재시작, HTTPS200·계정401·구독페이지404를 확인했다.14개 정책 class와 모든 POLICY/PROVIDER 값은 이전 실제 실행 JAR와 동일하다.

Docker image store에서 이전 index ID의 재실행 조회가 되지 않는 것을 중지 전 확인했다. 실행 중이던 원본 JAR의 SHA256을 기존 증빙과 대조하고, 비밀값이 없는 동일 Ubuntu/Java 기반에 그 JAR만 복사한 복구 이미지를 검사·보관했다. 컨테이너 Env·비밀 mount를 이미지로 export/commit하지 않았다. 새 운영 이미지에도 별도의 verified 태그를 남겼다. 이후 빌드에서도 이전 실행 이미지를 별도 태그로 먼저 보존해야 한다.

정제 결과는 [증빙](evidence/platform-merge-review-20261109.json)에 있으며 원시 검사·백업·원래 이미지 설정·복구용 override는 .local/platform-merge-20261109에만 있다. V28은 추가형 스키마라 복구 시 컬럼/이력을 파괴적으로 되돌리지 않는다. 실제 운영에 고의 장애를 발생시킨 시험은 수행하지 않았다.

새 최종 앱에서도 정상 HTTPS·기본117roots·공식4root 제거·폐기/악성14chain 거부와 MailBox STARTTLS/TLS1.3·CA·hostname·AUTH235를 재확인했다. 메일 메시지는 발송하지 않았다.
