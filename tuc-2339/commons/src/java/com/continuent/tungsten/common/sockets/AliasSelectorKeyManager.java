/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2007-2013 Continuent Inc.
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
 * Initial developer(s): http://alesaudate.wordpress.com/2010/08/09/how-to-dynamically-select-a-certificate-alias-when-invoking-web-services/
 * Contributors: Ludovic Launer
 */

package com.continuent.tungsten.common.sockets;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;

import javax.net.ssl.X509KeyManager;

import org.apache.log4j.Logger;

import com.continuent.tungsten.common.security.SecurityHelper;

/**
 * This class defines a AliasSelectorKeyManager
 * This enable the SSLSocketFactory class to select a particular alias in a Keystore
 * 
 * @author <a href="mailto:ludovic.launer@continuent.com">Ludovic Launer</a>
 * @version 1.0
 */
public class AliasSelectorKeyManager implements X509KeyManager
{
    private static final Logger logger           = Logger.getLogger(AliasSelectorKeyManager.class);

    private X509KeyManager      sourceKeyManager = null;
    private String              alias;

    public AliasSelectorKeyManager(X509KeyManager keyManager, String alias)
    {
        this.sourceKeyManager = keyManager;
        this.alias = alias;
    }
    
    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers,
            Socket socket)
    {
        return sourceKeyManager.chooseClientAlias(keyType, issuers, socket);
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket)
    {
        if (this.alias == null)
            return sourceKeyManager.chooseServerAlias(keyType, issuers, socket);
        else
        {
            boolean aliasFound = false;

            String[] validAliases = sourceKeyManager.getClientAliases(keyType,
                    issuers);
            if (validAliases != null)
            {
                for (int j = 0; j < validAliases.length && !aliasFound; j++)
                {
                    if (validAliases[j].equals(alias))
                        aliasFound = true;
                }
            }

            if (aliasFound)
                return alias;
            else
            {
                String keyStoreLocation = SecurityHelper.getKeyStoreLocation();
                logger.error(MessageFormat.format(
                        "Could not find alias: {0} in keystore: {1}",
                        this.alias, keyStoreLocation));
            }
        }

        return null;
    }

    public X509Certificate[] getCertificateChain(String alias)
    {
        return sourceKeyManager.getCertificateChain(alias);
    }

    public String[] getClientAliases(String keyType, Principal[] issuers)
    {
        return sourceKeyManager.getClientAliases(keyType, issuers);
    }

    public PrivateKey getPrivateKey(String alias)
    {

        return sourceKeyManager.getPrivateKey(alias);
    }

    public String[] getServerAliases(String keyType, Principal[] issuers)
    {
        return sourceKeyManager.getServerAliases(keyType, issuers);
    }

}