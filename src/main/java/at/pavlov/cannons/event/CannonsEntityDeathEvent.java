package at.pavlov.cannons.event;

import at.pavlov.cannons.projectile.Projectile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class CannonsEntityDeathEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private LivingEntity killedEntity;
    private Projectile projectile;
    private UUID cannonID;
    private UUID shooter;
    private boolean cancelled;

    public CannonsEntityDeathEvent(LivingEntity killedEntity, Projectile projectile, UUID cannonID, UUID shooter) {
        this.killedEntity = killedEntity;
        this.projectile = projectile;
        this.cannonID = cannonID;
        this.shooter = shooter;
        this.cancelled = false;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public Projectile getProjectile() {
        return projectile;
    }

    public void setProjectile(Projectile projectile) {
        this.projectile = projectile;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public UUID getCannonID() {
        return cannonID;
    }

    public void setCannonID(UUID cannonID) {
        this.cannonID = cannonID;
    }

    public UUID getShooter() {
        return shooter;
    }

    public void setShooter(UUID shooter) {
        this.shooter = shooter;
    }

    public LivingEntity getKilledEntity() {
        return killedEntity;
    }

    public void setKilledEntity(LivingEntity killedEntity) {
        this.killedEntity = killedEntity;
    }
}
