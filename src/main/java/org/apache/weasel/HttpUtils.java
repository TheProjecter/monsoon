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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HttpUtils {

  /**
   * Read HTTP headers.
   * 
   * TODO: replace with more full-featured header parsing, including support for
   * repeated headers, line folding, etc.
   * 
   * @param reader
   * @return map of HTTP headers
   * @throws IOException
   */
  public static Map<String, String> readHttpHeaders(BufferedReader reader)
      throws IOException {
    Map<String, String> headers = new HashMap<String, String>();
    String line = null;
    while ((line = reader.readLine()) != null) {
      if (line.length() == 0) {
        break;
      }
      int colon = line.indexOf(':');
      if (colon <= 0) {
        throw new WebSocketException("Missing colon in HTTP header");
      }
      String tag = line.substring(0, colon);
      if (tag.indexOf(' ') >= 0) {
        throw new WebSocketException("HTTP header tag can't contain space");
      }
      if (++colon < line.length() && line.charAt(colon) == ' ') {
        colon++;
      }
      String value = line.substring(colon);
      headers.put(tag.toLowerCase(), value);
    }
    return headers;
  }

  private HttpUtils() {
  }
}
