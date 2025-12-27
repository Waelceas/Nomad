package com.glacio.nomad;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import com.glacio.nomad.database.DatabaseManager;
import com.glacio.nomad.commands.StatsCommand;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class Nomad extends JavaPlugin implements CommandExecutor, Listener {

    private List<Map<String, Object>> currentDailyItems = new ArrayList<>();
    private String menuTitle;
    private static Economy econ = null;
    private BukkitRunnable refreshScheduler;
    private DatabaseManager databaseManager;
    private StatsCommand statsCommand;

    @Override
    public void onEnable() {
        getLogger().info("=== Starting Nomad v" + getDescription().getVersion() + " ===");
        
        try {
            // Save default config if it doesn't exist
            getLogger().info("Loading configuration...");
            saveDefaultConfig();
            reloadConfig();
            
            // Load configuration
            try {
                loadConfigData();
                getLogger().info("Configuration loaded successfully!");
            } catch (Exception e) {
                getLogger().severe("Failed to load configuration! " + e.getMessage());
                getLogger().severe("Disabling plugin due to configuration error!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // Setup economy (Vault)
            getLogger().info("Setting up Vault economy...");
            if (!setupEconomy()) {
                getLogger().warning("Vault or an economy plugin was not found. Purchase features will be disabled.");
            } else {
                getLogger().info("Vault economy integration successful!");
            }

            // Register command
            getLogger().info("Registering commands...");
            try {
                getCommand("nomad").setExecutor(this);
                getLogger().info("Commands registered successfully!");
            } catch (Exception e) {
                getLogger().severe("Failed to register command: " + e.getMessage());
                e.printStackTrace();
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // Register events
            getLogger().info("Registering events...");
            try {
                getServer().getPluginManager().registerEvents(this, this);
                getLogger().info("Events registered successfully!");
            } catch (Exception e) {
                getLogger().severe("Failed to register events: " + e.getMessage());
                e.printStackTrace();
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // Start scheduler
            getLogger().info("Starting scheduler...");
            try {
                startRefreshScheduler();
                getLogger().info("Scheduler started successfully!");
            } catch (Exception e) {
                getLogger().severe("Failed to start scheduler: " + e.getMessage());
                e.printStackTrace();
                // Don't disable the plugin for scheduler errors
            }
            
            // Initialize database
            getLogger().info("Initializing database...");
            try {
                databaseManager = new DatabaseManager(this);
                if (databaseManager.connect()) {
                    getLogger().info("Database initialized successfully!");
                    statsCommand = new StatsCommand(this, databaseManager);
                } else {
                    getLogger().warning("Database initialization failed, continuing without database features.");
                }
            } catch (Exception e) {
                getLogger().severe("Failed to initialize database: " + e.getMessage());
                e.printStackTrace();
                // Don't disable the plugin for database errors
            }
            
            getLogger().info("Nomad plugin has been enabled successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to enable Nomad plugin: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    private void loadConfigData() {
        reloadConfig();
        menuTitle = ChatColor.translateAlternateColorCodes('&', getConfig().getString("menu-title", "&6Nomad"));
        
        String lastRefresh = getConfig().getString("last-refresh-date", "");
        String today = LocalDate.now().toString();

        if (!today.equals(lastRefresh) || getConfig().getList("current-daily-items") == null || getConfig().getList("current-daily-items").isEmpty()) {
            refreshDailyItems();
        } else {
            loadCurrentItemsFromConfig();
        }
    }

    private void loadCurrentItemsFromConfig() {
        currentDailyItems.clear();
        List<?> list = getConfig().getList("current-daily-items");
        if (list != null) {
            for (Object obj : list) {
                if (obj instanceof Map) {
                    currentDailyItems.add((Map<String, Object>) obj);
                }
            }
        }
    }

    private void refreshDailyItems() {
        List<Map<?, ?>> pool = getConfig().getMapList("item-pool");
        if (pool.isEmpty()) return;

        List<Map<?, ?>> shuffledPool = new ArrayList<>(pool);
        Collections.shuffle(shuffledPool, new Random());

        currentDailyItems.clear();
        int count = getConfig().getInt("daily-item-count", 5);
        int itemsToAdd = Math.min(count, shuffledPool.size());
        
        for (int i = 0; i < itemsToAdd; i++) {
            Map<String, Object> itemData = new HashMap<>();
            Map<?, ?> source = shuffledPool.get(i);
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                itemData.put(entry.getKey().toString(), entry.getValue());
            }
            currentDailyItems.add(itemData);
        }

        getConfig().set("current-daily-items", currentDailyItems);
        getConfig().set("last-refresh-date", LocalDate.now().toString());
        saveConfig();
    }

    private void startRefreshScheduler() {
        if (refreshScheduler != null) {
            refreshScheduler.cancel();
        }
        
        refreshScheduler = new BukkitRunnable() {
            @Override
            public void run() {
                int refreshHour = getConfig().getInt("refresh-hour", 18);
                LocalTime now = LocalTime.now();
                String lastRefresh = getConfig().getString("last-refresh-date", "");
                String today = LocalDate.now().toString();

                if (now.getHour() >= refreshHour && !today.equals(lastRefresh)) {
                    refreshDailyItems();
                }
            }
        };
        refreshScheduler.runTaskTimer(this, 20L * 30, 20L * 60);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            handleHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String permission = getConfig().getString("permissions." + subCommand, "nomad.use");
        
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(ChatColor.RED + "Bu komutu kullanmak için yetkiniz yok!");
            return true;
        }

        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "help":
                return handleHelp(sender);
            case "refresh":
                return handleRefresh(sender);
            case "settime":
                return handleSetTime(sender, args);
            case "pool":
                return handlePool(sender, args);
            case "when":
                return handleWhen(sender);
            case "shop":
                if (sender instanceof Player) {
                    openMerchantMenu((Player) sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "Bu komutu sadece oyuncular kullanabilir.");
                }
                return true;
            case "stats":
                if (statsCommand != null) {
                    String[] statsArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                    return statsCommand.handleStats(sender, statsArgs);
                } else {
                    sender.sendMessage(ChatColor.RED + "Veritabanı bağlantısı yok!");
                    return true;
                }
            default:
                sender.sendMessage(ChatColor.RED + "Bilinmeyen komut! /nomad help yazarak yardım alabilirsiniz.");
                return true;
        }
    }

    private void openMerchantMenu(Player player) {
        int size = 27;
        Inventory inv = Bukkit.createInventory(null, size, menuTitle);
        int[] slots = {11, 12, 13, 14, 15, 10, 16}; 
        
        for (int i = 0; i < Math.min(currentDailyItems.size(), slots.length); i++) {
            Map<String, Object> data = currentDailyItems.get(i);
            try {
                Material mat = Material.valueOf((String) data.get("material"));
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();

                if (meta != null) {
                    String name = (String) data.get("name");
                    if (name != null) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

                    List<String> lore = new ArrayList<>();
                    lore.add("");
                    lore.add(ChatColor.GRAY + "Fiyat: " + ChatColor.GOLD + data.get("price") + " Para");
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Satın almak için tıklayın!");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slots[i], item);
            } catch (Exception ignored) {}
        }
        player.openInventory(inv);
        
        // Play sound effect when menu opens
        try {
            String soundName = getConfig().getString("sounds.open-menu", "BLOCK_CHEST_OPEN");
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception e) {
            if (getConfig().getBoolean("debug", false)) {
                getLogger().warning("Failed to play open-menu sound: " + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(menuTitle)) return;
        event.setCancelled(true);
        
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        
        int[] slots = {11, 12, 13, 14, 15, 10, 16};
        int itemIndex = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                itemIndex = i;
                break;
            }
        }
        
        if (itemIndex == -1 || itemIndex >= currentDailyItems.size()) return;
        
        Map<String, Object> itemData = currentDailyItems.get(itemIndex);
        double price = Double.parseDouble(itemData.get("price").toString());
        Material mat = Material.valueOf((String) itemData.get("material"));
        
        if (econ == null) {
            player.sendMessage(ChatColor.RED + "Ekonomi sistemi bagli degil!");
            return;
        }
        
        if (econ.getBalance(player) >= price) {
            econ.withdrawPlayer(player, price);
            player.getInventory().addItem(new ItemStack(mat, 1));
            player.sendMessage(ChatColor.GREEN + "Başarıyla satın aldınız: " + ChatColor.YELLOW + mat.name() + ChatColor.GREEN + " (-" + price + " Para)");
            
            // Record purchase in database
            if (databaseManager != null) {
                String itemName = (String) itemData.get("name");
                if (itemName == null) itemName = mat.name();
                databaseManager.recordPurchase(player.getUniqueId(), player.getName(), mat.name(), itemName, price);
            }
            
            // Play success sound
            try {
                String soundName = getConfig().getString("sounds.purchase", "ENTITY_PLAYER_LEVELUP");
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (Exception e) {
                if (getConfig().getBoolean("debug", false)) {
                    getLogger().warning("Failed to play purchase sound: " + e.getMessage());
                }
            }
        } else {
            player.sendMessage(ChatColor.RED + "Yeterli paranız yok! Gereken: " + price + " Para");
            
            // Play error sound
            try {
                String soundName = getConfig().getString("sounds.error", "ENTITY_VILLAGER_NO");
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (Exception e) {
                if (getConfig().getBoolean("debug", false)) {
                    getLogger().warning("Failed to play error sound: " + e.getMessage());
                }
            }
        }
    }

    private boolean handleReload(CommandSender sender) {
        reloadConfig();
        loadConfigData();
        startRefreshScheduler();
        sender.sendMessage(ChatColor.GREEN + "Nomad yapılandırması yeniden yüklendi!");
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        boolean isAdmin = sender.hasPermission("nomad.admin.info");
        
        sender.sendMessage(ChatColor.GOLD + "=== Nomad Yardım ===");
        sender.sendMessage(ChatColor.YELLOW + "/nomad" + ChatColor.GRAY + " - Bu yardım menüsünü gösterir");
        sender.sendMessage(ChatColor.YELLOW + "/nomad shop" + ChatColor.GRAY + " - Ticaret menüsünü açar");
        sender.sendMessage(ChatColor.YELLOW + "/nomad stats" + ChatColor.GRAY + " - İstatistikleri gösterir");
        
        if (isAdmin) {
            sender.sendMessage(ChatColor.RED + "--- Admin Komutları ---");
            sender.sendMessage(ChatColor.YELLOW + "/nomad reload" + ChatColor.GRAY + " - Yapılandırmayı yeniden yükler");
            sender.sendMessage(ChatColor.YELLOW + "/nomad refresh" + ChatColor.GRAY + " - Günlük ürünleri yeniler");
            sender.sendMessage(ChatColor.YELLOW + "/nomad settime <dakika>" + ChatColor.GRAY + " - Yenileme süresini ayarlar");
            sender.sendMessage(ChatColor.YELLOW + "/nomad pool" + ChatColor.GRAY + " - Ürün havuzunu gösterir");
            sender.sendMessage(ChatColor.YELLOW + "/nomad pool add/remove" + ChatColor.GRAY + " - Ürün havuzunu yönetir");
        }
        
        sender.sendMessage(ChatColor.AQUA + "--- Oyuncu Komutları ---");
        sender.sendMessage(ChatColor.YELLOW + "/nomad when" + ChatColor.GRAY + " - Sonraki yenileme süresini gösterir");
        sender.sendMessage(ChatColor.YELLOW + "/nomad help" + ChatColor.GRAY + " - Bu yardım menüsünü gösterir");
        
        return true;
    }

    private boolean handleRefresh(CommandSender sender) {
        refreshDailyItems();
        sender.sendMessage(ChatColor.GREEN + "Günlük ürünler başarıyla yenilendi!");
        return true;
    }

    private boolean handleSetTime(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Kullanım: /nomad settime <dakika>");
            return true;
        }

        try {
            int minutes = Integer.parseInt(args[1]);
            if (minutes < 1) {
                sender.sendMessage(ChatColor.RED + "Süre 1 dakikadan az olamaz!");
                return true;
            }

            getConfig().set("refresh-interval-minutes", minutes);
            saveConfig();
            startRefreshScheduler();
            
            sender.sendMessage(ChatColor.GREEN + "Yenileme süresi " + minutes + " dakika olarak ayarlandı!");
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Geçersiz sayı! Lütfen geçerli bir dakika değeri girin.");
            return true;
        }
    }

    private boolean handlePool(CommandSender sender, String[] args) {
        if (args.length == 1) {
            showPool(sender);
            return true;
        }

        String action = args[1].toLowerCase();
        if (action.equals("add")) {
            return handlePoolAdd(sender, args);
        } else if (action.equals("remove")) {
            return handlePoolRemove(sender, args);
        } else {
            sender.sendMessage(ChatColor.RED + "Kullanım: /nomad pool <add/remove>");
            return true;
        }
    }

    private void showPool(CommandSender sender) {
        List<Map<?, ?>> pool = getConfig().getMapList("item-pool");
        sender.sendMessage(ChatColor.GOLD + "=== Ürün Havuzu ===");
        
        if (pool.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Havuzda ürün bulunmuyor.");
            return;
        }

        for (int i = 0; i < pool.size(); i++) {
            Map<?, ?> item = pool.get(i);
            String material = (String) item.get("material");
            String name = (String) item.get("name");
            String price = item.get("price").toString();
            
            sender.sendMessage(ChatColor.YELLOW + String.valueOf(i + 1) + ". " + ChatColor.WHITE + 
                (name != null ? name : material) + ChatColor.GRAY + " - " + ChatColor.GOLD + price + " Para");
        }
    }

    private boolean handlePoolAdd(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Kullanım: /nomad pool add <materyal> <isim> <fiyat>");
            return true;
        }

        String material = args[2].toUpperCase();
        String name = ChatColor.translateAlternateColorCodes('&', String.join(" ", Arrays.copyOfRange(args, 3, args.length - 1)));
        
        try {
            double price = Double.parseDouble(args[args.length - 1]);
            
            try {
                Material.valueOf(material);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "Geçersiz materyal: " + material);
                return true;
            }

            List<Map<?, ?>> pool = getConfig().getMapList("item-pool");
            Map<String, Object> newItem = new HashMap<>();
            newItem.put("material", material);
            newItem.put("name", name);
            newItem.put("price", price);
            
            pool.add(newItem);
            getConfig().set("item-pool", pool);
            saveConfig();
            
            sender.sendMessage(ChatColor.GREEN + "Ürün havuza eklendi: " + name + " (" + material + ")");
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Geçersiz fiyat: " + args[args.length - 1]);
            return true;
        }
    }

    private boolean handlePoolRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Kullanım: /noma<d pool remove <numara>");
            return true;
        }

        try {
            int index = Integer.parseInt(args[2]) - 1;
            List<Map<?, ?>> pool = getConfig().getMapList("item-pool");
            
            if (index < 0 || index >= pool.size()) {
                sender.sendMessage(ChatColor.RED + "Geçersiz numara! 1-" + pool.size() + " arası bir numara girin.");
                return true;
            }

            Map<?, ?> removed = pool.remove(index);
            getConfig().set("item-pool", pool);
            saveConfig();
            
            sender.sendMessage(ChatColor.GREEN + "Ürün havuzdan kaldırıldı: " + removed.get("name"));
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Geçersiz numara: " + args[2]);
            return true;
        }
    }

    private boolean handleWhen(CommandSender sender) {
        String lastRefresh = getConfig().getString("last-refresh-date", "");
        String today = LocalDate.now().toString();
        int refreshHour = getConfig().getInt("refresh-hour", 18);
        int refreshInterval = getConfig().getInt("refresh-interval-minutes", 1440);
        
        LocalTime now = LocalTime.now();
        
        if (today.equals(lastRefresh)) {
            LocalTime refreshTime = LocalTime.of(refreshHour, 0);
            if (now.isBefore(refreshTime)) {
                long minutesUntilRefresh = java.time.Duration.between(now, refreshTime).toMinutes();
                sender.sendMessage(ChatColor.YELLOW + "Sonraki ürün yenilemesine " + minutesUntilRefresh + " dakika kaldı.");
            } else {
                long minutesUntilNext = refreshInterval * 60 - (now.toSecondOfDay() - refreshTime.toSecondOfDay()) / 60;
                sender.sendMessage(ChatColor.YELLOW + "Sonraki ürün yenilemesine " + minutesUntilNext + " dakika kaldı.");
            }
        } else {
            LocalTime refreshTime = LocalTime.of(refreshHour, 0);
            if (now.isAfter(refreshTime)) {
                long minutesUntilTomorrow = (24 * 60 - now.toSecondOfDay() / 60) + refreshHour * 60;
                sender.sendMessage(ChatColor.YELLOW + "Sonraki ürün yenilemesine " + minutesUntilTomorrow + " dakika kaldı.");
            } else {
                long minutesUntilRefresh = java.time.Duration.between(now, refreshTime).toMinutes();
                sender.sendMessage(ChatColor.YELLOW + "Sonraki ürün yenilemesine " + minutesUntilRefresh + " dakika kaldı.");
            }
        }
        
        return true;
    }
    
    @Override
    public void onDisable() {
        getLogger().info("=== Disabling Nomad v" + getDescription().getVersion() + " ===");
        
        // Cancel scheduler
        if (refreshScheduler != null) {
            refreshScheduler.cancel();
        }
        
        // Disconnect database
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        
        getLogger().info("Nomad plugin has been disabled successfully!");
    }
}
