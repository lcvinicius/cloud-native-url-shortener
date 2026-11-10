# ==========================================
# Estágio 1: Build da Aplicação com Maven
# ==========================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 1. Otimização de Cache: Copia primeiro o pom.xml e baixa as dependências
# Isso garante que se o código mudar mas as dependências não, o Docker não rebaixa tudo.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Copia o código-fonte e gera o pacote
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Estágio 2: Extração das Camadas (Layered JAR)
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS extractor

WORKDIR /build

# Copia o JAR gerado no estágio anterior
COPY --from=builder /build/target/*.jar application.jar

# Extrai as camadas do Spring Boot
# Separa dependências de terceiros do código da sua aplicação
RUN java -Djarmode=layertools -jar application.jar extract

# ==========================================
# Estágio 3: Runtime de Produção Seguro
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Boas práticas de Segurança (DevSecOps):
# Nunca rodar containers como 'root'. Criamos um usuário sem privilégios.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copia as camadas na ordem da que menos muda para a que mais muda
# Garantindo que o cache do Docker seja aproveitado ao máximo em novos deploys
COPY --from=extractor --chown=appuser:appgroup /build/dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /build/spring-boot-loader/ ./
COPY --from=extractor --chown=appuser:appgroup /build/snapshot-dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /build/application/ ./

# Muda para o usuário não-privilegiado
USER appuser:appgroup

# Expõe a porta da aplicação
EXPOSE 8080

# Flags de Engenharia de JVM para Containers:
# -XX:+UseContainerSupport: Garante que a JVM leia os limites de CPU/RAM do cgroups (K8s/Docker)
# -XX:MaxRAMPercentage=75.0: Evita que a JVM tome 100% da RAM e seja morta pelo kernel (OOMKilled)
# -XX:InitialRAMPercentage=50.0: Aloca metade da memória na inicialização para reduzir pausas de GC
# -XX:+ExitOnOutOfMemoryError: Se a JVM der falta de memória, o processo cai imediatamente e o Kubernetes reinicia
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

