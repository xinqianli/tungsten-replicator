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
import java.net.UnknownHostException;

import org.apache.log4j.Logger;

/**
 * Tests for reachability using the Java InetAddress.isReachable() method.
 * 
 * @author <a href="mailto:robert.hodges@continuent.com">Robert Hodges</a>
 */
public class InetAddressPing implements PingMethod
{
    private String        notes;
    private static Logger logger = Logger.getLogger(InetAddressPing.class);

    /**
     * Tests a host for reachability.
     * 
     * @param address Host name
     * @param timeout Timeout in milliseconds
     * @return True if host is reachable, otherwise false.
     */
    public boolean ping(HostAddress address, int port, int timeout)
            throws HostException
    {
        notes = "InetAddress.isReachable()";
        InetAddress inetAddress = address.getInetAddress();
        try
        {
            return inetAddress.isReachable(timeout);
        }
        catch (IOException e)
        {
            if (logger.isTraceEnabled())
            {
                logger.trace(String.format(
                        "Exception during reachable test. Returning false",
                        inetAddress, e));
            }
            return false;
        }
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
                logger.trace(String.format(
                        "Unable to resolve host %s, %s. Returning false", host,
                        u), u);
            }

            return false;
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
}