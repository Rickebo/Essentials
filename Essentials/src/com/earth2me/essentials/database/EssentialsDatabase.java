package com.earth2me.essentials.database;

import com.earth2me.essentials.*;
import jdk.jfr.internal.Logger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedList;
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
    
    private final Essentials essentials;
    
    private static EssentialsDatabase instance = null;
    public static EssentialsDatabase getInstance()
    {
        return instance;
    }
    
    public static void setup(Essentials essentials, String driver, String hostname, String database,
                             int port, String username, String password, String tablePrefix)
    {
        if (instance != null)
            return;
        
        instance = new EssentialsDatabase(
                essentials,
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
    
    public void enumerateIds(Consumer<String> enumerator) throws SQLException
    {
        StringBuffer sql;
        PreparedStatement statement = connection.prepareStatement("SELECT uuid FROM " + userDataTableName);
        
        ResultSet resultSet = statement.executeQuery();
        
        resultSet.beforeFirst();
        
        while (resultSet.next())
            enumerator.accept(resultSet.getString(1));
    }
    
    public static void invalidate()
    {
        instance = null;
    }
    
    public EssentialsDatabase(Essentials essentials, String driver, String hostname, String database, int port, String username, String password, String tablePrefix)
    {
        this.driver = driver;
        this.hostname = hostname;
        this.database = database;
        this.port = port;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix;
        
        this.essentials = essentials;
        
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
    
    public boolean hasPlayerName(String name)
            throws SQLException
    {
        PreparedStatement statement = connection.prepareStatement("SELECT EXISTS(SELECT * FROM " + userDataTableName + " WHERE name = ?)");
        
        setValues(statement, name);
        
        ResultSet resultSet = statement.executeQuery();
        
        resultSet.beforeFirst();
        
        return resultSet.next() && resultSet.getInt(1) > 0;
    }
    
    public boolean hasPlayer(UUID uuid)
            throws SQLException
    {
        PreparedStatement statement = connection.prepareStatement("SELECT EXISTS(SELECT * FROM " + userDataTableName + " WHERE uuid = ?)");
    
        setValues(statement, uuid.toString());
    
        ResultSet resultSet = statement.executeQuery();
    
        resultSet.beforeFirst();
    
        return resultSet.next() && resultSet.getInt(1) > 0;
    }
    
    public DbUserData getUserData(UUID uuid)
            throws SQLException
    {
        PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM " + userDataTableName + " WHERE uuid = ?");
        
        setValues(statement, uuid.toString());
        
        ResultSet resultSet = statement.executeQuery();
        
        resultSet.beforeFirst();
        
        if (!resultSet.next())
            return null;
        
        return new DbUserData(resultSet);
    }
    
    public void importData()
    {
        final File userdir = new File(essentials.getDataFolder(), "userdata");
        int currentUser = 0;
        
        File[] files = userdir.listFiles();
        
        if (files == null)
        {
            essentials.getLogger().info("Cannot import userdata since there is no userdata to import.");
            return;
        }
    
        EssentialsConf.setEnableDatabaseLoading(false);

        try
        {
            for (File userFile : files)
            {
                if (currentUser++ % 100 == 0)
                    essentials.getLogger().info("Importing userdata " + currentUser + " (" + userFile.getName() + ")");
        
                String uuidStr = userFile.getName().replace(".yml", "");
        
                UUID uuid = UUID.fromString(uuidStr);
        
                if (uuid == null)
                    continue;
        
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        
                EssentialsUserConf conf = new EssentialsUserConf(player.getName(), uuid, userFile);
                conf.load();
                conf.save();
            }
        } finally
        {
            EssentialsConf.setEnableDatabaseLoading(true);
        }
    }
    
    public BalanceTopResult getBalanceTop(int index, int count) throws SQLException
    {
        PreparedStatement statement = connection.prepareStatement(
                "SELECT (@rowNum:=@rowNum + 1) AS num, uuid, name, money FROM " + userDataTableName +
                ", (SELECT @rowNum:=0) as t WHERE exempt_balancetop = FALSE ORDER BY money DESC LIMIT ? OFFSET ?");
    
        statement.setInt(1, count);
        statement.setInt(2, index);
        
        ResultSet resultSet = statement.executeQuery();
        
        resultSet.beforeFirst();
        
        List<BalanceTopEntry> userData = new LinkedList<>();
        
        
        while (resultSet.next())
        {
            int num = resultSet.getInt(1);
            String uuid = resultSet.getString("uuid");
            String name = resultSet.getString("name");
            double money = resultSet.getDouble("money");
            
            userData.add(new BalanceTopEntry(num, money, uuid, name));
        }
        
        statement = connection.prepareStatement("SELECT SUM(money), COUNT(money) FROM " + userDataTableName + " WHERE exempt_balancetop = FALSE");
        
        resultSet = statement.executeQuery();
        
        resultSet.beforeFirst();
        
        double total = 0;
        int accountCount = 0;
        
        if (resultSet.next())
        {
            total = resultSet.getDouble(1);
            accountCount = resultSet.getInt(2);
        }
        
        return new BalanceTopResult(userData, total, accountCount);
    }
    
    public boolean save(DbUserData userData, boolean isExemptOverride)
            throws SQLException
    {
        PreparedStatement statement = connection.prepareStatement(
                "REPLACE INTO " + userDataTableName + " (uuid, money, last_seen, data, exempt_balancetop, name) VALUES (?, ?, ?, ?, ?, ?)");
        
        User user = essentials.getUser(UUID.fromString(userData.getUuid()));
        
        boolean isExempt = true;
        String name = "";
        
        if (user != null && user.getBase() instanceof OfflinePlayer)
        {
            if (!isExemptOverride)
                isExempt = user.getBase().hasPermission("essentials.balancetop.exclude");
            
            name = user.getName();
        }
        
        setValues(statement,
                  userData.getUuid(),
                  userData.getMoney(),
                  userData.getLastSeen(),
                  userData.getData(),
                  isExempt,
                  name
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
    
    public Essentials getEssentials()
    {
        return essentials;
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
    
    public static class BalanceTopResult
    {
        private final List<BalanceTopEntry> entries;
        private final double total;
        private final int count;
        
        public BalanceTopResult(List<BalanceTopEntry> entries, double total, int count)
        {
            this.entries = entries;
            this.total = total;
            this.count = count;
        }
    
        public List<BalanceTopEntry> getEntries()
        {
            return entries;
        }
    
        public double getTotal()
        {
            return total;
        }
        
        public int getCount()
        {
            return count;
        }
    }
    
    public static class BalanceTopEntry
    {
        private final int index;
        private final double money;
        private final String uuid;
        private final String name;
        
        public BalanceTopEntry(int index, double money, String uuid, String name)
        {
            this.index = index;
            this.money = money;
            this.uuid = uuid;
            this.name = name;
        }
    
        public int getIndex()
        {
            return index;
        }
    
        public double getMoney()
        {
            return money;
        }
    
        public String getUuid()
        {
            return uuid;
        }
    
        public String getName()
        {
            return name;
        }
    }
}
