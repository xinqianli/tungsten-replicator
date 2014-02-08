# HDFS data source. 
replicator.datasource.applier=com.continuent.tungsten.replicator.datasource.HdfsDataSource
replicator.datasource.applier.serviceName=${service.name}
# Storage location for replication catalog data. 
replicator.datasource.applier.directory=/user/tungsten/metadata

# HDFS-specific information. 
replicator.datasource.applier.hdfsUri=hdfs://@{APPLIER.REPL_DBHOST}:@{APPLIER.REPL_DBPORT}/user/tungsten/metadata
replicator.datasource.applier.hdfsConfigProperties=${replicator.home.dir}/conf/hdfs-config.properties

# CSV type.  If set to custom, use the custom CSV specification settings. 
# Other supported settings are default, hive, etc. 
replicator.datasource.applier.csvType=hive

# CSV generation configuration settings for custom cvs type. The file and record 
# separator values are congenial for Hive external tables but it is probably simpler 
# just to use the hive csvType. 
replicator.datasource.applier.csv=com.continuent.tungsten.common.csv.CsvSpecification
replicator.datasource.applier.csv.fieldSeparator=\\u0001
replicator.datasource.applier.csv.RecordSeparator=\\n
replicator.datasource.applier.csv.useQuotes=false
