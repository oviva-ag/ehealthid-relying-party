package com.oviva.gesundheitsid.crypto;

import com.nimbusds.jose.jwk.JWK;
import java.util.function.Function;

public interface KeySupplier extends Function<String, JWK> {}
