# 이메일 인증·복구 검증서

> 문서번호: MP-DOC-038 · 상태: 검증 중 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

## 1. 범위

REQ-03/08/10/11과 사용자 승인 이메일 요구를 검증한다. 사용자 입력 길이, 기존 계정/뉴스/구독 보존, CSRF/세션, 이메일 목적 제한을 회귀 검증한다. 테스트는 합성 수신자만 사용하며 실제 회원 주소·비밀번호·인증번호를 증빙에 출력하지 않는다.

## 2. 실행 기록

2026-11-03 1차: EmailIdentityIntegrationTest 8개 및 기존 SubscriptionAccessIntegrationTest 공개/구독 차단 1개를 PostgreSQL Testcontainers에서 실행, 9 PASS. 실제 HTTP 세션 쿠키를 유지해 가입·재사용/만료/목적 혼동/5회 오입력/동시 소비/전체 세션 복구 폐기/발송3회 제한/목적·동의를 검증했다. 초기 테스트의 MockHttpSession 전달이 JDBC Session을 대체하지 못해 실패했고 실제 SESSION 쿠키 방식으로 고쳐 재검증했다. 판정 기준은 유지했다.

전체 회귀, 화면/SMTP TLS, 보안·이미지, 서비스 반영, PR 독립 리뷰는 아직 미완료다. 실제 SMTP 수락/외부 받은편지함 전달은 구분해 기록하며 미실행을 PASS로 합산하지 않는다.

## 3. 회귀·보안 실제 결과

2026-11-03 재검증: check/integrationTest/e2eTest/bootJar/sbom 명령 성공. 단위179, PostgreSQL141, 브라우저72 =392 PASS, FAIL0, SKIP0. 이후 계정 삭제 시 이메일 카운터 정리와 회귀1개를 추가했고 최종393개 재실행 중이다. 독립 사전 리뷰가 지적한 잠금 역전과 외부 SMTP 평문 fallback을 수정했고 재리뷰에서 추가 차단 결함 없음(정식현재HEAD 승인은 아님)을 받았다.

Nu HTML26.10.12(0a70d65) 전체 페이지 검사 메시지0. 390/768/1440px signup/login/recovery 합성 스크린샷과 비밀번호 찾기 링크/한영 전환/명시적 요청 전 비전송/코드 미저장/용도 고지를 Playwright로 확인했다. SMTP TLS 단위4개는 실제 JSSE 서버에서 정상/다른호스트/불신CA/STARTTLS없음을 검사했다.

Postfix3.11.7 실제 컨테이너에서 STARTTLS+인증서/호스트 검증을 켠 JavaMail 전송과 합성 local mailbox 증가를 확인했다(실제 외부 수신자는 미사용). external policy는 smtp_tls_security_level=verify, smtp_tls_verify_cert_match=hostname이다. 테스트용2525 loopback fixture는 종료했으며 운영 SMTP 포트는 공개하지 않는다.

앱 image sha256:aea1a0b788d33441d6538eaa38c5283ba948ac2d32022f5115116e86dcb22787 및 SMTP image sha256:0230b6dc5351df59653651d9d288772fd4e66a610eea40ed2dd5b1d5b7b8b8c4의 Trivy0.76.1+moapan-security.1 취약점/비밀 검사 gate 통과. 앱 이미지는 마지막 계정 삭제 수정 후 다시 빌드/검사해야 하므로 최종 배포 digest가 아니다. 공급자 Maven Central 원본 다운로드와 공개 checksum으로 신규 runtime5개를 독립 대조했고 라이선스/NOTICE/대응 소스도 보관했다. SBOM107개.

전체 securityCheck는 SMTP Dockerfile DS-0002 HIGH(root) 1건으로 실제 FAIL, acceptanceTest는 보안 게이트 미통과로 미완료다. Debian12의34건, Debian13의44건 HIGH 이미지는 채택하지 않았다. 최종 Alpine SMTP 이미지의 영향 HIGH/CRITICAL/secret gate는 통과했으나 root 설정 위험은 별도다. 이 경고를 숨기거나 예외 처리하지 않았고 사용자 위험 수용 답변 대기 중이다.

운영 DB는V24/기존 이미지, 기존 계정/구독/기사 유지. 신규 화면/API와V25 운영 반영, 실제 외부 받은편지함 전달, 최종 PR/정식독립리뷰/푸시는 아직 미완료다. develop/main은 변경하지 않았다.

## 4. 최종 기능 검증과 보류

최종 전체 실행(role-fixed-tests.log): 단위179 + PostgreSQL145 + 브라우저72 =396 PASS, FAIL0, SKIP0. 추가17개 PostgreSQL 검증은 이메일16개와 비웹 worker 역할 부팅/발송1개다. 처음 비웹 부팅 시험은 웹용 PasswordEncoder 의존성으로 실제 실패했고, EmailIdentity/IdentityPasswords를 웹 역할에만 등록해 수정 후 전체 명령을 재실행했다. web/worker/관리 CLI 역할 분리와 기존 인증/구독 회귀를 유지한다.

메일 큐 용량10000건 초과 시 원자적503과 도전 생성 롤백, 실제 가입 증명으로5자/129자 거부·6자 허용, 탈퇴 시 이메일 도전/발송/계정·주소 제한 정리도 포함한다. scripts/test/smtp-check.sh는 새 독립 테스트 프로젝트에서 SMTP read-only rootfs와 메모리 큐를 사용해 TLS/합성 Maildir 전달을 재현하고 테스트 컨테이너·볼륨을 정리한다. 최종 SMTP는 Postfix3.11.7/LMDB, 입출력 필수 TLS와 외부 인증서/호스트 검증을 사용한다.

추가 Helm mail.server 옵션은 기본 비활성으로 차트만 작성·렌더링했고 Kubernetes에 적용하지 않았다. 비고정 이미지/전체 신뢰망/맞지 않는 TLS 모드3개도 거부했다. 명시한 private worker Pod CIDR·CNI NetworkPolicy·분리된 TLS Secret·메모리 큐를 요구한다.

보류: root master DS-0002 HIGH에 대한 사용자 위험 수용 답변 전에는 예외를 추가하지 않으며 전체 securityCheck/acceptanceTest는 미완료다. MailBox 기본 발신 도메인은 공개 DNS의 SPF가 다른 MX 서버만 허용하고 있어, 현재 Mac에서 사용할 발신 주소/DNS를 확인하기 전 실제 회원 메일이나 신규 운영 서비스를 활성화하지 않는다. 이 두 정보를 사용자에게 질문했다. 개발 소스/로컬 합성 검증과 운영 반영을 구분한다.

## 5. 최종 증빙 식별

최종 앱 이미지: sha256:e095d9a36f260e8adb6e0105c7f900ff7e55fa6644e06dcf1c1d3906e3ba66ec. SMTP 이미지: sha256:96737b40de9f24d54bae15c22499dd706351db4cd534a955b0b43c70cd618a7b. 최신 소스의 privacy-race 회귀는 계정 잠금 중 credential 버전이 바뀌어도 공개 복구 요청이 같은202로 끝나고 사용할 수 없는 증명이 되도록 검증한다. 최종 전체 명령과 이미지 검사는 .local/email-auth-20261103의 identity-delivery-tests/release-image-scans/release-acceptance 로그를 구분해 기록한다. securityCheck는 DS-0002에 대한 승인 대기 상태이며, acceptanceTest를 PASS로 합산하지 않는다.

최종 재빌드 식별(위 중간 식별을 대체): 앱 sha256:e095d9a36f260e8adb6e0105c7f900ff7e55fa6644e06dcf1c1d3906e3ba66ec, SMTP sha256:96737b40de9f24d54bae15c22499dd706351db4cd534a955b0b43c70cd618a7b. identity-delivery-tests의396 PASS 및 release-image-scans의 두 이미지 gate PASS를 확인했다. release-acceptance 명령은 securityCheck DS-0002 HIGH로 실제 exit1이며 acceptanceTest는 실행 완료가 아니다. private TLS/SMTP 검증 외 운영 발신은 미실행이다.
