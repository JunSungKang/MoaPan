variable "UBUNTU_IMAGE" {
 default = "ubuntu:26.04@sha256:6a06111c3b0a8600feadb270aec771dc239bf407b25625f93f44ab00e11f4d3c"
}
variable "NODE_IMAGE" {
 default = "node:24.18.0@sha256:5711a0d445a1af54af9589066c646df387d1831a608226f4cd694fc59e745059"
}
variable "JAVA_BUILD_IMAGE" { default = "" }
variable "JAVA_RUNTIME_IMAGE" { default = "" }
variable "NATIVE_BUILD_IMAGE" { default = "" }
variable "OS_RUNTIME_IMAGE" { default = "" }
variable "TAG" { default = "ubuntu26.04-java25-20261109" }
variable "PLATFORMS" { default = ["linux/arm64"] }
variable "JAVA_DIST_DIR" { default = "scripts/platform/java-dist" }
group "default" { targets = ["app", "proxy", "ai"] }
group "bases" { targets = ["base-os", "java-build", "java-runtime", "native-build"] }
target "_base" {
 context = "."
 dockerfile = "scripts/platform/base.Dockerfile"
 platforms = PLATFORMS
 args = { UBUNTU_IMAGE = UBUNTU_IMAGE, NODE_IMAGE = NODE_IMAGE }
 contexts = { java-dist = JAVA_DIST_DIR }
}
target "base-os" {
 inherits = ["_base"]
 target = "os"
 tags = ["damgox2-ubuntu:${TAG}"]
}
target "java-build" {
 inherits = ["_base"]
 target = "java-build"
 tags = ["damgox2-java-build:${TAG}"]
}
target "java-runtime" {
 inherits = ["_base"]
 target = "java-runtime"
 tags = ["damgox2-java-runtime:${TAG}"]
}
target "native-build" {
 inherits = ["_base"]
 target = "native-build"
 tags = ["damgox2-native-build:${TAG}"]
}
target "_project" {
 context = "."
 dockerfile = "Dockerfile"
 platforms = PLATFORMS
 contexts = {
  java-build-base = JAVA_BUILD_IMAGE != "" ? "docker-image://${JAVA_BUILD_IMAGE}" : "target:java-build"
  java-runtime-base = JAVA_RUNTIME_IMAGE != "" ? "docker-image://${JAVA_RUNTIME_IMAGE}" : "target:java-runtime"
  native-build-base = NATIVE_BUILD_IMAGE != "" ? "docker-image://${NATIVE_BUILD_IMAGE}" : "target:native-build"
  os-runtime-base = OS_RUNTIME_IMAGE != "" ? "docker-image://${OS_RUNTIME_IMAGE}" : "target:base-os"
 }
 args = { JAVA_BUILD_IMAGE = "java-build-base", JAVA_RUNTIME_IMAGE = "java-runtime-base", NATIVE_BUILD_IMAGE = "native-build-base", OS_RUNTIME_IMAGE = "os-runtime-base" }
}
target "app" {
 inherits = ["_project"]
 target = "runtime"
 tags = ["damgox2-app:${TAG}"]
}
target "proxy" {
 inherits = ["_project"]
 target = "public-proxy"
 tags = ["damgox2-proxy:${TAG}"]
}
target "ai" {
 inherits = ["_project"]
 target = "ai"
 tags = ["damgox2-ai:${TAG}"]
}
target "proxy-check" {
 inherits = ["_project"]
 target = "proxy-build"
 tags = ["damgox2-security-validation:ubuntu26.04-20261109"]
}
