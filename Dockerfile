FROM gcr.io/distroless/base-debian13:nonroot
WORKDIR /work
COPY --chown=nonroot:nonroot build/*-runner /work/application
EXPOSE 8080
USER nonroot
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
