/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2013-2014 Continuent Inc.
 * Contact: tungsten@continuent.org
 *
 * Load script for Hadoop data that uses direct connection to HDFS. 
 */

// Called once when applier goes online. 
function prepare()
{
  // Ensure target directory exists. 
  logger.info("Ensuring data directory is created");
  hadoop_base = "/user/tungsten/staging";
  hdfs.mkdir(hadoop_base, true);
}

// Called at start of batch transaction. 
function begin()
{
  // Does nothing. 
}

// Appends data from a single table into a global file. 
function apply(csvinfo)
{
  // Assemble the parts of the file. 
  sqlParams = csvinfo.getSqlParameters();
  csv_file = sqlParams.get("%%CSV_FILE%%");
  schema = csvinfo.schema;
  table = csvinfo.table;
  seqno = csvinfo.startSeqno;
  hadoop_dir = hadoop_base + '/' + schema + "/" + table;
  hadoop_file = hadoop_dir + '/' + table + '-' + seqno + ".csv";
  logger.info("Writing file: " + csv_file + " to: " + hadoop_file);

  // Ensure the directory exists. 
  hdfs.mkdir(hadoop_dir, true);

  // Copy the file into HDFS. 
  hdfs.put(csv_file, hadoop_file);
}

// Called at commit time for a batch. 
function commit()
{
  // Does nothing. 
}

// Called when the applier goes offline. 
function release()
{
  // Does nothing. 
}
