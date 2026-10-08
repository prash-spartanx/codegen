services:

  # ================================================================
  # Main FastAPI application
  # ================================================================

  app:

    build:
      context: .
      dockerfile: Dockerfile

    container_name: ${projectName?lower_case}_app

    command: uvicorn app.main:app --host 0.0.0.0 --port 8000

    ports:
      - "8000:8000"

    restart: on-failure

    <#if hasPostgres || envVars?has_content>
    environment:

      <#if hasPostgres>
      DATABASE_URL: "postgresql://postgres:postgres@postgres:5432/${projectName?lower_case}"
      </#if>

      <#if envVars?has_content>
        <#list envVars?keys as key>
      ${key}: "${envVars[key]}"
        </#list>
      </#if>

    </#if>

    depends_on:

      <#if hasPostgres>
      postgres:
        condition: service_healthy
      </#if>

      <#if hasRedis>
      redis:
        condition: service_started
      </#if>

      <#if hasKafka>
      kafka:
        condition: service_started
      </#if>


  <#-- ================================================================
       PostgreSQL
       ================================================================ -->

  <#if hasPostgres>

  postgres:

    image: postgres:16-alpine

    container_name: ${projectName?lower_case}_postgres

    restart: on-failure

    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: ${projectName?lower_case}

    ports:
      - "5432:5432"

    volumes:
      - ${projectName?lower_case}_pgdata:/var/lib/postgresql/data

    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 10

  </#if>


  <#-- ================================================================
       Redis
       ================================================================ -->

  <#if hasRedis>

  redis:

    image: redis:7-alpine

    container_name: ${projectName?lower_case}_redis

    ports:
      - "6379:6379"

  </#if>


  <#-- ================================================================
       Kafka
       ================================================================ -->

  <#if hasKafka>

  kafka:

    image: bitnami/kafka:3.7

    container_name: ${projectName?lower_case}_kafka

    ports:
      - "9092:9092"

    environment:
      KAFKA_CFG_NODE_ID: 0
      KAFKA_CFG_PROCESS_ROLES: controller,broker
      KAFKA_CFG_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CFG_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 0@kafka:9093

  </#if>


  <#-- ================================================================
       API Gateway
       ================================================================ -->

  <#if hasApiGateway>

  gateway:

    build:
      context: .
      dockerfile: Dockerfile

    container_name: ${projectName?lower_case}_gateway

    command: uvicorn app.gateway:app --host 0.0.0.0 --port 8080

    ports:
      - "8080:8080"

    depends_on:
      - app

  </#if>


<#-- ================================================================
     Named volumes
     ================================================================ -->

<#if hasPostgres>

volumes:

  ${projectName?lower_case}_pgdata:

</#if>