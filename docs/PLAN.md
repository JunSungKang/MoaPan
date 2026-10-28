# Sprint Delivery 실행 계획

허용 영역: /Users/kkkk/Desktop/jskang/03.workspace/01.project/moapan. 기존 README와 이전 bootstrap 산출물을 보존·발전시킨다. 추가 worktree와 workspace 외부 파일은 만들지 않는다.
목표: REQ-01~12, FS-01~26, AC-01~25를 실제 코드·PostgreSQL·브라우저·보안 증빙으로 연결한다.
제외: 사용자 최신 지시에 따라 CI/CD, GitHub Actions, 예약 workflow, Dependabot/Renovate 자동화는 구현·실행하지 않는다. 기타 V1 제외 기능과 유료 자원 구매도 제외한다. 로컬 수동 검증과 승인된 수동 배포는 범위다.

## 1. 재현 가능한 실행 기반
### 1.1 버전·공급망
공식 GA·공급자 빌드·보안 공지를 조회하고 checksum, Wrapper, lock, strict verification을 검증한다. 완료 증거: VERSIONS, SECURITY_REVIEW, 실제 JVM·Gradle 출력.
### 1.2 PostgreSQL·역할·컨테이너
Flyway, web/worker/migrate/admin-cli, Compose를 구성한다. 빈 DB 마이그레이션·재시작 영속성·백업 복원·포트 격리를 검증한다.
## 2. 인증·접근·개인정보
### 2.1 최소 계정·세션
Argon2id, 가입 필드 제한, 인증 관리자, 세션 교체·만료·철회·CSRF를 구현하고 MockMvc/PostgreSQL로 검증한다.
### 2.2 API 제한
공통 edition, 서명 cursor, 원자적 카운터·동시 lease, 공개 DTO, 안전한 오류·헤더를 구현한다. 변조·재로그인·경쟁·비공개 반영 검증이 완료 기준이다.
## 3. 수집·편집
### 3.1 사이트·주소·권리
DB CRUD·상태 전이·버전 충돌·미리보기·작업 202를 구현한다. 중지와 늦은 저장 경쟁, 보관 이력·복원 재검토를 검증한다.
### 3.2 worker·요약·태그
DNS를 고정한 공개 HTTPS, 안전한 XML/JSON 파서, 크기·재시도·lease·중복·수정·수동 잠금을 구현한다. fixture 검증과 권리 확인된 실제 출처 smoke를 구분한다.
## 4. 사용자 화면
### 4.1 로컬 개인화
계정 UUID별 IndexedDB, 관심 문장 로컬 추출·확정, 제외·가중치·순위·다양성, 로컬 검색을 구현한다. Playwright Java로 계정 분리와 네트워크 비전송을 검증한다.
### 4.2 신문·관리·안내
정적 HTML/CSS/ES Modules로 신문 4/2/1단, 관리자·정책·인쇄를 구성한다. 390/768/1440px 스크린샷과 키보드·긴 제목·오류 상태를 검증한다.
## 5. 인수·전달
### 5.1 로컬 검증·보안
check, integrationTest, e2eTest, bootJar, sbom, securityCheck, acceptanceTest를 실제 실행하고 검사 실패를 통과로 합산하지 않는다. 최종 이미지·truststore와 최신 scanner DB를 대조한다.
### 5.2 수동 배포·GitHub
사용자 변경 기준에 따라 로컬 Mac에서 검증 이미지로 실행하고 외부 휴대폰 데이터망 HTTPS 접속을 확인한다. 별도 호스팅 배포 검증은 제외한다. 지정 브랜치에 사람 wrapper commit/push 후 원격 SHA를 확인한다. main merge는 승인되지 않았다.

공유 코드 변경은 위 순서로 진행한다. 외부 권한·출처 이용권·고위험 UNKNOWN은 해당 외부 동작을 차단하되 독립 구현은 계속한다. 각 단계의 상태는 PROGRESS와 ACCEPTANCE_MATRIX에 기록한다.

## 6. 이용자 안내 문구 개정
### 6.1 문구
약관·개인정보 처리방침의 기술 용어를 일상어로 바꾸고 실제 수집·보관·삭제 범위를 유지한다. 상세 구현·검증 설명은 docs에만 둔다.
### 6.2 검증·전달
약관 새 버전 동의 검증, 기존 브라우저 테스트, 최종 JAR/이미지 검사 후 공개 서비스에 반영하고 guarded push한다. 운영자 정보는 만들지 않는다.

## 7. 로그인 제한 안내
### 7.1 서버 응답
5~9번째 로그인 실패에 남은 횟수,10번째 실패부터 실제 제한 잔여 초를 반환한다. 첫 실패 시점 기준15분 정책과 계정 존재 여부를 구분하지 않는 응답을 유지한다.
### 7.2 화면·검증
제한 시에만 카운트다운·버튼 비활성화를 보여주고 만료 시 수동 재시도를 허용한다. PostgreSQL에서 횟수·기한·재시도로 연장 없음·만료 후 성공을, Playwright에서 시간 감소·재활성화·다른 아이디 입력을 검증한다.

## 8. 가입 입력 길이 변경
### 8.1 정책과 저장
사용자 승인: 아이디1~128자, 비밀번호6~128 Unicode code point. 기존 문자 허용·정규화·해시·로그인 제한은 유지. V4로 기존 계정 보존하며 DB 제약을 변경한다.
### 8.2 검증
아이디 빈값/129자 거부·1/128자 가입 및 로그인, 비밀번호5자 거부·6자 가입/로그인/변경과128 Unicode 상한 회귀를 검증한다. 화면 안내·공개 실행·검사·푸시까지 갱신한다.
