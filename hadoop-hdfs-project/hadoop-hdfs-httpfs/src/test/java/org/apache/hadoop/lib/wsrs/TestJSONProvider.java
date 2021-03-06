/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.lib.wsrs;

import junit.framework.Assert;
import org.json.simple.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class TestJSONProvider {

  @Test
  @SuppressWarnings("unchecked")
  public void test() throws Exception {
    JSONProvider p = new JSONProvider();
    Assert.assertTrue(p.isWriteable(JSONObject.class, null, null, null));
    Assert.assertFalse(p.isWriteable(this.getClass(), null, null, null));
    Assert.assertEquals(p.getSize(null, null, null, null, null), -1);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    JSONObject json = new JSONObject();
    json.put("a", "A");
    p.writeTo(json, JSONObject.class, null, null, null, null, baos);
    baos.close();
    Assert.assertEquals(new String(baos.toByteArray()).trim(), "{\"a\":\"A\"}");
  }

}
