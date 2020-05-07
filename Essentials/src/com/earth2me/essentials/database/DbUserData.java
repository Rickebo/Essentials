package com.earth2me.essentials.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DbUserData
{
    private static final String uuidColumnName = "uuid";
    private static final String moneyColumnName = "money";
    private static final String lastSeenColumnName = "last_seen";
    private static final String dataColumnName = "data";
    
    @DatabaseColumn(name = uuidColumnName, type = "CHAR(36) NOT NULL PRIMARY KEY", indexName = "uuid_index")
    private String uuid;
    
    @DatabaseColumn(name = moneyColumnName, type = "DOUBLE NOT NULL", indexName = "money_index")
    private double money;
    
    @DatabaseColumn(name = lastSeenColumnName, type = "BIGINT UNSIGNED", indexName = "last_seen")
    private long lastSeen;
    
    @DatabaseColumn(name = dataColumnName, type = "TEXT NOT NULL")
    private String data;
    
    public DbUserData()
    {
    
    }
    
    public DbUserData(UUID uuid, double money, long lastSeen, String data)
    {
        this.uuid = uuid.toString();
        this.money = money;
        this.lastSeen = lastSeen;
        this.data = data;
    }
    
    public DbUserData(ResultSet resultSet)
            throws SQLException
    {
        uuid = resultSet.getString(uuidColumnName);
        money = resultSet.getDouble(moneyColumnName);
        lastSeen = resultSet.getLong(lastSeenColumnName);
        data = resultSet.getString(dataColumnName);
    }
    
    public String getUuid()
    {
        return uuid;
    }
    
    public void setUuid(String uuid)
    {
        this.uuid = uuid;
    }
    
    public double getMoney()
    {
        return money;
    }
    
    public void setMoney(double money)
    {
        this.money = money;
    }
    
    public long getLastSeen()
    {
        return lastSeen;
    }
    
    public void setLastSeen(long lastSeen)
    {
        this.lastSeen = lastSeen;
    }
    
    public String getData()
    {
        return data;
    }
    
    public void setData(String data)
    {
        this.data = data;
    }
}
