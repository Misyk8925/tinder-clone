# Keycloak Service-to-Service Authentication Setup

## Overview

This project uses Keycloak for service-to-service authentication. The `deck-service` acts as an OAuth2 client and makes authenticated requests to `profiles-service` and `swipes-service`, which act as OAuth2 resource servers.

## Architecture

- **deck-service**: OAuth2 Client (uses client_credentials grant)
- **profiles-service**: OAuth2 Resource Server (validates JWT tokens)
- **swipes-service**: OAuth2 Resource Server (validates JWT tokens)

## Keycloak Configuration

### 1. Create Realm

Create a realm named `spring` in Keycloak (or use an existing one).

### 2. Create Client for deck-service

1. Go to **Clients** → **Create**
2. Set the following:
   - **Client ID**: `deck-service`
   - **Client Protocol**: `openid-connect`
   - **Access Type**: `confidential`
   - **Service Accounts Enabled**: `ON`
   - **Valid Redirect URIs**: Leave empty (not needed for client_credentials)

3. Go to the **Credentials** tab
4. Copy the **Client Secret**
5. Set this value as the `KEYCLOAK_CLIENT_SECRET` environment variable or update `application.yml`

### 3. Create Client for profiles-service (Optional)

If you want profiles-service to also validate specific scopes:

1. Go to **Clients** → **Create**
2. Set **Client ID**: `profiles-service`
3. This client is used for resource server validation

### 4. Create Client for swipes-service (Optional)

Similar to profiles-service if needed.

## Service Configuration

### deck-service (OAuth2 Client)

The deck service is configured to obtain access tokens from Keycloak and include them in requests to other services.

**application.yml:**
```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          keycloak:
            issuer-uri: http://localhost:9080/realms/spring
            token-uri: http://localhost:9080/realms/spring/protocol/openid-connect/token
        registration:
          keycloak-client:
            provider: keycloak
            client-id: deck-service
            client-secret: ${KEYCLOAK_CLIENT_SECRET:your-deck-service-secret}
            authorization-grant-type: client_credentials
            scope: openid,profile
```

**Environment Variable:**
```bash
export KEYCLOAK_CLIENT_SECRET=<your-secret-from-keycloak>
```

### profiles-service (OAuth2 Resource Server)

The profiles service validates JWT tokens from incoming requests.

**application.yml:**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:9080/realms/spring/protocol/openid-connect/certs
```

### swipes-service (OAuth2 Resource Server)

The swipes service validates JWT tokens from incoming requests.

**application.yml:**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:9080/realms/spring/protocol/openid-connect/certs
```

## How It Works

1. When deck-service starts, it registers the OAuth2 client configuration
2. When deck-service makes a request to profiles-service or swipes-service:
   - The `ServerOAuth2AuthorizedClientExchangeFilterFunction` intercepts the request
   - It obtains an access token from Keycloak using client_credentials grant
   - The token is cached and reused until it expires
   - The token is added to the request as `Authorization: Bearer <token>`
3. profiles-service and swipes-service validate the JWT token:
   - They fetch the public keys from Keycloak's JWK Set URI
   - They verify the token signature and claims
   - If valid, the request is processed

## Testing

### Running Tests

Tests are configured to run without Keycloak by using the `test` profile, which disables OAuth2 auto-configuration:

```bash
cd services/deck
mvn test
```

### Manual Testing with Keycloak

1. Start Keycloak:
   ```bash
   docker run -p 9080:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:latest start-dev
   ```

2. Configure Keycloak as described above

3. Set the client secret:
   ```bash
   export KEYCLOAK_CLIENT_SECRET=<your-secret>
   ```

4. Start the services:
   ```bash
   # Start profiles-service
   cd services/profiles
   mvn spring-boot:run

   # Start swipes-service
   cd services/swipes
   mvn spring-boot:run

   # Start deck-service
   cd services/deck
   mvn spring-boot:run
   ```

5. Make a request to deck-service - it will automatically authenticate with profiles and swipes services

## Troubleshooting

### "Unable to resolve Configuration with the provided Issuer"

- Ensure Keycloak is running and accessible at the configured URL
- Check that the realm name is correct
- Verify network connectivity to Keycloak

### "401 Unauthorized" from profiles-service or swipes-service

- Check that the JWT token is being sent in the request
- Verify that the resource servers can reach Keycloak's JWK Set URI
- Check Keycloak logs for token validation errors
- Ensure the client_id in deck-service matches the expected audience

### Tests fail with OAuth2 errors

- Ensure you're using the `test` profile which disables OAuth2
- Check that `application-test.yml` excludes OAuth2 auto-configuration

## Security Notes

- **Never commit secrets**: Use environment variables for client secrets
- **HTTPS in production**: Use HTTPS for all Keycloak communication in production
- **Token expiration**: Tokens are automatically refreshed by Spring Security OAuth2 Client
- **Scope validation**: Consider adding scope validation in resource servers for fine-grained access control
