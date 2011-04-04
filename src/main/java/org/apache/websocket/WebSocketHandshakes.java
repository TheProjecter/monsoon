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

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of configured handshakes.
 */
// TODO: should we parameterize this class to allow non-socket uses?
class WebSocketHandshakes {

  // singleton instances of default handshake
  private static final WebSocketHandshake<SocketChannel> defaultHandshake;

  /**
   * List of handshakes in the order to attempt them.
   */
  private static final List<WebSocketHandshake<SocketChannel>> handshakes;

  static {
    defaultHandshake = new V06Handshake<SocketChannel>();
    ArrayList<WebSocketHandshake<SocketChannel>> list = new ArrayList<WebSocketHandshake<SocketChannel>>();
    list.add(defaultHandshake);
    // TODO: add other handshakes
    handshakes = Collections.unmodifiableList(list);
  }

  /**
   * @return the default handshake to use for outgoing connections.
   */
  public static WebSocketHandshake<SocketChannel> getDefaultHanshake() {
    return defaultHandshake;
  }

  public static Iterable<WebSocketHandshake<SocketChannel>> getHandshakes() {
    return handshakes;
  }
}
