package com.glacio.nomad.commands;

import com.glacio.nomad.Nomad;
import com.glacio.nomad.database.DatabaseManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StatsCommand {
    
    private final Nomad plugin;
    private final DatabaseManager databaseManager;
    
    public StatsCommand(Nomad plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }
    
    public boolean handleStats(CommandSender sender, String[] args) {
        if (databaseManager == null) {
            sender.sendMessage(ChatColor.RED + "Veritabanı bağlantısı yok!");
            return true;
        }
        
        if (args.length == 0) {
            // Show personal stats
            if (sender instanceof Player) {
                showPlayerStats(sender, (Player) sender);
            } else {
                sender.sendMessage(ChatColor.RED + "Bu komutu sadece oyuncular kullanabilir!");
            }
        } else {
            // Show different stats based on argument
            String type = args[0].toLowerCase();
            switch (type) {
                case "top":
                    showTopSpenders(sender);
                    break;
                case "items":
                    showTopItems(sender);
                    break;
                case "help":
                    showStatsHelp(sender);
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Bilinmeyen istatistik türü! /nomad stats help yazarak yardım alabilirsiniz.");
            }
        }
        
        return true;
    }
    
    private void showPlayerStats(CommandSender sender, Player player) {
        try {
            ResultSet rs = databaseManager.getPlayerPurchases(player.getUniqueId());
            
            sender.sendMessage(ChatColor.GOLD + "=== " + player.getName() + "'in Satın Alma Geçmişi ===");
            
            int totalPurchases = 0;
            double totalSpent = 0.0;
            boolean hasPurchases = false;
            
            while (rs.next()) {
                hasPurchases = true;
                String itemName = rs.getString("item_name");
                double price = rs.getDouble("price");
                String purchaseDate = rs.getString("purchase_date");
                
                sender.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + itemName + 
                    ChatColor.GRAY + " - " + ChatColor.GOLD + price + " Para" +
                    ChatColor.GRAY + " (" + purchaseDate.substring(0, 19) + ")");
                
                totalPurchases++;
                totalSpent += price;
            }
            
            if (!hasPurchases) {
                sender.sendMessage(ChatColor.GRAY + "Henüz satın alma geçmişiniz yok.");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Toplam: " + totalPurchases + " satın alma, " + totalSpent + " Para harcanmış.");
            }
            
            rs.close();
            
        } catch (SQLException e) {
            sender.sendMessage(ChatColor.RED + "İstatistikler yüklenirken hata oluştu!");
            plugin.getLogger().severe("Error showing player stats: " + e.getMessage());
        }
    }
    
    private void showTopSpenders(CommandSender sender) {
        try {
            ResultSet rs = databaseManager.getTopSpenders(10);
            
            sender.sendMessage(ChatColor.GOLD + "=== En Çok Para Harcayan Oyuncular ===");
            sender.sendMessage(ChatColor.GRAY + "İlk 10 oyuncu gösteriliyor:");
            
            int rank = 1;
            boolean hasData = false;
            
            while (rs.next()) {
                hasData = true;
                String playerName = rs.getString("player_name");
                int purchases = rs.getInt("total_purchases");
                double spent = rs.getDouble("total_spent");
                
                sender.sendMessage(ChatColor.YELLOW + String.valueOf(rank) + ". " + ChatColor.WHITE + playerName +
                    ChatColor.GRAY + " - " + ChatColor.GOLD + spent + " Para" +
                    ChatColor.GRAY + " (" + purchases + " satın alma)");
                
                rank++;
            }
            
            if (!hasData) {
                sender.sendMessage(ChatColor.GRAY + "Henüz veri bulunmuyor.");
            }
            
            rs.close();
            
        } catch (SQLException e) {
            sender.sendMessage(ChatColor.RED + "İstatistikler yüklenirken hata oluştu!");
            plugin.getLogger().severe("Error showing top spenders: " + e.getMessage());
        }
    }
    
    private void showTopItems(CommandSender sender) {
        try {
            ResultSet rs = databaseManager.getTopItems(10);
            
            sender.sendMessage(ChatColor.GOLD + "=== En Çok Satılan Ürünler ===");
            sender.sendMessage(ChatColor.GRAY + "İlk 10 ürün gösteriliyor:");
            
            int rank = 1;
            boolean hasData = false;
            
            while (rs.next()) {
                hasData = true;
                String itemName = rs.getString("item_name");
                int timesPurchased = rs.getInt("times_purchased");
                double revenue = rs.getDouble("total_revenue");
                
                sender.sendMessage(ChatColor.YELLOW + String.valueOf(rank) + ". " + ChatColor.WHITE + itemName +
                    ChatColor.GRAY + " - " + ChatColor.AQUA + timesPurchased + " satış" +
                    ChatColor.GRAY + " (" + ChatColor.GOLD + revenue + " Para" + ChatColor.GRAY + ")");
                
                rank++;
            }
            
            if (!hasData) {
                sender.sendMessage(ChatColor.GRAY + "Henüz veri bulunmuyor.");
            }
            
            rs.close();
            
        } catch (SQLException e) {
            sender.sendMessage(ChatColor.RED + "İstatistikler yüklenirken hata oluştu!");
            plugin.getLogger().severe("Error showing top items: " + e.getMessage());
        }
    }
    
    private void showStatsHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== İstatistik Komutları ===");
        sender.sendMessage(ChatColor.YELLOW + "/nomad stats" + ChatColor.GRAY + " - Kişisel satın alma geçmişini gösterir");
        sender.sendMessage(ChatColor.YELLOW + "/nomad stats top" + ChatColor.GRAY + " - En çok para harcayan oyuncuları gösterir");
        sender.sendMessage(ChatColor.YELLOW + "/nomad stats items" + ChatColor.GRAY + " - En çok satılan ürünleri gösterir");
        sender.sendMessage(ChatColor.YELLOW + "/nomad stats help" + ChatColor.GRAY + " - Bu yardım menüsünü gösterir");
    }
}
