package com.oviva.gesundheitsid.crypto;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

public record ECKeyPair(ECPublicKey pub, ECPrivateKey priv) {}
