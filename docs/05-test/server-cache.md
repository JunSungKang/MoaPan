# 서버 메모리 캐시 검증서

> 문서번호: MP-DOC-031 · 기준: 저장소 현재 리비전 · 상태: 검증 중 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

## 범위와 상태
Caffeine 기반을 도메인 적용과 분리한다. 이 기반은 아직 뉴스/태그/뉴스레터 API에 연결하지 않았다. 운영 조회 성능 향상·운영 배포·데이터 마이그레이션 성공으로 표시하지 않는다. 브라우저 최적화는 별도 PR16이다.

## 기반 검증
VersionedReadCacheTest 신규9건과 ReadCacheIntegrationTest 신규4건을 실제 실행해 통과했다. 고정 ticker로60분 만료·해시 변경 시 조기 재적재·동시 miss 병합·값 무게·큰 값/실패 제외를 검증한다. 실제 PostgreSQL18 Testcontainers에서 V1~17 빈 DB migrate/validate/재실행, 동일 해시 행 불필요 갱신 방지, commit/rollback, 두 인스턴스 및 초기 해시와 miss snapshot 사이 변경을 검사한다.

## 최종 기반 검증
전체288 PASS(단위163/PG71/E2E54), 실패·오류·skip0. strict verification·lock·licenseCheck·문서·소스/JAR/SBOM·격리 최종 이미지 gate PASS. 실제 이미지와 호스트 JAR SHA-256 일치. [실행 증빙](evidence/server-cache-foundation-20261101.json)에 정확한 image ID·JAR SHA·scanner DB·도구 정보를 기록했다.

추가 DB 재시작 시험의 첫 실행은 Docker가 임시 공개 포트를 바꾸어 이전 JDBC URL이 실패했다. 최신 포트를 재조회해 시험 연결을 갱신했고 실제 재시작 전후 데이터·지문 보존을 같은 기준으로 통과했다. 이후 전체 PG71건을 재실행해 통과했다. 최초 실패는 증빙에 기록했으며 최종 성공으로 과거 실패를 숨기지 않는다.

독립 사전 리뷰에 차단 결함이 없었으며 현재 head formal 리뷰·PR 전달을 진행한다.
## 후속 도메인 적용 전 미완료 사항
도메인 API별 실제 조회 감소·권리 철회·공개 숨김·quota 보존·최종 합산 검증은 도메인 적용 후 수행한다. 논리 가중치 예산은 실제 JVM heap과 다르며 실제 할당/GC도 도메인 적용 후 계측한다.
