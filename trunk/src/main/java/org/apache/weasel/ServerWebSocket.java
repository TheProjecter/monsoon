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

package org.apache.weasel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 * A WebSocketListener which listens on a ServerSocket for new connections.
 */
public class ServerWebSocket extends HttpWebSocketListener<SocketChannel> {

  private ServerSocketChannel socket;

  @Override
  protected SocketChannel acceptChannel() throws IOException {
    SocketChannel channel = socket.accept();
    return channel;
  }

  /**
   * Create a ServerWebSocket listening at the specified URI.
   * 
   * @param uri
   * @throws IOException
   */
  public ServerWebSocket(URI uri) throws IOException {
    socket = ServerSocketChannel.open();
    socket.socket().bind(new InetSocketAddress(uri.getHost(), uri.getPort()));
    System.out.println("Listening on port " + uri.getPort() + "...");
  }

  /**
   * @return the blocking-mode object
   * @see java.nio.channels.spi.AbstractSelectableChannel#blockingLock()
   */
  @Override
  public final Object blockingLock() {
    return socket.blockingLock();
  }

  /**
   * @param block
   * @return the selectable channel
   * @throws IOException
   * @see java.nio.channels.spi.AbstractSelectableChannel#configureBlocking(boolean)
   */
  @Override
  public final SelectableChannel configureBlocking(boolean block)
      throws IOException {
    return socket.configureBlocking(block);
  }

  /**
   * @return true if this channel is in blocking mode
   * @see java.nio.channels.spi.AbstractSelectableChannel#isBlocking()
   */
  @Override
  public final boolean isBlocking() {
    return socket.isBlocking();
  }

  /**
   * @return true if this channel is registered
   * @see java.nio.channels.spi.AbstractSelectableChannel#isRegistered()
   */
  @Override
  public final boolean isRegistered() {
    return socket.isRegistered();
  }

  /**
   * @param sel
   * @return the last registered key or null if not registered
   * @see java.nio.channels.spi.AbstractSelectableChannel#keyFor(java.nio.channels.Selector)
   */
  @Override
  public final SelectionKey keyFor(Selector sel) {
    return socket.keyFor(sel);
  }

  /**
   * @return the provider that created this channel
   * @see java.nio.channels.spi.AbstractSelectableChannel#provider()
   */
  @Override
  public final SelectorProvider provider() {
    return socket.provider();
  }

  /**
   * @param sel
   * @param ops
   * @param att
   * @return a key representing this registration
   * @throws ClosedChannelException
   * @see java.nio.channels.spi.AbstractSelectableChannel#register(java.nio.channels.Selector,
   *      int, java.lang.Object)
   */
  @Override
  public final SelectionKey register(Selector sel, int ops, Object att)
      throws ClosedChannelException {
    return socket.register(sel, ops, att);
  }
}
