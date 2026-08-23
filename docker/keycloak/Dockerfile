FROM quay.io/keycloak/keycloak:26.3.5

# Dokploy replaces its checkout directory during deployments. Baking the theme
# into the image keeps Keycloak independent from that directory's inode and
# prevents stale bind mounts after a fresh clone.
COPY --chown=keycloak:keycloak docker/keycloak/themes/ /opt/keycloak/themes/
COPY --chown=keycloak:keycloak --chmod=0755 docker/keycloak/configure-realm.sh /opt/keycloak/bin/configure-connect-realm.sh
