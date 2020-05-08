package com.earth2me.essentials.commands;

import com.earth2me.essentials.CommandSource;
import com.earth2me.essentials.User;
import com.earth2me.essentials.database.EssentialsDatabase;
import com.earth2me.essentials.textreader.SimpleTextInput;
import com.earth2me.essentials.textreader.TextPager;
import com.earth2me.essentials.utils.NumberUtil;
import com.google.common.collect.Lists;
import org.bukkit.ChatColor;
import org.bukkit.Server;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.earth2me.essentials.I18n.tl;


public class Commandbalancetop extends EssentialsCommand {
    public Commandbalancetop() {
        super("balancetop");
    }

    private static final int CACHETIME = 2 * 60 * 1000;
    public static final int MINUSERS = 50;
    private static final SimpleTextInput cache = new SimpleTextInput();
    private static long cacheage = 0;
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private static final DecimalFormat countFormatter = new DecimalFormat("###,###");
    private static final DecimalFormat moneyFormatter = new DecimalFormat("0.00");
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    protected void run(final Server server, final CommandSource sender, final String commandLabel, final String[] args) throws Exception {
        int page = 0;
        boolean force = false;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                if (args[0].equalsIgnoreCase("force") && (!sender.isPlayer() || ess.getUser(sender.getPlayer()).isAuthorized("essentials.balancetop.force"))) {
                    force = true;
                }
            }
        }
    
        EssentialsDatabase database = EssentialsDatabase.getInstance();
        if (database != null)
        {
            runDb(database, sender, page);
            return;
        }

        if (!force && lock.readLock().tryLock()) {
            try {
                if (cacheage > System.currentTimeMillis() - CACHETIME) {
                    outputCache(sender, commandLabel, page);
                    return;
                }
                if (ess.getUserMap().getUniqueUsers() > MINUSERS) {
                    sender.sendMessage(tl("orderBalances", ess.getUserMap().getUniqueUsers()));
                }
            } finally {
                lock.readLock().unlock();
            }
            ess.runTaskAsynchronously(new Viewer(sender, commandLabel, page, force));
        } else {
            if (ess.getUserMap().getUniqueUsers() > MINUSERS) {
                sender.sendMessage(tl("orderBalances", ess.getUserMap().getUniqueUsers()));
            }
            ess.runTaskAsynchronously(new Viewer(sender, commandLabel, page, force));
        }

    }
    
    protected void runDb(final EssentialsDatabase db, final CommandSource sender, int page)
    {
        int pageSize = 8;
        
        try
        {
            EssentialsDatabase.BalanceTopResult data = db.getBalanceTop(page * pageSize, pageSize);
            
            LocalDateTime now = LocalDateTime.now();
            int totalPages = (int) Math.ceil(data.getCount() / (double)pageSize);
            
            // sender.sendMessage(ChatColor.GOLD + "Ordering balances of " + ChatColor.RED + countFormatter.format(data.getCount()) + " users, please wait...");
            sender.sendMessage(ChatColor.GOLD + "Top balances (" + now.format(dateTimeFormatter) + ")");
            sender.sendMessage(ChatColor.GOLD + " ---- Balancetop -- Page " + ChatColor.RED + countFormatter.format(page) +
                                       ChatColor.GOLD + "/" + ChatColor.RED + countFormatter.format(totalPages) + ChatColor.RED + " ----");
            
            sender.sendMessage(ChatColor.GOLD + "Server Total: " + ChatColor.RED + NumberUtil.displayCurrency(BigDecimal.valueOf(data.getTotal()), ess));
            
            int index = 1;
            for (EssentialsDatabase.BalanceTopEntry entry : data.getEntries())
                sender.sendMessage(countFormatter.format(entry.getIndex()) + ". " + entry.getName() + ", " +
                                           NumberUtil.displayCurrency(BigDecimal.valueOf(entry.getMoney()), ess));
            
            if (data.getEntries().size() >= pageSize)
                sender.sendMessage(ChatColor.GOLD + "Type " + ChatColor.RED + "/balancetop " + (page + 1) + ChatColor.GOLD + " to read the next page.");
            
        } catch (SQLException ex)
        {
            sender.sendMessage(ChatColor.RED + "An exception has occurred while processing your command.");
        }
    }
    
    private static void outputCache(final CommandSource sender, String command, int page) {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(cacheage);
        final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        sender.sendMessage(tl("balanceTop", format.format(cal.getTime())));
        new TextPager(cache).showPage(Integer.toString(page), null, "balancetop", sender);
    }


    private class Calculator implements Runnable {
        private final transient Viewer viewer;
        private final boolean force;

        public Calculator(final Viewer viewer, final boolean force) {
            this.viewer = viewer;
            this.force = force;
        }

        @Override
        public void run() {
            lock.writeLock().lock();
            try {
                if (force || cacheage <= System.currentTimeMillis() - CACHETIME) {
                    cache.getLines().clear();
                    final Map<String, BigDecimal> balances = new HashMap<>();
                    BigDecimal totalMoney = BigDecimal.ZERO;
                    if (ess.getSettings().isEcoDisabled()) {
                        if (ess.getSettings().isDebug()) {
                            ess.getLogger().info("Internal economy functions disabled, aborting baltop.");
                        }
                    } else {
                        for (UUID u : ess.getUserMap().getAllUniqueUsers()) {
                            final User user = ess.getUserMap().getUser(u);
                            if (user != null) {
                                if (!ess.getSettings().isNpcsInBalanceRanking() && user.isNPC()) {
                                    // Don't list NPCs in output
                                    continue;
                                }
                                if (!user.isAuthorized("essentials.balancetop.exclude")) {
                                    final BigDecimal userMoney = user.getMoney();
                                    user.updateMoneyCache(userMoney);
                                    totalMoney = totalMoney.add(userMoney);
                                    final String name = user.isHidden() ? user.getName() : user.getDisplayName();
                                    balances.put(name, userMoney);
                                }
                            }
                        }
                    }

                    final List<Map.Entry<String, BigDecimal>> sortedEntries = new ArrayList<>(balances.entrySet());
                    sortedEntries.sort((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));

                    cache.getLines().add(tl("serverTotal", NumberUtil.displayCurrency(totalMoney, ess)));
                    int pos = 1;
                    for (Map.Entry<String, BigDecimal> entry : sortedEntries) {
                        cache.getLines().add(pos + ". " + entry.getKey() + ", " + NumberUtil.displayCurrency(entry.getValue(), ess));
                        pos++;
                    }
                    cacheage = System.currentTimeMillis();
                }
            } finally {
                lock.writeLock().unlock();
            }
            ess.runTaskAsynchronously(viewer);
        }
    }


    private class Viewer implements Runnable {
        private final transient CommandSource sender;
        private final transient int page;
        private final transient boolean force;
        private final transient String commandLabel;

        public Viewer(final CommandSource sender, final String commandLabel, final int page, final boolean force) {
            this.sender = sender;
            this.page = page;
            this.force = force;
            this.commandLabel = commandLabel;
        }

        @Override
        public void run() {
            lock.readLock().lock();
            try {
                if (!force && cacheage > System.currentTimeMillis() - CACHETIME) {
                    outputCache(sender, commandLabel, page);
                    return;
                }
            } finally {
                lock.readLock().unlock();
            }
            ess.runTaskAsynchronously(new Calculator(new Viewer(sender, commandLabel, page, false), force));
        }
    }

    @Override
    protected List<String> getTabCompleteOptions(Server server, CommandSource sender, String commandLabel, String[] args) {
        if (args.length == 1) {
            List<String> options = Lists.newArrayList("1");
            if (!sender.isPlayer() || ess.getUser(sender.getPlayer()).isAuthorized("essentials.balancetop.force")) {
                options.add("force");
            }
            return options;
        } else {
            return Collections.emptyList();
        }
    }
}
