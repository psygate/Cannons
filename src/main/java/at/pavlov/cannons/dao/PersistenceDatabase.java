package at.pavlov.cannons.dao;

import at.pavlov.cannons.Cannons;
import at.pavlov.cannons.cannon.Cannon;
import at.pavlov.cannons.cannon.CannonDesign;
import at.pavlov.cannons.cannon.CannonManager;
import at.pavlov.cannons.projectile.Projectile;
import at.pavlov.cannons.projectile.ProjectileStorage;
import at.pavlov.cannons.scheduler.CreateCannon;
import at.pavlov.cannons.utils.DelayedTask;
import com.avaje.ebean.Query;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PersistenceDatabase {
    private Cannons plugin;

    public PersistenceDatabase(Cannons _plugin) {
        plugin = _plugin;
    }

    /**
     * loads all cannons from the database
     */
    public void loadCannonsAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            public void run() {
                loadCannons();
            }
        });
    }


    /**
     * loads all cannons from the database
     *
     * @return true if loading was successful
     */
    private boolean loadCannons() {
        plugin.getCannonManager().clearCannonList();
        // create a query that returns CannonBean
        Query<CannonBean> query = plugin.getDatabase().find(CannonBean.class);
        List<CannonBean> beans = query.findList();

        // process the result
        if (beans == null || beans.size() == 0) {
            // nothing found; list is empty
            return false;
        } else {
            ArrayList<UUID> invalid = new ArrayList<UUID>();
            int i = 0;
            // found cannons - load them
            for (CannonBean bean : beans) {

                //check if cannon design exists
                CannonDesign design = plugin.getCannonDesign(bean.getDesignId());
                if (design == null) {
                    plugin.logSevere("Design " + bean.getDesignId() + " not found in plugin/designs");
                    invalid.add(bean.getId());
                    //deleteCannon(bean.getId());
                } else {
                    //load values for the cannon
                    UUID world = bean.getWorld();
                    //test if world is valid
                    World w = Bukkit.getWorld(world);
                    if (w == null) {
                        plugin.logDebug("World of cannon " + bean.getId() + " is not valid");
                        invalid.add(bean.getId());
                        continue;
                    }
                    UUID owner = bean.getOwner();
                    if (owner == null) {
                        plugin.logDebug("Owner of cannon " + bean.getId() + " is null");
                        invalid.add(bean.getId());
                        continue;
                    }
                    Vector offset = new Vector(bean.getLocX(), bean.getLocY(), bean.getLocZ());
                    BlockFace cannonDirection = BlockFace.valueOf(bean.getCannonDirection());

                    //make a cannon
                    Cannon cannon = new Cannon(design, world, offset, cannonDirection, owner);
                    // cannon created - load properties
                    cannon.setUID(bean.getId());
                    cannon.setCannonName(bean.getName());
                    cannon.setSoot(bean.getSoot());
                    cannon.setLoadedGunpowder(bean.getGunpowder());

                    //load projectile
                    cannon.setLoadedProjectile(ProjectileStorage.getProjectile(cannon, bean.getProjectileID()));

                    cannon.setProjectilePushed(bean.getProjectilePushed());

                    //angles
                    cannon.setHorizontalAngle(bean.getHorizontalAngle());
                    cannon.setVerticalAngle(bean.getVerticalAngle());

                    //temperature
                    cannon.setTemperature(bean.getCannonTemperature());
                    cannon.setTemperatureTimeStamp(bean.getCannonTemperatureTimestamp());

                    //amount of fired cannonballs
                    cannon.setFiredCannonballs(bean.getFiredCannonballs());

                    //add a cannon to the cannon list
                    BukkitTask task = new CreateCannon(plugin, cannon).runTask(plugin);
                    //plugin.createCannon(cannon);
                    i++;
                }
            }
            plugin.getDatabase().beginTransaction();
            //remove invalid cannons form the database
            for (UUID inv : invalid) {
                deleteCannon(inv);
            }
            plugin.getDatabase().commitTransaction();
            plugin.getDatabase().endTransaction();

            plugin.logDebug(i + " cannons loaded from the database");
            return true;
        }
    }

    /**
     * save all cannons in the database
     */
    public void saveAllCannonsAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            public void run() {
                saveAllCannons();
            }
        });
    }


    /**
     * save all cannons in the database
     */
    public void saveAllCannons() {

        // get list of all cannons
        ConcurrentHashMap<UUID, Cannon> cannonList = CannonManager.getCannonList();

        if (plugin == null || plugin.getDatabase() == null)
            return;

        plugin.getDatabase().beginTransaction();
        // save all cannon to database
        for (Cannon cannon : cannonList.values()) {
            boolean noError = saveCannon(cannon);
            if (!noError) {
                //if a error occurs while save the cannon, stop it
                plugin.getDatabase().endTransaction();
                return;
            }
        }
        plugin.getDatabase().commitTransaction();
        plugin.getDatabase().endTransaction();
    }

    /**
     * saves this cannon in the database
     *
     * @param cannon the cannon to store
     */
    public void saveCannonAsync(Cannon cannon) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new DelayedTask(cannon) {
            public void run(Object object) {
                Cannon cannon = (Cannon) object;
                saveCannon(cannon);
            }
        });
    }

    /**
     * saves this cannon in the database
     *
     * @param cannon the cannon to store
     */
    private boolean saveCannon(Cannon cannon) {
        //don't save cannons which are not valid anymore
        if (!cannon.isValid())
            return true;


        try {
            // search if the is cannon already stored in the database
            //CannonBean bean = plugin.getDatabase().find(CannonBean.class).where().idEq(cannon.getID()).findUnique();
            CannonBean bean = plugin.getDatabase().find(CannonBean.class, cannon.getUID());

            if (bean == null) {
                plugin.logDebug("creating new database entry");
                // create a new bean that is managed by bukkit
                bean = plugin.getDatabase().createEntityBean(CannonBean.class);

                bean.setId(cannon.getUID());
            } else {
                plugin.logDebug("saving cannon: " + cannon.getUID());
            }

            if (cannon.getOwner() == null) {
                plugin.logDebug("Owner of cannon is null");
                return false;
            }


            // fill the bean with values to store
            // since bukkit manages the bean, we do not need to set
            // the ID property
            bean.setOwner(cannon.getOwner());
            bean.setWorld(cannon.getWorld());
            // save offset
            bean.setLocX(cannon.getOffset().getBlockX());
            bean.setLocY(cannon.getOffset().getBlockY());
            bean.setLocZ(cannon.getOffset().getBlockZ());
            // cannon direction
            bean.setCannonDirection(cannon.getCannonDirection().toString());
            // name
            bean.setName(cannon.getCannonName());
            // must the barrel be clean with the ramrod
            bean.setSoot(cannon.getSoot());
            // amount of gunpowder
            bean.setGunpowder(cannon.getLoadedGunpowder());

            // load projectile
            // if no projectile is found, set it to air
            Projectile projectile = cannon.getLoadedProjectile();
            if (projectile != null) {
                bean.setProjectileID(projectile.getProjectileID());
            } else {
                bean.setProjectileID("none");
            }
            //is the projectile already pushed in the barrel
            bean.setProjectilePushed(cannon.getProjectilePushed());

            // angles
            bean.setHorizontalAngle(cannon.getHorizontalAngle());
            bean.setVerticalAngle(cannon.getVerticalAngle());
            // id
            bean.setDesignId(cannon.getDesignID());
            //temperature
            bean.setCannonTemperature(cannon.getTemperature(false));
            bean.setCannonTemperatureTimestamp(cannon.getTemperatureTimeStamp());
            //load fired cannonballs
            bean.setFiredCannonballs(cannon.getFiredCannonballs());

            List<WhitelistBean> whitelist = new ArrayList<WhitelistBean>();
            for (UUID player : cannon.getWhitelist()) {
                WhitelistBean white = new WhitelistBean();
                white.setPlayer(player);
                whitelist.add(white);
            }
            bean.setWhitelist(whitelist);


            // store the bean
            plugin.getDatabase().save(bean);
            //cannon.setID(bean.getId());
            return true;
        } catch (Exception e) {
            plugin.logDebug("can't save to database. " + e);
            return false;
        }
    }

    /**
     * removes all cannon of this player from the database
     *
     * @param owner the unique id of the owner of the cannon
     */
    public void deleteCannonsAsync(UUID owner) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new DelayedTask(owner) {
            public void run(Object object) {
                UUID owner = (UUID) object;
                deleteCannons(owner);
            }
        });
    }

    /**
     * removes all cannon of this player from the database
     *
     * @param owner the unique id of the owner of the cannon
     * @return returns true is there is an entry of this player in the database
     */
    private boolean deleteCannons(UUID owner) {
        // create a query that returns CannonBean
        List<CannonBean> beans = plugin.getDatabase().find(CannonBean.class).where().eq("owner", owner).findList();


        // process the result
        if (beans == null || beans.size() == 0) {
            // nothing found; list is empty
            return false;
        } else {
            plugin.getDatabase().beginTransaction();
            try {
                // found cannons - load them
                for (CannonBean bean : beans) {
                    plugin.getDatabase().delete(CannonBean.class, bean.getId());
                }
                plugin.getDatabase().commitTransaction();

            } finally {
                plugin.getDatabase().endTransaction();
            }
            return true;
        }


    }

    /**
     * removes this cannon from the database
     *
     * @param cannonID ID of the cannon to delete
     */
    public void deleteCannonAsync(UUID cannonID) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new DelayedTask(cannonID) {
            public void run(Object object) {
                UUID cannonID = (UUID) object;
                deleteCannon(cannonID);
            }
        });
    }

    /**
     * removes this cannon from the database
     *
     * @param cannonID id of the cannon to delete
     */
    private void deleteCannon(UUID cannonID) {
        CannonBean bean = plugin.getDatabase().find(CannonBean.class, cannonID);
        // if the database id is null, it is not saved in the database
        if (bean != null) {
            plugin.logDebug("removing cannon " + cannonID.toString());
            plugin.getDatabase().delete(bean);
        }
    }

    /**
     * removes all cannons from the database
     */
    public void deleteAllCannonsAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            public void run() {
                deleteAllCannons();
            }
        });
    }

    /**
     * removes all cannons from the database
     */
    private void deleteAllCannons() {
        List<CannonBean> cbean = plugin.getDatabase().find(CannonBean.class).findList();
        // if the database id is null, it is not saved in the database
        if (cbean != null) {
            plugin.getDatabase().delete(cbean);
        }
    }

}
