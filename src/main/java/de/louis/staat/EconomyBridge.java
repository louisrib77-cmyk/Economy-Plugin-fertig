package de.louis.staat;

import de.louis.economy.JobsBridge;
import de.louis.economy.LouisEconomy;
import org.bukkit.OfflinePlayer;

/**
 * Verbindung zum Geld: Das Geld liegt im JobsPlugin (über die JobsBridge von LouisEconomy).
 * Kein Vault nötig.
 */
final class EconomyBridge {

    private final LouisEconomy owner;

    EconomyBridge(LouisEconomy owner) {
        this.owner = owner;
    }

    /** Der Kontostand kann immer geprüft werden (Jobs-Geld). */
    boolean canVerify() {
        return true;
    }

    /** Nimmt Geld vom Spieler, aber nur, wenn genug da ist. */
    boolean withdraw(OfflinePlayer p, double amount) {
        if (amount <= 0) return false;
        try {
            JobsBridge bridge = owner.bridge();
            double bal = bridge.getMoney(p.getUniqueId());
            if (bal + 0.0001 < amount) return false;
            bridge.setMoney(p.getUniqueId(), StaatsPlugin.round2(bal - amount));
            return true;
        } catch (RuntimeException e) {
            owner.getLogger().warning("Abbuchung fehlgeschlagen: " + e);
            return false;
        }
    }

    /** Gibt dem Spieler Geld. */
    boolean deposit(OfflinePlayer p, double amount) {
        if (amount <= 0) return false;
        try {
            JobsBridge bridge = owner.bridge();
            double bal = bridge.getMoney(p.getUniqueId());
            bridge.setMoney(p.getUniqueId(), StaatsPlugin.round2(bal + amount));
            return true;
        } catch (RuntimeException e) {
            owner.getLogger().warning("Gutschrift fehlgeschlagen: " + e);
            return false;
        }
    }

    /** Kontostand oder -1, wenn nicht ermittelbar. */
    double balance(OfflinePlayer p) {
        try {
            return owner.bridge().getMoney(p.getUniqueId());
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
