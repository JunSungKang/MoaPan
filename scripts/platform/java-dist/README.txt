Optional pre-fetched, official checksum-verified Temurin archives may be supplied with
JAVA_DIST_DIR=<workspace-local-directory> to docker buildx bake. Without archives,
the build downloads the same official URLs and enforces the same SHA256 pins.
Do not place credentials here. Archives and cache contents must never be committed.
