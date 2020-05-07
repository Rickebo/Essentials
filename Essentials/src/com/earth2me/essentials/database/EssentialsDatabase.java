package com.earth2me.essentials.database;

import com.earth2me.essentials.Essentials;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class EssentialsDatabase
{
    private Connection connection;
    
    private final String driver;
    private final String tablePrefix;
    private final String hostname;
    private final String database;
    private final int port;
    private final String username;
    private final String password;
    
    private final String userDataTableName;
    
    private static EssentialsDatabase instance = null;
    public static EssentialsDatabase getInstance()
    {
        return instance;
    }
    
    public static void setup(String driver, String hostname, String database,
                             int port, String username, String password, String tablePrefix)
    {
        if (instance != null)
            return;
        
        instance = new EssentialsDatabase(
                driver,
                hostname,
                database,
                port,
                username,
                password,
                tablePrefix
        );
    
    
        try
        {
            instance.connect();
        } catch (SQLException throwables)
        {
            throwables.printStackTrace();
        }
    }
    
    public static void invalidate()
    {
        instance = null;
    }
    
    public EssentialsDatabase(String driver, String hostname, String database, int port, String username, String password, String tablePrefix)
    {
        this.driver = driver;
        this.hostname = hostname;
        this.database = database;
        this.port = port;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix;
        
        this.userDataTableName = tablePrefix + "userdata";
    }
    
    public void connect()
            throws SQLException
    {
        String url = driver + "://" + hostname + ":" + port + "/" + database;
        connection = DriverManager.getConnection(url, username, password);
        
        createTable(userDataTableName, DbUserData.class);
        createIndices(userDataTableName, DbUserData.class);
    }
    
    private void iterateFields(Class cls, Consumer<DatabaseColumn> columnProcessor)
    {
        Field[] fields = cls.getDeclaredFields();
        Class annotationType = DatabaseColumn.class;
        
        for (Field field : fields)
        {
            DatabaseColumn annotationObj = (DatabaseColumn) field.getAnnotation(annotationType);
            
            if (annotationObj == null)
                continue;
            
            columnProcessor.accept(annotationObj);
        }
    }
    
    public DbUserData getUserData(UUID uuid)
            throws SQLException
    {
        PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid, money, last_seen, data FROM " + userDataTableName + " WHERE uuid = ?");
        
        setValues(statement, uuid.toString());
        
        ResultSet resultSet = statement.executeQuery();
        
        resultSet.beforeFirst();
        
        if (!resultSet.next())
            return null;
        
        return new DbUserData(resultSet);
    }
    
    public boolean save(DbUserData userData)
            throws SQLException
    {
        PreparedStatement statement = connection.prepareStatement(
                "REPLACE INTO " + userDataTableName + " (uuid, money, last_seen, data) VALUES (?, ?, ?, ?)");
        
        setValues(statement,
                  userData.getUuid(),
                  userData.getMoney(),
                  userData.getLastSeen(),
                  userData.getData()
        );
        
        return statement.executeUpdate() != 0;
    }
    
    private void setValues(PreparedStatement statement, Object... arguments)
            throws SQLException
    {
        for (int i = 1; i <= arguments.length; i++)
            statement.setObject(i, arguments[i - 1]);
    }
    
    public void createIndices(String tableName, Class cls)
            throws SQLException
    {
        List<Index> indices = new ArrayList<>();
        
        iterateFields(cls, column -> {
            if (column.indexName().isEmpty())
                return;
    
            indices.add(new Index(column.indexName(), tableName, column.name()));
        });
        
        for (Index index : indices)
            createIndex(index.getIndexName(), index.getTableName(), index.getColumn());
    }
    
    public void createIndex(String name, String table, String column)
            throws SQLException
    {
            PreparedStatement statement = connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS " + name + " ON " + table + "(" + column + ")");

            statement.execute();
    }
    
    public boolean createTable(String tableName, Class cls)
            throws SQLException
    {
        List<String> columns = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        
        iterateFields(cls, column -> {
            sb.append(column.name()).append(' ').append(column.type());
            columns.add(sb.toString());
            
            sb.setLength(0);
        });
        
        return createTable(tableName, columns.toArray(new String[0]));
    }
    
    public boolean createTable(String tableName, String... columns)
            throws SQLException
    {
        tableName = formatTableName(tableName);
        
        StringBuilder fullStatement = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        fullStatement.append(tableName).append(" (\n");
        
        for (int i = 0; i < columns.length; i++)
        {
            fullStatement.append("                    ").append(columns[i]);
            
            if (i != columns.length - 1)
                fullStatement.append(",");
            
            fullStatement.append('\n');
        }
        
        fullStatement.append(')');
        
        Statement statement = connection.createStatement();
        statement.execute(fullStatement.toString());
        
        return true;
    }
    
    public String formatTableName(String tableName)
    {
        if (tablePrefix == null || tablePrefix.length() == 0)
            return tableName;
        
        if (!tableName.startsWith(tablePrefix))
            tableName = tablePrefix + tableName;
        
        return tableName;
    }
    
    private static class Index
    {
        private final String indexName;
        private final String tableName;
        private final String column;
        
        public Index(String name, String table, String column)
        {
            this.indexName = name;
            this.tableName = table;
            this.column = column;
        }
    
        public String getIndexName()
        {
            return indexName;
        }
    
        public String getTableName()
        {
            return tableName;
        }
    
        public String getColumn()
        {
            return column;
        }
    }
}
