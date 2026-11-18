#!/bin/bash

set -e

>&2 ./mvnw --quiet package -DskipTests -am -pl=ehealthid-rp

./cli.sh keygen --issuer-uri=https://mydiga.example.com

cat > ehealthid-rp/target/RpKeyGen.java <<'JAVA'
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.relyingparty.util.KeyGenerator;
import java.nio.file.*;
class RpKeyGen { public static void main(String[] args) throws Exception {
Files.writeString(Path.of("rp_sig_" + args[0] + "_jwks.json"), new JWKSet(KeyGenerator.generateSigningKey()).toString(false));
Files.writeString(Path.of("rp_enc_" + args[0] + "_jwks.json"), new JWKSet(KeyGenerator.generateEncryptionKey()).toString(false));
}}
JAVA
java -cp ehealthid-rp/target/ehealthid-rp-jar-with-dependencies.jar ehealthid-rp/target/RpKeyGen.java mydiga_example_com

export EHEALTHID_RP_APP_NAME=Awesome DiGA
export EHEALTHID_RP_APP_URI=https://sso-mydiga.example.com/app
export EHEALTHID_RP_BASE_URI=https://mydiga.example.com
export EHEALTHID_RP_FEDERATION_ES_JWKS_PATH=./sig_mydiga_example_com_jwks.json
export EHEALTHID_RP_OPENID_RP_SIG_JWKS_PATH=./rp_sig_mydiga_example_com_jwks.json
export EHEALTHID_RP_OPENID_RP_ENC_JWKS_PATH=./rp_enc_mydiga_example_com_jwks.json
export EHEALTHID_RP_FEDERATION_MASTER=https://app-ref.federationmaster.de
export EHEALTHID_RP_REDIRECT_URIS=https://sso-mydiga.example.com/auth/callback
export EHEALTHID_RP_ES_TTL=PT5M
export EHEALTHID_RP_LOG_LEVEL=DEBUG
export EHEALTHID_RP_IDP_DISCOVERY_URI=https://sso-mydiga.example.com/.well-known/openid-configuration

java -jar ehealthid-rp/target/ehealthid-rp-jar-with-dependencies.jar
