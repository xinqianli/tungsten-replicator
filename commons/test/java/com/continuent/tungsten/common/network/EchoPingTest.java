/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2010-2015 Continuent Inc.
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
 * Initial developer(s): Csaba Endre Simon
 * Contributor(s): 
 */

package com.continuent.tungsten.common.network;

import com.continuent.tungsten.common.config.TungstenProperties;
import com.continuent.tungsten.common.network.Echo.EchoStatus;
import com.continuent.tungsten.common.sockets.EchoServer;

import junit.framework.TestCase;

public class EchoPingTest extends TestCase
{
    // An IP address we hope is unknown. This is the IP address test range
    // 198.51.100.0/24 (aka TEST-NET-2 in RFC5735, used for sample code and
    // documentation). It should not be allocated.
    private static String  UNKNOWN_IP   = "198.51.100.100";

    // IP address of localhost
    private static String  LOCALHOST_IP = "127.0.0.1";

    // Echo port for testing
    private static int     PORT         = 11233;

    // Do not use SSL
    private static boolean NO_SSL       = false;

    // Default timeout 1000 ms
    private static int     TIMEOUT      = 1000;

    // Echo server
    private EchoServer     server;

    protected void setUp() throws Exception
    {
        server = new EchoServer(LOCALHOST_IP, PORT, NO_SSL);
        server.start();
    }

    protected void tearDown()
    {
        server.shutdown();
    }

    public void testEchoToLocalHost() throws Exception
    {
        TungstenProperties result = Echo.isReachable(LOCALHOST_IP, PORT,
                TIMEOUT);
        assertEquals("Can ping localhost", EchoStatus.OK,
                result.getObject(Echo.STATUS_KEY));
    }

    public void testEchoToUnknown() throws Exception
    {
        TungstenProperties result = Echo.isReachable(UNKNOWN_IP, PORT, TIMEOUT);
        assertEquals("Cannot ping unknown address",
                EchoStatus.SOCKET_CONNECT_TIMEOUT,
                result.getObject(Echo.STATUS_KEY));
    }

    // TODO implement a special echo server where the connect, send, receive
    // timeout, message received, message send is configurable and test
    // different scenarios
}
