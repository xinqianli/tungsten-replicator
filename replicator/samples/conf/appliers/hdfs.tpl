# Batch applier basic configuration information. 
replicator.applier.dbms=com.continuent.tungsten.replicator.applier.batch.SimpleBatchApplier
replicator.applier.dbms.dataSource=applier

# Template file to execute when connecting initially.  
replicator.applier.dbms.startupScript=${replicator.home.dir}/samples/scripts/batch/@{SERVICE.BATCH_LOAD_TEMPLATE}-connect.@{SERVICE.BATCH_LOAD_LANGUAGE}

# Timezone and character set.  
replicator.applier.dbms.timezone=GMT+0:00
#replicator.applier.dbms.charset=UTF-8

# Parameters for loading and merging via stage tables.  
replicator.applier.dbms.stageColumnPrefix=tungsten_
replicator.applier.dbms.stageTablePrefix=stage_xxx_
replicator.applier.dbms.stageSchemaPrefix=
replicator.applier.dbms.stageDirectory=/tmp/staging
replicator.applier.dbms.stageMergeScript=${replicator.home.dir}/samples/scripts/batch/@{SERVICE.BATCH_LOAD_TEMPLATE}-merge.@{SERVICE.BATCH_LOAD_LANGUAGE}

# Fail if there is a discrepancy between the number of rows loaded and the 
# number of rows in CSV.  This is somewhat obsolete at this point. 
replicator.applier.dbms.onLoadMismatch=fail

# Clear files after each transaction.  
replicator.applier.dbms.cleanUpFiles=false

# Enable to log batch script commands as they are executed.  (Generates
# potentially large amount of output.)
replicator.applier.dbms.showCommands=false

# Included to provide default pkey for tables that omit such.  This is not 
# a good practice in general. 
#replicator.applier.dbms.stagePkeyColumn=id
