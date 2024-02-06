#!/bin/bash

export OIDC_SERVER_FEDERATION_ENC_JWKS_PATH=enc_jwks.json
export OIDC_SERVER_FEDERATION_SIG_JWKS_PATH=sig_jwks.json

java -jar oidc-server/target/oidc-server-*-jar-with-dependencies.jar
