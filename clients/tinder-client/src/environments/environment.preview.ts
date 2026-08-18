export const environment = {
  production: false,
  designPreview: true,
  apiGatewayUrl: '/design-api',
  wsUrl: 'ws://localhost/design-ws',
  keycloak: {
    url: 'http://localhost:9080',
    realm: 'design-preview',
    clientId: 'tinder-client'
  }
};
