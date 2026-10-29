# MOAPAN Helm chart

사용자 요청에 따라 Kubernetes 배치를 추가했다. 현재 Compose 실행과 별개이며 실제 클러스터 설치는 아직 수행하지 않았다. Helm4.3.1 lint/template로 검증한다. 한 전용 namespace에 하나의 release만 설치한다. db/web/translator/classifier/proxy Service 이름은 앱의 허용 호스트 및 proxy 구성과 일치하도록 고정돼 있다.

## 배포 전에 준비할 것

- Kubernetes 클러스터, NetworkPolicy 지원 CNI, RWO PVC storage class, HTTPS Ingress controller와 TLS Secret. HTTP 강제 HTTPS 전환은 선택한 controller에서 설정한다. Ingress 접근 로그는 URI/IP/회원 추적을 보관하지 않도록 운영 설정한다.
- 검증한 앱(runtime)·DB(database)·proxy(proxy)·translator·classifier 이미지를 승인된 registry에 게시하고 실제 registry digest를 values에 넣는다. 로컬 image ID를 registry digest로 간주하지 않는다. chart는 latest/tag-only 값을 거부한다. imagePullSecrets는 필요 시 설정한다. 이미지 게시·클러스터 생성·유료 구매는 자동 수행하지 않는다.
- 기존 Secret에 DATABASE_PASSWORD(64자 이상 random hex), DATABASE_ADMIN_PASSWORD, MOAPAN_SECRET, TRANSLATION_KEY, CLASSIFICATION_KEY를 안전하게 준비한다. 값 파일이나 비밀값을 Git에 넣지 않는다. 기존 DB 복원 시 role password와 Secret 일치가 필요하다.

비공개 values 파일(예: .local/kubernetes/values.yaml)에 publicHost, existingSecret, images의5개 digest, storageClass, ingress.className/tlsSecret을 설정한 뒤:

```sh
helm lint scripts/deploy/helm/moapan --strict -f .local/kubernetes/values.yaml
helm template moapan scripts/deploy/helm/moapan -n moapan -f .local/kubernetes/values.yaml
helm upgrade --install moapan scripts/deploy/helm/moapan --namespace moapan --create-namespace -f .local/kubernetes/values.yaml --wait --wait-for-jobs --timeout 40m
```

Secret과 TLS Secret은 설치 namespace에 먼저 준비해야 한다. 출력 template에는 Secret 참조만 있고 실제 secret 값은 없다. values 없이 설치하면 필수값 누락으로 실패한다. 환경별 포트나 PVC provisioner를 추측해 자동 생성하지 않는다.

## 시작과 저장
DB StatefulSet → revision별 migrate Job → schema version 확인 init container → web/worker. 새 migration 추가 시 migrationVersion도 갱신한다. 무중단 rolling schema 변경을 보장하지 않으며 운영 업그레이드 전 백업과 schema 호환성 점검이 필요하다. migrator는600초 상한, 실패 시 web/worker가 준비되지 않는다. 모델은 translator init container에서 아래 고정 Hugging Face URL을 다운로드·검증한 뒤 PVC에 보관하며 본 컨테이너는 read-only로 읽는다. 빈 볼륨은 자동 준비되고 기존 파일은 checksum 검사 후 재사용된다. 실패/손상은 기동을 차단한다.

- 모델: IndexTeam/Index-Translate-2B-GGUF / Index-Translate-2B.Q8_0.gguf
- revision: 3a0eca6ce931d88d00614d427ab3ed8ff0743902
- URL: https://huggingface.co/IndexTeam/Index-Translate-2B-GGUF/resolve/3a0eca6ce931d88d00614d427ab3ed8ff0743902/Index-Translate-2B.Q8_0.gguf
- SHA256: a09d287f41d297206947a2e51acc29dd03c8b22356111fdb1e289c0c97c173f3
- CPU2/thread2, memory limit4GiB(전체 클러스터 필요량 아님), 모델 PVC5Gi. 기본 replica1/Recreate. GPU는 미구현.

DB·모델 PVC는 Helm 삭제 시 keep 정책으로 보존한다. keep은 백업을 대체하지 않는다. PostgreSQL18 alpine UID70/fsGroup70과 모델UID10001을 storage driver가 지원해야 하며 실클러스터 권한/attach 검증은 필요하다. Compose volume은 Kubernetes PVC로 자동 복사되지 않는다. 별도 암호화 DB 백업·복원과 세션/탈퇴계정 정책을 따른다. 모델은 다시 다운로드할 수 있다.

## 네트워크와 한계
Service는 모두 ClusterIP다. ingress NetworkPolicy는 DB를 앱/migrate에, web을 proxy에, translator를 worker에 제한한다. chart의 egress는 아직 차단하지 않는다. init container와 실행 컨테이너가 Pod 네트워크를 공유하므로 HF/CDN 다운로드 허용과 실행 후 외부 통신 차단을 표준 NetworkPolicy 하나로 시간별 분리할 수 없기 때문이다. Compose의 runtime internal-network 차단과 동일하다고 주장하지 않는다. 운영 클러스터에서는 별도 model provisioning Job/egress proxy 또는 CNI FQDN 정책으로 다운로드 경계를 설계·검증해야 한다. 현재 차트만으로 전체 운영 보안 승인을 통과했다고 간주하지 않는다.

HTTPS 종료는 Ingress에서 수행하고 Caddy는 내부 HTTP reverse proxy만 담당한다. 자동 TLS 발급은 controller/cert-manager 운영 설정 영역이다. Kubernetes backup 스케줄/HA/registry push/CI/CD는 포함하지 않는다.

공식 동작 근거: https://kubernetes.io/docs/concepts/workloads/pods/init-containers/ , https://helm.sh/docs/topics/charts_hooks/ . 최초 DB가 필요한 migrate를 pre-install hook으로 실행해 DB 생성 전 대기하는 순환을 피하고 일반 Job으로 구성했다.

차트 회귀 검사: `HELM_BIN=/path/to/verified/helm sh scripts/test/helm-check.sh`. 합성값으로만 렌더링하며 클러스터 리소스를 생성하지 않는다.

## 분류 서비스 추가
values.images.classifier에도 registry `@sha256` digest를 지정하고 existingSecret에 CLASSIFICATION_KEY를 추가한다. classifier 모델은 별도 moapan-classification-model PVC(기본5Gi)에 HF revision e87f176479d0855a907a41277aca2f8ee7a09523의 Qwen3.5-4B-Q4_K_M.gguf를 내려받아 SHA256 00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4를 검증한다. worker→classifier:11436 경로만 ingress 허용, public Ingress 없음. migrationVersion은11이다. 전체 이미지5개/모델PVC2개이며 CLASSIFICATION_KEY와 TRANSLATION_KEY는 서로 다른 키다. 이 추가 구성도 파일 준비·lint/template 검증 대상이며 실제 클러스터 설치는 하지 않는다. 위 egress 제한 설명이 classifier에도 적용된다.
