package com.starrypvp;

import com.starrypvp.arena.ArenaManager;
import com.starrypvp.command.PvpCommand;
import com.starrypvp.data.DataManager;
import com.starrypvp.gui.SetupGui;
import com.starrypvp.listener.GameListener;
import com.starrypvp.match.MatchManager;
import com.starrypvp.party.PartyManager;
import com.starrypvp.arena.ArenaProtectionManager;
import com.starrypvp.data.RecoveryManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class StarryPvP extends JavaPlugin {
    private DataManager dataManager;
    private ArenaManager arenaManager;
    private PartyManager partyManager;
    private MatchManager matchManager;
    private SetupGui setupGui;
    private RecoveryManager recoveryManager;
    private ArenaProtectionManager arenaProtectionManager;
    private YamlConfiguration messages;
    private File messagesFile;

    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("arenas.yml");
        reloadFiles();

        dataManager = new DataManager(this);
        arenaManager = new ArenaManager(this);
        partyManager = new PartyManager();
        recoveryManager = new RecoveryManager(this);
        arenaProtectionManager = new ArenaProtectionManager(this);
        matchManager = new MatchManager(this);
        setupGui = new SetupGui(this);

        PvpCommand command = new PvpCommand(this);
        getCommand("pvp").setExecutor(command);
        getCommand("pvp").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(setupGui, this);
        getServer().getPluginManager().registerEvents(recoveryManager, this);
        getServer().getPluginManager().registerEvents(arenaProtectionManager, this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        recoveryManager.restoreOnlinePlayers();
    }

    public void onDisable() {
        if (matchManager != null) {
            matchManager.shutdown();
        }
        if (dataManager != null) {
            dataManager.save();
        }
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    public void reloadFiles() {
        reloadConfig();
        messagesFile = new File(getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        if (arenaManager != null) {
            arenaManager.reload();
        }
    }

    public String message(String key) {
        String prefix = messages.getString("prefix", "&8[&dStarryPvP&8] &r");
        String value = messages.getString(key, "&cMissing message: " + key);
        return ChatColor.translateAlternateColorCodes('&', prefix + value);
    }

    public String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public void saveMessages() {
        try {
            messages.save(messagesFile);
        } catch (IOException exception) {
            getLogger().warning(exception.getMessage());
        }
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public SetupGui getSetupGui() {
        return setupGui;
    }
    public RecoveryManager getRecoveryManager() {
        return recoveryManager;
    }

    public ArenaProtectionManager getArenaProtectionManager() {
        return arenaProtectionManager;
    }
}
