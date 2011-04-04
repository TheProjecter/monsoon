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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SocketChannel;

public class WebSocketConnector {

  /**
   * Issue a CONNECT request to a proxy and verify success.
   * 
   * @param uri
   * @param channel
   */
  private static void issueProxyConnect(URI uri, SocketChannel channel)
      throws IOException {
    throw new IOException("Proxy connect not yet implemented");
  }

  private URI proxy;

  /**
   * Create a WebSocketConnector with default proxy settings.
   */
  public WebSocketConnector() {
    this((URI) null);
  }

  /**
   * Create a WebSocketConnector with the specified proxy, supplied as a String.
   * 
   * @param proxy proxy to use in the form of "proxy:3128", must not be null
   * @throws URISyntaxException
   */
  public WebSocketConnector(String proxy) throws URISyntaxException {
    // TODO: better verification here?
    this(proxy == null ? (URI) null : new URI("http://" + proxy));
  }

  /**
   * Create a WebSocketConnector with the specified proxy.
   * 
   * @param proxy URI of proxy to use -- only host and port are referenced, and
   *          the proxy at that location must support the HTTP connect method
   *          and allow connection to arbitrary ports for most WebSocket
   *          connections to go through
   */
  public WebSocketConnector(URI proxy) {
    this.proxy = proxy;
  }

  /**
   * Open a connection to a WebSocket server.
   * 
   * @param uri the WebSocket URI, such as ws://host:port/path
   * @param subprotocol the subprotocol, or null if none
   * @param origin the origin of the web page making this request
   * @param cookies the value of the Cookies header to send, or null if none
   * @param otherHeaders key/value pairs of other headers to include in the
   *          handshake request
   * @return a connected WebSocket instance
   * @throws IOException if an error occurs during connection
   */
  public WebSocket<SocketChannel> connect(URI uri, String subprotocol,
      String origin, String cookies, String... otherHeaders) throws IOException {
    return connect(WebSocketHandshakes.getDefaultHanshake(), uri, subprotocol,
        origin, cookies, otherHeaders);
  }

  /**
   * Open a connection to a WebSocket server.
   * 
   * @param handshake the WebSocket handshake to use
   * @param uri the WebSocket URI, such as ws://host:port/path
   * @param subprotocol the subprotocol, or null if none
   * @param origin the origin of the web page making this request, must not be
   *          null
   * @param cookies the value of the Cookies header to send, or null if none
   * @param otherHeaders key/value pairs of other headers to include in the
   *          handshake request
   * @return a connected WebSocket instance
   * @throws IOException if an error occurs during connection
   */
  // TODO: do we want to expose this publicly?
  private WebSocket<SocketChannel> connect(
      WebSocketHandshake<SocketChannel> handshake, URI uri, String subprotocol,
      String origin, String cookies, String... otherHeaders) throws IOException {
    if (handshake == null) {
      throw new IllegalArgumentException("Handshake must not be null");
    }
    if (origin != null && !origin.equals(origin.toLowerCase())) {
      throw new IllegalArgumentException(
          "Origin must contain only lowercase letters");
    }
    String scheme = uri.getScheme();
    if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
      throw new IllegalArgumentException("Invalid scheme '" + scheme + "'");
    }
    if (uri.getFragment() != null) {
      throw new IllegalArgumentException("URL cannot contain a fragment");
    }
    boolean secure = "wss".equals(scheme);
    String wsHost = uri.getHost();
    int wsPort = uri.getPort();
    if (wsPort < 0) {
      wsPort = secure ? 443 : 80;
    }
    // TODO: DoS checks, such as limiting the number of simultaneous
    // connection attempts
    String connectHost;
    int connectPort;
    if (proxy != null) {
      connectHost = proxy.getHost();
      connectPort = proxy.getPort();
      if (connectPort < 0) {
        connectPort = 3128;
      }
    } else {
      connectPort = wsPort;
      connectHost = wsHost;
    }
    SocketAddress connectAddress = new InetSocketAddress(connectHost,
        connectPort);
    SocketChannel channel = SocketChannel.open(connectAddress);
    Socket sock = channel.socket();
    sock.setKeepAlive(true);
    sock.setTcpNoDelay(true);
    if (proxy != null) {
      issueProxyConnect(uri, channel);
    }
    if (secure) {
      channel = wrapChannelTLS(channel);
      // TODO: TLS support
      throw new IOException("wss not yet supported");
    }
    // TODO: add something similar to WebSocketApplication for the client to
    // process server response like subprotocol
    return handshake.clientHandshake(channel, uri, subprotocol, origin,
        cookies, otherHeaders);
  }

  /**
   * Create an SSL channel.
   * 
   * @param channel
   * @return the channel wrapped with SSL encode/decode
   */
  private SocketChannel wrapChannelTLS(SocketChannel channel) {
    // TODO Auto-generated method stub
    return null;
  }
}
