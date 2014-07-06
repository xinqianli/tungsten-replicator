/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2010-2014 Continuent Inc.
 * Contact: tungsten@continuent.org
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of version 2 of the GNU General Public License as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 *
 * Initial developer(s): Robert Hodges
 * Contributor(s): Stephane Giron
 */

package com.continuent.tungsten.replicator.event;

/**
 * This class provides a list of defined metadata option values.
 * 
 * @author <a href="mailto:robert.hodges@continuent.com">Robert Hodges</a>
 * @version 1.0
 */
public class ReplOptionParams
{
    /**
     * Indicates a heart beat event. The value is the optional heartbeat name or
     * "DEFAULT".
     */
    public static String       HEARTBEAT               = "heartbeat";

    /**
     * Where clause in a consistency check. This signals that the current event
     * contains a consistency check.
     */
    public static String       CONSISTENCY_WHERE       = "consistency_where";

    /**
     * Service that originally generated this event.
     */
    public static String       SERVICE                 = "service";

    /**
     * If set to "true", this event is generated by Tungsten. If absent, the
     * event represents a normal transaction.
     */
    public static String       TUNGSTEN_METADATA       = "is_metadata";

    /**
     * If set, contains the name of a shard to which this event is assigned.
     */
    public static String       SHARD_ID                = "shard";

    /**
     * The default shard ID if no other ID can be assigned.
     */
    public static String       SHARD_ID_UNKNOWN        = "#UNKNOWN";

    /**
     * Prefix for internal Tungsten options.
     */
    public static String       INTERNAL_OPTIONS_PREFIX = "##";

    /**
     * Java character set name of byte-encoded strings. This is a statement
     * option.
     */
    public static String       JAVA_CHARSET_NAME       = "##charset";

    /**
     * Schema name. This is a statement option.
     */
    public static String       SCHEMA_NAME             = "##schema";

    /**
     * Table name. This is a statement option.
     */
    public static String       TABLE_NAME              = "##table";

    /**
     * DDL operation name. This is a statement option.
     */
    public static String       OPERATION_NAME          = "##operation";

    /**
     * ServerId. This is a statement option.
     */
    public static String       SERVER_ID               = "mysql_server_id";

    /**
     * Names the source DBMS type so that we can parse and otherwise process
     * transactions properly when they contain features.
     */
    public static String       DBMS_TYPE               = "dbms_type";

    /**
     * Contains value "true" if the transaction is unsafe for bi-directional
     * replication.
     */
    public static String       BIDI_UNSAFE             = "bidi_unsafe";

    /**
     * Indicates whether this transaction needs to rollback. No value is
     * required. If this is defined, then the transaction should rollback.
     */
    public static final String ROLLBACK                = "rollback";

    /**
     * Indicates whether this transaction is unsafe for block commit. As above,
     * no value is required. If this property is defined, then the transaction
     * is unsafe. If not defined, it is safe for block commit.
     */
    public static final String UNSAFE_FOR_BLOCK_COMMIT = "unsafe_for_block_commit";

    /**
     * Indicates that this transaction should force a commit regardless of any
     * other rules regarding block commit.
     */
    public static final String FORCE_COMMIT            = "force_commit";

    /**
     * Indicates that this transaction contains a schema change.
     */
    public static final String SCHEMA_CHANGE           = "schema_change";

    /**
     * Indicates that this transaction contains a table truncate command.
     */
    public static final String TRUNCATE                = "truncate";

    /**
     * This is the domain id from the GTID (if any). GTID is composed of domain
     * id, server id and seqno.
     */
    public static final String GTID_DOMAIN_ID          = "gtid_domain_id";

    /**
     * This is the seqno from the GTID (if any)
     */
    public static final String GTID_SEQNO              = "gtid_seqno";
}