# 💳 FiadoPay Simulator (Spring Boot + H2)

[![Feito com Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Banco de Dados H2](https://img.shields.io/badge/Banco%20de%20Dados-H2-darkblue?style=for-the-badge&logo=h2&logoColor=white)](http://www.h2database.com/html/main.html)

---

## 🎯 Objetivo

Este projeto é um **Gateway de Pagamento FiadoPay** simulado, desenvolvido para o contexto acadêmico (AVI/POOA). Ele substitui Provedores de Serviços de Pagamento (PSPs) reais, utilizando um backend em memória (H2) para facilitar testes e desenvolvimento local.

## ⚙️ Rodar o Projeto

O projeto utiliza o Maven Wrapper (`./mvnw`).

```bash
# Opção 1: Usando Maven Wrapper (
./mvnw spring-boot:run

# Opção 2: Usando Maven 
mvn spring-boot:run

1) Cadastrar Merchart
curl -X POST http://localhost:8080/fiadopay/admin/merchants \
  -H "Content-Type: application/json" \
  -d '{
    "name":"MinhaLoja ADS",
    "webhookUrl":"http://localhost:8081/webhooks/payments"
}'

2) Obter Token de Autenticação

curl -X POST http://localhost:8080/fiadopay/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "client_id":"<clientId>", 
    "client_secret":"<clientSecret>"
}'

3) Criar Pagamento

curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-<merchantId>" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "method":"CARD",
    "currency":"BRL",
    "amount":250.50,
    "installments":12,
    "metadataOrderId":"ORD-123"
}'

curl http://localhost:8080/fiadopay/gateway/payments/<paymentId>

### ⚙️ Configuração do Simulador (application.properties)

Este simulador utiliza as seguintes variáveis de configuração para replicar cenários de PSP:

| Variável | Tipo | Descrição | Exemplo |
| :--- | :--- | :--- | :--- |
| `fiadopay.processing-delay-ms` | `long` | Atraso em milissegundos antes que o pagamento mude de PENDING. | `3000` (3 segundos) |
| `fiadopay.failure-rate` | `double` | Probabilidade de recusa do pagamento (entre 0.0 e 1.0). | `0.2` (20% de falha) |

**Exemplo no seu `application.properties`:**
```properties
fiadopay.processing-delay-ms=3000
fiadopay.failure-rate=0.2
