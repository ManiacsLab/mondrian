/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2012-2012 Pentaho
// All Rights Reserved.
*/
package mondrian.spi.impl;

import mondrian.olap.Util;
import mondrian.server.Execution;
import mondrian.spi.Dialect;
import mondrian.spi.StatisticsProvider;

import java.sql.*;
import javax.sql.DataSource;

/**
 * Implementation of {@link mondrian.spi.StatisticsProvider} that uses JDBC
 * metadata calls to count rows and distinct values.
 */
public class JdbcStatisticsProvider implements StatisticsProvider {
    public int getTableCardinality(
        Dialect dialect,
        DataSource dataSource,
        String catalog,
        String schema,
        String table,
        Execution execution)
    {
        Connection connection = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            resultSet =
                connection
                    .getMetaData()
                    .getIndexInfo(catalog, schema, table, false, true);
            int maxNonUnique = -1;
            while (resultSet.next()) {
                final int type = resultSet.getInt(7); // "TYPE" column
                final int cardinality = resultSet.getInt(11);
                final boolean unique =
                    !resultSet.getBoolean(4); // "NON_UNIQUE" column
                switch (type) {
                case DatabaseMetaData.tableIndexStatistic:
                    return cardinality; // "CARDINALITY" column
                }
                if (!unique) {
                    maxNonUnique = Math.max(maxNonUnique, cardinality);
                }
            }
            // The cardinality of each non-unique index will be the number of
            // non-NULL values in that index. Unless we're unlucky, one of those
            // columns will cover most of the table.
            return maxNonUnique;
        } catch (SQLException e) {
            throw Util.newInternal(
                e, "while computing cardinality of table [" + table + "]");
        } finally {
            Util.close(resultSet, null, connection);
        }
    }

    public int getQueryCardinality(
        Dialect dialect,
        DataSource dataSource,
        String sql,
        Execution execution)
    {
        // JDBC cannot help with this. Defer to another statistics provider.
        return -1;
    }

    public int getColumnCardinality(
        Dialect dialect,
        DataSource dataSource,
        String catalog,
        String schema,
        String table,
        String column,
        Execution execution)
    {
        Connection connection = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            resultSet =
                connection
                    .getMetaData()
                    .getIndexInfo(catalog, schema, table, false, true);
            while (resultSet.next()) {
                int type = resultSet.getInt(7); // "TYPE" column
                switch (type) {
                case DatabaseMetaData.tableIndexStatistic:
                    return resultSet.getInt(11); // "CARDINALITY" column
                }
            }
            return -1; // information not available, apparently
        } catch (SQLException e) {
            throw Util.newInternal(
                e, "while computing cardinality of table [" + table + "]");
        } finally {
            Util.close(resultSet, null, connection);
        }
    }
}

// End JdbcStatisticsProvider.java