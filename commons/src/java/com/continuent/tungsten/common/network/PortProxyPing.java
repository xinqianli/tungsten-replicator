/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2011-2012 Continuent Inc.
 * Contact: tungsten@continuent.org
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of version 2 of the GNU General Public License as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 *
 * Initial developer(s): Robert Hodges
 * Contributor(s): 
 */

package com.continuent.tungsten.common.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Tests for reachability using the Java InetAddress.isReachable() method.
 * 
 * @author <a href="mailto:edward.archibald@continuent.com">Edward Archibald</a>
 */
public class PortProxyPing implements PingMethod
{
    private static Logger logger = Logger.getLogger(PortProxyPing.class);
    private String        notes;

    public static void main(String argv[])
    {
        logger.setLevel(Level.ALL);

        String serverHost = "localhost";
        int serverPort = 15002;
        int timeoutMillis = 2000;

        SimpleServer server = new SimpleServer(serverHost, serverPort);

        PingMethod pinger = new PortProxyPing();

        for (int i = 0; i < 10; i++)
        {
            try
            {
                System.out.println("PingResult="
                        + pinger.ping(serverHost, serverPort, timeoutMillis));

            }
            catch (Exception e)
            {
                System.out.println("Ping got an exception: " + e);
            }
        }

        serverHost = "hostdoesnotexist";
        serverPort = 15002;
        timeoutMillis = 2000;

        try
        {

            System.out.println("PingResult="
                    + pinger.ping(serverHost, serverPort, timeoutMillis));
        }
        catch (Exception e)
        {
            System.out.println("Ping got an exception: " + e);
        }

        serverHost = "localhost";
        serverPort = 99999;
        timeoutMillis = 2000;

        try
        {

            System.out.println("PingResult="
                    + pinger.ping(serverHost, serverPort, timeoutMillis));
        }
        catch (Exception e)
        {
            System.out.println("Ping got an exception: " + e);
        }

        serverHost = "localhost";
        serverPort = 23;
        timeoutMillis = 2000;

        try
        {

            System.out.println("PingResult="
                    + pinger.ping(serverHost, serverPort, timeoutMillis));
        }
        catch (Exception e)
        {
            System.out.println("Ping got an exception: " + e);
        }

        server.stop();

    }

    public boolean ping(String host, int port, int timeoutMillis)
            throws HostException
    {
        try
        {
            InetAddress inetAddr = InetAddress.getByName(host);
            return (ping(new HostAddress(inetAddr), port, timeoutMillis));
        }
        catch (UnknownHostException u)
        {
            if (logger.isTraceEnabled())
            {
                logger.trace(
                        String.format("Unable to resolve host %s, %s", host, u),
                        u);
            }

            return false;
        }

    }

    /**
     * Tries to get a connection to a port that is bound by a host port proxy
     * for loopback ports. An example of where this applies is in OpenShift
     * where each 'Gear' on a given compute 'Node' is compartmentalized via SE
     * Linux and other system properties and then assigned a unique IP address
     * in the loopback range on that 'Node'. Then the OpenShift framework uses
     * ha-proxy, on each 'Node' to allow for extra-Gear communications by
     * mapping a unique port in ha-proxy to a unique loopback:port combination
     * in the gear. Because of this setup, ha-proxy will have a bound port on
     * the 'Node' only when the Gear is actually active. With this technique, we
     * are counting on a relatively fast turnaround in the OpenShift framework
     * vis-a-vis removing a given port from ha-proxy if a gear is stopped etc.
     * 
     * @param address Host name
     * @param port proxy port to try to connect to
     * @param timeout Timeout in milliseconds
     * @return True if the port in question can be connected to, otherwise false
     */
    public boolean ping(HostAddress address, int port, int timeoutMillis)
            throws HostException
    {
        if (port <= 0)
        {
            throw new HostException(String.format(
                    "This method cannot be used with a port with value '%d'",
                    port));
        }

        if (logger.isTraceEnabled())
        {
            logger.trace(String.format("ping(%s, %d, %d)", address, port,
                    timeoutMillis));
        }

        notes = "Connects to loopback proxy";

        Socket socket = null;
        SocketAddress sockaddr = null;
        long afterHostResolution = 0;

        try
        {
            long beforeConnect = System.currentTimeMillis();

            try
            {
                /*
                 * The following call can block for many seconds, before
                 * returning, if the host specified is not up or doesn't exist.
                 * Be forewarned.
                 */
                sockaddr = new InetSocketAddress(address.getInetAddress(), port);

                afterHostResolution = System.currentTimeMillis();

                if (afterHostResolution - beforeConnect > timeoutMillis)
                {
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("Returning false because we timed out during host name resolution etc.");
                        return false;
                    }
                }
            }
            catch (Exception e)
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug(String
                            .format("Got an exception during host resolution: %s. Returning false.",
                                    e));
                }

                return false;
            }

            // Create the socket without connecting yet...
            socket = new Socket();

            // ... because we want to set a connection timeout first
            socket.setSoTimeout(timeoutMillis);

            /*
             * This can be called fairly frequently, and we want the FD used to
             * be reclaimed quickly.
             */
            socket.setReuseAddress(true);

            /*
             * Adjust the timeout to account for the host resolution.
             */
            int timeLimitForConnect = (int) (timeoutMillis - (afterHostResolution - beforeConnect));

            /*
             * Connect to the proxy port. The way that it works is that if the
             * proxy doesn't have an actual loopback-based port that it's
             * proxying, it will disconnect us pretty quickly.
             */
            socket.connect(sockaddr, timeLimitForConnect);

            long timeToConnectMs = System.currentTimeMillis() - beforeConnect;

            if (logger.isTraceEnabled())

            {
                logger.trace(String.format(
                        "Connection to %s:%d took %d ms out of %d limit",
                        address, port, timeToConnectMs, timeLimitForConnect));
            }

            if (timeToConnectMs > timeoutMillis)
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug(String
                            .format("Connect time of %d exceeded limit of %d. Returning false",
                                    timeToConnectMs, timeoutMillis));
                }

                return false;
            }

            if (logger.isTraceEnabled())
            {
                logger.trace("Returning true");
            }
            return true;

        }
        catch (IOException e)
        {
            if (logger.isTraceEnabled())
            {
                logger.trace(String.format(

                "Ping operation failed, exception=%s, Returning false", e));
            }
            return false;
        }
        finally
        {
            if (socket != null && !socket.isClosed())
            {
                try
                {
                    socket.close();
                }
                catch (Exception ignored)
                {

                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.common.network.PingMethod#getNotes()
     */
    public String getNotes()
    {
        return notes;
    }

    static private class SimpleServer implements Runnable
    {
        private ServerSocket serverSocket = null;
        private int          port         = -1;
        private boolean      running      = true;

        public SimpleServer(String host, int port)
        {
            this.port = port;
            start();
        }

        public void start()
        {
            (new Thread(this, "Server")).start();
        }

        public void stop()
        {
            System.out.println("Server has been stopped!");
            running = false;
        }

        @Override
        public void run()
        {

            int connectionsAccepted = 0;

            try
            {
                serverSocket = new ServerSocket(port);
                serverSocket.setSoTimeout(5000);
            }
            catch (IOException e)
            {
                System.out.println(String.format(
                        "Could not listen on port %d\nReason=%s", port, e));
                System.exit(1);
            }

            System.out.println("Listening on port " + port);

            try
            {
                Socket clientSocket = null;

                while (running)
                {

                    try
                    {
                        clientSocket = serverSocket.accept();
                    }
                    catch (SocketTimeoutException timeout)
                    {
                        if (logger.isTraceEnabled())
                        {
                            logger.trace("Accept timed out. Continuing...");
                            continue;
                        }
                    }
                    connectionsAccepted++;
                    System.out.println(String.format("%d accepted",
                            connectionsAccepted));
                    clientSocket.close();
                    System.out.println(String.format("%d closed",
                            connectionsAccepted));

                }

                System.out.println(String.format(
                        "Server exiting after accepting %s connections.",
                        connectionsAccepted));

                System.exit(0);
            }
            catch (Exception e)
            {
                System.out.println(String.format("Accept failed: %d", port));
                System.exit(-1);
            }
        }

    }
}