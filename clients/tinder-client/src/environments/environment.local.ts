export const environment = {
  production: false,
  designPreview: false,
  apiGatewayUrl: 'http://localhost:8222',
  wsUrl: 'ws://localhost:8222/ws',
  keycloak: {
    url: 'http://localhost:9080',
    realm: 'spring',
    clientId: 'tinder-client'
  }
};
