# Batch applier basic configuration information. 
replicator.applier.dbms=com.continuent.tungsten.replicator.applier.batch.SimpleBatchApplier

# Data source to which to apply. 
replicator.applier.dbms.dataSource=applier

# Location of the load script. 
replicator.applier.dbms.loadScript=${replicator.home.dir}/samples/scripts/batch/@{SERVICE.BATCH_LOAD_TEMPLATE}.js

# Timezone and character set.  
replicator.applier.dbms.timezone=GMT+0:00
#replicator.applier.dbms.charset=UTF-8

# Location for writing CSV files. 
replicator.applier.dbms.stageDirectory=/tmp/staging/${service.name}

# Prefixes for stage table and schema names.  These are added to the base
# table and schema respectively. 
replicator.applier.dbms.stageTablePrefix=stage_xxx_
replicator.applier.dbms.stageSchemaPrefix=

# Staging header columns to apply.  Changing these can cause incompatibilities
# with generated SQL for Hive tables. 
replicator.applier.dbms.stageColumnNames=opcode,seqno,row_id,commit_timestamp

# Prefix for Tungsten staging column names.  This is added to header 
# column names and prevents naming clashes with user tables. 
replicator.applier.dbms.stageColumnPrefix=tungsten_

# Properties to enable 'partition by' support, which splits CSV files for a 
# single table based on a key generated from one of the tungsten header files.
# To partition files by commit hour including date, set the partitionBy
# property to tungsten_commit_timestamp.  Note that the full name including 
# prefix must be used here. 
replicator.applier.dbms.partitionBy=
replicator.applier.dbms.partitionByClass=com.continuent.tungsten.replicator.applier.batch.DateTimeValuePartitioner
replicator.applier.dbms.partitionByFormat='commit_hour='yyyy-MM-dd-HH

# Clear files after each transaction.  
replicator.applier.dbms.cleanUpFiles=true
