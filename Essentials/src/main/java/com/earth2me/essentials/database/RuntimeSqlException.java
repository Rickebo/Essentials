package com.earth2me.essentials.database;

import java.sql.SQLException;

public class RuntimeSqlException extends RuntimeException
{
    public RuntimeSqlException(SQLException ex)
    {
        super(ex);
    }
    
    public RuntimeSqlException(String message, SQLException ex)
    {
        super(message, ex);
    }
}
