/**
 * Copyright 2012 Florian Schmaus
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.avos.avoscloud.jivesoftware.smackx.ping.packet;

import com.avos.avoscloud.jivesoftware.smack.packet.IQ;
import com.avos.avoscloud.jivesoftware.smackx.ping.PingManager;

public class Ping extends IQ {
    
    public Ping() {
    }
    
    public Ping(String from, String to) {
        setTo(to);
        setFrom(from);
        setType(IQ.Type.GET);
        setPacketID(getPacketID());
    }
    
    public String getChildElementXML() {
        return "<" + PingManager.ELEMENT + " xmlns=\'" + PingManager.NAMESPACE + "\' />";
    }

}