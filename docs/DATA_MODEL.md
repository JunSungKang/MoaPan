# 데이터 모델

SQL 진실 원천: src/main/resources/db/migration/V1__schema.sql, V2__tags.sql.

users: UUID·정규화 아이디·해시·활성·권한만. spring_session/attributes: 세션 운영. auth_rate_limits/api_quotas/api_request_leases: 짧은 목적별 집계와 동시 요청 lease.

sites 1:N sources 1:N articles. sites와 sources는 상태·권리·기술점검·version을 분리한다. source GUID와 canonical URL 각각 고유 제약으로 중복을 막고 article_revisions는 입력 해시 변경 이력을 보관한다. 태그는 aliases·redirects·article_tags로 연결한다. edition items는 공통 위치를 고정한다. content_jobs는 source별 활성 작업 1개, lease 선점과 재시도를 관리한다. fetch_runs·worker_status·admin_actions는 운영 최소 정보다.

사이트·주소·기사·작업 간 FK는 보관 시 cascade 삭제하지 않는다. 계정 삭제에 연결된 세션은 명시 삭제하고 quota는 FK cascade로 삭제한다. 만료 세션·카운터는 짧게, 오래된 edition은 만료 후1일, 완료 작업·fetch 이력은30일로 정리한다. 회원별 기사 행위 테이블은 생성하지 않는다.

## 가입 약관 기록 (V3)
users.terms_version varchar(32), terms_accepted_at timestamptz. 둘 다 NULL이거나 둘 다 값이 있어야 한다. 기존 회원은 NULL 유지(과거 동의 추정 금지), 신규 가입은 서버가 현재 버전 및 현재 시각을 저장한다. 탈퇴 시 계정과 함께 삭제한다. 동의용 IP·UA·기기 정보를 추가하지 않는다.

V4: users.username을 varchar(128)로 확대하고 CHECK를 영문 소문자·숫자·밑줄1~128자로 변경했다. 기존 계정·고유성·식별자·동의 기록을 보존하며 기존 마이그레이션 파일은 수정하지 않는다.

V5 representative_interests: singleton boolean PK/check true, weights jsonb object, version bigint>=0. 회원 FK/개인 관심사 없음. 활성 태그 검증·최대30개·가중치1~5는 service에서 강제하며 ADMIN 갱신은 version 조건 UPDATE. 기존 계정/동의 데이터 보존.

V6: sources.published_since(date), topic_terms(jsonb array max40); source_access_gate는 arXiv 공개 요청의 공유 간격/동시성 제어용 단일행이며 회원 데이터와 무관하다.

V7: `articles.author varchar(2000) NOT NULL DEFAULT ''` 추가. 회원 프로필이 아닌 공개 기사 저작자 메타데이터이며 기존 계정 정보와 연결하지 않는다. `processing_version=research-v2`는 연구 관점 문장 우선 추출을 나타내고 재수집 시 이전 버전도 재처리한다. 수동 요약 잠금은 보존한다.
