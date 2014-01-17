
package com.continuent.tungsten.common.utils;

import java.net.InetAddress;
import java.net.ServerSocket;

public class BindPort
{

    public static void main(String[] args)
    {

        if (args.length < 2)
        {
            System.err.println("usage: bindPort <host> <port>");
            System.exit(1);
        }

        int iterations = 0;
        while (true)
        {
            String host = args[0];
            int port = Integer.parseInt(args[1]);

            try
            {
                InetAddress addr = InetAddress.getByName(host);
                new ServerSocket(port, 100, addr);
                System.out.println("listening on port " + port);
                sleep(Long.MAX_VALUE);

            }
            catch (Exception ex)
            {
                System.out.println(ex);
                System.exit(1);
            }
        }

    }

    public static void sleep(long milliseconds)
    {
        try
        {
            Thread.sleep(milliseconds);
        }
        catch (Exception ignored)
        {
        }

    }
}