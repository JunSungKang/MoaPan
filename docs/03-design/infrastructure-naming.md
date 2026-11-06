# MP-DOC-041 인프라 명칭·데이터 이관 설계서

## 범위
사용자 승인으로 Docker/Compose/Helm의 프로젝트·이미지·컨테이너·네트워크·볼륨, 실행 JAR/OS 사용자/고지 경로, 환경 변수, PostgreSQL DB/역할 이름을 damgox2로 변경한다. 결제 기능·CI/CD·타 프로젝트·전역 스킬은 제외한다.

Java com.moapan/package와 moapan.* property는 소스 호환 식별자다. 기존 세션의 직렬화 클래스·MOAPAN_CREDENTIAL_VERSION·MOAPAN_REMEMBER_UNTIL, 브라우저 저장 키, 원래 SQL 마이그레이션과 역사적 증빙은 변경하지 않는다. 기존 DB/역할은 OID를 유지한 RENAME으로 바꾸며 비밀번호·권한·데이터·암호화 키를 재생성하지 않는다. 외부 MailBox 설정·인증서·네트워크는 그대로 연결한다. 기사/모델/회원 데이터는 출력하지 않는다.

## 이관 순서와 실패 처리
1. 원래 리소스/비밀 파일의 지문을 비공개 작업 경로에 기록하고 새 이미지 빌드·스캔을 완료한다.
2. web/worker를 정지하여 쓰기를 멈춘다. 암호화 pg_dump와 격리 DB 복원·전체 테이블 행 지문을 검증한다.
3. 기존 DB/proxy/모델을 정지한다. scripts/deploy/infrastructure-migration.mjs가 실행 중인 소비자·누락 원본·기존 목적지를 거부한다. 8개 볼륨의 소유자/모드/링크/파일 SHA256을 보존·비교한다. 원본은 즉시 지우지 않는다.
4. 복사 DB에서 DB/역할 이름을 바꾼 뒤 모든 public 테이블 건수·행 SHA256을 비교한다. Compose에 같은 secret 값과 검증한 정확한 이미지 ID를 적용한다. AI 정책14개 클래스 bytes와 실제 POLICY/PROVIDER를 기준 JAR와 비교하고 기존 unlocked 번역/완료 분류가 read·claim 후 남는지 검증한 뒤 worker를 시작한다.
5. 새 서비스/HTTPS/모델/이메일 연결·접근 경계를 확인한 뒤 정확히 기존 프로젝트 소유인 리소스만 정리한다. 불일치 시 새 서비스 시작/원본 삭제를 중단하고 원래 이미지·볼륨으로 복귀한다. down -v 또는 전역 prune을 사용하지 않는다.

## Helm
차트는 scripts/deploy/helm/damgox2, release/namespace 예시는 damgox2, PVC·selector·Secret 참조는 새 이름이다. 기존 클러스터의 release 이름을 바꾸는 것은 upgrade가 아니며 새 PVC가 자동으로 기존 데이터를 사용하지 않는다. 실클러스터는 이번 작업에 제공되지 않았다. 기존 설치가 있다면 먼저 기존 namespace/release와 PVC/Secret 백업을 확보하고 DB/모델을 별도 복원·검증한 뒤 전환한다. 삭제 시 keep은 백업이 아니다.

## 검증
전체 Gradle 계약, 새 명칭/오프라인 이관 거부 시험, Compose 모든 overlay, Helm lint/template·부적절 값 거부, 최종 이미지 검사, 실제 데이터 일치/HTTPS를 수행한다. 결과는 [MP-DOC-042](../05-test/infrastructure-naming.md)에 기록하며 미실행을 PASS로 표시하지 않는다.

## AI 처리 식별자 호환
모델 어댑터의 전체 class bytes는 실행 정책 해시의 입력이다. 배포 이름만 바꾸면서 @Value나 요청 model 문자열을 바꾸면 기존 번역이 무효화·삭제되고 전체 분류가 재대기한다. 기존 두 Java 어댑터와14개 정책 입력 클래스 bytes, POLICY/PROVIDER를 그대로 유지한다. DAMGOX2 환경→기존 내부 Java property는 application.properties에서 연결한다. private 요청의 legacy model 필드는 호환 식별자이며 Docker/server alias는 damgox2다. 두 실제 서버가 이를 정상 처리함을 시험했다. AiPolicy의 실제 class 해시 검증이나 stale-provider 거부를 약화하지 않는다. 실질 정책 변경은 별도 데이터 영향·보안 검토가 필요하다.

worker 재개 전의 지문 일치만으로 이관을 완료로 판단하지 않는다. 기존 대표 번역과 분류 job/tag를 읽고 claim한 후 보존되는지 확인한다. 후속 복구는 기사·권리·주제·운영자 변경 부재를 먼저 확인하고4개 파생 테이블만 단일 DELETE/COPY 트랜잭션으로 복원했다. 모든 캐시 trigger를 유지하고 지문을 재검증했으며 실제 소모 예산은 초기화하지 않는다.
