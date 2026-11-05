FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --gid 10001 appgroup \
    && useradd --uid 10001 --gid appgroup --no-create-home appuser

COPY --chown=appuser:appgroup target/asset-management-0.0.1-SNAPSHOT.jar /app/app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-Xms128m", "-Xmx768m", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]