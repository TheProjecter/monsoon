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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SocketChannel;

import org.apache.weasel.WebSocket;
import org.apache.weasel.WebSocketConnector;

/**
 * A sample IO-based, simple client.
 */
public class SampleClient1 {

  /**
   * Connect to the requested URI. Usage:
   * 
   * [-proxy host:port] ws://host:port/path
   * 
   * @param args
   * @throws IOException
   * @throws URISyntaxException
   */
  public static void main(String[] args) throws IOException, URISyntaxException {
    String proxy = null;
    WebSocketConnector wsc = new WebSocketConnector(proxy);
    String origin = "apache-library";
    URI uri = new URI("ws://127.0.0.1:8090/app1");
    WebSocket<SocketChannel> ws = wsc.connect(uri, null, origin, null);
    ws.sendText("Hello");
    System.out.println("Received: " + ws.receiveText());
    // System.out.println("Received: " + ws.receiveText());
    System.out.println("Done");
    ws.close();
  }
}
