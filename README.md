# Tungsten Replicator
Tungsten Replicator is an open source replication engine supporting a variety of different extractor and applier modules. Data can be extracted from MySQL, Oracle and Amazon RDS, and applied to transactional stores, including MySQL, Oracle, and Amazon RDS; NoSQL stores such as MongoDB, and datawarehouse stores such as Vertica, InfiniDB, and Hadoop.

During replication, Tungsten Replication assigns data a unique global transaction ID, and enables flexible statement and/or row-based replication of data. This enables data to be exchanged between different databases and different database versions. During replication, information can be filtered and modified, and deployment can be between on-premise or cloud-based databases. For performance, Tungsten Replicator includes support for parallel replication, and advanced topologies such as fan-in, star and multi-master, and can be used efficiently in cross-site deployments.

Tungsten Replicator is the core foundation for Continuent Tungsten clustering solution for HA, DR and geographically distributed solutions.

# Tungsten Replicator 3.0
Includes support for replicating into Hadoop (including Apache Hadoop, Cloudera, HortonWorks, MapR, Amazon EMR)

Includes support for replicating into Amazon RedShift, including storing change data within Amazon S3

Includes support for replicating to and from Amazon RDS (MySQL) deployments

SSL Support for managing MySQL deployments

Network Client filter for handling complex data translation/migration needs during replication

# Tungsten Replicator 2.2
Replication from Oracle and MySQL, to MySQL, Oracle, MongoDB, Vertica, InfiniDB

New installation tool, tpm, providing easier installation from the command-line, and heterogenous replication easier to deploy

New INI file configuration, making complex deployments easier

Additional command-line tools to make deploying and running replicator easier

New multi_trepctl tool to make running multi host and service deployments easier

Parallel Extractor for Oracle-based data provisioning

# Related Tools and components
Tungsten Toolbox consists of a number of deployment and testing tools Tungsten Toolbox

Bristlecone provides load testing and benchmarking functionality Bristlecone

Continuent Github includes deployment and testing tools, database specific tools (e.g. Hadoop), and sample scripts and filters

# Keep in touch
Google Group discussion on Tungsten Replicator

Keep updated with the project issues

There is a #tungsten channel on irc.freenode.net
Short link to this page: http://tungsten-replicator.org
