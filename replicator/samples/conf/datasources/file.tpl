# File datasource. 
replicator.datasource.applier=com.continuent.tungsten.replicator.datasource.FileDataSource
replicator.datasource.applier.serviceName=${service.name}

# Storage location for replication catalog data. 
replicator.datasource.applier.directory=${replicator.home.dir}/data

# CSV type.  If set to custom, use the custom CSV specification settings. 
# Other supported settings are default, hive, etc. 
replicator.datasource.applier.csvType=custom

# CSV type settings.  These are used if the csv type is custom. 
replicator.datasource.applier.csv=com.continuent.tungsten.common.csv.CsvSpecification
replicator.datasource.applier.csv.fieldSeparator=,
replicator.datasource.applier.csv.RecordSeparator=\\n
replicator.datasource.applier.csv.nullValue=\\N
replicator.datasource.applier.csv.useQuotes=true
