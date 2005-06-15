/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright 2003-2004 Jive Software.
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

package org.jivesoftware.smack;

import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.debugger.*;
import org.jivesoftware.smack.filter.*;

import javax.net.SocketFactory;
import java.lang.reflect.Constructor;
import java.net.*;
import java.util.*;
import java.io.*;

/**
 * Creates a connection to a XMPP server. A simple use of this API might
 * look like the following:
 * <pre>
 * // Create a connection to the jivesoftware.com XMPP server.
 * XMPPConnection con = new XMPPConnection("jivesoftware.com");
 * // Most servers require you to login before performing other tasks.
 * con.login("jsmith", "mypass");
 * // Start a new conversation with John Doe and send him a message.
 * Chat chat = con.createChat("jdoe@jabber.org");
 * chat.sendMessage("Hey, how's it going?");
 * </pre>
 *
 * @author Matt Tucker
 */
public class XMPPConnection {

    /**
     * Value that indicates whether debugging is enabled. When enabled, a debug
     * window will apear for each new connection that will contain the following
     * information:<ul>
     *      <li> Client Traffic -- raw XML traffic generated by Smack and sent to the server.
     *      <li> Server Traffic -- raw XML traffic sent by the server to the client.
     *      <li> Interpreted Packets -- shows XML packets from the server as parsed by Smack.
     * </ul>
     *
     * Debugging can be enabled by setting this field to true, or by setting the Java system
     * property <tt>smack.debugEnabled</tt> to true. The system property can be set on the
     * command line such as "java SomeApp -Dsmack.debugEnabled=true".
     */
    public static boolean DEBUG_ENABLED = false;

    private static List connectionEstablishedListeners = new ArrayList();

    static {
        // Use try block since we may not have permission to get a system
        // property (for example, when an applet).
        try {
            DEBUG_ENABLED = Boolean.getBoolean("smack.debugEnabled");
        }
        catch (Exception e) {
        }
        // Ensure the SmackConfiguration class is loaded by calling a method in it.
        SmackConfiguration.getVersion();
    }
    private SmackDebugger debugger = null;

    String host;
    int port;
    Socket socket;

    String connectionID;
    private String user = null;
    private boolean connected = false;
    private boolean authenticated = false;
    private boolean anonymous = false;

    PacketWriter packetWriter;
    PacketReader packetReader;

    Roster roster = null;
    private AccountManager accountManager = null;

    Writer writer;
    Reader reader;

    /**
     * Creates a new connection to the specified XMPP server. The default port of 5222 will
     * be used.
     *
     * @param host the name of the XMPP server to connect to; e.g. <tt>jivesoftware.com</tt>.
     * @throws XMPPException if an error occurs while trying to establish the connection.
     *      Two possible errors can occur which will be wrapped by an XMPPException --
     *      UnknownHostException (XMPP error code 504), and IOException (XMPP error code
     *      502). The error codes and wrapped exceptions can be used to present more
     *      appropiate error messages to end-users.
     */
    public XMPPConnection(String host) throws XMPPException {
        this(host, 5222);
    }

    /**
     * Creates a new connection to the specified XMPP server on the given port.
     *
     * @param host the name of the XMPP server to connect to; e.g. <tt>jivesoftware.com</tt>.
     * @param port the port on the server that should be used; e.g. <tt>5222</tt>.
     * @throws XMPPException if an error occurs while trying to establish the connection.
     *      Two possible errors can occur which will be wrapped by an XMPPException --
     *      UnknownHostException (XMPP error code 504), and IOException (XMPP error code
     *      502). The error codes and wrapped exceptions can be used to present more
     *      appropiate error messages to end-users.
     */
    public XMPPConnection(String host, int port) throws XMPPException {
        this.host = host;
        this.port = port;
        try {
            this.socket = new Socket(host, port);
        }
        catch (UnknownHostException uhe) {
            throw new XMPPException(
                "Could not connect to " + host + ":" + port + ".",
                new XMPPError(504),
                uhe);
        }
        catch (IOException ioe) {
            throw new XMPPException(
                "XMPPError connecting to " + host + ":" + port + ".",
                new XMPPError(502),
                ioe);
        }
        init();
    }

    /**
     * Creates a new connection to the specified XMPP server on the given port using the specified SocketFactory.
     *
     * <p>A custom SocketFactory allows fine-grained control of the actual connection to the XMPP server. A typical
     * use for a custom SocketFactory is when connecting through a SOCKS proxy.
     *
     * @param host the name of the XMPP server to connect to; e.g. <tt>jivesoftware.com</tt>.
     * @param port the port on the server that should be used; e.g. <tt>5222</tt>.
     * @param socketFactory a SocketFactory that will be used to create the socket to the XMPP server.
     * @throws XMPPException if an error occurs while trying to establish the connection.
     *      Two possible errors can occur which will be wrapped by an XMPPException --
     *      UnknownHostException (XMPP error code 504), and IOException (XMPP error code
     *      502). The error codes and wrapped exceptions can be used to present more
     *      appropiate error messages to end-users.
     */
    public XMPPConnection(String host, int port, SocketFactory socketFactory) throws XMPPException {
        this.host = host;
        this.port = port;
        try {
            this.socket = socketFactory.createSocket(host, port);
        }
        catch (UnknownHostException uhe) {
            throw new XMPPException(
                "Could not connect to " + host + ":" + port + ".",
                new XMPPError(504),
                uhe);
        }
        catch (IOException ioe) {
            throw new XMPPException(
                "XMPPError connecting to " + host + ":" + port + ".",
                new XMPPError(502),
                ioe);
        }
        init();
    }

    /**
     * Returns the connection ID for this connection, which is the value set by the server
     * when opening a XMPP stream. If the server does not set a connection ID, this value
     * will be null.
     *
     * @return the ID of this connection returned from the XMPP server.
     */
    public String getConnectionID() {
        return connectionID;
    }

    /**
     * Returns the host name of the XMPP server for this connection.
     *
     * @return the host name of the XMPP server.
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the port number of the XMPP server for this connection. The default port
     * for normal connections is 5222. The default port for SSL connections is 5223.
     *
     * @return the port number of the XMPP server.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the full XMPP address of the user that is logged in to the connection or
     * <tt>null</tt> if not logged in yet. An XMPP address is in the form
     * username@server/resource.
     *
     * @return the full XMPP address of the user logged in.
     */
    public String getUser() {
        if (!isAuthenticated()) {
            return null;
        }
        return user;
    }

    /**
     * Logs in to the server using the strongest authentication mode supported by
     * the server, then set our presence to available. If more than five seconds
     * (default timeout) elapses in each step of the authentication process without
     * a response from the server, or if an error occurs, a XMPPException will be thrown.
     *
     * @param username the username.
     * @param password the password.
     * @throws XMPPException if an error occurs.
     */
    public void login(String username, String password) throws XMPPException {
        login(username, password, "Smack");
    }

    /**
     * Logs in to the server using the strongest authentication mode supported by
     * the server, then sets presence to available. If more than five seconds
     * (default timeout) elapses in each step of the authentication process without
     * a response from the server, or if an error occurs, a XMPPException will be thrown.
     *
     * @param username the username.
     * @param password the password.
     * @param resource the resource.
     * @throws XMPPException if an error occurs.
     * @throws IllegalStateException if not connected to the server, or already logged in
     *      to the serrver.
     */
    public synchronized void login(String username, String password, String resource)
            throws XMPPException
    {
        login(username, password, resource, true);
    }

    /**
     * Logs in to the server using the strongest authentication mode supported by
     * the server, and optionally sends an available presence. if <tt>sendPresence</tt>
     * is false, a presence packet must be sent manually later. If more than five seconds
     * (default timeout) elapses in each step of the authentication process without a
     * response from the server, or if an error occurs, a XMPPException will be thrown.
     *
     * @param username the username.
     * @param password the password.
     * @param resource the resource.
     * @param sendPresence if <tt>true</tt> an available presence will be sent automatically
     *      after login is completed.
     * @throws XMPPException if an error occurs.
     * @throws IllegalStateException if not connected to the server, or already logged in
     *      to the serrver.
     */
    public synchronized void login(String username, String password, String resource,
            boolean sendPresence) throws XMPPException
    {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        if (authenticated) {
            throw new IllegalStateException("Already logged in to server.");
        }
        // Do partial version of nameprep on the username.
        username = username.toLowerCase().trim();
        // If we send an authentication packet in "get" mode with just the username,
        // the server will return the list of authentication protocols it supports.
        Authentication discoveryAuth = new Authentication();
        discoveryAuth.setType(IQ.Type.GET);
        discoveryAuth.setUsername(username);

        PacketCollector collector =
            packetReader.createPacketCollector(new PacketIDFilter(discoveryAuth.getPacketID()));
        // Send the packet
        packetWriter.sendPacket(discoveryAuth);
        // Wait up to a certain number of seconds for a response from the server.
        IQ response = (IQ) collector.nextResult(SmackConfiguration.getPacketReplyTimeout());
        if (response == null) {
            throw new XMPPException("No response from the server.");
        }
        // If the server replied with an error, throw an exception.
        else if (response.getType() == IQ.Type.ERROR) {
            throw new XMPPException(response.getError());
        }
        // Otherwise, no error so continue processing.
        Authentication authTypes = (Authentication) response;
        collector.cancel();

        // Now, create the authentication packet we'll send to the server.
        Authentication auth = new Authentication();
        auth.setUsername(username);

        // Figure out if we should use digest or plain text authentication.
        if (authTypes.getDigest() != null) {
            auth.setDigest(connectionID, password);
        }
        else if (authTypes.getPassword() != null) {
            auth.setPassword(password);
        }
        else {
            throw new XMPPException("Server does not support compatible authentication mechanism.");
        }

        auth.setResource(resource);

        collector = packetReader.createPacketCollector(new PacketIDFilter(auth.getPacketID()));
        // Send the packet.
        packetWriter.sendPacket(auth);
        // Wait up to a certain number of seconds for a response from the server.
        response = (IQ) collector.nextResult(SmackConfiguration.getPacketReplyTimeout());
        if (response == null) {
            throw new XMPPException("Authentication failed.");
        }
        else if (response.getType() == IQ.Type.ERROR) {
            throw new XMPPException(response.getError());
        }
        // Set the user.
        if (response.getTo() != null) {
            this.user = response.getTo();
        }
        else {
            this.user = username + "@" + this.host;
            if (resource != null) {
                this.user += "/" + resource;
            }
        }
        // We're done with the collector, so explicitly cancel it.
        collector.cancel();

        // Create the roster.
        this.roster = new Roster(this);
        roster.reload();

        // Set presence to online.
        if (sendPresence) {
            packetWriter.sendPacket(new Presence(Presence.Type.AVAILABLE));
        }

        // Indicate that we're now authenticated.
        authenticated = true;
        anonymous = false;

        // If debugging is enabled, change the the debug window title to include the
        // name we are now logged-in as.
        // If DEBUG_ENABLED was set to true AFTER the connection was created the debugger 
        // will be null
        if (DEBUG_ENABLED && debugger != null) {
            debugger.userHasLogged(user);
        }
    }

    /**
     * Logs in to the server anonymously. Very few servers are configured to support anonymous
     * authentication, so it's fairly likely logging in anonymously will fail. If anonymous login
     * does succeed, your XMPP address will likely be in the form "server/123ABC" (where "123ABC" is a
     * random value generated by the server).
     *
     * @throws XMPPException if an error occurs or anonymous logins are not supported by the server.
     * @throws IllegalStateException if not connected to the server, or already logged in
     *      to the serrver.
     */
    public synchronized void loginAnonymously() throws XMPPException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        if (authenticated) {
            throw new IllegalStateException("Already logged in to server.");
        }

        // Create the authentication packet we'll send to the server.
        Authentication auth = new Authentication();

        PacketCollector collector =
            packetReader.createPacketCollector(new PacketIDFilter(auth.getPacketID()));
        // Send the packet.
        packetWriter.sendPacket(auth);
        // Wait up to a certain number of seconds for a response from the server.
        IQ response = (IQ) collector.nextResult(SmackConfiguration.getPacketReplyTimeout());
        if (response == null) {
            throw new XMPPException("Anonymous login failed.");
        }
        else if (response.getType() == IQ.Type.ERROR) {
            throw new XMPPException(response.getError());
        }
        // Set the user value.
        if (response.getTo() != null) {
            this.user = response.getTo();
        }
        else {
            this.user = this.host + "/" + ((Authentication) response).getResource();
        }
        // We're done with the collector, so explicitly cancel it.
        collector.cancel();

        // Anonymous users can't have a roster.
        roster = null;

        // Set presence to online.
        packetWriter.sendPacket(new Presence(Presence.Type.AVAILABLE));

        // Indicate that we're now authenticated.
        authenticated = true;
        anonymous = true;

        // If debugging is enabled, change the the debug window title to include the
        // name we are now logged-in as.
        // If DEBUG_ENABLED was set to true AFTER the connection was created the debugger 
        // will be null
        if (DEBUG_ENABLED && debugger != null) {
            debugger.userHasLogged(user);
        }
    }

    /**
     * Returns the roster for the user logged into the server. If the user has not yet
     * logged into the server (or if the user is logged in anonymously), this method will return
     * <tt>null</tt>.
     *
     * @return the user's roster, or <tt>null</tt> if the user has not logged in yet.
     */
    public Roster getRoster() {
        if (roster == null) {
            return null;
        }
        // If this is the first time the user has asked for the roster after calling
        // login, we want to wait for the server to send back the user's roster. This
        // behavior shields API users from having to worry about the fact that roster
        // operations are asynchronous, although they'll still have to listen for
        // changes to the roster. Note: because of this waiting logic, internal
        // Smack code should be wary about calling the getRoster method, and may need to
        // access the roster object directly.
        if (!roster.rosterInitialized) {
            try {
                synchronized (roster) {
                    long waitTime = SmackConfiguration.getPacketReplyTimeout();
                    long start = System.currentTimeMillis();
                    while (!roster.rosterInitialized) {
                        if (waitTime <= 0) {
                            break;
                        }
                        roster.wait(waitTime);
                        long now = System.currentTimeMillis();
                        waitTime -= now - start;
                        start = now;
                    }
                }
            }
            catch (InterruptedException ie) { }
        }
        return roster;
    }

    /**
     * Returns an account manager instance for this connection.
     *
     * @return an account manager for this connection.
     */
    public synchronized AccountManager getAccountManager() {
        if (accountManager == null) {
            accountManager = new AccountManager(this);
        }
        return accountManager;
    }

    /**
     * Creates a new chat with the specified participant. The participant should
     * be a valid XMPP user such as <tt>jdoe@jivesoftware.com</tt> or
     * <tt>jdoe@jivesoftware.com/work</tt>.
     *
     * @param participant the person to start the conversation with.
     * @return a new Chat object.
     */
    public Chat createChat(String participant) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        return new Chat(this, participant);
    }

    /**
     * Creates a new group chat connected to the specified room. The room name
     * should be full address, such as <tt>room@chat.example.com</tt>.
     * <p>
     * Most XMPP servers use a sub-domain for the chat service (eg chat.example.com
     * for the XMPP server example.com). You must ensure that the room address you're
     * trying to connect to includes the proper chat sub-domain.
     *
     * @param room the fully qualifed name of the room.
     * @return a new GroupChat object.
     */
    public GroupChat createGroupChat(String room) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        return new GroupChat(this, room);
    }

    /**
     * Returns true if currently connected to the XMPP server.
     *
     * @return true if connected.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Returns true if the connection is a secured one, such as an SSL connection.
     *
     * @return true if a secure connection to the server.
     */
    public boolean isSecureConnection() {
        return false;
    }

    /**
     * Returns true if currently authenticated by successfully calling the login method.
     *
     * @return true if authenticated.
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Returns true if currently authenticated anonymously.
     *
     * @return true if authenticated anonymously.
     */
    public boolean isAnonymous() {
        return anonymous;
    }

    /**
     * Closes the connection by setting presence to unavailable then closing the stream to
     * the XMPP server. Once a connection has been closed, it cannot be re-opened.
     */
    public void close() {
        // Set presence to offline.
        packetWriter.sendPacket(new Presence(Presence.Type.UNAVAILABLE));
        packetReader.shutdown();
        packetWriter.shutdown();
        // Wait 150 ms for processes to clean-up, then shutdown.
        try {
            Thread.sleep(150);
        }
        catch (Exception e) {
        }

		// Close down the readers and writers.
		if (reader != null)
		{
			try { reader.close(); } catch (Throwable ignore) { }
			reader = null;
		}
		if (writer != null)
		{
			try { writer.close(); } catch (Throwable ignore) { }
			writer = null;
		}

        try {
            socket.close();
        }
        catch (Exception e) {
        }
        authenticated = false;
        connected = false;
    }

    /**
     * Sends the specified packet to the server.
     *
     * @param packet the packet to send.
     */
    public void sendPacket(Packet packet) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        if (packet == null) {
            throw new NullPointerException("Packet is null.");
        }
        packetWriter.sendPacket(packet);
    }

    /**
     * Registers a packet listener with this connection. A packet filter determines
     * which packets will be delivered to the listener.
     *
     * @param packetListener the packet listener to notify of new packets.
     * @param packetFilter the packet filter to use.
     */
    public void addPacketListener(PacketListener packetListener, PacketFilter packetFilter) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        packetReader.addPacketListener(packetListener, packetFilter);
    }

    /**
     * Removes a packet listener from this connection.
     *
     * @param packetListener the packet listener to remove.
     */
    public void removePacketListener(PacketListener packetListener) {
        packetReader.removePacketListener(packetListener);
    }

    /**
     * Registers a packet listener with this connection. The listener will be
     * notified of every packet that this connection sends. A packet filter determines
     * which packets will be delivered to the listener.
     *
     * @param packetListener the packet listener to notify of sent packets.
     * @param packetFilter the packet filter to use.
     */
    public void addPacketWriterListener(PacketListener packetListener, PacketFilter packetFilter) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        packetWriter.addPacketListener(packetListener, packetFilter);
    }

    /**
     * Removes a packet listener from this connection.
     *
     * @param packetListener the packet listener to remove.
     */
    public void removePacketWriterListener(PacketListener packetListener) {
        packetWriter.removePacketListener(packetListener);
    }

    /**
     * Creates a new packet collector for this connection. A packet filter determines
     * which packets will be accumulated by the collector.
     *
     * @param packetFilter the packet filter to use.
     * @return a new packet collector.
     */
    public PacketCollector createPacketCollector(PacketFilter packetFilter) {
        return packetReader.createPacketCollector(packetFilter);
    }

    /**
     * Adds a connection listener to this connection that will be notified when
     * the connection closes or fails.
     *
     * @param connectionListener a connection listener.
     */
    public void addConnectionListener(ConnectionListener connectionListener) {
        if (connectionListener == null) {
            return;
        }
        synchronized (packetReader.connectionListeners) {
            if (!packetReader.connectionListeners.contains(connectionListener)) {
                packetReader.connectionListeners.add(connectionListener);
            }
        }
    }

    /**
     * Removes a connection listener from this connection.
     *
     * @param connectionListener a connection listener.
     */
    public void removeConnectionListener(ConnectionListener connectionListener) {
        synchronized (packetReader.connectionListeners) {
            packetReader.connectionListeners.remove(connectionListener);
        }
    }

    /**
     * Adds a connection established listener that will be notified when a new connection 
     * is established.
     *
     * @param connectionEstablishedListener a listener interested on connection established events.
     */
    public static void addConnectionListener(ConnectionEstablishedListener connectionEstablishedListener) {
        synchronized (connectionEstablishedListeners) {
            if (!connectionEstablishedListeners.contains(connectionEstablishedListener)) {
                connectionEstablishedListeners.add(connectionEstablishedListener);
            }
        }
    }

    /**
     * Removes a listener on new established connections.
     *
     * @param connectionEstablishedListener a listener interested on connection established events.
     */
    public static void removeConnectionListener(ConnectionEstablishedListener connectionEstablishedListener) {
        synchronized (connectionEstablishedListeners) {
            connectionEstablishedListeners.remove(connectionEstablishedListener);
        }
    }

    /**
     * Initializes the connection by creating a packet reader and writer and opening a
     * XMPP stream to the server.
     *
     * @throws XMPPException if establishing a connection to the server fails.
     */
    private void init() throws XMPPException {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        }
        catch (IOException ioe) {
            throw new XMPPException(
                "XMPPError establishing connection with server.",
                new XMPPError(502),
                ioe);
        }

        // If debugging is enabled, we open a window and write out all network traffic.
        if (DEBUG_ENABLED) {
            // Detect the debugger class to use.
            String className = null;
            // Use try block since we may not have permission to get a system
            // property (for example, when an applet).
            try {
                className = System.getProperty("smack.debuggerClass");
            }
            catch (Throwable t) {
            }
            Class debuggerClass = null;
            if (className != null) {
                try {
                    debuggerClass = Class.forName(className);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (debuggerClass == null) {
                try {
                    debuggerClass =
                            Class.forName("org.jivesoftware.smackx.debugger.EnhancedDebugger");
                }
                catch (Exception ex) {
                    try {
                        debuggerClass = Class.forName("org.jivesoftware.smack.debugger.LiteDebugger");
                    }
                    catch (Exception ex2) {
                        ex2.printStackTrace();
                    }
                }
            }
            // Create a new debugger instance. If an exception occurs then disable the debugging
            // option
            try {
                Constructor constructor =
                    debuggerClass.getConstructor(
                        new Class[] { XMPPConnection.class, Writer.class, Reader.class });
                debugger =
                    (SmackDebugger) constructor.newInstance(new Object[] { this, writer, reader });
                reader = debugger.getReader();
                writer = debugger.getWriter();
            }
            catch (Exception e) {
                e.printStackTrace();
                DEBUG_ENABLED = false;
            }
        }

		try
		{
            packetWriter = new PacketWriter(this);
            packetReader = new PacketReader(this);

            // If debugging is enabled, we should start the thread that will listen for
            // all packets and then log them.
            if (DEBUG_ENABLED) {
                packetReader.addPacketListener(debugger.getReaderListener(), null);
                if (debugger.getWriterListener() != null) {
                    packetWriter.addPacketListener(debugger.getWriterListener(), null);
                }
            }
            // Start the packet writer. This will open a XMPP stream to the server
            packetWriter.startup();
            // Start the packet reader. The startup() method will block until we
            // get an opening stream packet back from server.
            packetReader.startup();

            // Make note of the fact that we're now connected.
            connected = true;

            // Notify that a new connection has been established
            connectionEstablished(this);
        }
		catch (XMPPException ex)
		{
			// An exception occurred in setting up the connection. Make sure we shut down the
			// readers and writers and close the socket.

			if (packetWriter != null) {
				try { packetWriter.shutdown(); } catch (Throwable ignore) { }
				packetWriter = null;
			}
			if (packetReader != null) {
				try { packetReader.shutdown(); } catch (Throwable ignore) { }
				packetReader = null;
			}
			if (reader != null) {
				try { reader.close(); } catch (Throwable ignore) { }
				reader = null;
			}
			if (writer != null) {
				try { writer.close(); } catch (Throwable ignore) { }
				writer = null;
			}
			if (socket != null) {
				try { socket.close(); } catch (Exception e) { }
				socket = null;
			}
			authenticated = false;
			connected = false;

			throw ex;		// Everything stoppped. Now throw the exception.
		}
    }

    /**
     * Fires listeners on connection established events.
     */
    private static void connectionEstablished(XMPPConnection connection) {
        ConnectionEstablishedListener[] listeners = null;
        synchronized (connectionEstablishedListeners) {
            listeners = new ConnectionEstablishedListener[connectionEstablishedListeners.size()];
            connectionEstablishedListeners.toArray(listeners);
        }
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].connectionEstablished(connection);
        }
    }
}