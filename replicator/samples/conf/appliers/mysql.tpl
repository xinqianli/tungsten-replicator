replicator.applier.dbms=com.continuent.tungsten.replicator.applier.MySQLDrizzleApplier
replicator.applier.dbms.host=${replicator.global.db.host}
replicator.applier.dbms.port=${replicator.global.db.port}
replicator.applier.dbms.user=${replicator.global.db.user}
replicator.applier.dbms.password=${replicator.global.db.password}
replicator.applier.dbms.ignoreSessionVars=autocommit
replicator.applier.dbms.getColumnMetadataFromDB=true
@{#(APPLIER.REPL_SVC_DATASOURCE_APPLIER_INIT_SCRIPT)}replicator.applier.dbms.initScript=@{APPLIER.REPL_SVC_DATASOURCE_APPLIER_INIT_SCRIPT}

# If true, similate time-zone unaware operation to process events from older
# Tungsten masters that do not extract events in a time zone-aware manner. 
# This option should only be enabled for upgrades if there is a chance of 
# processing an older replicator log. 
replicator.applier.dbms.supportNonTzAwareMode=false
