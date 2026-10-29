FROM ubuntu:24.04@sha256:24737cef17f4e22b1977df7d9a05e77c4e28b7da5b8911f8d8ccda136bf3573c AS build
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl cmake g++ make && rm -rf /var/lib/apt/lists/*
WORKDIR /source
RUN curl -fL --retry 2 -o source.tar.gz https://github.com/ggml-org/llama.cpp/archive/8642541b255bfb5dc39cd351a178be09013a55d6.tar.gz \
    && echo '1688a167a8edd9499cdd0c19b8bd044f0fa184b466981977bbf2222d884953a4  source.tar.gz' | sha256sum -c - \
    && tar -xzf source.tar.gz --strip-components=1 && rm source.tar.gz
RUN cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DGGML_NATIVE=OFF -DBUILD_SHARED_LIBS=OFF -DLLAMA_CURL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=ON \
    && cmake --build build --target llama-server -j 2
FROM ubuntu:24.04@sha256:24737cef17f4e22b1977df7d9a05e77c4e28b7da5b8911f8d8ccda136bf3573c AS classifier
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl util-linux libgomp1 libstdc++6 && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 10001 classifier && useradd -u 10001 -g 10001 --no-create-home classifier && mkdir /models && chown 10001:10001 /models
COPY --from=build /source/build/bin/llama-server /usr/local/bin/llama-server
COPY --chmod=0555 scripts/classification/model-init.sh scripts/classification/serve.sh /opt/classification/
USER 10001:10001
ENV LLAMA_CACHE=/tmp/cache XDG_CONFIG_HOME=/tmp/config
ENTRYPOINT ["/opt/classification/serve.sh"]
