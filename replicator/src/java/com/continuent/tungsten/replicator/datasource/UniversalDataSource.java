/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2013 Continuent Inc.
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

package com.continuent.tungsten.replicator.datasource;

import com.continuent.tungsten.replicator.ReplicatorException;

/**
 * Denotes a generic data source that a replicator may connect at either end of
 * a pipeline. Data source implementions encapsulate the following data:
 * <ul>
 * <li>Replicator catalogs, which consist of a set of "tables" that hold
 * metadata used to control replication. Data sources may implement such tables
 * using relational tables, files, or any other suitable means.</li>
 * <li>Connection manager, which parcels out connections to the data source, be
 * this a JDBC connection, a MongoDB connection, a connection to HDFS, etc.</li>
 * </ul>
 * All data required for operation must be provided through property setters.
 * Data sources do not implement the ReplicatorPlugin lifecycle or access the
 * PluginContext implementation as this introduces dependencies that prevent
 * easy testing and hurt portability between store types.
 * 
 * @see com.continuent.tungsten.replicator.plugin.ReplicatorPlugin
 * @see com.continuent.tungsten.replicator.plugin.PluginContext
 */
public interface UniversalDataSource extends CatalogEntity
{
    /**
     * Set the name of the replicator service that is using this data source.
     */
    public void setServiceName(String serviceName);

    /**
     * Return the name of the replicator service that is using this data source.
     */
    public String getServiceName();

    /**
     * Set the number of channels to use when extracting from or applying to the
     * data source. This is the basic mechanism to support parallel replication.
     */
    public void setChannels(int channels);

    /**
     * Return the number of channels to track.
     */
    public int getChannels();

    /**
     * Returns a ready-to-use CommitSeqno instance for operations on commit
     * seqno data.
     */
    public CommitSeqno getCommitSeqno();

    /**
     * Returns a ready-to-use wrapped connection for operations on the data
     * source.
     */
    public UniversalConnection getConnection() throws ReplicatorException;

    /**
     * Releases a wrapped connection.
     */
    public void releaseConnection(UniversalConnection conn);
}