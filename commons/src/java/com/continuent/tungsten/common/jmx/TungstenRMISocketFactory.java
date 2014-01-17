
package com.continuent.tungsten.common.jmx;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;

import org.apache.log4j.Logger;

public class TungstenRMISocketFactory extends RMISocketFactory
        implements
            Serializable
{
    /**
     * 
     */
    private static final long   serialVersionUID = 1L;

    private static final Logger logger           = Logger.getLogger(TungstenRMISocketFactory.class);

    private String              host             = null;
    private int                 start_port       = -1;
    private int                 end_port         = -1;

    public TungstenRMISocketFactory(String host, int start_port, int end_port)
    {
        this.host = host;
        this.start_port = start_port;
        this.end_port = end_port;
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException
    {
        if (port < start_port || port > end_port)
        {
            throw new IOException(String.format(
                    "Port %d is not in the range %d to %d", port, start_port,
                    end_port));
        }

        InetAddress addr = InetAddress.getByName(this.host);
        if (logger.isDebugEnabled())
        {
            logger.debug(String.format(
                    "createServerSocket(port=%d, backlog=0, address=%s)", port,
                    addr));
        }
        return new ServerSocket(port, 0, addr);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException
    {
        if (port < start_port || port > end_port)
        {
            throw new IOException(String.format(
                    "Port %d is not in the range %d to %", port, start_port,
                    end_port));
        }

        InetAddress addr = InetAddress.getByName(this.host);
        if (logger.isDebugEnabled())
        {
            logger.debug(String.format("createSocket(port=%d, address=%s)",
                    port, addr));
        }
        return new Socket(addr, port);

    }

    public String getHost()
    {
        return host;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public int getStart_port()
    {
        return start_port;
    }

    public void setStart_port(int start_port)
    {
        this.start_port = start_port;
    }

    public int getEnd_port()
    {
        return end_port;
    }

    public void setEnd_port(int end_port)
    {
        this.end_port = end_port;
    }
}