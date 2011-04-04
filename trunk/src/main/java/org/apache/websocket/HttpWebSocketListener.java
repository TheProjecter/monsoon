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
import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for HTTP-compatible WebSocket listeners.
 * 
 * @param <T> channel type
 */
public abstract class HttpWebSocketListener<T extends SelectableChannel & GatheringByteChannel & ScatteringByteChannel>
    extends SelectableChannel implements Closeable, Runnable {

  private List<WebSocketHandshake<T>> registeredHandshakes;
  private Map<String, WebSocketApplication<T>> prefixMap;

  public HttpWebSocketListener() {
    prefixMap = new HashMap<String, WebSocketApplication<T>>();
    registeredHandshakes = new ArrayList<WebSocketHandshake<T>>();
    registeredHandshakes.add(new V06Handshake<T>());
    // TODO import handshakes from WebSocketHandshakes#getHandshakes?
  }

  /**
   * TODO: document me.
   * 
   * @return a connected WebSocket or null if no connection is available (only
   *         if in non-blocking mode).
   * @throws IOException if an IO error occurs or a handshake error occurs
   */
  public WebSocket<T> accept() throws IOException {
    T channel = null;
    try {
      channel = acceptChannel();
      if (channel == null) {
        return null;
      }
      BufferedReader reader = new BufferedReader(Channels.newReader(channel,
          "UTF-8"));
      String requestLine = reader.readLine();
      Map<String, String> headers = HttpUtils.readHttpHeaders(reader);
      DebugUtils.printHttpMessage(requestLine, headers);
      for (WebSocketHandshake<T> handshake : registeredHandshakes) {
        if (handshake.matches(requestLine, headers)) {
          String path = handshake.getRealPath(requestLine, headers);
          WebSocketApplication<T> app = getApplication(path);
          try {
            WebSocket<T> ws = handshake.serverHandshake(channel, requestLine,
                headers, app);
            app.onConnection(ws);
            return ws;
          } catch (IOException e) {
            app.onHandshakeError(e);
            throw e;
          }
        }
      }
      throw new WebSocketException("Unrecognized handshake");
    } catch (WebSocketException e) {
      // send an HTTP response if we had a WebSocket error during the handshake
      if (channel != null) {
        writeErrorResponse(channel);
      }
      throw e;
    }
  }

  public void register(String path, WebSocketApplication<T> app) {
    prefixMap.put(path, app);
  }

  /**
   * Receive new connections on the socket and pass them to callbacks, returning
   * only when the socket is closed.
   */
  public void run() {
    // TODO: verify underlying channel is set to non-blocking?
    while (true) {
      try {
        System.out.println("Waiting for incoming connections...");
        WebSocket<T> socket = accept();
        assert socket != null;
      } catch (IOException e) {
        // TODO: what about introducing a specific callback for the
        // HttpWebSocketListener for connections and handshake errors which
        // occur for all applications in the same websocket endpoint? Would such
        // a fine grained control be useful with callbacks on both applications
        // and websocket endpoint?
      }
    }
  }

  public void unregister(String path, WebSocketApplication<T> callback) {
    prefixMap.remove(path);
  }

  @Override
  public final int validOps() {
    return SelectionKey.OP_ACCEPT;
  }

  /**
   * Accept a new connection and return a channel for it. The returned channel
   * must be left in blocking mode.
   * 
   * @return a SelectableChannel instance for the new connection
   */
  protected abstract T acceptChannel() throws IOException;

  protected WebSocketApplication<T> getApplication(String path) {
    // TODO: support wildcards
    while (path != null) {
      if (prefixMap.containsKey(path)) {
        return prefixMap.get(path);
      }
      int slash = path.lastIndexOf('/');
      if (slash <= 0) {
        break;
      }
      path = path.substring(0, slash);
    }
    return null;
  }

  @Override
  protected void implCloseChannel() throws IOException {
    // TODO Auto-generated method stub
  }

  /**
   * Send an error response for an error during the WebSocket handshake.
   * 
   * @param channel
   */
  private void writeErrorResponse(T channel) throws IOException {
    Writer writer = Channels.newWriter(channel, "UTF-8");
    writer.append("HTTP/1.1 500 invalid handshake\r\n\r\n");
    writer.close();
  }
}