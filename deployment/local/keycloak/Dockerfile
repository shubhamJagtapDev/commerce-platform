FROM quay.io/keycloak/keycloak:26.7.0

COPY extensions/keycloak-registration-gate/build/libs/keycloak-registration-gate-0.1.0-SNAPSHOT.jar \
    /opt/keycloak/providers/commerce-keycloak-registration-gate.jar

RUN /opt/keycloak/bin/kc.sh build
