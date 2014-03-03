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

// Loads CSV file to HDFS.  If the key is present that becomes a sub-directory
// so that data are distributed. 
function apply(csvinfo)
{
  // Assemble the parts of the file. 
  csv_file = csvinfo.file.getAbsolutePath();
  schema = csvinfo.schema;
  table = csvinfo.table;
  seqno = csvinfo.startSeqno;
  key = csvinfo.key;
  if (key == "") {
    hadoop_dir = hadoop_base + '/' + schema + "/" + table;
  }
  else {
    hadoop_dir = hadoop_base + '/' + schema + "/" + table + "/" + key
  }
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
