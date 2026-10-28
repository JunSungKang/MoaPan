# 아키텍처

브라우저 → proxy → web → PostgreSQL. 별도 worker는 승인된 RSS/Atom/GET JSON → 정규화·추출형 요약·태그 → PostgreSQL. 동일 JAR의 역할을 MOAPAN_ROLE로 선택한다. migrate/admin-cli/worker는 HTTP 포트를 열지 않는다.

Account는 JPA, 기능별 CatalogRepository/JdbcClient는 명시 SQL을 사용한다. Controller→Service→Repository 경계를 유지하고 Entity는 응답으로 반환하지 않는다. 서버에 회원 개인 관심사·검색 이력 저장·클릭 추적 API는 없다. 기사 검색 API는 POST 조건을 일시 처리하며 공개 DB 자료만 조회한다. 관리자 공통 대표 관심사 singleton은 서버 DB에 저장하고 공개 기본값 API로 제공한다. IndexedDB 설정과 로컬 정렬은 내려받은 후보 안에서만 동작한다.

수집의 DNS 검증 결과를 Apache HTTP 연결 resolver에 고정한다. redirect는 같은 공개 HTTPS hostname만 허용한다. worker 결과 저장과 사이트/주소 변경은 site→source 잠금 순서를 사용한다. version과 lease token을 재검사하여 오래된 작업의 반영을 차단한다.
