# Load delete keys only for Infobright. 
SET time_zone = '+0:00';
LOAD DATA INFILE '%%CSV_FILE%%' INTO TABLE %%STAGE_TABLE_FQN%% 
  CHARACTER SET utf8 FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
  LINES STARTING BY '"D",' 
  (@seqno, @rowid, %%PKEY%%);

