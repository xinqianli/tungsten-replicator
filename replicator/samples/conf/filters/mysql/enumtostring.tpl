# Transform enum's int to string before it reaches THL.
replicator.filter.enumtostring=com.continuent.tungsten.replicator.filter.EnumToStringFilter
replicator.filter.enumtostring.url=jdbc:mysql:thin://${replicator.global.extract.db.host}:${replicator.global.extract.db.port}/${replicator.schema}
# Commenting out : ?createDB=true - Database should be created at the time the filter is loaded
replicator.filter.enumtostring.user=${replicator.global.extract.db.user}
replicator.filter.enumtostring.password=${replicator.global.extract.db.password}