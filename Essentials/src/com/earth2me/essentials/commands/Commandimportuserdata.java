package com.earth2me.essentials.commands;

import com.earth2me.essentials.CommandSource;
import com.earth2me.essentials.User;
import com.earth2me.essentials.database.EssentialsDatabase;
import org.bukkit.ChatColor;
import org.bukkit.Server;

import java.util.function.Consumer;

public class Commandimportuserdata extends EssentialsCommand
{
    private static final String permission = "essentials.importuserdata";
    
    public Commandimportuserdata()
    {
        super("importuserdata");
    }
    
    
    @Override
    public void run(Server server, User user, String commandLabel, String[] args) throws Exception {
        if (!user.isAuthorized(permission))
        {
            user.sendMessage("You are not authorized to do that.");
            return;
        }
        
        startImport(user::sendMessage);
    }
    
    @Override
    public void run(Server server, CommandSource sender, String commandLabel, String[] args) throws Exception {
        if (sender.isPlayer() && !sender.getSender().hasPermission(permission))
        {
            sender.sendMessage("You are not authorized to do that.");
            return;
        }
        
        startImport(sender::sendMessage);
    }
    
    private void startImport(Consumer<String> messenger)
    {
        EssentialsDatabase database = EssentialsDatabase.getInstance();
    
        try
        {
            messenger.accept("Starting import of userdata. See console for current status.");
            database.importData();
        } catch (Throwable ex)
        {
            ex.printStackTrace();
            messenger.accept(ChatColor.RED + "An exception has occurred while importing userdata. See console for more information.");
        }
    }
}
