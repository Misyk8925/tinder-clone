export const environment = {
  production: true,
  designPreview: false,
  apiGatewayUrl: 'https://matchapp-api.misyk.tech',
  wsUrl: 'wss://matchapp-api.misyk.tech/ws',
  keycloak: {
    url: 'https://auth-app.misyk.tech',
    realm: 'spring',
    clientId: 'tinder-client'
  }
};
