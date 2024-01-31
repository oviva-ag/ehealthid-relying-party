package com.oviva.gesundheitsid.test;

import static org.mockito.Mockito.spy;

import com.oviva.gesundheitsid.crypto.ECKeyPair;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Random;

public class ECKeyPairGenerator {

  private static ECKeyPair EXAMPLE = generateExample();

  private ECKeyPairGenerator() {}

  public static ECKeyPair generate() {
    return generateP256(null);
  }

  public static ECKeyPair example() {
    return EXAMPLE;
  }

  private static ECKeyPair generateExample() {
    var notVerySecureRandom = spy(new NotSoSecureRandom());
    return generateP256(notVerySecureRandom);
  }

  private static ECKeyPair generateP256(SecureRandom sr) {
    try {
      var ecGenSpec = new ECGenParameterSpec("secp256r1");
      var keyPairGenerator = KeyPairGenerator.getInstance("EC");
      keyPairGenerator.initialize(ecGenSpec, sr);
      var pair = keyPairGenerator.generateKeyPair();

      return new ECKeyPair((ECPublicKey) pair.getPublic(), (ECPrivateKey) pair.getPrivate());

    } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
      throw new RuntimeException(e);
    }
  }

  private static class NotSoSecureRandom extends SecureRandom {

    private final Random prng = new Random(1337L);

    @Override
    public void nextBytes(byte[] bytes) {
      prng.nextBytes(bytes);
    }
  }
}
