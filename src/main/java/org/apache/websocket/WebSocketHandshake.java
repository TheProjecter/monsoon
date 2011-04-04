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
import java.net.URI;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.util.Map;

/**
 * Implements an HTTP-compatible WebSocket handshake. The caller will have
 * already read the entire HTTP request, including the first line and the
 * headers up to the blank line. A server supporting multiple handshake versions
 * will ask each one, in a configured order, if it recognizes the request as
 * appropriate for that handshake (note that since some handshakes overlap,
 * order of testing is important) and if so, what the real path for the request
 * is (since some handshakes may carry that somewhere besides the URI in the
 * method). Finally, the caller will ask the handshake to perform the client or
 * server-side portion of the handshake and return a connected WebSocket
 * instance.
 * 
 * @param <T> the channel type used by this handshake, which must both be a
 *          SelectableChannel and a ByteChannel
 */
public interface WebSocketHandshake<T extends SelectableChannel
    & GatheringByteChannel & ScatteringByteChannel> {

  /**
   * Perform the client side of this handshake.
   *
   * @param channel the connected channel to the WebSocket server (must be in
   *     blocking mode)
   * @param uri the WebSocket URI, such as ws://host:port/path
   * @param subprotocol the subprotocol, or null if none
   * @param origin the origin of the web page making this request
   * @param cookies the value of the Cookies header to send, or null if none
   * @param otherHeaders key/value pairs of other headers to include in the
   *     handshake request
   * @return a connected WebSocket instance
   * @throws IOException
   */
  WebSocket<T> clientHandshake(T channel, URI uri, String subprotocol,
      String origin, String cookies, String... otherHeaders) throws IOException;

  /**
   * Return the real path of the URI the WebSocket is connecting to.
   *
   * @param requestLine
   * @param headers
   * @return real path from the URI
   */
  String getRealPath(String requestLine, Map<String, String> headers);

  /**
   * See if the HTTP header could be a valid client request for this handshake.
   *
   * @param requestLine
   * @param headers
   * @return true if this request could be from a client of this handshake
   */
  boolean matches(String requestLine, Map<String, String> headers);

  /**
   * Perform the server side of this handshake.

   * @param channel the connected channel from the WebSocket client (must be in
   *     blocking mode)
   * @param requestLine
   * @param headers
   * @param app the application matching the requested path
   * @return a connected WebSocket instance
   * @throws IOException
   */
	WebSocket<T> serverHandshake(T channel, String requestLine,
      Map<String, String> headers, WebSocketApplication<T> app) throws IOException;
}
