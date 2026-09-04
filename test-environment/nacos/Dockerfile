FROM nacos/nacos-server:v3.1.2-slim@sha256:8e3a5eed20f3fa58f91d806bfad83b5742c73b13280a42c1c3e3c13e16e45f61
# Upstream docker-startup.sh enables xtrace before checking server authentication secrets.
# Keep the upstream startup logic while preventing those values entering container logs.
RUN sed -i 's/^set -x$/set +x/' /home/nacos/bin/docker-startup.sh \
    && ! grep -q '^set -x$' /home/nacos/bin/docker-startup.sh
