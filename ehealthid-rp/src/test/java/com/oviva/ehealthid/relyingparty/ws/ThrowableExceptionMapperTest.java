package com.oviva.ehealthid.relyingparty.ws;

import static com.oviva.ehealthid.relyingparty.svc.ValidationException.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.mustachejava.util.HtmlEscaper;
import com.oviva.ehealthid.auth.AuthException;
import com.oviva.ehealthid.fedclient.FederationException;
import com.oviva.ehealthid.relyingparty.svc.AuthenticationException;
import com.oviva.ehealthid.relyingparty.svc.LocalizedException.Message;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo;
import com.oviva.ehealthid.relyingparty.svc.ValidationException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.*;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith({MockitoExtension.class})
class ThrowableExceptionMapperTest {

  private static final URI REQUEST_URI = URI.create("https://example.com/my/request");
  @Mock UriInfo uriInfo;
  @Mock Request request;
  @Mock HttpHeaders headers;
  @Mock SessionRepo sessionRepo;
  @Spy Logger logger = LoggerFactory.getLogger(ThrowableExceptionMapper.class);
  ThrowableExceptionMapper mapper;

  @BeforeEach
  void setUp() throws Exception {
    mapper = new ThrowableExceptionMapper(sessionRepo);
    setField(mapper, "uriInfo", uriInfo);
    setField(mapper, "request", request);
    setField(mapper, "headers", headers);
    setField(mapper, "logger", logger);
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    var field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Stream<Arguments> listValidLocale() {
    return Stream.of(
        Arguments.of("en-US,de-DE"),
        Arguments.of("en-US"),
        Arguments.of("de-DE"),
        Arguments.of("de-DE,it-IT"));
  }

  private static Stream<Arguments> provideKeyWithDynamicContentMessageError() {
    return Stream.of(
        Arguments.of(
            "en-US",
            "error.insecureRedirect",
            "Insecure redirect_uri='https://idp.example.com'. Misconfigured server, please use 'https'."),
        Arguments.of(
            "de-DE",
            "error.insecureRedirect",
            "Unsicherer redirect_uri='https://idp.example.com'. Falsch konfigurierter Server, bitte verwenden Sie 'https'."),
        Arguments.of(
            "en-US",
            "error.badRedirect",
            "Bad redirect_uri='https://idp.example.com'. Passed link is not valid."),
        Arguments.of(
            "de-DE",
            "error.badRedirect",
            "Ungültige redirect_uri='https://idp.example.com'. Übergebener Link ist nicht gültig."),
        Arguments.of(
            "en-US",
            "error.untrustedRedirect",
            "Untrusted redirect_uri=https://idp.example.com. Misconfigured server."),
        Arguments.of(
            "de-DE",
            "error.untrustedRedirect",
            "Nicht vertrauenswürdiger redirect_uri=https://idp.example.com. Falsch konfigurierter Server."));
  }

  @ParameterizedTest
  @MethodSource("listValidLocale")
  void toResponse(String locales) {

    when(uriInfo.getRequestUri()).thenReturn(REQUEST_URI);

    mockHeaders(locales);

    // when
    var res = mapper.toResponse(new IllegalArgumentException());

    // then
    assertEquals(500, res.getStatus());
  }

  @Test
  void toResponse_propagateWebApplicationException() {

    var ex = new NotFoundException();

    // when
    var res = mapper.toResponse(ex);

    // then
    assertEquals(404, res.getStatus());
  }

  @Test
  void toResponse_propagateWebApplicationException_forbidden() {

    when(uriInfo.getRequestUri()).thenReturn(REQUEST_URI);

    var status = 500;
    var ex = new ServerErrorException(status);

    // when
    var res = mapper.toResponse(ex);

    // then
    assertEquals(status, res.getStatus());
  }

  @Test
  void toResponse_isLogged() {

    when(uriInfo.getRequestUri()).thenReturn(REQUEST_URI);
    mockHeaders("de-DE");

    // when
    mapper.toResponse(new UnsupportedOperationException());

    // then
    verify(logger).atError();
  }

  @Test
  void toResponse_authentication() {

    // when
    var res = mapper.toResponse(new AuthenticationException(null));

    // then
    assertEquals(401, res.getStatus());
  }

  @Test
  void toResponse_withBody() {

    when(uriInfo.getRequestUri()).thenReturn(REQUEST_URI);
    mockHeaders("de-DE");

    // when
    var res = mapper.toResponse(new IllegalArgumentException());

    // then
    assertEquals(500, res.getStatus());
    assertEquals(MediaType.TEXT_HTML_TYPE, res.getMediaType());
    assertNotNull(res.getEntity());
  }

  @Test
  void toResponse_withBody_Unauthorized() {
    // when
    var res = mapper.toResponse(new AuthenticationException("Unauthorized"));

    // then
    assertEquals(401, res.getStatus());
  }

  @Test
  void toResponse_withBody_seeOthers() {
    // when
    var res =
        mapper.toResponse(
            new ValidationException(
                new Message("error.unsupportedScope", "https://example.com/see/other"),
                URI.create("https://example.com/see/other")));

    // then
    assertEquals(303, res.getStatus());
  }

  @Test
  void toResponse_withBody_withValidationException() {

    doReturn("de-DE").when(headers).getHeaderString("Accept-Language");

    // when
    var res = mapper.toResponse(new ValidationException(new Message("error.invalidSession", null)));

    // then
    assertEquals(400, res.getStatus());
    assertEquals(MediaType.TEXT_HTML_TYPE, res.getMediaType());
    assertNotNull(res.getEntity());
  }

  @Test
  void toResponse_withBody_withFederationException() {

    doReturn("de-DE").when(headers).getHeaderString("Accept-Language");
    doReturn(URI.create("https://example.com")).when(uriInfo).getRequestUri();
    doReturn(null).when(headers).getHeaderString("user-agent");

    // when
    var res =
        mapper.toResponse(
            new FederationException("Nope!", FederationException.Reason.BAD_FEDERATION_MASTER));

    // then
    assertEquals(500, res.getStatus());
    assertEquals(MediaType.TEXT_HTML_TYPE, res.getMediaType());
    assertNotNull(res.getEntity());
  }

  @Test
  void toResponse_withBody_withAuthenticationException() {

    doReturn("de-DE").when(headers).getHeaderString("Accept-Language");
    doReturn(URI.create("https://example.com")).when(uriInfo).getRequestUri();
    doReturn(null).when(headers).getHeaderString("user-agent");

    // when
    var res = mapper.toResponse(new AuthException("Nope!", AuthException.Reason.INVALID_ID_TOKEN));

    // then
    assertEquals(500, res.getStatus());
    assertEquals(MediaType.TEXT_HTML_TYPE, res.getMediaType());
    assertNotNull(res.getEntity());
  }

  @ParameterizedTest
  @MethodSource("provideKeyWithDynamicContentMessageError")
  void toResponse_withBody_withValidationExceptionAndDynamicContent(
      String language, String messageKey, String message) {

    doReturn(language).when(headers).getHeaderString("Accept-Language");

    // when
    var res =
        mapper.toResponse(
            new ValidationException(new Message(messageKey, "https://idp.example.com")));

    // then
    assertEquals(400, res.getStatus());
    assertBodyContains(res, message);
    assertEquals(MediaType.TEXT_HTML_TYPE, res.getMediaType());
    assertNotNull(res.getEntity());
  }

  private static void assertBodyContains(Response res, String message) {
    if (!(res.getEntity() instanceof byte[] bytes)) {
      fail("unsupported entity type for tests: %s".formatted(res.getEntity().getClass()));
      return;
    }

    var htmlBody = new String(bytes, StandardCharsets.UTF_8);

    // the rendered response is already HTML escaped, let's do the same to the raw message
    var escapedMessage = htmlEscape(message);

    assertThat(htmlBody, containsString(escapedMessage));
  }

  private static String htmlEscape(String s) {
    var w = new StringWriter();
    HtmlEscaper.escape(s, w);
    return w.toString();
  }

  @Test
  void toResponse_getsAppUriFromSession() {
    var appUri = URI.create("https://app.example.com/app");
    var sessionId = "test-session-id";
    var sessionCookie = mock(Cookie.class);
    var cookies = Map.of("session_id", sessionCookie);
    var session = SessionRepo.Session.create().id(sessionId).appUri(appUri).build();

    when(uriInfo.getRequestUri()).thenReturn(REQUEST_URI);
    when(request.getMethod()).thenReturn("GET");
    when(headers.getCookies()).thenReturn(cookies);
    when(sessionCookie.getValue()).thenReturn(sessionId);
    when(sessionRepo.load(sessionId)).thenReturn(session);
    when(headers.getHeaderString("Accept-Language")).thenReturn("de-DE");
    when(headers.getHeaderString("user-agent")).thenReturn("test-agent");

    var res = mapper.toResponse(new IllegalArgumentException("Test exception"));

    assertEquals(500, res.getStatus());
    assertEquals(MediaType.TEXT_HTML_TYPE, res.getMediaType());
    assertNotNull(res.getEntity());

    verify(sessionRepo).load(sessionId);

    var page = (byte[]) res.getEntity();
    var htmlBody = new String(page, StandardCharsets.UTF_8);
    assertThat(htmlBody, containsString(appUri.toString()));
  }

  private void mockHeaders(String locales) {
    doReturn(locales).when(headers).getHeaderString("Accept-Language");
    doReturn(
            "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko)")
        .when(headers)
        .getHeaderString("user-agent");
  }
}
