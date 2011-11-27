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

package org.apache.weasel.sample;

import org.apache.weasel.ServerWebSocket;
import org.apache.weasel.WebSocket;
import org.apache.weasel.WebSocketApplication;
import org.apache.weasel.WebSocketException;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Sample server that forks a thread per connection, and then receives callbacks
 * on each socket's thread when a new message arrives.
 */
public class SampleThreadedServer1 {

  public static class App1 implements WebSocketApplication<SocketChannel>, Runnable {

    private WebSocket<SocketChannel> websocket;

    public void onConnection(WebSocket<SocketChannel> websocket) {
      System.out.println("New connection established.");
      this.websocket = websocket;
      new Thread(this).start();
    }

    public void onHandshakeError(IOException e) {
      e.printStackTrace();
    }

    public String acceptProtocol(String protocol) {
      return protocol;
    }

    public boolean acceptOrigin(String origin) {
      return true;
    }

    public Map<String, String> acceptExtensions(Map<String, String> headers)
        throws WebSocketException {
      return new HashMap<String, String>();
    }

    public void run() {
      try {
        String text = websocket.receiveText();
        System.out.println("Received: " + text);
        websocket.sendText("Hello to you too");
        // System.out.println("Sending more...");
        // websocket.sendText("I can send you more");
        // System.out.println("Sent more");
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * TODO: document me
   * 
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    URI uri = new URI(args[0]);
    ServerWebSocket server = new ServerWebSocket(uri);
    server.register("/app1", new App1());
    server.run();
  }
}
