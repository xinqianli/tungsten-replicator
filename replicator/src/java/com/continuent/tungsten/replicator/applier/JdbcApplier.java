/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2007-2011 Continuent Inc.
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
 * Contributor(s): Robert Hodges, Scott Martin, Seppo Jaakola, Linas Virbalas, Stephane Giron
 */

package com.continuent.tungsten.replicator.applier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.continuent.tungsten.replicator.ReplicatorException;
import com.continuent.tungsten.replicator.conf.ReplicatorRuntime;
import com.continuent.tungsten.replicator.consistency.ConsistencyCheck;
import com.continuent.tungsten.replicator.consistency.ConsistencyCheckFactory;
import com.continuent.tungsten.replicator.consistency.ConsistencyException;
import com.continuent.tungsten.replicator.consistency.ConsistencyTable;
import com.continuent.tungsten.replicator.database.Column;
import com.continuent.tungsten.replicator.database.Database;
import com.continuent.tungsten.replicator.database.DatabaseFactory;
import com.continuent.tungsten.replicator.database.MySQLOperationMatcher;
import com.continuent.tungsten.replicator.database.SqlOperation;
import com.continuent.tungsten.replicator.database.SqlOperationMatcher;
import com.continuent.tungsten.replicator.database.Table;
import com.continuent.tungsten.replicator.database.TableMetadataCache;
import com.continuent.tungsten.replicator.dbms.DBMSData;
import com.continuent.tungsten.replicator.dbms.LoadDataFileDelete;
import com.continuent.tungsten.replicator.dbms.LoadDataFileFragment;
import com.continuent.tungsten.replicator.dbms.LoadDataFileQuery;
import com.continuent.tungsten.replicator.dbms.OneRowChange;
import com.continuent.tungsten.replicator.dbms.OneRowChange.ColumnSpec;
import com.continuent.tungsten.replicator.dbms.RowChangeData;
import com.continuent.tungsten.replicator.dbms.RowIdData;
import com.continuent.tungsten.replicator.dbms.StatementData;
import com.continuent.tungsten.replicator.event.DBMSEmptyEvent;
import com.continuent.tungsten.replicator.event.DBMSEvent;
import com.continuent.tungsten.replicator.event.ReplDBMSEvent;
import com.continuent.tungsten.replicator.event.ReplDBMSFilteredEvent;
import com.continuent.tungsten.replicator.event.ReplDBMSHeader;
import com.continuent.tungsten.replicator.event.ReplOption;
import com.continuent.tungsten.replicator.event.ReplOptionParams;
import com.continuent.tungsten.replicator.heartbeat.HeartbeatTable;
import com.continuent.tungsten.replicator.plugin.PluginContext;
import com.continuent.tungsten.replicator.thl.CommitSeqnoTable;
import com.continuent.tungsten.replicator.thl.THLManagerCtrl;

/**
 * Implements a DBMS implementation-independent applier. DBMS-specific features
 * must be subclassed. This applier can be used directly by specifying the DBMS
 * driver and full JDBC URL.
 * 
 * @author <a href="mailto:teemu.ollakka@continuent.com">Teemu Ollakka</a>
 * @version 1.0
 */
public class JdbcApplier implements RawApplier
{
    static Logger                     logger               = Logger.getLogger(JdbcApplier.class);

    protected int                     taskId               = 0;
    protected ReplicatorRuntime       runtime              = null;
    protected String                  driver               = null;
    protected String                  url                  = null;
    protected String                  user                 = "root";
    protected String                  password             = "rootpass";
    protected String                  ignoreSessionVars    = null;

    protected String                  metadataSchema       = null;
    protected String                  consistencyTable     = null;
    protected String                  consistencySelect    = null;
    protected Database                conn                 = null;
    protected Statement               statement            = null;
    protected Pattern                 ignoreSessionPattern = null;

    protected CommitSeqnoTable        commitSeqnoTable     = null;
    protected HeartbeatTable          heartbeatTable       = null;

    protected String                  lastSessionId        = "";

    // Values of schema and timestamp which are buffered to avoid
    // unnecessary commands on the SQL connection.
    protected String                  currentSchema        = null;
    protected long                    currentTimestamp     = -1;

    // Statistics.
    protected long                    eventCount           = 0;
    protected long                    commitCount          = 0;

    /**
     * Maximum length of SQL string to log in case of an error. This is needed
     * because some statements may be very large. TODO: make this configurable
     * via replicator.properties
     */
    private int                       maxSQLLogLength      = 1000;

    private TableMetadataCache        tableMetadataCache;

    private boolean                   transactionStarted   = false;

    private ReplDBMSHeader            lastProcessedEvent   = null;

    private Hashtable<Integer, File>  fileTable;

    protected HashMap<String, String> currentOptions;

    // SQL parser.
    SqlOperationMatcher               sqlMatcher           = new MySQLOperationMatcher();

    // Setters.

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.applier.RawApplier#setTaskId(int)
     */
    public void setTaskId(int id) throws ApplierException
    {
        this.taskId = id;
        logger.info("Set task id: id=" + taskId);
    }

    public void setDriver(String driver)
    {
        this.driver = driver;
    }

    public Database getDatabase()
    {
        return conn;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public void setUser(String user)
    {
        this.user = user;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public void setIgnoreSessionVars(String ignoreSessionVars)
    {
        this.ignoreSessionVars = ignoreSessionVars;
    }

    /**
     * Trim whitespace. Needed, because of different DBMS policies on returning
     * trailing whitespace from char(x) fields (PostgreSQL) or not (MySQL).
     */
    private String trim(String value)
    {
        if (value != null)
            return value.trim();
        else
            return value;
    }

    /**
     * Compares consistency check values obtained on master and here.
     * 
     * @param where Where clause for consistency check
     * @throws SQLException
     * @throws ConsistencyException
     */
    private void consistencyCheck(String where) throws SQLException,
            ConsistencyException
    {
        String schemaName = null;
        String tableName = null;
        int id = -1;
        int rowOffset = ConsistencyTable.ROW_UNSET;
        int rowLimit = ConsistencyTable.ROW_UNSET;
        String method;
        int this_cnt = 0, master_cnt;
        String this_crc = null, master_crc;

        String select = consistencySelect + where;
        logger.debug("ConsistencyTable row SELECT: " + select);

        ResultSet res = null;
        ResultSet ccres = null;
        try
        {
            res = statement.executeQuery(select);

            if (res.next())
            {
                // Get consistency check parameters:
                schemaName = trim(res.getString(ConsistencyTable.dbColumnName));
                tableName = trim(res.getString(ConsistencyTable.tblColumnName));
                id = res.getInt(ConsistencyTable.idColumnName);
                master_cnt = res.getInt(ConsistencyTable.masterCntColumnName);
                // logger.info("master_cnt: " + master_cnt);
                master_crc = trim(res
                        .getString(ConsistencyTable.masterCrcColumnName));
                // logger.info("master_crc: " + master_crc);
                rowOffset = res.getInt(ConsistencyTable.offsetColumnName);
                rowLimit = res.getInt(ConsistencyTable.limitColumnName);
                method = trim(res.getString(ConsistencyTable.methodColumnName));

                // get checked table definition
                Table table = conn.findTable(schemaName, tableName);

                if (table == null)
                    throw new ConsistencyException("Table not found: "
                            + schemaName + "." + tableName);

                // re-create consistency check
                ConsistencyCheck cc = ConsistencyCheckFactory
                        .createConsistencyCheck(id, table, rowOffset, rowLimit,
                                method,
                                runtime.isConsistencyCheckColumnNames(),
                                runtime.isConsistencyCheckColumnTypes());
                logger.debug("Got consistency check: " + cc.toString());

                // perform local consistency check
                ccres = cc.performConsistencyCheck(conn);
                if (ccres.next())
                {
                    this_cnt = ccres.getInt(ConsistencyTable.thisCntColumnName);
                    // logger.info("this_cnt : " + this_cnt);
                    this_crc = trim(ccres
                            .getString(ConsistencyTable.thisCrcColumnName));
                    // logger.info("this_crc : " + this_crc);

                    // record local values in the consistency table
                    StringBuffer update = new StringBuffer(256);
                    update.append("UPDATE ");
                    update.append(consistencyTable);
                    update.append(" SET ");
                    update.append(ConsistencyTable.thisCntColumnName);
                    update.append(" = ");
                    update.append(this_cnt);
                    update.append(", ");
                    update.append(ConsistencyTable.thisCrcColumnName);
                    update.append(" = '");
                    update.append(this_crc);
                    update.append("' ");
                    update.append(where);
                    logger.debug(update.toString());
                    statement.executeUpdate(update.toString());
                }
                else
                {
                    String msg = "Consistency check returned empty ResultSet.";
                    logger.warn(msg);
                }
            }
            else
            {
                throw new ConsistencyException(
                        "Failed to retrieve consistency check result.");
            }
        }
        finally
        {
            if (res != null)
            {
                res.close();
            }
            if (ccres != null)
            {
                ccres.close();
            }
        }

        if (master_cnt == this_cnt)
        {
            if (0 == master_crc.compareTo(this_crc))
            {
                String msg = "Consistency check succeeded on table '"
                        + schemaName + "." + tableName + "' id: " + id
                        + ", offset: " + rowOffset + ", limit: " + rowLimit
                        + ", method: '" + method + "' succeeded:"
                        + "\nthis_cnt  : " + this_cnt + "\nmaster_cnt: "
                        + master_cnt + "\nthis_crc  : " + this_crc
                        + "\nmaster_crc: " + master_crc;
                logger.info(msg);
                return;
            }
        }

        String msg = "Consistency check failed on table '" + schemaName + "."
                + tableName + "' id: " + id + ", offset: " + rowOffset
                + ", limit: " + rowLimit + ", method: '" + method + "' failed:"
                + "\nthis_cnt  : " + this_cnt + "\nmaster_cnt: " + master_cnt
                + "\nthis_crc  : " + this_crc + "\nmaster_crc: " + master_crc;

        throw new ConsistencyException(msg);
    }

    enum PrintMode
    {
        ASSIGNMENT, NAMES_ONLY, VALUES_ONLY, PLACE_HOLDER
    }

    /**
     * @param keyValues Is used to identify NULL values, in which case, if the
     *            mode is ASSIGNMENT, "x IS ?" is constructed instead of "x =
     *            ?".
     */
    protected void printColumnSpec(StringBuffer stmt,
            ArrayList<OneRowChange.ColumnSpec> cols,
            ArrayList<OneRowChange.ColumnVal> keyValues,
            ArrayList<OneRowChange.ColumnVal> colValues, PrintMode mode,
            String separator)
    {
        boolean first = true;
        for (int i = 0; i < cols.size(); i++)
        {
            OneRowChange.ColumnSpec col = cols.get(i);
            if (!first)
                stmt.append(separator);
            else
                first = false;
            if (mode == PrintMode.ASSIGNMENT)
            {
                if (keyValues != null)
                {
                    if (keyValues.get(i).getValue() == null)
                    {
                        // TREP-276: use "IS NULL" vs. "= NULL"
                        // stmt.append(col.getName() + " IS ? ");
                        // Oracle cannot handle "IS ?" and then binding a NULL
                        // value. It needs
                        // an explicit "IS NULL".
                        stmt.append(conn.getDatabaseObjectName(col.getName())
                                + " IS NULL ");
                    }
                    else
                    {
                        stmt.append(conn.getDatabaseObjectName(col.getName())
                                + " = "
                                + conn.getPlaceHolder(col, keyValues.get(i)
                                        .getValue(), col.getTypeDescription()));
                    }
                }
                else
                    stmt.append(conn.getDatabaseObjectName(col.getName())
                            + " = "
                            + conn.getPlaceHolder(col, colValues.get(i)
                                    .getValue(), col.getTypeDescription()));
            }
            else if (mode == PrintMode.PLACE_HOLDER)
            {
                stmt.append(conn.getPlaceHolder(col, colValues.get(i)
                        .getValue(), col.getTypeDescription()));
            }
            else if (mode == PrintMode.NAMES_ONLY)
            {
                stmt.append(conn.getDatabaseObjectName(col.getName()));
            }
        }
    }

    /**
     * Queries database for column names of a table that OneRowChange is
     * affecting. Fills in column names and key names for the given
     * OneRowChange.
     * 
     * @param data
     * @return Number of columns that a table has. Zero, if no columns were
     *         retrieved (table does not exist or has no columns).
     * @throws SQLException
     */
    protected int fillColumnNames(OneRowChange data) throws SQLException
    {
        Table t = tableMetadataCache.retrieve(data.getSchemaName(), data
                .getTableName());
        if (t == null)
        {
            // Not yet in cache
            t = new Table(data.getSchemaName(), data.getTableName());
            DatabaseMetaData meta = conn.getDatabaseMetaData();
            ResultSet rs = null;

            try
            {
                rs = conn.getColumnsResultSet(meta, data.getSchemaName(),
                        data.getTableName());
                while (rs.next())
                {
                    String columnName = rs.getString("COLUMN_NAME");
                    int columnIdx = rs.getInt("ORDINAL_POSITION");

                    Column column = addColumn(rs, columnName);
                    column.setPosition(columnIdx);
                    t.AddColumn(column);
                }
                tableMetadataCache.store(t);
            }
            finally
            {
                if (rs != null)
                {
                    rs.close();
                }
            }
        }

        // Set column names.
        for (Column column : t.getAllColumns())
        {
            ListIterator<OneRowChange.ColumnSpec> litr = data.getColumnSpec()
                    .listIterator();
            for (; litr.hasNext();)
            {
                OneRowChange.ColumnSpec cv = litr.next();
                if (cv.getIndex() == column.getPosition())
                {
                    cv.setName(column.getName());
                    cv.setSigned(column.isSigned());
                    cv.setTypeDescription(column.getTypeDescription());

                    // Check whether column is real blob on the applier side
                    if (cv.getType() == Types.BLOB)
                        cv.setBlob(column.isBlob());

                    break;
                }
            }

            litr = data.getKeySpec().listIterator();
            for (; litr.hasNext();)
            {
                OneRowChange.ColumnSpec cv = litr.next();
                if (cv.getIndex() == column.getPosition())
                {
                    cv.setName(column.getName());
                    cv.setSigned(column.isSigned());
                    cv.setTypeDescription(column.getTypeDescription());

                    // Check whether column is real blob on the applier side
                    if (cv.getType() == Types.BLOB)
                        cv.setBlob(column.isBlob());

                    break;
                }
            }

        }
        return t.getColumnCount();
    }

    /**
     * Returns a new column definition.
     * 
     * @param rs Metadata resultset
     * @param columnName Name of the column to be added
     * @return the column definition
     * @throws SQLException if an error occurs
     */
    protected Column addColumn(ResultSet rs, String columnName)
            throws SQLException
    {
        return new Column(columnName, rs.getInt("DATA_TYPE"));
    }

    protected int bindColumnValues(PreparedStatement prepStatement,
            ArrayList<OneRowChange.ColumnVal> values, int startBindLoc,
            ArrayList<OneRowChange.ColumnSpec> specs, boolean skipNulls)
            throws SQLException
    {
        int bindLoc = startBindLoc; /*
                                     * prepared stmt variable index starts from
                                     * 1
                                     */

        for (int idx = 0; idx < values.size(); idx++)
        {
            OneRowChange.ColumnVal value = values.get(idx);
            if (value.getValue() == null)
            {
                if (skipNulls)
                    continue;
                if (conn.nullsBoundDifferently(specs.get(idx)))
                    continue;
            }
            setObject(prepStatement, bindLoc, value, specs.get(idx));

            bindLoc += 1;
        }
        return bindLoc;
    }

    protected void setObject(PreparedStatement prepStatement, int bindLoc,
            OneRowChange.ColumnVal value, ColumnSpec columnSpec)
            throws SQLException
    {
        // By default, type is not used. If specific operations have to be done,
        // this should happen in specific classes (e.g. OracleApplier).
        prepStatement.setObject(bindLoc, value.getValue());
    }

    protected void applyRowIdData(RowIdData data) throws ApplierException
    {
        logger.warn("No applier for rowid data specified");
    }

    protected void applyStatementData(StatementData data)
            throws ApplierException
    {
        /*
         * TODO: This was mentioned in code review about batch updates: if one
         * statement fails you may get a situation where a partial batch commits
         * and statements after the failure will not be recorded. Need to
         * investigate if this is the case and fix applying code below
         * accordingly.
         */
        try
        {
            int[] updateCount = null;
            String schema = data.getDefaultSchema();
            Long timestamp = data.getTimestamp();
            List<ReplOption> options = data.getOptions();

            applyUseSchema(schema);

            applySetTimestamp(timestamp);

            applySessionVariables(options);

            if (data.getQuery() != null)
                statement.addBatch(data.getQuery());
            else
                statement.addBatch(new String(data.getQueryAsBytes()));

            statement.setEscapeProcessing(false);
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
            catch (SQLException e)
            {
                if (data.getErrorCode() == 0)
                {
                    SQLException sqlException = new SQLException(
                            "Statement failed on slave but succeeded on master");
                    sqlException.initCause(e);
                    throw sqlException;
                }
                // Check if the query produced the same error on master
                if (e.getErrorCode() == data.getErrorCode())
                {
                    logger.info("Ignoring statement failure as it also failed "
                            + "on master with the same error code ("
                            + e.getErrorCode() + ")");
                }
                else
                {
                    SQLException sqlException = new SQLException(
                            "Statement failed on slave with error code "
                                    + e.getErrorCode()
                                    + " but failed on master with a different one ("
                                    + data.getErrorCode() + ")");
                    sqlException.initCause(e);
                    throw sqlException;
                }
            }

            while (statement.getMoreResults())
            {
                statement.getResultSet();
            }
            statement.clearBatch();

            if (logger.isDebugEnabled() && updateCount != null)
            {
                int cnt = 0;
                for (int i = 0; i < updateCount.length; cnt += updateCount[i], i++)
                    ;

                logger.debug("Applied event (update count " + cnt + "): "
                        + data.toString());
            }
        }
        catch (SQLException e)
        {
            logFailedStatementSQL(data.getQuery(), e);
            throw new ApplierException(e);
        }
    }

    /**
     * applySetTimestamp adds to the batch the query used to change the server
     * timestamp, if needed and if possible (if the database support such a
     * feature)
     * 
     * @param timestamp the timestamp to be used
     * @throws SQLException if an error occurs
     */
    protected void applySetTimestamp(Long timestamp) throws SQLException
    {
        if (timestamp != null && conn.supportsControlTimestamp())
        {
            if (timestamp.longValue() != currentTimestamp)
            {
                currentTimestamp = timestamp.longValue();
                statement.addBatch(conn.getControlTimestampQuery(timestamp));
            }
        }
    }

    /**
     * applySetUseSchema adds to the batch the query used to change the current
     * schema where queries should be executed, if needed and if possible (if
     * the database support such a feature)
     * 
     * @param schema the schema to be used
     * @throws SQLException if an error occurs
     */
    protected void applyUseSchema(String schema) throws SQLException
    {
        if (schema != null && schema.length() > 0
                && !schema.equals(this.currentSchema))
        {
            currentSchema = schema;
            if (conn.supportsUseDefaultSchema())
                statement.addBatch(conn.getUseSchemaQuery(schema));
        }
    }

    /**
     * applyOptionsToStatement adds to the batch queries used to change the
     * connection options, if needed and if possible (if the database support
     * such a feature)
     * 
     * @param options
     * @return true if any option changed
     * @throws SQLException
     */
    protected boolean applySessionVariables(List<ReplOption> options)
            throws SQLException
    {
        boolean sessionVarChange = false;

        if (options != null && conn.supportsSessionVariables())
        {
            if (currentOptions == null)
                currentOptions = new HashMap<String, String>();

            for (ReplOption statementDataOption : options)
            {
                // if option already exists and have the same value, skip it
                // Otherwise, we need to set it on the current connection
                String optionName = statementDataOption.getOptionName();
                String optionValue = statementDataOption.getOptionValue();

                // Ignore internal Tungsten options.
                if (optionName
                        .startsWith(ReplOptionParams.INTERNAL_OPTIONS_PREFIX))
                    continue;

                // If we are ignoring this option, just continue.
                if (ignoreSessionPattern != null)
                {
                    if (ignoreSessionPattern.matcher(optionName).matches())
                    {
                        if (logger.isDebugEnabled())
                            logger.debug("Ignoring session variable: "
                                    + optionName);
                        continue;
                    }
                }

                if (optionName.equals(StatementData.CREATE_OR_DROP_DB))
                {
                    // Clearing current used schema, so that it will force a new
                    // "use" statement to be issued for the next query
                    currentSchema = null;
                    continue;
                }

                String currentOptionValue = currentOptions.get(optionName);
                if (currentOptionValue == null
                        || !currentOptionValue.equalsIgnoreCase(optionValue))
                {
                    String optionSetStatement = conn.prepareOptionSetStatement(
                            optionName, optionValue);
                    if (optionSetStatement != null)
                    {
                        if (logger.isDebugEnabled())
                            logger.debug("Issuing " + optionSetStatement);
                        statement.addBatch(optionSetStatement);
                    }
                    currentOptions.put(optionName, optionValue);
                    sessionVarChange = true;
                }
            }
        }
        return sessionVarChange;
    }

    protected void logFailedStatementSQL(String sql)
    {
        logFailedStatementSQL(sql, null);
    }

    /**
     * Logs SQL into error log stream. Trims the message if it exceeds
     * maxSQLLogLength.<br/>
     * In addition, extracts and logs next exception of the SQLException, if
     * available. This extends logging detail that is provided by general
     * exception logging mechanism.
     * 
     * @see #maxSQLLogLength
     * @param sql the sql statement to be logged
     */
    protected void logFailedStatementSQL(String sql, SQLException ex)
    {
        try
        {
            String log = "Statement failed: " + sql;
            if (log.length() > maxSQLLogLength)
                log = log.substring(0, maxSQLLogLength);
            logger.error(log);

            // Sometimes there's more details to extract from the exception.
            if (ex != null && ex.getCause() != null
                    && ex.getCause() instanceof SQLException)
            {
                SQLException nextException = ((SQLException) ex.getCause())
                        .getNextException();
                if (nextException != null)
                {
                    logger.error(nextException.getMessage());
                }
            }
        }
        catch (Exception e)
        {
            logger.debug("logFailedStatementSQL failed to log, because: "
                    + e.getMessage());
        }
    }

    /**
     * Constructs a SQL statement template later used for prepared statement.
     * 
     * @param action INSERT/UPDATE/DELETE
     * @param schemaName Database name to work on.
     * @param tableName Table name to work on.
     * @param columns Columns to INSERT/UPDATE.
     * @param keys Columns to search on.
     * @param keyValues Column values that are being searched for. Used for
     *            identifying NULL values and constructing "x IS NULL" instead
     *            of "x = NULL". May be null, in which case "x = NULL" is always
     *            used.
     * @return Constructed SQL statement with "?" instead of real values.
     */
    private StringBuffer constructStatement(RowChangeData.ActionType action,
            String schemaName, String tableName,
            ArrayList<OneRowChange.ColumnSpec> columns,
            ArrayList<OneRowChange.ColumnSpec> keys,
            ArrayList<OneRowChange.ColumnVal> keyValues,
            ArrayList<OneRowChange.ColumnVal> colValues)
    {
        StringBuffer stmt = new StringBuffer();
        if (action == RowChangeData.ActionType.INSERT)
        {
            stmt.append("INSERT INTO ");
            stmt.append(conn.getDatabaseObjectName(schemaName) + "."
                    + conn.getDatabaseObjectName(tableName));
            stmt.append(" ( ");
            printColumnSpec(stmt, columns, null, colValues,
                    PrintMode.NAMES_ONLY, " , ");
            stmt.append(" ) ");
            stmt.append(" VALUES ( ");
            printColumnSpec(stmt, columns, null, colValues,
                    PrintMode.PLACE_HOLDER, " , ");
            stmt.append(" ) ");
        }
        else if (action == RowChangeData.ActionType.UPDATE)
        {
            stmt.append("UPDATE ");
            stmt.append(conn.getDatabaseObjectName(schemaName) + "."
                    + conn.getDatabaseObjectName(tableName));
            stmt.append(" SET ");
            printColumnSpec(stmt, columns, null, colValues,
                    PrintMode.ASSIGNMENT, " , ");
            stmt.append(" WHERE ");
            printColumnSpec(stmt, keys, keyValues, colValues,
                    PrintMode.ASSIGNMENT, " AND ");
        }
        else if (action == RowChangeData.ActionType.DELETE)
        {
            stmt.append("DELETE FROM ");
            stmt.append(conn.getDatabaseObjectName(schemaName) + "."
                    + conn.getDatabaseObjectName(tableName));
            stmt.append(" WHERE ");
            printColumnSpec(stmt, keys, keyValues, colValues,
                    PrintMode.ASSIGNMENT, " AND ");
        }
        return stmt;
    }

    /**
     * Compares current key values to the previous key values and determines
     * whether null values changed. Eg. {1, 3, null} vs. {5, 2, null} returns
     * false, but {1, 3, null} vs. {1, null, null} returns true.<br/>
     * Size of both arrays must be the same.
     * 
     * @param currentKeyValues Current key values.
     * @param previousKeyValues Previous key values.
     * @return true, if positions of null values in currentKeyValues changed
     *         compared to previousKeyValues.
     */
    private static boolean didNullKeysChange(
            ArrayList<OneRowChange.ColumnVal> currentKeyValues,
            ArrayList<OneRowChange.ColumnVal> previousKeyValues)
    {
        for (int i = 0; i < currentKeyValues.size(); i++)
        {
            if (previousKeyValues.get(i).getValue() == null
                    || currentKeyValues.get(i).getValue() == null)
                if (!(previousKeyValues.get(i).getValue() == null && currentKeyValues
                        .get(i).getValue() == null))
                    return true;
        }
        return false;
    }

    /**
     * Return true if column values from previous row contain different
     * NULL/NON-NULL pattern from values of current row AND for the given
     * connection than difference matters.
     */
    private boolean didNullColsChange(
            ArrayList<OneRowChange.ColumnSpec> colSpecs,
            ArrayList<OneRowChange.ColumnVal> currentColValues,
            ArrayList<OneRowChange.ColumnVal> previousColValues)
    {
        if (!conn.nullsEverBoundDifferently())
            return false;
        for (int i = 0; i < currentColValues.size(); i++)
        {
            if (conn.nullsBoundDifferently(colSpecs.get(i)))
            {
                if (previousColValues.get(i).getValue() == null
                        && currentColValues.get(i).getValue() != null)
                    return true;
                if (previousColValues.get(i).getValue() != null
                        && currentColValues.get(i).getValue() == null)
                    return true;
            }

        }
        return false;
    }

    protected boolean needNewSQLStatement(int row,
            ArrayList<ArrayList<OneRowChange.ColumnVal>> keyValues,
            ArrayList<OneRowChange.ColumnSpec> keySpecs,
            ArrayList<ArrayList<OneRowChange.ColumnVal>> colValues,
            ArrayList<OneRowChange.ColumnSpec> colSpecs)
    {
        if (keyValues.size() > row
                && didNullKeysChange(keyValues.get(row), keyValues.get(row - 1)))
            return true;
        if (colValues.size() > row
                && didNullColsChange(colSpecs, colValues.get(row),
                        colValues.get(row - 1)))
            return true;
        return false;
    }

    protected void applyOneRowChangePrepared(OneRowChange oneRowChange)
            throws ApplierException
    {
        PreparedStatement prepStatement = null;

        try
        {
            int colCount = fillColumnNames(oneRowChange);
            if (colCount <= 0)
                logger.warn("No column information found for table: "
                        + oneRowChange.getSchemaName() + "."
                        + oneRowChange.getTableName());
        }
        catch (SQLException e1)
        {
            logger.error("column name information could not be retrieved");
            e1.printStackTrace();
        }
        StringBuffer stmt = null;

        ArrayList<OneRowChange.ColumnSpec> key = oneRowChange.getKeySpec();
        ArrayList<OneRowChange.ColumnSpec> columns = oneRowChange
                .getColumnSpec();

        try
        {
            ArrayList<ArrayList<OneRowChange.ColumnVal>> keyValues = oneRowChange
                    .getKeyValues();
            ArrayList<ArrayList<OneRowChange.ColumnVal>> columnValues = oneRowChange
                    .getColumnValues();
            int updateCount = 0;

            int row = 0;
            for (row = 0; row < columnValues.size() || row < keyValues.size(); row++)
            {
                if (row == 0
                        || needNewSQLStatement(row, keyValues, key,
                                columnValues, columns))
                {
                    ArrayList<OneRowChange.ColumnVal> keyValuesOfThisRow = null;
                    if (keyValues.size() > 0)
                        keyValuesOfThisRow = keyValues.get(row);

                    // Construct separate SQL for every row, because there might
                    // be NULLs in keys in which case SQL is different
                    // (TREP-276).
                    ArrayList<OneRowChange.ColumnVal> colValuesOfThisRow = null;
                    if (columnValues.size() > 0)
                        colValuesOfThisRow = columnValues.get(row);

                    stmt = constructStatement(oneRowChange.getAction(),
                            oneRowChange.getSchemaName(),
                            oneRowChange.getTableName(), columns, key,
                            keyValuesOfThisRow, colValuesOfThisRow);

                    runtime.getMonitor().incrementEvents(columnValues.size());
                    prepStatement = conn.prepareStatement(stmt.toString());
                }

                int bindLoc = 1; /* Start binding at index 1 */

                /* bind column values */
                if (columnValues.size() > 0)
                {
                    bindLoc = bindColumnValues(prepStatement,
                            columnValues.get(row), bindLoc, columns, false);
                }
                /* bind key values */
                if (keyValues.size() > 0)
                {
                    bindLoc = bindColumnValues(prepStatement,
                            keyValues.get(row), bindLoc, key, true);
                }

                try
                {
                    updateCount += prepStatement.executeUpdate();
                }
                catch (SQLWarning e)
                {
                    String msg = "While applying SQL event:\n"
                            + stmt.toString() + "\nWarning: " + e.getMessage();
                    logger.warn(msg);
                }
            }

            if (logger.isDebugEnabled())
            {
                logger.debug("Applied event (update count " + updateCount
                        + "): " + stmt.toString());
            }
        }
        catch (SQLException e)
        {
            logFailedRowChangeSQL(stmt, oneRowChange);
            throw new ApplierException(e);
        }
        finally
        {
            if (prepStatement != null)
            {
                try
                {
                    prepStatement.close();
                }
                catch (SQLException ignore)
                {
                }
            }
        }
    }

    /**
     * Logs prepared statement and it's arguments into error log stream. Trims
     * the message if it exceeds maxSQLLogLength.
     * 
     * @see #maxSQLLogLength
     * @param stmt SQL template for PreparedStatement
     */
    private void logFailedRowChangeSQL(StringBuffer stmt,
            OneRowChange oneRowChange)
    {
        // TODO: use THLManagerCtrl for logging exact failing SQL after
        // branch thl_meta is merged into HEAD. Now this duplicates
        // functionality.
        try
        {
            ArrayList<OneRowChange.ColumnSpec> keys = oneRowChange.getKeySpec();
            ArrayList<OneRowChange.ColumnSpec> columns = oneRowChange
                    .getColumnSpec();
            ArrayList<ArrayList<OneRowChange.ColumnVal>> keyValues = oneRowChange
                    .getKeyValues();
            ArrayList<ArrayList<OneRowChange.ColumnVal>> columnValues = oneRowChange
                    .getColumnValues();
            String log = "PreparedStatement failed: " + stmt.toString()
                    + "\nArguments:";
            for (int row = 0; row < columnValues.size()
                    || row < keyValues.size(); row++)
            {
                log += "\n - ROW# = " + row;
                // Print column values.
                for (int c = 0; c < columns.size(); c++)
                {
                    if (columnValues.size() > 0)
                    {
                        OneRowChange.ColumnSpec colSpec = columns.get(c);
                        ArrayList<OneRowChange.ColumnVal> values = columnValues
                                .get(row);
                        OneRowChange.ColumnVal value = values.get(c);
                        log += "\n"
                                + THLManagerCtrl.formatColumn(colSpec, value,
                                        "COL", null);
                    }
                }
                // Print key values.
                for (int k = 0; k < keys.size(); k++)
                {
                    if (keyValues.size() > 0)
                    {
                        OneRowChange.ColumnSpec colSpec = keys.get(k);
                        ArrayList<OneRowChange.ColumnVal> values = keyValues
                                .get(row);
                        OneRowChange.ColumnVal value = values.get(k);
                        log += "\n"
                                + THLManagerCtrl.formatColumn(colSpec, value,
                                        "KEY", null);
                    }
                }
            }
            // Output the error message, truncate it if too large.
            if (log.length() > maxSQLLogLength)
                log = log.substring(0, maxSQLLogLength);
            logger.error(log);
        }
        catch (Exception e)
        {
            logger.debug("logFailedRowChangeSQL failed to log, because: "
                    + e.getMessage());
        }
    }

    protected void applyRowChangeData(RowChangeData data,
            List<ReplOption> options) throws ApplierException
    {
        if (options != null)
        {
            try
            {
                if (applySessionVariables(options))
                {
                    // Apply session variables to the connection only if
                    // something changed
                    statement.executeBatch();
                    statement.clearBatch();
                }
            }
            catch (SQLException e)
            {
                throw new ApplierException("Failed to apply session variables",
                        e);
            }
        }

        for (OneRowChange row : data.getRowChanges())
        {
            applyOneRowChangePrepared(row);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.applier.RawApplier#apply(com.continuent.tungsten.replicator.event.DBMSEvent,
     *      com.continuent.tungsten.replicator.event.ReplDBMSHeader, boolean,
     *      boolean)
     */
    public void apply(DBMSEvent event, ReplDBMSHeader header, boolean doCommit,
            boolean doRollback) throws ApplierException, ConsistencyException
    {
        boolean transactionCommitted = false;
        boolean consistencyCheckFailure = false;
        boolean heartbeatFailure = false;
        boolean emptyEvent = false;
        long appliedLatency = (System.currentTimeMillis() - event
                .getSourceTstamp().getTime()) / 1000;

        // Ensure we are not trying to apply a previously applied event. This
        // case can arise during restart.
        if (lastProcessedEvent != null && lastProcessedEvent.getLastFrag()
                && lastProcessedEvent.getSeqno() >= header.getSeqno()
                && !(event instanceof DBMSEmptyEvent))
        {
            logger.info("Skipping over previously applied event: seqno="
                    + header.getSeqno() + " fragno=" + header.getFragno());
            return;
        }
        if (logger.isDebugEnabled())
            logger.debug("Applying event: seqno=" + header.getSeqno()
                    + " fragno=" + header.getFragno() + " commit=" + doCommit);

        try
        {
            if (!transactionStarted)
            {
                startTransaction();

                // If we are starting on a fragmented transaction, write to
                // trep_commit_seqno so that we can detect services properly.
                if (!header.getLastFrag())
                {
                    updateCommitSeqno(header, appliedLatency);
                }
            }
        }
        catch (SQLException e)
        {
            throw new ApplierException("Failed to start new transaction", e);
        }

        try
        {
            if (event instanceof DBMSEmptyEvent)
            {
                if (doCommit)
                {
                    updateCommitSeqno(header, appliedLatency);
                    lastProcessedEvent = header;
                    commitTransaction();
                    transactionCommitted = true;
                    return;
                }
                else
                {
                    // No need to apply an empty event
                    emptyEvent = true;
                }
            }
            else if (header instanceof ReplDBMSFilteredEvent)
            {
                // This is a range of filtered events
                // Just update the commit seqno
                ((ReplDBMSFilteredEvent) header).updateCommitSeqno();
                updateCommitSeqno(header, appliedLatency);
                commitTransaction();
                transactionCommitted = true;
                return;
            }
            else
            {
                ArrayList<DBMSData> data = event.getData();
                for (DBMSData dataElem : data)
                {
                    if (dataElem instanceof RowChangeData)
                    {
                        applyRowChangeData((RowChangeData) dataElem,
                                event.getOptions());
                    }
                    else if (dataElem instanceof LoadDataFileFragment)
                    {
                        LoadDataFileFragment fileFrag = (LoadDataFileFragment) dataElem;
                        applyFileFragment(fileFrag);
                    }
                    else if (dataElem instanceof LoadDataFileQuery)
                    {
                        LoadDataFileQuery fileQuery = (LoadDataFileQuery) dataElem;
                        applyLoadFile(fileQuery);
                    }
                    else if (dataElem instanceof LoadDataFileDelete)
                    {
                        applyDeleteFile(((LoadDataFileDelete) dataElem)
                                .getFileID());
                    }
                    else if (dataElem instanceof StatementData)
                    {
                        StatementData sdata = (StatementData) dataElem;
                        applyStatementData(sdata);

                        // Check for table metadata cache invalidation.
                        String query = sdata.getQuery();
                        if (query == null)
                            query = new String(sdata.getQueryAsBytes());
                        SqlOperation sqlOperation = sqlMatcher.match(query);

                        int invalidated = tableMetadataCache.invalidate(
                                sqlOperation, sdata.getDefaultSchema());
                        if (invalidated > 0)
                        {
                            if (logger.isDebugEnabled())
                                logger.debug("Table metadata invalidation: stmt="
                                        + query + " invalidated=" + invalidated);
                        }
                    }
                    else if (dataElem instanceof RowIdData)
                    {
                        applyRowIdData((RowIdData) dataElem);
                    }
                }
            }

            if (doCommit)
            {
                updateCommitSeqno(header, appliedLatency);
            }
            // Update the last processed even when it was committed, otherwise,
            // we can commit an older seqno
            lastProcessedEvent = header;

            if (emptyEvent)
                return;

            // Process consistency check/heartbeat event.
            try
            {
                if (event
                        .getMetadataOptionValue(ReplOptionParams.CONSISTENCY_WHERE) != null)
                {
                    consistencyCheckFailure = true;
                    String whereClause = event
                            .getMetadataOptionValue(ReplOptionParams.CONSISTENCY_WHERE);
                    consistencyCheck(whereClause);
                    consistencyCheckFailure = false;
                }
                else if (event
                        .getMetadataOptionValue(ReplOptionParams.HEARTBEAT) != null)
                {
                    heartbeatTable.completeHeartbeat(conn, header.getSeqno(),
                            event.getEventId());
                }
            }
            catch (SQLException e)
            {
                throw e;
            }
            catch (ConsistencyException e)
            {
                if (this.runtime.isConsistencyFailureStop())
                    throw e;
                else
                {
                    logger.warn("Consistency check failed: " + e.getMessage());
                }
            }
            finally
            {
                if (!emptyEvent)
                {
                    // at this point we want to commit or rollback transaction
                    // even if consistencyCheck() or heartbeat failed.
                    if (doRollback)
                    {
                        rollbackTransaction();
                        updateCommitSeqno(lastProcessedEvent, appliedLatency);
                        // And commit
                        commitTransaction();
                        transactionCommitted = true;                    }
                    else if (doCommit)
                    {
                        commitTransaction();
                        transactionCommitted = true;
                    }
                    else if (consistencyCheckFailure || heartbeatFailure)
                    {
                        // Update commitSeqnoTable
                        updateCommitSeqno(lastProcessedEvent, appliedLatency);
                        // And commit
                        commitTransaction();
                        transactionCommitted = true;
                    }
                }
            }
        }
        catch (SQLException e)
        {
            if (!transactionCommitted)
            {
                try
                {
                    rollbackTransaction();
                }
                catch (SQLException e1)
                {
                    e1.initCause(e);
                    e = e1;
                }
            }
            throw new ApplierException(e);
        }

        // Update statistics.
        this.eventCount++;
        if (logger.isDebugEnabled() && eventCount % 20000 == 0)
            logger.debug("Apply statistics: events=" + eventCount + " commits="
                    + commitCount);
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.applier.RawApplier#commit()
     */
    public void commit() throws ApplierException, InterruptedException
    {
        // If there's nothing to commit, go back.
        if (this.lastProcessedEvent == null || !this.transactionStarted)
            return;

        try
        {
            // Add applied latency so that we can easily track how far back each
            // partition is. If we don't have data we just put in zero.
            long appliedLatency;
            if (lastProcessedEvent instanceof ReplDBMSEvent)
                appliedLatency = (System.currentTimeMillis() - ((ReplDBMSEvent) lastProcessedEvent)
                        .getExtractedTstamp().getTime()) / 1000;
            else
                appliedLatency = 0;

            updateCommitSeqno(lastProcessedEvent, appliedLatency);
            commitTransaction();
            transactionStarted = false;
        }
        catch (SQLException e)
        {
            throw new ApplierException("Unable to commit transaction: "
                    + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.applier.RawApplier#rollback()
     */
    public void rollback() throws InterruptedException
    {
        try
        {
            rollbackTransaction();
        }
        catch (SQLException e)
        {
            // Stack trace is not normally desirable as it creates
            // a lot of extra information in the log.
            logger.info("Unable to roll back transaction");
            if (logger.isDebugEnabled())
                logger.debug("Transaction rollback error", e);
        }
    }

    private void applyDeleteFile(int fileID) throws ApplierException
    {
        if (logger.isDebugEnabled())
            logger.debug("Dropping file id: " + fileID);
        File temporaryFile = fileTable.remove(Integer.valueOf(fileID));
        if (temporaryFile == null)
            throw new ApplierException("Unable to find file " + fileID);

        fileTable.remove(temporaryFile);
        temporaryFile.delete();
    }

    private void applyLoadFile(LoadDataFileQuery fileQuery)
            throws ApplierException
    {
        int fileID = fileQuery.getFileID();
        File temporaryFile = fileTable.remove(Integer.valueOf(fileID));
        if (temporaryFile == null)
            throw new ApplierException("Unable to find file " + fileID);

        if (fileQuery.isLocal())
        {
            if (logger.isDebugEnabled())
                logger.debug("LOAD DATA using LOCAL option identified");

            applyLoadDataLocal(fileQuery, temporaryFile);
        }
        else
        {
            if (logger.isDebugEnabled())
                logger.debug("LOAD DATA not using LOCAL option identified");

            fileQuery.setLocalFile(temporaryFile);
            applyStatementData(fileQuery);
        }
        logger.info("File deleted : " + temporaryFile.delete());
    }

    /**
     * applyLoadDataLocal executes the LoadDataFileQuery. This method is most
     * likely to be database specific. See for example :
     * 
     * @see com.continuent.tungsten.replicator.applier.MySQLApplier#applyLoadDataLocal(LoadDataFileQuery,
     *      File)
     * @param data The LoadDataFileQuery containing the query that has to be
     *            executed
     * @param temporaryFile The file containing data to be loaded
     * @throws ApplierException if an error occurs
     */
    protected void applyLoadDataLocal(LoadDataFileQuery data, File temporaryFile)
            throws ApplierException
    {
        data.setLocalFile(temporaryFile);
        applyStatementData(data);
    }

    private void applyFileFragment(LoadDataFileFragment fileFrag)
            throws ApplierException
    {
        Integer fileId = Integer.valueOf(fileFrag.getFileID());
        File temporaryFile = fileTable.get(fileId);
        if (temporaryFile == null)
        {
            try
            {
                temporaryFile = File.createTempFile("SQL_LOAD_MB-" + fileId
                        + "-", ".tmp");
                if (logger.isDebugEnabled())
                    logger.debug("Opening temporary file: fileId=" + fileId
                            + " path=" + temporaryFile);
            }
            catch (IOException e)
            {
                throw new ApplierException("Unable to create temporary file", e);
            }
            fileTable.put(fileId, temporaryFile);
        }
        else
        {
            logger.debug("Appending data to temporary file: fileId=" + fileId
                    + " path=" + temporaryFile);
        }

        try
        {
            FileOutputStream fos = new FileOutputStream(temporaryFile, true);
            fos.write(fileFrag.getData());
            fos.close();
        }
        catch (IOException e)
        {
            throw new ApplierException("Unable to write into file "
                    + temporaryFile.getPath(), e);
        }
    }

    private void updateCommitSeqno(ReplDBMSHeader header, long appliedLatency)
            throws SQLException
    {
        if (commitSeqnoTable == null)
            return;
        if (logger.isDebugEnabled())
            logger.debug("Updating commit seqno to " + header.getSeqno());
        commitSeqnoTable.updateLastCommitSeqno(taskId, header, appliedLatency);
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.applier.RawApplier#getLastEvent()
     */
    public ReplDBMSHeader getLastEvent() throws ApplierException
    {
        if (commitSeqnoTable == null)
            return null;

        try
        {
            return commitSeqnoTable.lastCommitSeqno(taskId);
        }
        catch (SQLException e)
        {
            throw new ApplierException(e);
        }
    }

    /**
     * startTransaction starts a new transaction.
     * 
     * @throws SQLException if a problem occurs.
     */
    private void startTransaction() throws SQLException
    {
        transactionStarted = true;
        conn.setAutoCommit(false);
    }

    /**
     * commitTransaction commits the current transaction.
     * 
     * @throws SQLException if a problem occurs.
     */
    private void commitTransaction() throws SQLException
    {
        try
        {
            conn.commit();
            commitCount++;
        }
        catch (SQLException e)
        {
            logger.error("Failed to commit : " + e);
            throw e;
        }
        finally
        {
            transactionStarted = false;
            // Switch connection back to autocommit
            conn.setAutoCommit(true);
        }
    }

    /**
     * rollbackTransaction rollbacks the current transaction.
     * 
     * @throws SQLException if a problem occurs.
     */
    private void rollbackTransaction() throws SQLException
    {
        try
        {
            conn.rollback();
        }
        catch (SQLException e)
        {
            logger.error("Failed to rollback : " + e);
            throw e;
        }
        finally
        {
            // Switch connection back to autocommit
            conn.setAutoCommit(true);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.plugin.ReplicatorPlugin#prepare(com.continuent.tungsten.replicator.plugin.PluginContext)
     */
    public void prepare(PluginContext context) throws ReplicatorException
    {
        try
        {
            // Load driver if provided.
            if (driver != null)
            {
                try
                {
                    Class.forName(driver);
                }
                catch (Exception e)
                {
                    throw new ReplicatorException("Unable to load driver: "
                            + driver, e);
                }
            }

            // Create the database.
            conn = DatabaseFactory.createDatabase(url, user, password);
            conn.connect(false);
            statement = conn.createStatement();

            // Enable binlogs at session level if this is supported and we are
            // either a remote service or slave logging is turned on. This
            // repeats logic in the connect() call but gives a clear log
            // message,
            // which is important for diagnostic purposes.
            if (conn.supportsControlSessionLevelLogging())
            {
                if (runtime.logReplicatorUpdates() || runtime.isRemoteService())
                {
                    logger.info("Slave updates will be logged");
                    conn.controlSessionLevelLogging(false);
                }
                else
                {
                    logger.info("Slave updates will not be logged");
                    conn.controlSessionLevelLogging(true);
                }
            }

            // Set session variable to show we are a slave.
            if (conn.supportsSessionVariables())
            {
                logger.info("Setting TREPSLAVE session variable");
                conn.setSessionVariable("TREPSLAVE", "YES");
            }

            tableMetadataCache = new TableMetadataCache(5000);

            // Set up heartbeat table.
            heartbeatTable = new HeartbeatTable(
                    context.getReplicatorSchemaName(),
                    runtime.getTungstenTableType());
            heartbeatTable.initializeHeartbeatTable(conn);

            // Create consistency table
            Table consistency = ConsistencyTable
                    .getConsistencyTableDefinition(metadataSchema);
            conn.createTable(consistency, false);

            // Set up commit seqno table and fetch the last processed event.
            commitSeqnoTable = new CommitSeqnoTable(conn,
                    context.getReplicatorSchemaName(),
                    runtime.getTungstenTableType(), false);
            commitSeqnoTable.prepare(taskId);
            lastProcessedEvent = commitSeqnoTable.lastCommitSeqno(taskId);

        }
        catch (SQLException e)
        {
            String message = String.format("Failed using url=%s, user=%s", url,
                    user);
            throw new ReplicatorException(message, e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.plugin.ReplicatorPlugin#configure(com.continuent.tungsten.replicator.plugin.PluginContext)
     */
    public void configure(PluginContext context) throws ApplierException
    {
        runtime = (ReplicatorRuntime) context;
        metadataSchema = context.getReplicatorSchemaName();
        consistencyTable = metadataSchema + "." + ConsistencyTable.TABLE_NAME;
        consistencySelect = "SELECT * FROM " + consistencyTable + " ";
        fileTable = new Hashtable<Integer, File>();
        if (ignoreSessionVars != null)
        {
            ignoreSessionPattern = Pattern.compile(ignoreSessionVars);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.plugin.ReplicatorPlugin#release(com.continuent.tungsten.replicator.plugin.PluginContext)
     */
    public void release(PluginContext context) throws ReplicatorException
    {
        if (commitSeqnoTable != null)
        {
            commitSeqnoTable.release();
            commitSeqnoTable = null;
        }

        currentOptions = null;

        statement = null;
        if (conn != null)
        {
            conn.close();
            conn = null;
        }

        if (tableMetadataCache != null)
        {
            tableMetadataCache.invalidateAll();
            tableMetadataCache = null;
        }
    }
}
