#!/bin/bash

set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

>&2 echo "INFO compiling"
>&2 ./mvnw --quiet package -DskipTests -f "$SCRIPT_DIR" -am -pl=ehealthid-rp

cat > "$SCRIPT_DIR/ehealthid-rp/target/RpKeyGen.java" <<'EOF'
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.relyingparty.util.KeyGenerator;
import java.nio.file.*;
class RpKeyGen { public static void main(String[] args) throws Exception {
  if (args.length != 1) { System.err.println("invalid args: ./rp-keygen.sh <name>"); System.exit(1); }
  Files.writeString(Path.of("rp_sig_" + args[0] + "_jwks.json"), new JWKSet(KeyGenerator.generateSigningKey()).toString(false));
  Files.writeString(Path.of("rp_enc_" + args[0] + "_jwks.json"), new JWKSet(KeyGenerator.generateEncryptionKey()).toString(false));
}}
EOF

>&2 echo "INFO running rp-keygen"
java -cp "$SCRIPT_DIR/ehealthid-rp/target/ehealthid-rp-jar-with-dependencies.jar" "$SCRIPT_DIR/ehealthid-rp/target/RpKeyGen.java" "$@"
