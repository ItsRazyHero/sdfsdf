package io.github.itsrazyhero.menuforge.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/** Vault economy through reflection: works when Vault is present, degrades gracefully when not. */
public final class VaultHook {

    private Object economy;
    private Method getBalance, withdraw, deposit, format, success;

    public VaultHook(Logger log) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(eco);
            if (rsp == null) return;
            economy = rsp.getProvider();
            getBalance = eco.getMethod("getBalance", OfflinePlayer.class);
            withdraw = eco.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            deposit = eco.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            format = eco.getMethod("format", double.class);
            success = Class.forName("net.milkbowl.vault.economy.EconomyResponse").getMethod("transactionSuccess");
            log.info("Hooked Vault economy (" + economy.getClass().getSimpleName() + ").");
        } catch (ReflectiveOperationException e) {
            log.warning("Vault found but economy could not be hooked: " + e);
            economy = null;
        }
    }

    public boolean available() {
        return economy != null;
    }

    public double balance(OfflinePlayer player) {
        if (!available()) return 0;
        try {
            return (double) getBalance.invoke(economy, player);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        return transact(withdraw, player, amount);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return transact(deposit, player, amount);
    }

    public String format(double amount) {
        if (available()) {
            try {
                return (String) format.invoke(economy, amount);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return amount == Math.floor(amount) ? String.valueOf((long) amount) : String.format("%.2f", amount);
    }

    private boolean transact(Method method, OfflinePlayer player, double amount) {
        if (!available()) return false;
        try {
            Object response = method.invoke(economy, player, amount);
            return (boolean) success.invoke(response);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
