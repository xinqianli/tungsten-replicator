/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2013-2014 Continuent Inc.
 * Contact: tungsten@continuent.org
 *
 * Load script for HDFS using hadoop utility.  Tables load to corresponding
 * directories in HDFS.  
 */

// Called once when applier goes online. 
function prepare()
{
  // Ensure target directory exists. 
  logger.info("Executing hadoop connect script to create data directory");
  hadoop_base = '/user/tungsten/staging';
  runtime.exec('hadoop fs -mkdir -p ' + hadoop_base);
}

// Called at start of batch transaction. 
function begin()
{
  // Does nothing. 
}

// Appends data from a single table into a file within an HDFS directory. 
function apply(csvinfo)
{
  // Collect useful data. 
  sqlParams = csvinfo.getSqlParameters();
  csv_file = sqlParams.get("%%CSV_FILE%%");
  schema = csvinfo.schema;
  table = csvinfo.table;
  seqno = csvinfo.startSeqno;
  hadoop_dir = hadoop_base + '/' + schema + "/" + table;
  hadoop_file = hadoop_dir + '/' + table + '-' + seqno + ".csv";
  logger.info("Writing file: " + csv_file + " to: " + hadoop_file);

  // Ensure the directory exists for the file. 
  runtime.exec('hadoop fs -mkdir -p ' + hadoop_dir);

  // Remove any previous file with this name.  That takes care of restart
  // by wiping out earlier loads of the same data. 
  runtime.exec('hadoop fs -rm -f ' + hadoop_file);

  // Load file to HDFS. 
  runtime.exec('hadoop fs -put ' + csv_file + ' ' + hadoop_file);
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
