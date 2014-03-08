/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2011-2014 Continuent Inc.
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
 * Contributor(s): Linas Virbalas
 */

package com.continuent.tungsten.replicator.csv;

import java.io.File;

import com.continuent.tungsten.replicator.database.Table;

/**
 * Defines a struct to hold batch CSV file information. When using staging the
 * stage table metadata field is filled in. Otherwise it is null.
 */
public class CsvInfo
{
    // Struct fields.
    public String schema;
    public String table;
    public String key;
    public Table  baseTableMetadata;
    public Table  stageTableMetadata;
    public File   file;
    public long   startSeqno = -1;
    public long   endSeqno   = -1;

    /**
     * Instantiates a new instance.
     */
    public CsvInfo()
    {
    }
}
