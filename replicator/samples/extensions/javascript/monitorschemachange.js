/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2014 Continuent Inc.
 * Contact: tungsten@continuent.org
 *
 * Monitors schema changes and optionally forces a commit when such 
 * schema changes occur.  This filter is useful when loading into Hadoop 
 * in order to flag table changes.  It should run in the final q-to-dbms 
 * stage for best results. 
 *
 * Example of how to define in replicator.properties:
 *
 * replicator.filter.monitorschemachange=com.continuent.tungsten.replicator.filter.JavaScriptFilter
 * replicator.filter.monitorschemachange.script=${replicator.home.dir}/samples/extensions/javascript/monitorschemachange.js
 * replicator.filter.monitorschemachange.commit=true
 *
 * Initial developer(s): Robert Hodges
 * Contributor(s): Linas Virbalas
 */
 
/**
 * Called on every filtered event. See replicator's javadoc for more details
 * on accessible classes. Also, JavaScriptFilter's javadoc contains description
 * about how to define a script like this.
 *
 * @param event Filtered com.continuent.tungsten.replicator.event.ReplDBMSEvent
 *
 * @see com.continuent.tungsten.replicator.filter.JavaScriptFilter
 * @see com.continuent.tungsten.replicator.event.ReplDBMSEvent
 * @see com.continuent.tungsten.replicator.dbms.DBMSData
 * @see com.continuent.tungsten.replicator.dbms.StatementData
 * @see com.continuent.tungsten.replicator.dbms.RowChangeData
 * @see com.continuent.tungsten.replicator.dbms.OneRowChange
 * @see com.continuent.tungsten.replicator.dbms.RowChangeData.ActionType
 * @see com.continuent.tungsten.replicator.thl.THLManagerCtrl#printRowChangeData(StringBuilder, RowChangeData, String, boolean, int)
 * @see java.lang.Thread
 * @see org.apache.log4j.Logger
 */

/**
 * Called once when JavaScriptFilter corresponding to this script is prepared.
 */
function prepare()
{
    commit = filterProperties.getString("commit");
    if (commit != null)
    { 
      doCommit = true;
      logger.info("monitorschemachange: will commit on schema change");
    }  
    else
    {
      doCommit = false;
    }
}

function filter(event)
{
    // Analyse what this event is holding.
    dbmsEvent = event.getDBMSEvent();
    data = event.getData();

    // If there is no schema change marked, we don't have to do anything. 
    if (dbmsEvent.getMetadataOptionValue("schema_change") == null)
    {
      //logger.info("Nothing to do: seqno=" + event.getSeqno());
      return;
    }

    // Log all schema changes that need attention. 
    for(i = 0; i < data.size(); i++)
    {
        // Get com.continuent.tungsten.replicator.dbms.DBMSData
        d = data.get(i);
    
        // We only care about statements in this filter. 
        if(d instanceof com.continuent.tungsten.replicator.dbms.StatementData)
        {
            // If it's a SQL statement event and has the "##operation" tag it's
            // a schema change and should be logged. 
            operation = d.getOption("##operation");
            if (operation != null)
            {
              schema = d.getOption("##schema");
              table = d.getOption("##table");
              sql = d.getQuery();
              logger.info("SCHEMA CHANGE: seqno=" + event.getSeqno() 
                + " schema=" + schema + " table=" + table 
                + " operation=" + operation + " sql=[" + sql + "]");
            }
        }
    }

    // If we should commit, mark the event now.  This will force commit on
    // current and any downstream stages. 
    if (doCommit)
    {
      logger.info("Forcing commit due to schema change: seqno=" 
        + event.getSeqno());
      dbmsEvent.setMetaDataOption("force_commit","");
    }
}
