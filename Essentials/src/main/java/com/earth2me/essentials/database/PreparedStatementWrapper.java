package com.earth2me.essentials.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PreparedStatementWrapper implements AutoCloseable
{
    private final PreparedStatement preparedStatement;
    private final Connection connection;
    
    public PreparedStatementWrapper(Connection connection, PreparedStatement statement)
    {
        this.connection = connection;
        this.preparedStatement = statement;
    }
    
    @Override
    public void close()
            throws SQLException
    {
        preparedStatement.close();
        connection.close();
    }
    
    // region Getters & setters
    public PreparedStatement getPreparedStatement()
    {
        return preparedStatement;
    }
    
    public Connection getConnection()
    {
        return connection;
    }
    // endregion
}
