package com.oviva.ehealthid.relyingparty.ws;

import static com.oviva.ehealthid.relyingparty.svc.ValidationException.*;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import com.github.jknack.handlebars.internal.text.StringEscapeUtils;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.oviva.ehealthid.relyingparty.svc.AuthenticationException;
import com.oviva.ehealthid.relyingparty.svc.ValidationException;
import com.oviva.ehealthid.relyingparty.ws.ThrowableExceptionMapper.Problem;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class})
class ThrowableExceptionMapperTest {

  private static final URI REQUEST_URI = URI.create("https://example.com/my/request");
  @Mock UriInfo uriInfo;
  @Mock Request request;
  @Mock HttpHeaders headers;
  @Spy Logger logger = LoggerFactory.getLogger(ThrowableExceptionMapper.class);
  @InjectMocks ThrowableExceptionMapper mapper = new ThrowableExceptionMapper();

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
    when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.WILDCARD_TYPE));
    mockHeaders("de-DE");

    // when
    var res = mapper.toResponse(new IllegalArgumentException());

    // then
    assertEquals(500, res.getStatus());
    assertEquals(MediaType.TEXT_HTML_TYPE, res.getMediaType());
    assertNotNull(res.getEntity());
  }

  @Test
  void toResponse_withJson() {

    when(headers.getAcceptableMediaTypes())
        .thenReturn(
            List.of(
                MediaType.APPLICATION_JSON_TYPE,
                MediaType.TEXT_HTML_TYPE,
                MediaType.WILDCARD_TYPE));

    var msg = "Ooops! An error :/";

    // when
    var res = mapper.toResponse(new ValidationException(msg));

    // then
    assertEquals(400, res.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, res.getMediaType());
    assertEquals(new Problem("/server_error", msg), res.getEntity());
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
  void toResponse_withBody_withValidationExc() {

    when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.WILDCARD_TYPE));
    doReturn("de-DE").when(headers).getHeaderString("Accept-Language");

    // when
    var res = mapper.toResponse(new ValidationException(new Message("error.invalidSession", null)));

    // then
    assertEquals(400, res.getStatus());
    System.out.println(res.getEntity());
    assertEquals(MediaType.TEXT_HTML_TYPE, res.getMediaType());
    assertNotNull(res.getEntity());
  }

  @ParameterizedTest
  @MethodSource("provideKeyWithDynamicContentMessageError")
  void toResponse_withBody_withValidationExceptionAndDynamicContent(
      String language, String messageKey, String message) {

    when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.WILDCARD_TYPE));
    doReturn(language).when(headers).getHeaderString("Accept-Language");

    // when
    var res =
        mapper.toResponse(
            new ValidationException(new Message(messageKey, "https://idp.example.com")));

    // then
    assertEquals(400, res.getStatus());
    assertTrue(StringEscapeUtils.unescapeHtml4(res.getEntity().toString()).contains(message));
    assertEquals(MediaType.TEXT_HTML_TYPE, res.getMediaType());
    assertNotNull(res.getEntity());
  }

  private void mockHeaders(String locales) {
    doReturn(locales).when(headers).getHeaderString("Accept-Language");
    doReturn("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
        .when(headers)
        .getHeaderString("traceparent");
    doReturn(
            "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko)")
        .when(headers)
        .getHeaderString("user-agent");
  }
}
