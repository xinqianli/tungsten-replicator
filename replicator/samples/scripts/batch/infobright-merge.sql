# Merge script for MySQL. 
#
# Delete rows.  Deletes any row that has a key in the staging table. 
DELETE %%BASE_TABLE%% 
  FROM %%STAGE_TABLE_FQN%% s
  INNER JOIN %%BASE_TABLE%% 
  ON s.%%PKEY%% = %%BASE_TABLE%%.%%PKEY%%

# Load inserts directly into base tables from CSV. 
SET time_zone = '+0:00';
LOAD DATA INFILE '%%CSV_FILE%%' INTO TABLE %%BASE_TABLE%% 
  CHARACTER SET utf8 FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
  LINES STARTING BY '"I",' 
  (@seqno, @rowid, %%BASE_COLUMNS%%);
