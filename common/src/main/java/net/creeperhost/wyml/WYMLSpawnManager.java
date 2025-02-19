package net.creeperhost.wyml;

import net.creeperhost.wyml.config.WymlConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class WYMLSpawnManager
{
    MobCategory classification;
    ChunkPos chunk;
    int spawningCount;
    private long startRate;
    private long finishRate;
    private int pauseTick;
    private int pausedFor;
    private int startSpawnSampleTick;
    private int lastSpawnRequestTick;
    private int spawnsInTick;
    private int slowModeStart;
    private int lastUpdatedTick;
    private boolean requiresSave;
    private boolean isPaused;
    private HashMap<Long, spawnLocation> prevSpawns = new HashMap<Long, spawnLocation>();
    boolean slowMode;

    public WYMLSpawnManager(ChunkPos pos, MobCategory classification)
    {
        this.classification = classification;
        this.chunk = pos;
    }

    public ChunkPos getChunk()
    {
        return chunk;
    }

    public MobCategory getClassification()
    {
        return classification;
    }

    public boolean isSlowMode()
    {
        return slowMode;
    }

    public long getFinishRate()
    {
        return finishRate;
    }

    public long getStartRate()
    {
        return startRate;
    }

    public boolean isSaved()
    {
        return !requiresSave;
    }

    public boolean isClaimed()
    {
        return WhyYouMakeLag.cachedClaimedChunks.get().contains(getChunk().toLong());
    }

    public boolean isForceLoaded()
    {
        return WhyYouMakeLag.cachedForceLoadedChunks.get().contains(getChunk().toLong());
    }

    public double getFailRate()
    {
        if (finishRate == 0) return 100;
        if (startRate == 0) return 0;
        double retVal = (100 - ((finishRate / startRate) * 100));
        if (finishRate > 0)
        {
            double wat1 = (double) ((double) finishRate / (double) startRate);
            double wat2 = wat1 * 100d;
            double wat3 = 100d - wat2;
            retVal = Math.round(wat3 * 100d) / 100d;
        }
        if (retVal < 0) return 0;
        if (retVal > 100) return 100;
        return retVal;
    }

    public synchronized void increaseSpawningCount(BlockPos pos)
    {
        startRate++;
        if (WhyYouMakeLag.getTicks() > (startSpawnSampleTick + WymlConfig.cached().SAMPLE_TICKS))
        {
            startSpawnSampleTick = WhyYouMakeLag.getTicks();
            spawnsInTick = 0;
        }
        spawnLocation sl = new spawnLocation();
        if (prevSpawns.containsKey(pos.asLong()))
        {
            sl = prevSpawns.get(pos.asLong());
        }
        if (!sl.success)
        {
            sl.position = pos;
            sl.success = false;
            sl.lastUpdated = WhyYouMakeLag.getTicks();
        }
        prevSpawns.put(pos.asLong(), sl);
        spawnsInTick++;
        lastSpawnRequestTick = WhyYouMakeLag.getTicks();
        spawningCount++;
        requiresSave = true;
    }

    public void isSaving()
    {
        requiresSave = false;
        lastUpdatedTick = WhyYouMakeLag.getTicks();
    }

    public boolean hasExpired()
    {
        return ((lastUpdatedTick + WymlConfig.cached().MANAGER_CACHE_TICKS) > WhyYouMakeLag.getTicks() && !isPaused() && isSaved());
    }

    public int countBlockCache()
    {
        return prevSpawns.size();
    }

    public synchronized int cleanBlockCache()
    {
        int removedCache = 0;
        try
        {
            List<Long> toRemove = new ArrayList<Long>();
            Set<Long> ids = prevSpawns.keySet();
            for (long id : ids)
            {
                spawnLocation sl = prevSpawns.get(id);
                if (sl.lastUpdated > (WhyYouMakeLag.getTicks() + WymlConfig.cached().SPAWNLOC_CACHE_TICKS) || sl.success)
                {
                    toRemove.add(id);
                }
            }
            for (long id : toRemove)
            {
                prevSpawns.remove(id);
                removedCache++;
            }
            if (toRemove.size() > 0)
            {
                requiresSave = true;
            }
        } catch (Exception ignored)
        {
        }
        return removedCache;
    }

    public synchronized void decreaseSpawningCount(BlockPos pos)
    {
        if (prevSpawns.containsKey(pos.asLong()))
        {
            spawnLocation sl = prevSpawns.get(pos.asLong());
            sl.success = true;
            sl.lastUpdated = WhyYouMakeLag.getTicks();
            prevSpawns.put(pos.asLong(), sl);
        }
        finishRate++;
        if (finishRate > startRate) startRate = finishRate;
        spawningCount--;
        requiresSave = true;
    }

    public int getSpawnsInSample()
    {
        if (WhyYouMakeLag.getTicks() < (startSpawnSampleTick + WymlConfig.cached().SAMPLE_TICKS))
        {
            int retVal = spawnsInTick;
            int sampleLength = (WhyYouMakeLag.getTicks() - startSpawnSampleTick);
            if (sampleLength > 0) retVal = spawnsInTick / sampleLength;
            return retVal;
        }
        return 0;
    }

    public void resetSpawningCount()
    {
        spawningCount = 0;
        requiresSave = true;
    }

    public void slowMode()
    {
        resetSpawningCount();
        slowMode = true;
        slowModeStart = WhyYouMakeLag.getTicks();
        requiresSave = true;
    }

    public int ticksSinceSlow()
    {
        int diff = WhyYouMakeLag.getTicks() - slowModeStart;
        if (diff < 0) diff = 99999;
        return diff;
    }

    public void fastMode()
    {
        slowModeStart = 0;
        resetSpawningCount();
        slowMode = false;
        requiresSave = true;
    }

    public int getLastSpawnRequestTick()
    {
        return lastSpawnRequestTick;
    }

    public void pauseSpawns(int ticks)
    {
        isPaused = true;
        pausedFor = ticks;
        pauseTick = WhyYouMakeLag.getTicks();
        requiresSave = true;
    }

    public boolean canPause()
    {
        boolean isPausable = WymlConfig.cached().ALLOW_PAUSE && (WhyYouMakeLag.minecraftServer.getPlayerList().getPlayerCount() > WymlConfig.cached().MINIMUM_PAUSE_PLAYERS);
        if (isPausable)
        {
            if (WhyYouMakeLag.isFtbChunksLoaded())
            {
                if (!WymlConfig.cached().ALLOW_PAUSE_CLAIMED)
                {
                    if (isClaimed()) return false;
                }
            }
            if (!WymlConfig.cached().ALLOW_PAUSE_FORCED)
            {
                if (isForceLoaded()) return false;
            }
        }
        return isPausable;
    }

    public boolean isPaused()
    {
        int resumeRate = isClaimed() ? WymlConfig.cached().RESUME_CLAIMED_RATE : WymlConfig.cached().RESUME_RATE;
        if ((isPaused && (pauseTick + pausedFor) > WhyYouMakeLag.getTicks()) || (isPaused && getFailRate() < (100d - resumeRate)))
        {
            return true;
        }
        else
        {
            if (isPaused)
            {
                if (WymlConfig.cached().DEBUG_PRINT)
                    System.out.println("Resuming spawns for class " + getClassification().getName() + " at " + getChunk() + " due to timeout or failure rate decease [" + getFailRate() + "%].");
                isPaused = false;
                startRate = 0;
                finishRate = 0;
                resetSpawningCount();
                pauseTick = 0;
                pausedFor = 0;
                requiresSave = true;
            }
            return false;
        }
    }

    public synchronized boolean isKnownBadLocation(BlockPos pos)
    {
        if (prevSpawns == null) return false;
        if (pos == null) return false;
        if (prevSpawns.containsKey(pos.asLong()))
        {
            spawnLocation sl = prevSpawns.get(pos.asLong());
            if (sl.lastUpdated < (WhyYouMakeLag.getTicks() + WymlConfig.cached().SPAWNLOC_CACHE_TICKS))
            {
                return !sl.success;
            }
        }
        return false;
    }

    class spawnLocation
    {
        BlockPos position;
        boolean success;
        int lastUpdated;
    }

}
