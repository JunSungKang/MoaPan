# MP-DOC-041 인프라 명칭·데이터 이관 설계서

## 범위
사용자 승인으로 Docker/Compose/Helm의 프로젝트·이미지·컨테이너·네트워크·볼륨, 실행 JAR/OS 사용자/고지 경로, 환경 변수, PostgreSQL DB/역할 이름을 damgox2로 변경한다. 결제 기능·CI/CD·타 프로젝트·전역 스킬은 제외한다.

Java com.moapan/package와 moapan.* property는 소스 호환 식별자다. 기존 세션의 직렬화 클래스·MOAPAN_CREDENTIAL_VERSION·MOAPAN_REMEMBER_UNTIL, 브라우저 저장 키, 원래 SQL 마이그레이션과 역사적 증빙은 변경하지 않는다. 기존 DB/역할은 OID를 유지한 RENAME으로 바꾸며 비밀번호·권한·데이터·암호화 키를 재생성하지 않는다. 외부 MailBox 설정·인증서·네트워크는 그대로 연결한다. 기사/모델/회원 데이터는 출력하지 않는다.

## 이관 순서와 실패 처리
1. 원래 리소스/비밀 파일의 지문을 비공개 작업 경로에 기록하고 새 이미지 빌드·스캔을 완료한다.
2. web/worker를 정지하여 쓰기를 멈춘다. 암호화 pg_dump와 격리 DB 복원·전체 테이블 행 지문을 검증한다.
3. 기존 DB/proxy/모델을 정지한다. scripts/deploy/infrastructure-migration.mjs가 실행 중인 소비자·누락 원본·기존 목적지를 거부한다. 8개 볼륨의 소유자/모드/링크/파일 SHA256을 보존·비교한다. 원본은 즉시 지우지 않는다.
4. 복사 DB에서 DB/역할 이름을 바꾼 뒤 모든 public 테이블 건수·행 SHA256을 비교한다. Compose에 같은 secret 값과 검증한 정확한 이미지 ID를 적용한다.
5. 새 서비스/HTTPS/모델/이메일 연결·접근 경계를 확인한 뒤 정확히 기존 프로젝트 소유인 리소스만 정리한다. 불일치 시 새 서비스 시작/원본 삭제를 중단하고 원래 이미지·볼륨으로 복귀한다. down -v 또는 전역 prune을 사용하지 않는다.

## Helm
차트는 scripts/deploy/helm/damgox2, release/namespace 예시는 damgox2, PVC·selector·Secret 참조는 새 이름이다. 기존 클러스터의 release 이름을 바꾸는 것은 upgrade가 아니며 새 PVC가 자동으로 기존 데이터를 사용하지 않는다. 실클러스터는 이번 작업에 제공되지 않았다. 기존 설치가 있다면 먼저 기존 namespace/release와 PVC/Secret 백업을 확보하고 DB/모델을 별도 복원·검증한 뒤 전환한다. 삭제 시 keep은 백업이 아니다.

## 검증
전체 Gradle 계약, 새 명칭/오프라인 이관 거부 시험, Compose 모든 overlay, Helm lint/template·부적절 값 거부, 최종 이미지 검사, 실제 데이터 일치/HTTPS를 수행한다. 결과는 [MP-DOC-042](../05-test/infrastructure-naming.md)에 기록하며 미실행을 PASS로 표시하지 않는다.
