FROM scratch

WORKDIR /app
COPY --chmod=0555 vault-server /app/vault-server
COPY --chmod=0555 vault-probe /app/vault-probe

USER 10001:10001
ENTRYPOINT ["/app/vault-server"]
