/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright 2003-2007 Jive Software.
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

package com.avos.smack.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.avos.smack.Connection;
import com.avos.smack.packet.Authentication;
import com.avos.smack.packet.Bind;
import com.avos.smack.packet.IQ;
import com.avos.smack.packet.Message;
import com.avos.smack.packet.Packet;
import com.avos.smack.packet.PacketExtension;
import com.avos.smack.packet.Presence;
import com.avos.smack.packet.StreamError;
import com.avos.smack.packet.XMPPError;
import com.avos.smack.provider.IQProvider;
import com.avos.smack.provider.ProviderManager;
import com.avos.smack.sasl.SASLMechanism.Failure;

/**
 * Utility class that helps to parse packets. Any parsing packets method that must be shared
 * between many clients must be placed in this utility class.
 *
 * @author Gaston Dombiak
 */
public class PacketParserUtils {

    /**
     * Namespace used to store packet properties.
     */
    private static final String PROPERTIES_NAMESPACE =
            "http://www.jivesoftware.com/xmlns/xmpp/properties";

    /**
     * Parses a message packet.
     *
     * @param parser the XML parser, positioned at the start of a message packet.
     * @return a Message packet.
     * @throws Exception if an exception occurs while parsing the packet.
     */
    public static Packet parseMessage(XmlPullParser parser) throws Exception {
        Message message = new Message();
        String id = parser.getAttributeValue("", "id");
        message.setPacketID(id == null ? Packet.ID_NOT_AVAILABLE : id);
        message.setTo(parser.getAttributeValue("", "to"));
        message.setFrom(parser.getAttributeValue("", "from"));
        message.setType(Message.Type.fromString(parser.getAttributeValue("", "type")));
        String language = getLanguageAttribute(parser);
        
        // determine message's default language
        String defaultLanguage = null;
        if (language != null && !"".equals(language.trim())) {
            message.setLanguage(language);
            defaultLanguage = language;
        } 
        else {
            defaultLanguage = Packet.getDefaultLanguage();
        }

        // Parse sub-elements. We include extra logic to make sure the values
        // are only read once. This is because it's possible for the names to appear
        // in arbitrary sub-elements.
        boolean done = false;
        String thread = null;
        Map<String, Object> properties = null;
        while (!done) {
            int eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG) {
                String elementName = parser.getName();
                String namespace = parser.getNamespace();
                if (elementName.equals("subject")) {
                    String xmlLang = getLanguageAttribute(parser);
                    if (xmlLang == null) {
                        xmlLang = defaultLanguage;
                    }

                    String subject = parseContent(parser);

                    if (message.getSubject(xmlLang) == null) {
                        message.addSubject(xmlLang, subject);
                    }
                }
                else if (elementName.equals("body")) {
                    String xmlLang = getLanguageAttribute(parser);
                    if (xmlLang == null) {
                        xmlLang = defaultLanguage;
                    }

                    String body = parseContent(parser);
                    
                    if (message.getBody(xmlLang) == null) {
                        message.addBody(xmlLang, body);
                    }
                }
                else if (elementName.equals("thread")) {
                    if (thread == null) {
                        thread = parser.nextText();
                    }
                }
                else if (elementName.equals("error")) {
                    message.setError(parseError(parser));
                }
                else if (elementName.equals("properties") &&
                        namespace.equals(PROPERTIES_NAMESPACE))
                {
                    properties = parseProperties(parser);
                }
                // Otherwise, it must be a packet extension.
                else {
//                    message.addExtension(
//                    PacketParserUtils.parsePacketExtension(elementName, namespace, parser));
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("message")) {
                    done = true;
                }
            }
        }

        message.setThread(thread);
        // Set packet properties.
        if (properties != null) {
            for (String name : properties.keySet()) {
                message.setProperty(name, properties.get(name));
            }
        }
        return message;
    }

    /**
     * Returns the content of a tag as string regardless of any tags included.
     * 
     * @param parser the XML pull parser
     * @return the content of a tag as string
     * @throws XmlPullParserException if parser encounters invalid XML
     * @throws IOException if an IO error occurs
     */
    private static String parseContent(XmlPullParser parser)
                    throws XmlPullParserException, IOException {
        int parserDepth = parser.getDepth();
        return parseContentDepth(parser, parserDepth);
    }

    public static String parseContentDepth(XmlPullParser parser, int depth) throws XmlPullParserException, IOException {
        StringBuffer content = new StringBuffer();
        while (!(parser.next() == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
            content.append(parser.getText());
        }
        return content.toString();
    }

    /**
     * Parses a presence packet.
     *
     * @param parser the XML parser, positioned at the start of a presence packet.
     * @return a Presence packet.
     * @throws Exception if an exception occurs while parsing the packet.
     */
    public static Presence parsePresence(XmlPullParser parser) throws Exception {
        Presence.Type type = Presence.Type.available;
        String typeString = parser.getAttributeValue("", "type");
        if (typeString != null && !typeString.equals("")) {
            try {
                type = Presence.Type.valueOf(typeString);
            }
            catch (IllegalArgumentException iae) {
                System.err.println("Found invalid presence type " + typeString);
            }
        }
        Presence presence = new Presence(type);
        presence.setTo(parser.getAttributeValue("", "to"));
        presence.setFrom(parser.getAttributeValue("", "from"));
        String id = parser.getAttributeValue("", "id");
        presence.setPacketID(id == null ? Packet.ID_NOT_AVAILABLE : id);

        String language = getLanguageAttribute(parser);
        if (language != null && !"".equals(language.trim())) {
        	presence.setLanguage(language);
        }
        presence.setPacketID(id == null ? Packet.ID_NOT_AVAILABLE : id);

        // Parse sub-elements
        boolean done = false;
        while (!done) {
            int eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG) {
                String elementName = parser.getName();
                String namespace = parser.getNamespace();
                if (elementName.equals("status")) {
                    presence.setStatus(parser.nextText());
                }
                else if (elementName.equals("priority")) {
                    try {
                        int priority = Integer.parseInt(parser.nextText());
                        presence.setPriority(priority);
                    }
                    catch (NumberFormatException nfe) {
                        // Ignore.
                    }
                    catch (IllegalArgumentException iae) {
                        // Presence priority is out of range so assume priority to be zero
                        presence.setPriority(0);
                    }
                }
                else if (elementName.equals("show")) {
                    String modeText = parser.nextText();
                    try {
                        presence.setMode(Presence.Mode.valueOf(modeText));
                    }
                    catch (IllegalArgumentException iae) {
                        System.err.println("Found invalid presence mode " + modeText);
                    }
                }
                else if (elementName.equals("error")) {
                    presence.setError(parseError(parser));
                }
                else if (elementName.equals("properties") &&
                        namespace.equals(PROPERTIES_NAMESPACE))
                {
                    Map<String,Object> properties = parseProperties(parser);
                    // Set packet properties.
                    for (String name : properties.keySet()) {
                        presence.setProperty(name, properties.get(name));
                    }
                }
                // Otherwise, it must be a packet extension.
                else {
                	try {
                     //   presence.addExtension(PacketParserUtils.parsePacketExtension(elementName, namespace, parser));
                	}
                	catch (Exception e) {
                		System.err.println("Failed to parse extension packet in Presence packet.");
                	}
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("presence")) {
                    done = true;
                }
            }
        }
        return presence;
    }

    /**
     * Parses an IQ packet.
     *
     * @param parser the XML parser, positioned at the start of an IQ packet.
     * @return an IQ object.
     * @throws Exception if an exception occurs while parsing the packet.
     */
    public static IQ parseIQ(XmlPullParser parser, Connection connection) throws Exception {
        IQ iqPacket = null;

        String id = parser.getAttributeValue("", "id");
        String to = parser.getAttributeValue("", "to");
        String from = parser.getAttributeValue("", "from");
        IQ.Type type = IQ.Type.fromString(parser.getAttributeValue("", "type"));
        XMPPError error = null;

        boolean done = false;
        while (!done) {
            int eventType = parser.next();

            if (eventType == XmlPullParser.START_TAG) {
                String elementName = parser.getName();
                String namespace = parser.getNamespace();
                if (elementName.equals("error")) {
                    error = PacketParserUtils.parseError(parser);
                }
                else if (elementName.equals("query") && namespace.equals("jabber:iq:auth")) {
                    iqPacket = parseAuthentication(parser);
                }
                else if (elementName.equals("query") && namespace.equals("jabber:iq:roster")) {
                    //iqPacket = parseRoster(parser);
                }
                else if (elementName.equals("query") && namespace.equals("jabber:iq:register")) {
                    //iqPacket = parseRegistration(parser);
                }
                else if (elementName.equals("bind") &&
                        namespace.equals("urn:ietf:params:xml:ns:xmpp-bind")) {
                    iqPacket = parseResourceBinding(parser);
                }
                // Otherwise, see if there is a registered provider for
                // this element name and namespace.
                else {
                    Object provider = ProviderManager.getInstance().getIQProvider(elementName, namespace);
                    if (provider != null) {
                        if (provider instanceof IQProvider) {
                            iqPacket = ((IQProvider)provider).parseIQ(parser);
                        }
                        else if (provider instanceof Class) {
                            iqPacket = (IQ)PacketParserUtils.parseWithIntrospection(elementName,
                                    (Class<?>)provider, parser);
                        }
                    }
                    // Only handle unknown IQs of type result. Types of 'get' and 'set' which are not understood
                    // have to be answered with an IQ error response. See the code a few lines below
                    else if (IQ.Type.RESULT == type){
                        // No Provider found for the IQ stanza, parse it to an UnparsedIQ instance
                        // so that the content of the IQ can be examined later on
                        iqPacket = new UnparsedResultIQ(parseContent(parser));
                    }
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("iq")) {
                    done = true;
                }
            }
        }
        // Decide what to do when an IQ packet was not understood
        if (iqPacket == null) {
            if (IQ.Type.GET == type || IQ.Type.SET == type ) {
                // If the IQ stanza is of type "get" or "set" containing a child element
                // qualified by a namespace it does not understand, then answer an IQ of
                // type "error" with code 501 ("feature-not-implemented")
                iqPacket = new IQ() {
                    @Override
                    public String getChildElementXML() {
                        return null;
                    }
                };
                iqPacket.setPacketID(id);
                iqPacket.setTo(from);
                iqPacket.setFrom(to);
                iqPacket.setType(IQ.Type.ERROR);
                iqPacket.setError(new XMPPError(XMPPError.Condition.feature_not_implemented));
                connection.sendPacket(iqPacket);
                return null;
            }
            else {
                // If an IQ packet wasn't created above, create an empty IQ packet.
                iqPacket = new IQ() {
                    @Override
                    public String getChildElementXML() {
                        return null;
                    }
                };
            }
        }

        // Set basic values on the iq packet.
        iqPacket.setPacketID(id);
        iqPacket.setTo(to);
        iqPacket.setFrom(from);
        iqPacket.setType(type);
        iqPacket.setError(error);

        return iqPacket;
    }

    private static Authentication parseAuthentication(XmlPullParser parser) throws Exception {
        Authentication authentication = new Authentication();
        boolean done = false;
        while (!done) {
            int eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("username")) {
                    authentication.setUsername(parser.nextText());
                }
                else if (parser.getName().equals("password")) {
                    authentication.setPassword(parser.nextText());
                }
                else if (parser.getName().equals("digest")) {
                    authentication.setDigest(parser.nextText());
                }
                else if (parser.getName().equals("resource")) {
                    authentication.setResource(parser.nextText());
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("query")) {
                    done = true;
                }
            }
        }
        return authentication;
    }


    private static Bind parseResourceBinding(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        Bind bind = new Bind();
        boolean done = false;
        while (!done) {
            int eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("resource")) {
                    bind.setResource(parser.nextText());
                }
                else if (parser.getName().equals("jid")) {
                    bind.setJid(parser.nextText());
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("bind")) {
                    done = true;
                }
            }
        }

        return bind;
    }

    /**
     * Parse the available SASL mechanisms reported from the server.
     *
     * @param parser the XML parser, positioned at the start of the mechanisms stanza.
     * @return a collection of Stings with the mechanisms included in the mechanisms stanza.
     * @throws Exception if an exception occurs while parsing the stanza.
     */
    public static Collection<String> parseMechanisms(XmlPullParser parser) throws Exception {
        List<String> mechanisms = new ArrayList<String>();
        boolean done = false;
        while (!done) {
            int eventType = parser.next();

            if (eventType == XmlPullParser.START_TAG) {
                String elementName = parser.getName();
                if (elementName.equals("mechanism")) {
                    mechanisms.add(parser.nextText());
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("mechanisms")) {
                    done = true;
                }
            }
        }
        return mechanisms;
    }

    /**
     * Parse the available compression methods reported from the server.
     *
     * @param parser the XML parser, positioned at the start of the compression stanza.
     * @return a collection of Stings with the methods included in the compression stanza.
     * @throws Exception if an exception occurs while parsing the stanza.
     */
    public static Collection<String> parseCompressionMethods(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        List<String> methods = new ArrayList<String>();
        boolean done = false;
        while (!done) {
            int eventType = parser.next();

            if (eventType == XmlPullParser.START_TAG) {
                String elementName = parser.getName();
                if (elementName.equals("method")) {
                    methods.add(parser.nextText());
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("compression")) {
                    done = true;
                }
            }
        }
        return methods;
    }

    /**
     * Parse a properties sub-packet. If any errors occur while de-serializing Java object
     * properties, an exception will be printed and not thrown since a thrown
     * exception will shut down the entire connection. ClassCastExceptions will occur
     * when both the sender and receiver of the packet don't have identical versions
     * of the same class.
     *
     * @param parser the XML parser, positioned at the start of a properties sub-packet.
     * @return a map of the properties.
     * @throws Exception if an error occurs while parsing the properties.
     */
    public static Map<String, Object> parseProperties(XmlPullParser parser) throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        while (true) {
            int eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG && parser.getName().equals("property")) {
                // Parse a property
                boolean done = false;
                String name = null;
                String type = null;
                String valueText = null;
                Object value = null;
                while (!done) {
                    eventType = parser.next();
                    if (eventType == XmlPullParser.START_TAG) {
                        String elementName = parser.getName();
                        if (elementName.equals("name")) {
                            name = parser.nextText();
                        }
                        else if (elementName.equals("value")) {
                            type = parser.getAttributeValue("", "type");
                            valueText = parser.nextText();
                        }
                    }
                    else if (eventType == XmlPullParser.END_TAG) {
                        if (parser.getName().equals("property")) {
                            if ("integer".equals(type)) {
                                value = Integer.valueOf(valueText);
                            }
                            else if ("long".equals(type))  {
                                value = Long.valueOf(valueText);
                            }
                            else if ("float".equals(type)) {
                                value = Float.valueOf(valueText);
                            }
                            else if ("double".equals(type)) {
                                value = Double.valueOf(valueText);
                            }
                            else if ("boolean".equals(type)) {
                                value = Boolean.valueOf(valueText);
                            }
                            else if ("string".equals(type)) {
                                value = valueText;
                            }
                            else if ("java-object".equals(type)) {
                                try {
                                    byte [] bytes = StringUtils.decodeBase64(valueText);
                                    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                                    value = in.readObject();
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            if (name != null && value != null) {
                                properties.put(name, value);
                            }
                            done = true;
                        }
                    }
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("properties")) {
                    break;
                }
            }
        }
        return properties;
    }

    /**
     * Parses SASL authentication error packets.
     * 
     * @param parser the XML parser.
     * @return a SASL Failure packet.
     * @throws Exception if an exception occurs while parsing the packet.
     */
    public static Failure parseSASLFailure(XmlPullParser parser) throws Exception {
        String condition = null;
        boolean done = false;
        while (!done) {
            int eventType = parser.next();

            if (eventType == XmlPullParser.START_TAG) {
                if (!parser.getName().equals("failure")) {
                    condition = parser.getName();
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("failure")) {
                    done = true;
                }
            }
        }
        return new Failure(condition);
    }

    /**
     * Parses stream error packets.
     *
     * @param parser the XML parser.
     * @return an stream error packet.
     * @throws Exception if an exception occurs while parsing the packet.
     */
    public static StreamError parseStreamError(XmlPullParser parser) throws IOException,
            XmlPullParserException {
    StreamError streamError = null;
    boolean done = false;
    while (!done) {
        int eventType = parser.next();

        if (eventType == XmlPullParser.START_TAG) {
            streamError = new StreamError(parser.getName());
        }
        else if (eventType == XmlPullParser.END_TAG) {
            if (parser.getName().equals("error")) {
                done = true;
            }
        }
    }
    return streamError;
}

    /**
     * Parses error sub-packets.
     *
     * @param parser the XML parser.
     * @return an error sub-packet.
     * @throws Exception if an exception occurs while parsing the packet.
     */
    public static XMPPError parseError(XmlPullParser parser) throws Exception {
        final String errorNamespace = "urn:ietf:params:xml:ns:xmpp-stanzas";
    	String errorCode = "-1";
        String type = null;
        String message = null;
        String condition = null;
        List<PacketExtension> extensions = new ArrayList<PacketExtension>();

        // Parse the error header
        for (int i=0; i<parser.getAttributeCount(); i++) {
            if (parser.getAttributeName(i).equals("code")) {
                errorCode = parser.getAttributeValue("", "code");
            }
            if (parser.getAttributeName(i).equals("type")) {
            	type = parser.getAttributeValue("", "type");
            }
        }
        boolean done = false;
        // Parse the text and condition tags
        while (!done) {
            int eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("text")) {
                    message = parser.nextText();
                }
                else {
                	// Condition tag, it can be xmpp error or an application defined error.
                    String elementName = parser.getName();
                    String namespace = parser.getNamespace();
                    if (errorNamespace.equals(namespace)) {
                    	condition = elementName;
                    }
                    else {
                    //	extensions.add(parsePacketExtension(elementName, namespace, parser));
                    }
                }
            }
                else if (eventType == XmlPullParser.END_TAG) {
                    if (parser.getName().equals("error")) {
                        done = true;
                    }
                }
        }
        // Parse the error type.
        XMPPError.Type errorType = XMPPError.Type.CANCEL;
        try {
            if (type != null) {
                errorType = XMPPError.Type.valueOf(type.toUpperCase());
            }
        }
        catch (IllegalArgumentException iae) {
            // Print stack trace. We shouldn't be getting an illegal error type.
            iae.printStackTrace();
        }
        return new XMPPError(Integer.parseInt(errorCode), errorType, condition, message, extensions);
    }

    private static String getLanguageAttribute(XmlPullParser parser) {
    	for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attributeName = parser.getAttributeName(i);
            if ( "xml:lang".equals(attributeName) ||
                    ("lang".equals(attributeName) &&
                            "xml".equals(parser.getAttributePrefix(i)))) {
    			return parser.getAttributeValue(i);
    		}
    	}
    	return null;
    }

    public static Object parseWithIntrospection(String elementName,
            Class<?> objectClass, XmlPullParser parser) throws Exception
    {
        boolean done = false;
        Object object = objectClass.newInstance();
        while (!done) {
            int eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG) {
                String name = parser.getName();
                String stringValue = parser.nextText();
                Class propertyType = object.getClass().getMethod(
                    "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1)).getReturnType();
                // Get the value of the property by converting it from a
                // String to the correct object type.
                Object value = decode(propertyType, stringValue);
                // Set the value of the bean.
                object.getClass().getMethod("set" + Character.toUpperCase(name.charAt(0)) + name.substring(1), propertyType)
                .invoke(object, value);
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(elementName)) {
                    done = true;
                }
            }
        }
        return object;
            }

    /**
     * Decodes a String into an object of the specified type. If the object
     * type is not supported, null will be returned.
     *
     * @param type the type of the property.
     * @param value the encode String value to decode.
     * @return the String value decoded into the specified type.
     * @throws Exception If decoding failed due to an error.
     */
    private static Object decode(Class<?> type, String value) throws Exception {
        if (type.getName().equals("java.lang.String")) {
            return value;
        }
        if (type.getName().equals("boolean")) {
            return Boolean.valueOf(value);
        }
        if (type.getName().equals("int")) {
            return Integer.valueOf(value);
        }
        if (type.getName().equals("long")) {
            return Long.valueOf(value);
        }
        if (type.getName().equals("float")) {
            return Float.valueOf(value);
        }
        if (type.getName().equals("double")) {
            return Double.valueOf(value);
        }
        if (type.getName().equals("java.lang.Class")) {
            return Class.forName(value);
        }
        return null;
    }

    /**
     * This class represents and unparsed IQ of the type 'result'. Usually it's created when no IQProvider
     * was found for the IQ element.
     * 
     * The child elements can be examined with the getChildElementXML() method.
     *
     */
    public static class UnparsedResultIQ extends IQ {
        public UnparsedResultIQ(String content) {
            this.str = content;
        }

        private final String str;

        @Override
        public String getChildElementXML() {
            return this.str;
        }
    }
}