/*export const environment = {
  production: false,
  apiGatewayUrl: 'http://localhost:8222',
  wsUrl: 'ws://localhost:8222/ws',
  keycloak: {
    url: 'http://localhost:9080',
    realm: 'spring',
    clientId: 'test client'
  }
};*/

export const environment = {
  production: true,
  apiGatewayUrl: 'https://api.mykh.studio',
  wsUrl: 'wss://api.mykh.studio/ws',
  keycloak: {
    url: 'https://auth.mykh.studio',
    realm: 'spring',
    clientId: 'tinder-client'
  }
};

