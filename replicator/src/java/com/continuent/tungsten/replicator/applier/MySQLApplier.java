/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2007-2014 Continuent Inc.
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
 * Initial developer(s): Teemu Ollakka
 * Contributor(s):Stephane Giron, Robert Hodges
 */

package com.continuent.tungsten.replicator.applier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

import org.apache.log4j.Logger;

import com.continuent.tungsten.replicator.ReplicatorException;
import com.continuent.tungsten.replicator.database.Column;
import com.continuent.tungsten.replicator.datatypes.MySQLUnsignedNumeric;
import com.continuent.tungsten.replicator.datatypes.Numeric;
import com.continuent.tungsten.replicator.dbms.LoadDataFileQuery;
import com.continuent.tungsten.replicator.dbms.OneRowChange.ColumnSpec;
import com.continuent.tungsten.replicator.dbms.OneRowChange.ColumnVal;
import com.continuent.tungsten.replicator.dbms.RowIdData;
import com.continuent.tungsten.replicator.event.DBMSEvent;
import com.continuent.tungsten.replicator.event.ReplDBMSHeader;
import com.continuent.tungsten.replicator.event.ReplOption;
import com.continuent.tungsten.replicator.event.ReplOptionParams;
import com.continuent.tungsten.replicator.extractor.mysql.SerialBlob;
import com.continuent.tungsten.replicator.plugin.PluginContext;

/**
 * Stub applier class that automatically constructs url from MySQL-specific
 * properties like host, port, and urlOptions.
 * 
 * @author <a href="mailto:robert.hodges@continuent.com">Robert Hodges</a>
 * @version 1.0
 */
public class MySQLApplier extends JdbcApplier
{
    private static Logger            logger                 = Logger.getLogger(MySQLApplier.class);

    protected String                 host                   = "localhost";
    protected int                    port                   = 3306;
    protected String                 urlOptions             = null;

    /**
     * If true the replicator is operating time zone unaware compatibility mode.
     * In this mode we set the JVM time zone to the host time zone and set the
     * MySQL session time zone to match the MySQL global time zone. This mode
     * requires extra clean-up at release time to ensure the JVM time zone is
     * set back correctly.
     */
    protected boolean                nonTzAwareMode         = false;

    /**
     * If true this applier will switch the replicator to time zone unaware
     * operation to apply events from a time zone unaware source. This is to
     * enable seamless upgrade of logs from older replicators.
     */
    protected boolean                supportNonTzAwareMode  = true;

    // Formatters for MySQL DATE, TIME, and DATETIME values.
    /**
     * Format DATE value according to MySQL expectations.
     */
    protected final SimpleDateFormat dateFormatter          = new SimpleDateFormat(
                                                                    "yyyy-MM-dd");
    /**
     * Format TIME value according to MySQL expectations.
     */
    protected final SimpleDateFormat timeFormatter          = new SimpleDateFormat(
                                                                    "HH:mm:ss");
    /**
     * Format MySQL DATETIME value according to MySQL expectations. The DATETIME
     * data type cannot change time zones or upgrade breaks.
     */
    protected final SimpleDateFormat mysqlDatetimeFormatter = new SimpleDateFormat(
                                                                    "yyyy-MM-dd HH:mm:ss");

    /**
     * Host name or IP address.
     */
    public void setHost(String host)
    {
        this.host = host;
    }

    /**
     * TCP/IP port number, a positive integer.
     */
    public void setPort(String portAsString)
    {
        this.port = Integer.parseInt(portAsString);
    }

    /**
     * JDBC URL options with a leading ?.
     */
    public void setUrlOptions(String urlOptions)
    {
        this.urlOptions = urlOptions;
    }

    /**
     * If set to true, time stamp-aware unaware events will be processed with
     * old replicator settings. If false, settings will not be altered.
     */
    public void setSupportNonTzAwareMode(boolean supportNonTzAwareMode)
    {
        this.supportNonTzAwareMode = supportNonTzAwareMode;
    }

    /**
     * Generate URL suitable for MySQL and then delegate remaining configuration
     * to superclass.
     * 
     * @see com.continuent.tungsten.replicator.plugin.ReplicatorPlugin#configure(PluginContext
     *      context)
     */
    public void configure(PluginContext context) throws ReplicatorException
    {
        if (url == null)
        {
            StringBuffer sb = new StringBuffer();
            sb.append("jdbc:mysql://");
            sb.append(host);
            if (port > 0)
            {
                sb.append(":");
                sb.append(port);
            }
            sb.append("/");
            if (context.getReplicatorSchemaName() != null)
                sb.append(context.getReplicatorSchemaName());
            if (urlOptions != null)
                sb.append(urlOptions);

            url = sb.toString();
        }
        else if (logger.isDebugEnabled())
            logger.debug("Property url already set; ignoring host and port properties");
        super.configure(context);
    }

    /**
     * Check to see if we have change in the time zone awareness of the event
     * and adjust time zone processing accordingly.
     */
    protected void checkEventCompatibility(ReplDBMSHeader header,
            DBMSEvent event) throws ReplicatorException
    {
        // If we don't support compatible operation with non-TZ aware events
        // there is nothing to do. Also, events without any metadata are empty
        // and are ignored.
        if (supportNonTzAwareMode == false)
            return;
        else if (event.getMetadata().size() == 0)
            return;

        // Compute time zone-awareness of the event.
        boolean timeZoneAwareEvent = event
                .getMetadataOptionValue(ReplOptionParams.TIME_ZONE_AWARE) != null;

        if (nonTzAwareMode)
        {
            // We are not enabled for TZ-aware operation. Check for a TZ-enabled
            // event.
            if (timeZoneAwareEvent)
            {
                // We are now processing a time zone-aware event.
                logger.info("Found a time zone-aware event while in non-TZ-aware mode: seqno="
                        + header.getSeqno());
                enableTzAwareMode();
            }
        }
        else
        {
            // We are enabled for TZ-aware operation. Check for an event that is
            // not time zone-aware.
            if (!timeZoneAwareEvent)
            {
                // We are now processing a time zone-unaware event.
                logger.info("Found a non-time zone-aware event while in TZ-aware mode: seqno="
                        + header.getSeqno());
                enableNonTzAwareMode();
            }
        }
    }

    /**
     * Reset formatters to replicator global time zone to time-zone aware
     * operation.
     */
    protected void enableTzAwareMode()
    {
        TimeZone replicatorTz = runtime.getReplicatorTimeZone();
        logger.info("Resetting time zones used for date-time to enable time zone-aware operation: new tz="
                + replicatorTz.getDisplayName());
        dateTimeFormatter.setTimeZone(replicatorTz);
        dateFormatter.setTimeZone(replicatorTz);
        timeFormatter.setTimeZone(replicatorTz);
        // Do not alter the formatter for MySQL DATETIME type.
        nonTzAwareMode = false;
    }

    /**
     * Initiate non-time zone-ware operation, which imitates operation of old
     * prior to the time zone fix.
     */
    protected void enableNonTzAwareMode() throws ReplicatorException
    {
        // Set the session time_zone back to the global time zone;
        logger.info("Resetting MySQL session time zone back to global value");
        String sql = "set session time_zone=@@global.time_zone";
        try
        {
            conn.execute(sql);
        }
        catch (SQLException e)
        {
            throw new ReplicatorException(
                    "Unable to reset MySQL session time_zone: sql=" + sql
                            + " message=" + e.getLocalizedMessage());
        }

        // Set the time zone to the host time zone.
        TimeZone hostTz = runtime.getHostTimeZone();
        logger.info("Resetting time zones used for date-time to enable non-time zone-aware operation: new tz="
                + hostTz.getDisplayName());
        dateTimeFormatter.setTimeZone(hostTz);
        dateFormatter.setTimeZone(hostTz);
        timeFormatter.setTimeZone(hostTz);
        // Do not alter the formatter for MySQL DATETIME type.
        nonTzAwareMode = true;
    }

    protected void applyRowIdData(RowIdData data) throws ReplicatorException
    {
        String query = "SET ";

        switch (data.getType())
        {
            case RowIdData.LAST_INSERT_ID :
                query += "LAST_INSERT_ID";
                break;
            case RowIdData.INSERT_ID :
                query += "INSERT_ID";
                break;
            default :
                // Old behavior
                query += "INSERT_ID";
                break;
        }
        query += " = " + data.getRowId();

        try
        {
            try
            {
                statement.execute(query);
            }
            catch (SQLWarning e)
            {
                String msg = "While applying SQL event:\n" + data.toString()
                        + "\nWarning: " + e.getMessage();
                logger.warn(msg);
            }
            statement.clearBatch();

            if (logger.isDebugEnabled())
            {
                logger.debug("Applied event: " + query);
            }
        }
        catch (SQLException e)
        {
            logFailedStatementSQL(query, e);
            throw new ApplierException(e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.applier.JdbcApplier#addColumn(java.sql.ResultSet,
     *      java.lang.String)
     */
    @Override
    protected Column addColumn(ResultSet rs, String columnName)
            throws SQLException
    {
        String typeDesc = rs.getString("TYPE_NAME").toUpperCase();
        boolean isSigned = !typeDesc.contains("UNSIGNED");
        int dataType = rs.getInt("DATA_TYPE");

        if (logger.isDebugEnabled())
            logger.debug("Adding column " + columnName + " (TYPE " + dataType
                    + " - " + (isSigned ? "SIGNED" : "UNSIGNED") + ")");

        Column column = new Column(columnName, dataType, false, isSigned);
        column.setTypeDescription(typeDesc);
        column.setLength(rs.getLong("column_size"));

        return column;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.applier.JdbcApplier#setObject(java.sql.PreparedStatement,
     *      int, com.continuent.tungsten.replicator.dbms.OneRowChange.ColumnVal,
     *      com.continuent.tungsten.replicator.dbms.OneRowChange.ColumnSpec)
     */
    @Override
    protected void setObject(PreparedStatement prepStatement, int bindLoc,
            ColumnVal value, ColumnSpec columnSpec) throws SQLException
    {

        int type = columnSpec.getType();

        if (type == Types.TIMESTAMP && value.getValue() instanceof Integer)
        {
            prepStatement.setInt(bindLoc, 0);
        }
        else if (type == Types.DATE && value.getValue() instanceof Integer)
        {
            prepStatement.setInt(bindLoc, 0);
        }
        else if (type == Types.INTEGER)
        {
            Object valToInsert = null;
            Numeric numeric = new Numeric(columnSpec, value);
            if (columnSpec.isUnsigned() && numeric.isNegative())
            {
                valToInsert = MySQLUnsignedNumeric
                        .negativeToMeaningful(numeric);
                setInteger(prepStatement, bindLoc, valToInsert);
            }
            else
                prepStatement.setObject(bindLoc, value.getValue());
        }
        else if (type == java.sql.Types.BLOB
                && value.getValue() instanceof SerialBlob)
        {
            SerialBlob val = (SerialBlob) value.getValue();
            prepStatement
                    .setBytes(bindLoc, val.getBytes(1, (int) val.length()));
        }
        else
            prepStatement.setObject(bindLoc, value.getValue());
    }

    protected void setInteger(PreparedStatement prepStatement, int bindLoc,
            Object valToInsert) throws SQLException
    {
        prepStatement.setObject(bindLoc, valToInsert);
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.applier.JdbcApplier#applyStatementData(com.continuent.tungsten.replicator.dbms.StatementData)
     */
    @Override
    protected void applyLoadDataLocal(LoadDataFileQuery data, File temporaryFile)
            throws ReplicatorException
    {
        try
        {
            int[] updateCount;
            String schema = data.getDefaultSchema();
            Long timestamp = data.getTimestamp();
            List<ReplOption> options = data.getOptions();

            applyUseSchema(schema);

            applySetTimestamp(timestamp);

            applySessionVariables(options);

            try
            {
                updateCount = statement.executeBatch();
            }
            catch (SQLWarning e)
            {
                String msg = "While applying SQL event:\n" + data.toString()
                        + "\nWarning: " + e.getMessage();
                logger.warn(msg);
                updateCount = new int[1];
                updateCount[0] = statement.getUpdateCount();
            }
            statement.clearBatch();
        }
        catch (SQLException e)
        {
            logFailedStatementSQL(data.getQuery(), e);
            throw new ApplierException(e);
        }

        try
        {
            FileInputStream fis = new FileInputStream(temporaryFile);
            ((com.mysql.jdbc.Statement) statement)
                    .setLocalInfileInputStream(fis);

            int cnt = statement.executeUpdate(data.getQuery());

            if (logger.isDebugEnabled())
                logger.debug("Applied event (update count " + cnt + "): "
                        + data.toString());
        }
        catch (SQLException e)
        {
            logFailedStatementSQL(data.getQuery(), e);
            throw new ApplierException(e);
        }
        catch (FileNotFoundException e)
        {
            logFailedStatementSQL(data.getQuery());
            throw new ApplierException(e);
        }
        finally
        {
            ((com.mysql.jdbc.Statement) statement)
                    .setLocalInfileInputStream(null);
        }

        // Clean up the temp file as we may not get a delete file event.
        if (logger.isDebugEnabled())
        {
            logger.debug("Deleting temp file: "
                    + temporaryFile.getAbsolutePath());
        }
        temporaryFile.delete();
    }

    private static final char[] hexArray = "0123456789abcdef".toCharArray();

    protected String hexdump(byte[] buffer)
    {
        char[] hexChars = new char[buffer.length * 2];
        for (int j = 0; j < buffer.length; j++)
        {
            int v = buffer[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

}