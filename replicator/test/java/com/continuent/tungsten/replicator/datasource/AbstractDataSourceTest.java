/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2013-2014 Continuent Inc.
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

import java.sql.Timestamp;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.junit.Test;

import com.continuent.tungsten.common.config.TungstenProperties;
import com.continuent.tungsten.replicator.ReplicatorException;
import com.continuent.tungsten.replicator.event.ReplDBMSHeader;
import com.continuent.tungsten.replicator.event.ReplDBMSHeaderData;

/**
 * Implements test cases that apply to any data source implementation.
 */
public class AbstractDataSourceTest
{
    private static Logger        logger            = Logger.getLogger(AbstractDataSourceTest.class);

    // Properties for data source test.
    protected TungstenProperties datasourceProps;
    protected String             datasourceClass;
    protected DataSourceManager  datasourceManager = new DataSourceManager();

    /**
     * Verify that after initialization the data source contents are available.
     */
    @Test
    public void testInitialization() throws Exception
    {
        if (!assertTestProperties())
            return;

        // Create a separate data source for this test.
        datasourceProps.setString("serviceName", "test_initialization");
        datasourceManager.add("testInitialization", datasourceClass,
                datasourceProps);

        // Get the data source and ensure tables are cleared.
        UniversalDataSource c = datasourceManager.find("testInitialization");
        c.clear();

        // Now initialize the tables.
        c.initialize();

        // Verify that we can find commit seqno data.
        CommitSeqno commitSeqno = c.getCommitSeqno();
        Assert.assertEquals("Looking for initialized commit seqno", -1,
                commitSeqno.minCommitSeqno().getSeqno());
    }

    /**
     * Verify that if we initialize a data source we can update the commit seqno
     * position and read the updated value back.
     */
    @Test
    public void testSeqno() throws Exception
    {
        if (!assertTestProperties())
            return;

        UniversalDataSource c = prepareCatalog("testSeqno");

        // Retrieve the initial data.
        UniversalConnection conn = c.getConnection();
        CommitSeqnoAccessor accessor = c.getCommitSeqno().createAccessor(0,
                conn);
        ReplDBMSHeader initial = accessor.lastCommitSeqno();
        Assert.assertNotNull("Expect non-null initial header", initial);
        Assert.assertEquals("Expected initial seqno", -1, initial.getSeqno());

        // Change the seqno and update.
        ReplDBMSHeaderData newHeader = new ReplDBMSHeaderData(4, (short) 2,
                true, "foo", 1, "someEvent#", "someShard", new Timestamp(
                        10000000), 25);
        accessor.updateLastCommitSeqno(newHeader, 30);

        // Retrieve the header and ensure values match.
        ReplDBMSHeader retrieved = accessor.lastCommitSeqno();
        Assert.assertEquals("Checking seqno", 4, retrieved.getSeqno());
        Assert.assertEquals("Checking fragno", 2, retrieved.getFragno());
        Assert.assertEquals("Checking lastFrag", true, retrieved.getLastFrag());
        Assert.assertEquals("Checking sourceId", "foo", retrieved.getSourceId());
        Assert.assertEquals("Checking epochNumber", 1,
                retrieved.getEpochNumber());
        Assert.assertEquals("Checking event ID", "someEvent#",
                retrieved.getEventId());
        Assert.assertEquals("Checking shard ID", "someShard",
                retrieved.getShardId());
        Assert.assertEquals("Checking extractedTstamp",
                new Timestamp(10000000), retrieved.getExtractedTstamp());
        Assert.assertEquals("Checking appliedLatency", 30,
                retrieved.getAppliedLatency());

        // Release resources and exit.
        c.releaseConnection(conn);
    }

    /**
     * Verify that we can allocate many accessors in succession to read and
     * update the commit seqno position.
     */
    @Test
    public void testSeqnoManyAccessors() throws Exception
    {
        if (!assertTestProperties())
            return;

        UniversalDataSource c = prepareCatalog("testSeqnoManyAccessors");
        CommitSeqno commitSeqno = c.getCommitSeqno();

        // Loop through many times.
        // TODO: Raise # to 10000.
        for (int i = 0; i < 100; i++)
        {
            if (i > 0 && (i % 1000) == 0)
                logger.info("Iteration: " + i);
            UniversalConnection conn = c.getConnection();
            CommitSeqnoAccessor accessor = commitSeqno.createAccessor(0, conn);

            // Check the last position updated.
            ReplDBMSHeader lastHeader = accessor.lastCommitSeqno();
            Assert.assertEquals("Checking seqno", i - 1, lastHeader.getSeqno());

            // Update the header to the current position.
            ReplDBMSHeaderData newHeader = new ReplDBMSHeaderData(i, (short) 2,
                    true, "foo", 1, "someEvent#", "someShard", new Timestamp(
                            10000000), 25);
            accessor.updateLastCommitSeqno(newHeader, 25);

            // Discard the accessor and connection.
            accessor.close();
            c.releaseConnection(conn);
        }
    }

    /**
     * Verify that the seqno is correctly stored and returned for each allocated
     * channel.
     */
    @Test
    public void testSeqnoChannels() throws Exception
    {
        if (!assertTestProperties())
            return;

        UniversalDataSource c = prepareCatalog("testSeqnoChannels");
        int channels = c.getChannels();
        UniversalConnection conn = c.getConnection();
        CommitSeqno commitSeqno = c.getCommitSeqno();
        CommitSeqnoAccessor[] accessors = new CommitSeqnoAccessor[channels];

        // Allocate accessor and update for each channel.
        for (int i = 0; i < channels; i++)
        {
            accessors[i] = commitSeqno.createAccessor(i, conn);
            ReplDBMSHeaderData header = new ReplDBMSHeaderData(i * 2,
                    (short) 0, true, "foo", 1, "someEvent#", "someShard",
                    new Timestamp(10000000), 25);
            accessors[i].updateLastCommitSeqno(header, 25);
        }

        // Read back stored header and deallocate accessor for each channel.
        for (int i = 0; i < channels; i++)
        {
            ReplDBMSHeader retrieved = accessors[i].lastCommitSeqno();
            Assert.assertEquals("Checking seqno: channel=" + i, i * 2,
                    retrieved.getSeqno());
            accessors[i].close();
        }

        // Release resources and exit.
        c.releaseConnection(conn);
    }

    /**
     * Verify that we can change channel number when the catalog is released and
     * restart the catalog without error. This test ensures that users do not
     * change channels unexpectedly during operations, which can cause serious
     * configuration errors.
     */
    @Test
    public void testChangingChannels() throws Exception
    {
        if (!assertTestProperties())
            return;

        // Start with 10 channels.
        this.datasourceProps.setInt("channels", 10);
        UniversalDataSource c = prepareCatalog("testChangingChannels");

        int channels = c.getChannels();
        Assert.assertEquals("Expect initial number of channels", 10, channels);

        // Shut down.
        datasourceManager.remove("testChangingChannels");

        // Start again with 20 channels.
        datasourceProps.setInt("channels", 20);
        UniversalDataSource c2 = prepareCatalog("testChangingChannels", false);

        int channels2 = c2.getChannels();
        Assert.assertEquals("Expect updated number of channels", 20, channels2);

        // Shut down.
        datasourceManager.remove("testChangingChannels");
    }

    /**
     * Verify that seqno values are persistent even if we allocate the data
     * source a second time.
     */
    @Test
    public void testSeqnoPersistence() throws Exception
    {
        if (!assertTestProperties())
            return;

        UniversalDataSource c = prepareCatalog("testSeqnoPersistence");
        int channels = c.getChannels();

        // Allocate accessor and update for each channel.
        CommitSeqno commitSeqno = c.getCommitSeqno();
        UniversalConnection conn = c.getConnection();
        for (int i = 0; i < channels; i++)
        {
            CommitSeqnoAccessor accessor = commitSeqno.createAccessor(i, conn);
            ReplDBMSHeaderData header = new ReplDBMSHeaderData(i * 2,
                    (short) 0, true, "foo", 1, "someEvent#", "someShard",
                    new Timestamp(10000000), 25);
            accessor.updateLastCommitSeqno(header, 25);
            accessor.close();
        }
        commitSeqno.release();

        // Close the data source and add a new one.
        c.release();
        datasourceManager.remove("testSeqnoPersistence");
        datasourceManager.add("testSeqnoPersistence", datasourceClass,
                datasourceProps);
        UniversalDataSource c2 = datasourceManager.find("testSeqnoPersistence");

        // Read back stored header and deallocate accessor for each channel.
        UniversalConnection conn2 = c2.getConnection();
        CommitSeqno commitSeqno2 = c2.getCommitSeqno();
        for (int i = 0; i < channels; i++)
        {
            CommitSeqnoAccessor accessor = commitSeqno2
                    .createAccessor(i, conn2);
            ReplDBMSHeader retrieved = accessor.lastCommitSeqno();
            Assert.assertEquals("Checking seqno: channel=" + i, i * 2,
                    retrieved.getSeqno());
            accessor.close();
        }
        commitSeqno.release();

        // Release resources and exit.
        c2.releaseConnection(conn);
    }

    /**
     * Prepares a data source and returns same to caller.
     */
    private UniversalDataSource prepareCatalog(String name, boolean clear)
            throws ReplicatorException, InterruptedException
    {
        datasourceProps.setString("serviceName", name);
        datasourceManager.add(name, datasourceClass, datasourceProps);

        // Get the data source and ensure tables are cleared.
        UniversalDataSource c = datasourceManager.find(name);
        if (clear)
        {
            c.clear();
        }
        c.initialize();

        return c;
    }

    /**
     * Convenience method to prepare catalog with automatic clearing of previous
     * data.
     */
    private UniversalDataSource prepareCatalog(String name)
            throws ReplicatorException, InterruptedException
    {
        return prepareCatalog(name, true);
    }

    // Returns false if the properties instance has not be set and test case
    // should return immediately.
    protected boolean assertTestProperties()
    {
        if (datasourceProps == null)
        {
            logger.warn("Data source properties are not defined; test case will not be run");
            return false;
        }
        else
            return true;
    }

}