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
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.util.Map;

/**
 * An interface implemented by a WebSocket application running in a server.
 *
 * @param <T> the type of channel used
 */
public interface WebSocketApplication<T extends SelectableChannel & GatheringByteChannel & ScatteringByteChannel> {

  /**
   * Called when a connection has been successfully established. The WebSocket
   * is initially in blocking mode.
   * 
   * @param socket
   */
  void onConnection(WebSocket<T> socket);

  /**
   * Called when an error occurs during the WebSocket handshake.
   * 
   * @param e
   */
  void onHandshakeError(IOException e);

  /**
   * Called during handshake to accept the protocol.
   * 
   * @param protocol the protocols that client knows (can be null or empty)
   * @return the protocol that will be used after the handshake
   */
  String acceptProtocol(String protocol);

  /**
   * Called during handshake to accept the origin.
   * 
   * @param origin the origin header (can be null or empty)
   * @return true if origin accepted, false otherwise
   */
  boolean acceptOrigin(String origin);

  /**
   * Called during handshake to check if headers correspond to extension
   * requirements.
   * 
   * @param headers request headers
   * @throws WebSocketException if headers don't meet requirements
   * @return headers to be included in the response
   */
  Map<String, String> acceptExtensions(Map<String, String> headers)
      throws WebSocketException;
}
