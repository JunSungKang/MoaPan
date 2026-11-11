# 메모리 실행 정책·운영 인계

문서번호: MP-DOC-054 · 기준일: 2026-11-11 · 상태: 기능·보안 검증 완료, 운영 반영 대기

같은 앱 이미지를 web/worker가 사용하고 image entrypoint가 역할에 맞는 기본값을 설정한다. 기본 JVM은 web Xmx256MiB, worker Xmx192MiB, Xms32MiB, compact object headers, code cache96MiB, MinHeapFreeRatio20/MaxHeapFreeRatio40 및 단계 없는 힙 축소를 사용한다. 명시한 JAVA_TOOL_OPTIONS와 MALLOC_ARENA_MAX는 보존한다. 기본 malloc arena는2다. GC 알고리즘·JIT tier는 기존 JVM 선택을 유지한다.

DB 풀은 web 최대8/유휴2, worker 최대6/유휴1이며 유휴 제한은60초이며 Hikari 점검 주기에 따라 반환까지 약30초가 추가될 수 있다. 중첩 트랜잭션과 3개 worker 스케줄러를 위한 연결 여유를 둔다. DAMGOX2_DB_POOL_MAX/MIN으로 조정할 수 있다. Compose는 DAMGOX2_WEB_DB_POOL_MAX/MIN, DAMGOX2_WORKER_DB_POOL_MAX/MIN을 받으며 Helm runtime.web/worker 값을 대응시킨다. HTTP 스레드는 최대32/유휴2, 최대값은 DAMGOX2_HTTP_THREADS로 조정한다.

컨테이너768MiB 한도와 AI 모델·context4096·동시 추론1·정책 지문·CA/truststore는 유지한다. 메모리 한도를 줄인 수치를 실사용 절감으로 보고하지 않는다. 메타스페이스·스레드 스택을 임의로 낮추거나 보안/추론 검증을 생략하지 않는다.

운영 교체는 공통 flock 아래 web/worker/translator/classifier만 대상으로 하고 기존 image/config/mount/network/secret/port를 기록한다. 공유 DB·proxy·메일·다른 서비스는 재생성하지 않는다. 실패하면 기록한 이전 실행 구성으로 복구한다. operations 파일을 수정하지 않으며 공통 운영 Compose가 재배포될 때는 운영 담당자가 새 앱 이미지 및 새 기본 환경 설정을 함께 인계받아야 한다.

공식 근거: [Java25 GC 선택 지침](https://docs.oracle.com/en/java/javase/25/gctuning/available-collectors.html), [HikariCP 설정](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby), [Spring Boot 설정](https://docs.spring.io/spring-boot/appendix/application-properties/index.html), [JEP519](https://openjdk.org/jeps/519). JEP 페이지는 브라우징403이었으며 실제 공급자 JVM의 product flag 지원을 실행 확인했다.

worker의 moapan.role을 DAMGOX2_ROLE에 연결하여 공유 캐시의 논리적 상한을16MiB에서8MiB로 맞춘다. 이 상한을 미리 할당한 실제 메모리로 계산하지 않는다.

공통 런타임에는 기존 서비스가 제외한 emSign G1 지문40f6af0346a99aa1cd1d555a4e9cce62c7f9634603ee406615833dc8c8d00367이 남아 있었다. 앱 이미지에서 기존 TrustStorePatch·OS 완화 도구를 재실행한다. 새 JDK 위에 예전 cacerts를 복사하지 않는다. PKCS12 저장 salt 차이를 제외한 실제 DER 인증서 지문 목록으로 이전117개 Java anchor와 동등성을 확인하고, 정상 HTTPS 및 공식14개 부정 체인 거부를 시험한다.

## AI 유휴 메모리

DAMGOX2_AI_IDLE_SECONDS는 기본300초, -1은 항상 준비 상태 유지, 허용 범위는1~86400초다. Helm의 aiIdleSeconds도 같다. llama.cpp의 기존 sleep 기능만 활성화한다. 모델·GGUF 체크섬·4096 context·동시성1·CPU2스레드·native 실행 파일·가드레일·API key와 네트워크 격리를 유지한다. /health와 /props는 깨우지 않으며 새 추론 작업만 모델을 다시 읽는다. Docker healthcheck와 watchdog이 정상 수면을 장애로 오판하지 않는지 검증한다.

장시간 연속 처리 중에는 기존 모델 RAM이 필요하다. 유휴 후 최초 배경 작업은 로딩 시간이 추가되며, 이미 저장된 뉴스를 읽는 HTTP 요청은 모델을 호출하지 않는다. 시험은10초 유휴로 두 차례 복귀를 확인했고 운영은300초로 설정한다. 출력 일치는 고정 합성 입력·seed42 기준이며 모델 정확도 전반의 보증이 아니다.

현재 공통 AI 실행 파일은 검증된 로컬 alias를 AI_EXECUTION_IMAGE로 받아 scripts/performance/ai-runtime.Dockerfile로 재사용했다. 원래 Dockerfile의 ai target으로 새로 빌드해도 동일 serve 스크립트를 사용한다. 다른 아키텍처에서는 그 아키텍처의 검증 이미지가 필요하다. 모델 원본과 secret은 이미지에 포함하지 않는다. [pinned llama.cpp sleep 계약](https://github.com/ggml-org/llama.cpp/blob/8642541b255bfb5dc39cd351a178be09013a55d6/tools/server/README.md#sleeping-on-idle).

## 공통 Compose 인계

공통 운영 Compose는 수정하지 않는다. 운영 담당자는 DAMGOX2_MEMORY_APP_IMAGE와 DAMGOX2_MEMORY_AI_IMAGE에 검증된 image ID를 넣고, 기존 Compose 뒤에 이 프로젝트 scripts/performance/compose.memory.yaml을 추가한다. --no-deps와 명시한 damgox2-web/worker/translator/classifier만 사용한다. 공개80/443·API20100·인증서/모델 볼륨·MailBox 연결은 그대로다. 복구에는 현재 컨테이너의 전체 유효 ENV와 원래 네 서비스 구성을 고정한 암호화 snapshot을 사용한다. .local/memory-performance-20261111/recovery-*.json.age에만0600으로 저장하고 평문은 메모리·stdin에서만 처리한다. age identity는 기본 .local/backups/keys/delivery-security-20261108.identity이며 DAMGOX2_RECOVERY_IDENTITY_FILE로 작업 폴더 안의 다른 identity를 지정할 수 있다. 공통 파일 변경은 정상 교체만 중단한다. 자동 복구는 변경된 공통 파일과 보간 환경을 다시 읽지 않는다. 수동 복구도 같은 운영 flock을 보유하고 age -d의 결과를 docker compose --env-file /dev/null -p platform -f - up -d --no-build --no-deps --pull never의 네 damgox2 서비스로 파이프한다. 공유 DB·다른 서비스는 포함하지 않는다.
