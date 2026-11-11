# DamGoX2 공식 산출물

[산출물대장](01-management/deliverable-register.md)에서 문서번호·책임·경로를 확인한다. 기준선은 develop의 병합 커밋이며 작성/검토 상태와 실제 사용자 승인은 구분한다.

| 단계 | 공식 산출물 | 폴더 |
|---|---|---|
| 사업·프로젝트 관리 | 수행계획, 진척현황, 인도목록, 산출물대장 | [01-management](01-management/) |
| 분석 | 요구사항정의서, 기능명세서 | [02-analysis](02-analysis/) |
| 설계 | 아키텍처, DB, 인터페이스, 도메인 설계서, 설계결정 | [03-design](03-design/) |
| 구현 | 프로그램목록, 모델 검토 자료 | [04-implementation](04-implementation/) |
| 시험 | 인수 추적표, 시험결과서, 결함대장, 증빙 | [05-test](05-test/) |
| 이행 | 설치·배포, Linux 이관, 버전 구성 | [06-transition](06-transition/) |
| 운영 | 보안, 개인정보, 콘텐츠 출처 관리 | [07-operation](07-operation/) |

관리 절차는 [문서관리계획서](01-management/document-control.md), 변경 경로는 [이관대장](01-management/path-mapping.json)을 따른다.

Docker/Compose/Helm 명칭 변경은 [이관 설계서 MP-DOC-041](03-design/infrastructure-naming.md), 실제 실행 결과는 [검증서 MP-DOC-042](05-test/infrastructure-naming.md)를 정본으로 관리한다.

## Ubuntu25 플랫폼 운영 전환
- [MP-DOC-049 운영 구성·공통 PG·복구 인계](06-transition/platform-ubuntu25.md)
- [MP-DOC-050 Java25·보안·실제 교체 검증](05-test/platform-ubuntu25.md)

- [메모리·성능 최적화 검증서](05-test/memory-performance.md)
- [메모리 실행 정책·공통 운영 인계](06-transition/memory-runtime.md)
