# Monitor schema change and optionally force a commit when such changes occur.
# This filter requires that events are previously processed using the 
# schemachange filter. 
replicator.filter.monitorschemachange=com.continuent.tungsten.replicator.filter.JavaScriptFilter                                  
replicator.filter.monitorschemachange.script=${replicator.home.dir}/samples/extensions/javascript/monitorschemachange.js
replicator.filter.monitorschemachange.commit=true
