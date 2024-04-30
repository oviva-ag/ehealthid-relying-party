#!/bin/bash

export EHEALTHID_RP_APP_NAME=Awesome DiGA
export EHEALTHID_RP_BASE_URI=https://t.oviva.io
export EHEALTHID_RP_FEDERATION_ENC_JWKS_PATH=./enc_t_oviva_io_jwks.json
export EHEALTHID_RP_FEDERATION_SIG_JWKS_PATH=./sig_t_oviva_io_jwks.json
export EHEALTHID_RP_FEDERATION_MASTER=https://app-ref.federationmaster.de
export EHEALTHID_RP_REDIRECT_URIS=https://sso-mydiga.example.com/auth/callback
export EHEALTHID_RP_ES_TTL=PT5M
export EHEALTHID_RP_IDP_DISCOVERY_URI=https://sso-mydiga.example.com/.well-known/openid-configuration

java -jar ehealthid-rp/target/ehealthid-rp-jar-with-dependencies.jar
