# FiadoPay - Gateway de Pagamento Simulado

## Sumário

- [Como Executar](#-como-executar)
- [Endpoints da API](#-endpoints-da-api)
- [Decisões de Design](#-decisões-de-design)
- [Anotações Customizadas](#-anotações-customizadas)
- [Mecanismo de Reflexão](#-mecanismo-de-reflexão)
- [Uso de Threads](#-uso-de-threads)
- [Padrões Aplicados](#-padrões-aplicados)
- [Limites Conhecidos](#-limites-conhecidos)
---
## Como Executar

### Executando a Aplicação

#### Clone o repositório
```bash
git clone https://github.com/SilvaOrdep/fiadopay.git
cd fiadopay
```
#### Execute com Maven Wrapper
```bash
./mvnw spring-boot:run
```
#### Ou faça dessa forma 
```bash
./mvnw clean package
java -jar target/fiadopay-sim-1.0.0.jar
```

### Acessando Interfaces
- **API REST**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **H2 Console**: http://localhost:8080/h2
  - JDBC URL: `jdbc:h2:mem:fiadopay`
  - Username: `sa`
  - Password: _(vazio)_

---

## Endpoints da API

### 1️ Cadastrar Merchant (Admin)

```bash
curl -X POST http://localhost:8080/fiadopay/admin/merchants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MinhaLoja ADS",
    "webhookUrl": "http://localhost:8081/webhooks/payments"
  }'
```

### 2️ Obter Token de Autenticação

```bash
curl -X POST http://localhost:8080/fiadopay/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "client_abc123",
    "client_secret": "secret_xyz789"
  }'
```


### 3 Criar Pagamento

```bash
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "CARD",
    "currency": "BRL",
    "amount": 250.50,
    "installments": 12,
    "metadataOrderId": "ORD-123"
  }'
```

### 4️ Consultar Pagamento

```bash
curl http://localhost:8080/fiadopay/gateway/payments/<paymentId>
```

### 5️ Criar Reembolso

```bash
curl -X POST http://localhost:8080/fiadopay/gateway/refunds \
  -H "Authorization: Bearer FAKE-1" \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "pay_a1b2c3d4"
  }'
```

### 6️ Health Check

```bash
curl http://localhost:8080/health
```
---

## Decisões de Design

### Arquitetura em Camadas
O projeto segue uma arquitetura em camadas bem definida:

```
Controller (REST API) → Service (Lógica de Negócio) → Repository (Persistência)
                            ↓
                   Provider (Estratégias de Pagamento)
```

**Justificativa**: Separação clara de responsabilidades, facilitando manutenção e testabilidade.

### Processamento Assíncrono
Pagamentos são processados de forma assíncrona utilizando `ExecutorService`:
- **Thread Pool**: 5 threads fixas para processar pagamentos
- **Delay Simulado**: 1500ms (configurável) para simular processamento real
- **Taxa de Falha**: 15% (configurável) para simular rejeições

**Justificativa**: Simula o comportamento real de PSPs onde o processamento não é instantâneo.

### Sistema de Webhooks
Implementado com retry automático e exponential backoff:
- **Tentativas**: Até 5 tentativas com delay crescente
- **Assinatura HMAC**: Garante integridade e autenticidade dos webhooks
- **Persistência**: Registro de todas as tentativas de entrega

**Justificativa**: Garante entrega confiável de eventos mesmo com falhas temporárias de rede.

### Idempotência
foi mantida

---

## Anotações Customizadas

O projeto utiliza duas anotações customizadas para implementar funcionalidades específicas:

### 1. `@PaymentMethod`

**Localização**: `edu.ucsal.fiadopay.annotation.PaymentMethod`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PaymentMethod {
    String paymentType();
}
```

**Metadados**:
- `paymentType`: String que identifica o tipo de pagamento (ex: "CARD", "PIX", "DEFAULT")

**Propósito**: Marca classes que implementam estratégias de pagamento específicas, permitindo registro automático no `PaymentMethodRegistry`.

**Exemplo de Uso**:
```java
@Component
@PaymentMethod(paymentType = "CARD")
public class CardPaymentProvider implements PaymentProvider {
    // Implementação específica para cartão
}
```

### 2. `@RateLimit`

**Localização**: `edu.ucsal.fiadopay.annotation.RateLimit`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {
    int maxRequest() default 10;
    long windowSeconds() default 60;
}
```

**Metadados**:
- `maxRequest`: Número máximo de requisições permitidas (padrão: 10)
- `windowSeconds`: Janela de tempo em segundos (padrão: 60)

**Propósito**: Implementa rate limiting em endpoints da API, protegendo contra abuso.

**Exemplo de Uso**:
```java
@PostMapping("/payments")
@RateLimit(maxRequest = 10, windowSeconds = 60)
public ResponseEntity<PaymentResponse> create(...) {
    // Limitado a 10 requisições por minuto por merchant
}
```

---

## Mecanismo de Reflexão

### Implementação no `PaymentMethodRegistry`

**Localização**: `edu.ucsal.fiadopay.registry.PaymentMethodRegistry`

```java
@PostConstruct
public void setProcessors() {
    // 1. Busca todos os beans anotados com @PaymentMethod
    Map<String, Object> beans = applicationContext.getBeansWithAnnotation(PaymentMethod.class);
    
    // 2. Para cada bean, extrai a anotação via reflexão
    for(Object bean : beans.values()) {
        PaymentMethod paymentMethod = bean.getClass().getAnnotation(PaymentMethod.class);
        if (paymentMethod != null && bean instanceof PaymentProvider) {
            // 3. Registra no mapa usando o paymentType como chave
            paymentProviders.put(paymentMethod.paymentType(), (PaymentProvider) bean);
        }
    }
}
```

### Fluxo de Reflexão:
1. **Descoberta Automática**: Spring escaneia classes anotadas com `@PaymentMethod`
2. **Leitura de Metadados**: Reflection API extrai o valor de `paymentType()`
3. **Registro Dinâmico**: Providers são registrados em um `Map<String, PaymentProvider>`
4. **Resolução em Runtime**: Ao processar pagamento, o registry retorna o provider correto
---

## Uso de Threads

O projeto utiliza threads de forma extensiva para simular comportamento assíncrono de sistemas de pagamento reais.

### 1. Processamento de Pagamentos (`PaymentService`)

```java
private final ExecutorService executorService = Executors.newFixedThreadPool(5);

// Submete processamento assíncrono após criar pagamento
executorService.submit(() -> processAndPublish(payment.getId()));
```

**Características**:
- **Pool Fixo**: 5 threads dedicadas ao processamento de pagamentos
- **Fire-and-Forget**: Cliente recebe resposta imediata com status PENDING
- **Simulação de Delay**: Thread.sleep(1500ms) simula processamento real
- **Atualização Assíncrona**: Status muda para APPROVED/DECLINED após processamento

### 2. Sistema de Webhooks (`WebhookListener`)

```java
private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
private final ExecutorService executorService = Executors.newFixedThreadPool(5);

// Envia webhook de forma assíncrona
executorService.submit(() -> tryDeliver(delivery.getId()));

// Agenda retry com delay crescente
private void scheduleRetry(Long deliveryId, long delayMs) {
    scheduler.schedule(() -> tryDeliver(deliveryId), delayMs, TimeUnit.MILLISECONDS);
}
```

**Características**:
- **Executor Pool**: 5 threads para envio de webhooks
- **Scheduled Pool**: 5 threads para gerenciar retries agendados
- **Exponential Backoff**: Delay crescente entre tentativas (tentativa * 1000ms)
- **Até 5 Tentativas**: Sistema desiste após 5 falhas consecutivas

### Modelo de Concorrência

```
Cliente HTTP → Controller → Service
                               ↓
                          ExecutorService (Thread Pool)
                               ↓
                          [Thread 1] → Processa Payment → Publica Event
                          [Thread 2] → Envia Webhook → Retry?
                          [Thread 3] → ...
```

### Sincronização e Thread-Safety:
- **ConcurrentHashMap**: Usado no `RateLimitInterceptor` para cache thread-safe
- **AtomicInteger**: Contadores de rate limit são atômicos
- **@Transactional**: Garante consistência nas operações de banco de dados
- **Event Publishing**: Comunicação entre componentes via Spring Events (thread-safe)

---

## Padrões Aplicados

### 1. Strategy Pattern
**Onde**: `PaymentProvider` e implementações (`CardPaymentProvider`, `DefaultPaymentProvider`)

**Propósito**: Encapsular algoritmos de cálculo de juros e valores totais por método de pagamento.

**Estrutura**:
```
PaymentProvider (interface)
    ↑
    ├── CardPaymentProvider (juros de 1% ao mês)
    └── DefaultPaymentProvider (sem juros)
```

### 2. Registry Pattern
**Onde**: `PaymentMethodRegistry`

**Propósito**: Centralizar o registro e recuperação de providers de pagamento.

**Implementação**:
```java
public PaymentProvider getProvider(String method) {
    return paymentProviders.getOrDefault(method.toUpperCase(), paymentProviders.get("DEFAULT"));
}
```

### 3. Observer Pattern (Event-Driven)
**Onde**: `PaymentUpdatedEvent` + `WebhookListener`

**Propósito**: Desacoplar atualização de status de pagamento do envio de webhooks.

**Fluxo**:
```
PaymentService.processAndPublish()
    → events.publishEvent(new PaymentUpdatedEvent(payment))
        → WebhookListener.sendWebhook(@EventListener)
            → Envia webhook assíncrono
```

### 4. Template Method Pattern
**Onde**: Webhook retry logic

**Propósito**: Define o esqueleto do algoritmo de retry, permitindo variações.

**Fluxo**:
```
tryDeliver() → HTTP Request → Success? → Save
                            → Failure? → Should Retry? → scheduleRetry()
```

### 5. Repository Pattern
**Onde**: `MerchantRepository`, `PaymentRepository`, `WebhookDeliveryRepository`

**Propósito**: Abstração da camada de persistência.

### 6. Builder Pattern
**Onde**: Entidades JPA (`Payment`, `Merchant`, `WebhookDelivery`) com Lombok `@Builder`

**Propósito**: Construção fluente e legível de objetos complexos.

**Exemplo**:
```java
Payment.builder()
    .id("pay_xxx")
    .merchantId(mid)
    .amount(req.amount())
    .status(Payment.Status.PENDING)
    .build();
```

### 7. Interceptor Pattern
**Onde**: `RateLimitInterceptor`

**Propósito**: Interceptar requisições HTTP para aplicar rate limiting antes de executar o controller.

**Implementação**: Via `HandlerInterceptor` do Spring MVC.

### 8. DTO Pattern
**Onde**: Pacote `dto` (`PaymentRequest`, `PaymentResponse`, etc.)

**Propósito**: Separar representação interna (entidades) de representação de API (DTOs).

---

## Limites Conhecidos

### 1. Persistência Volátil
**Problema**: Dados são perdidos ao reiniciar a aplicação (H2 em memória).
**Impacto**: Não adequado para produção.
**Mitigação Futura**: Trocar para PostgreSQL/MySQL com persistência real.

### 2. Autenticação Simplificada
**Problema**: Token é apenas "FAKE-{merchantId}", sem validação real.
**Impacto**: Segurança inexistente.
**Mitigação Futura**: Implementar JWT com assinatura e expiração.

### 3. Rate Limiting em Memória
**Problema**: Cache de rate limit é local à JVM.
**Impacto**: Em ambiente distribuído (múltiplas instâncias), cada instância tem seu próprio limite.
**Mitigação Futura**: Usar Redis para cache distribuído.

### 4. Webhook Retry Limitado
**Problema**: Apenas 5 tentativas com scheduler em memória.
**Impacto**: Webhooks podem ser perdidos em caso de falhas prolongadas.
**Mitigação Futura**: Fila de mensagens (RabbitMQ/Kafka) para retry mais robusto.

### 5. Thread Pools Fixos
**Problema**: Pools de 5 threads podem ser insuficientes sob alta carga.
**Impacto**: Processamento pode enfileirar e degradar latência.
**Mitigação Futura**: Pools configuráveis e métricas de observabilidade.

### 6. Sem Validação de Dados de Pagamento
**Problema**: Não valida dados reais de cartão, conta bancária, etc.
**Impacto**: Simulador aceita qualquer entrada.
**Mitigação Futura**: Validações de formato (Luhn para cartão, CPF para PIX).

### 7. Sem Logging Estruturado
**Problema**: Usa apenas `System.out` ou logs padrão do Spring.
**Impacto**: Dificulta debugging em produção.
**Mitigação Futura**: Implementar SLF4J com MDC e agregação (ELK Stack).

### 9. Concorrência em Idempotência
**Problema**: Race condition possível entre verificação e criação de pagamento idempotente.
**Impacto**: Raro, mas possível duplicação em alta concorrência.
**Mitigação Futura**: Lock pessimista ou SELECT FOR UPDATE.

### 10. Falta de Monitoramento
**Problema**: Sem métricas de desempenho, saúde de threads, taxa de sucesso de webhooks.
**Impacto**: Impossível observar comportamento em runtime.
**Mitigação Futura**: Spring Boot Actuator + Prometheus + Grafana.

---

