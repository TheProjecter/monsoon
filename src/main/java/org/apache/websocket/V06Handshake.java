/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */
package org.apache.websocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Random;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

public class V06Handshake<T extends SelectableChannel & GatheringByteChannel & ScatteringByteChannel>
    implements WebSocketHandshake<T> {

  private static final Charset UTF8 = Charset.forName("UTF-8");
  private static final String HOST_HEADER = "Host";
  private static final String UPGRADE_HEADER = "Upgrade";
  private static final String CONNECTION_HEADER = "Connection";
  private static final String KEY_HEADER = "Sec-WebSocket-Key";
  private static final String ORIGIN_HEADER = "Sec-WebSocket-Origin";
  private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
  private static final String VERSION_HEADER = "Sec-WebSocket-Version";
  private static final String VERSION = "6";
  private static final String ACCEPT_HEADER = "Sec-WebSocket-Accept";
  private static final String COOKIE_HEADER = "Cookie";
  private static final String[] MANDATORY_HEADERS = {
      HOST_HEADER, KEY_HEADER, VERSION_HEADER };
  private static final String SERVER_KEY_ADDON = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
  private static final String CRLF = "\r\n";

  public WebSocket<T> clientHandshake(T channel, URI uri, String subprotocol,
      String origin, String cookies, String... otherHeaders) throws IOException {
    // TODO: support non-blocking
    String key = generateKey();
    ByteBuffer request = buildRequest(uri, key, origin, subprotocol, cookies,
        otherHeaders);
    channel.write(request);
    BufferedReader reader = new BufferedReader(Channels.newReader(channel,
        "UTF-8"));
    if (!processResponse(reader, key)) {
      channel.close();
      throw new WebSocketException("Handshake failed");
    }
    return new V06WebSocket<T>(channel, true);
  }

  private ByteBuffer buildRequest(URI uri, String key, String origin,
      String subprotocol, String cookies, String... otherHeaders) {
    String path = uri.getPath().isEmpty() ? "/" : uri.getPath();
    StringBuilder request = new StringBuilder();
    request.append("GET ").append(path).append(" HTTP/1.1").append(CRLF);
    request.append(HOST_HEADER).append(": ").append(uri.getHost()).append(CRLF);
    request.append(UPGRADE_HEADER).append(": websocket").append(CRLF);
    request.append(CONNECTION_HEADER).append(": Upgrade").append(CRLF);
    request.append(KEY_HEADER).append(": ").append(key).append(CRLF);
    if (origin != null && !origin.isEmpty()) {
      request.append(ORIGIN_HEADER).append(": ").append(origin).append(CRLF);
    }
    if (subprotocol != null && !subprotocol.isEmpty()) {
      request.append(PROTOCOL_HEADER).append(": ").append(subprotocol)
          .append(CRLF);
    }
    request.append(VERSION_HEADER).append(": ").append(VERSION).append(CRLF);
    if (cookies != null && !cookies.isEmpty()) {
      request.append(COOKIE_HEADER).append(": ").append(cookies).append(CRLF);
    }
    // TODO is this the expected format of otherHeaders?
    if (otherHeaders != null) {
      for (String header : otherHeaders) {
        request.append(header).append(CRLF);
      }
    }
    request.append(CRLF);
    return ByteBuffer.wrap(request.toString().getBytes(UTF8));
  }

  private boolean processResponse(BufferedReader reader, String key)
      throws IOException {
    String responseLine = reader.readLine();
    Map<String, String> headers = HttpUtils.readHttpHeaders(reader);
    DebugUtils.printHttpMessage(responseLine, headers);
    return checkResponseLine(responseLine)
        && checkResponseHeaders(headers, key);
  }

  private boolean checkResponseLine(String responseLine) {
    return responseLine.startsWith("HTTP/1.1 101");
  }

  private boolean checkResponseHeaders(Map<String, String> headers, String key) {
    String expectedKey = new String(Base64.encodeBase64(DigestUtils.sha(key
        + SERVER_KEY_ADDON)));
    return "websocket".equalsIgnoreCase(headers.get(UPGRADE_HEADER
        .toLowerCase()))
        && "Upgrade".equalsIgnoreCase(headers.get(CONNECTION_HEADER
            .toLowerCase()))
        && expectedKey.equals(headers.get(ACCEPT_HEADER.toLowerCase()));
  }

  private String generateKey() {
    Random random = new Random(System.currentTimeMillis());
    byte[] key = new byte[16];
    random.nextBytes(key);
    return new String(Base64.encodeBase64(key));
  }

  public String getRealPath(String requestLine, Map<String, String> headers) {
    return requestLine.split(" ")[1];
  }

  public boolean matches(String requestLine, Map<String, String> headers) {
    return matchesRequestLine(requestLine) && matchesHeaders(headers);
  }

  private boolean matchesRequestLine(String requestLine) {
    String[] tokens = requestLine.split(" ");
    return tokens.length == 3 && tokens[0].equals("GET")
        && tokens[1].startsWith("/") && tokens[2].equals("HTTP/1.1");
  }

  private boolean matchesHeaders(Map<String, String> headers) {
    for (String headerKey : MANDATORY_HEADERS) {
      if (!headers.containsKey(headerKey.toLowerCase())
          || headers.get(headerKey.toLowerCase()) == null
          || headers.get(headerKey.toLowerCase()).isEmpty()) {
        return false;
      }
    }
    byte[] key = headers.get(KEY_HEADER.toLowerCase()).getBytes();
    if (Base64.decodeBase64(key).length != 16) {
      return false;
    }
    if (!headers.get(VERSION_HEADER.toLowerCase()).equals(VERSION)) {
      return false;
    }
    return true;
  }

  public WebSocket<T> serverHandshake(T channel, String requestLine,
      Map<String, String> headers, WebSocketApplication<T> app)
      throws IOException {
    StringBuilder response = new StringBuilder();
    response.append("HTTP/1.1 101 Switching Protocols").append(CRLF);
    String key = headers.get(KEY_HEADER.toLowerCase()) + SERVER_KEY_ADDON;
    byte[] sha1AcceptKey = DigestUtils.sha(key);
    byte[] base64AceeptKey = Base64.encodeBase64(sha1AcceptKey);
    response.append(ACCEPT_HEADER).append(": ")
        .append(new String(base64AceeptKey)).append(CRLF);
    response.append(UPGRADE_HEADER).append(": ").append("websocket")
        .append(CRLF);
    response.append(CONNECTION_HEADER).append(": ").append("Upgrade")
        .append(CRLF);
    String protocol = headers.get(PROTOCOL_HEADER.toLowerCase());
    String acceptedProtocol = app.acceptProtocol(protocol);
    if (acceptedProtocol != null && !protocol.isEmpty()) {
      response.append(PROTOCOL_HEADER).append(": ").append(acceptedProtocol)
          .append(CRLF);
    }
    String origin = headers.get(ORIGIN_HEADER.toLowerCase());
    if (!app.acceptOrigin(origin)) {
      throw new WebSocketException("Origin not accepted");
    }
    // TODO: add extension abstraction
    Map<String, String> responseHeaders = app.acceptExtensions(headers);
    if (responseHeaders != null) {
      for (String header : responseHeaders.keySet()) {
        response.append(header).append(": ")
            .append(responseHeaders.get(header)).append(CRLF);
      }
    }
    response.append(CRLF);
    channel.write(ByteBuffer.wrap(response.toString().getBytes(UTF8)));
    // TODO determine that client received the response and didn't fail the
    // connection
    return new V06WebSocket<T>(channel, false);
  }
}
