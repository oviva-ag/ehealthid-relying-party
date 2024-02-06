package com.oviva.ehealthid.fedclient.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class CachedFederationApiClientTest {

  @Mock FederationApiClient delegate;

  @Spy Cache<EntityStatementJWS> entityStatementCache = new NopCache<>();
  @Spy Cache<EntityStatementJWS> federationStatementCache = new NopCache<>();
  @Spy Cache<IdpListJWS> idpListCache = new NopCache<>();

  @InjectMocks CachedFederationApiClient sut;

  @Test
  void fetchFederationStatement() {

    var url = URI.create("https://example.com");
    var iss = "myiss";
    var sub = "mysub";

    var expected = new EntityStatementJWS(null, null);

    when(delegate.fetchFederationStatement(url, iss, sub)).thenReturn(expected);

    // when
    var got = sut.fetchFederationStatement(url, iss, sub);

    // then
    verify(delegate).fetchFederationStatement(url, iss, sub);
    assertEquals(expected, got);
  }

  @Test
  void fetchIdpList() {
    var uri = URI.create("https://example.com/idpList");

    var expected = new IdpListJWS(null, null);
    when(delegate.fetchIdpList(uri)).thenReturn(expected);

    // when
    var got = sut.fetchIdpList(uri);

    // then
    verify(delegate).fetchIdpList(uri);
    assertEquals(expected, got);
  }

  @Test
  void fetchEntityConfiguration() {
    var uri = URI.create("https://example.com");

    var expected = new EntityStatementJWS(null, null);
    when(delegate.fetchEntityConfiguration(uri)).thenReturn(expected);

    // when
    var got = sut.fetchEntityConfiguration(uri);

    // then
    verify(delegate).fetchEntityConfiguration(uri);
    assertEquals(expected, got);
  }

  static class NopCache<T extends TemporalValid> implements Cache<T> {

    @Override
    public T computeIfAbsent(String key, Function<String, T> supplier) {
      return supplier.apply(key);
    }
  }
}
