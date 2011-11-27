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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SocketChannel;

import org.apache.weasel.WebSocket;
import org.apache.weasel.WebSocketConnector;
import org.junit.Test;

public class WebSocketConnectorTest {

  @Test(expected = IllegalArgumentException.class)
  public void testRelativeURL() throws IOException, URISyntaxException {
    testURL(new URI("relative/url"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRelativeURLWithSlash() throws IOException, URISyntaxException {
    testURL(new URI("/relative/url"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoScheme() throws IOException, URISyntaxException {
    testURL(new URI("www.apache.org"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidScheme() throws IOException, URISyntaxException {
    testURL(new URI("http://www.apache.org"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testURLWithFragment() throws IOException, URISyntaxException {
    testURL(new URI("ws://www.apache.org/websocket#fragment"));
  }

  private WebSocket<SocketChannel> testURL(URI uri) throws IOException {
    WebSocketConnector wsc = new WebSocketConnector();
    return wsc.connect(uri, null, "apache-library", null);
  }

}
