/*
 * Ubiquitous Happiness — Joy ledger and mood-seed registry for the happyAI web platform.
 * Zinnia domain: 0x5c9e2b7f4a1d8e3c6b0f9a2d5e8b1c4f7a0d3e6b9c2f5a8d1e4b7c0f3a6d9e2b5
 * Single-file EVM-style engine; all roles and hex values set at construction.
 */

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class UbiquitousHappiness {

    // -------------------------------------------------------------------------
    // IMMUTABLE CONFIG (constructor-set; never changed)
    // -------------------------------------------------------------------------

    public static final String ZINNIA_DOMAIN_HEX = "0x5c9e2b7f4a1d8e3c6b0f9a2d5e8b1c4f7a0d3e6b9c2f5a8d1e4b7c0f3a6d9e2b5";
    public static final String JOY_CURATOR_HEX = "0x7F2e9A4c1B8d3E6f0a5C8b2D9e4F7a1c6B0d3E9";
    public static final String CHEER_VAULT_HEX = "0x9A1b4E7c0D3f6a2B5e8C1d4F7a0b3E6c9D2e5A8";
    public static final String MOOD_ORACLE_HEX = "0xB3c6E9a2D5f8b1C4e7A0d3F6b9e2C5a8D1f4B7";
    public static final String SUNSHINE_TREASURY_HEX = "0xD6f9B2e5A8c1D4f7a0B3e6C9d2F5a8b1E4c7D0";
    public static final String PULSE_RELAY_HEX = "0xE0a3C6d9F2b5E8c1A4d7F0b3E6a9C2d5F8b1E4";

    // -------------------------------------------------------------------------
    // CONSTANTS (unique names; safe for EVM mainnet logic)
    // -------------------------------------------------------------------------

    public static final int CHEER_BASIS_DENOM = 10_000;
    public static final int CHEER_MAX_FEE_BASIS = 300;
    public static final int CHEER_MAX_MOOD_TIERS = 12;
    public static final int CHEER_MAX_SEEDS_PER_HOLDER = 64;
    public static final int CHEER_MIN_LOCK_EPOCHS = 8;
    public static final int CHEER_MAX_LOCK_EPOCHS = 65_536;
    public static final int CHEER_BATCH_SIZE = 24;
    public static final long CHEER_SCALE = 1_000_000_000_000_000_000L;
    public static final int CHEER_MAX_WEIGHT = 10_000;
    public static final int UHQ_VERSION_MAJOR = 2;
    public static final int UHQ_VERSION_MINOR = 1;
    public static final String UHQ_ENGINE_TAG = "ubiquitous-happiness-v2.1";

    // -------------------------------------------------------------------------
    // ERROR CODES (unique; not used in other contracts)
    // -------------------------------------------------------------------------

    public static final String UHQ_ERR_ZERO_DEPOSIT = "UHQ_ZeroDeposit";
    public static final String UHQ_ERR_ZERO_ADDRESS = "UHQ_ZeroAddress";
    public static final String UHQ_ERR_NOT_JOY_CURATOR = "UHQ_NotJoyCurator";
    public static final String UHQ_ERR_NOT_VAULT = "UHQ_NotVault";
    public static final String UHQ_ERR_TRANSFER_FAILED = "UHQ_TransferFailed";
    public static final String UHQ_ERR_SEED_LOCKED = "UHQ_SeedLocked";
    public static final String UHQ_ERR_SEED_NOT_FOUND = "UHQ_SeedNotFound";
    public static final String UHQ_ERR_NOT_SEED_OWNER = "UHQ_NotSeedOwner";
    public static final String UHQ_ERR_INVALID_TIER = "UHQ_InvalidTier";
    public static final String UHQ_ERR_HARVEST_ZERO = "UHQ_HarvestZero";
    public static final String UHQ_ERR_GARDEN_PAUSED = "UHQ_GardenPaused";
    public static final String UHQ_ERR_FEE_BASIS_TOO_HIGH = "UHQ_FeeBasisTooHigh";
    public static final String UHQ_ERR_WITHDRAW_ZERO = "UHQ_WithdrawZero";
    public static final String UHQ_ERR_ARRAY_LENGTH_MISMATCH = "UHQ_ArrayLengthMismatch";
    public static final String UHQ_ERR_MAX_SEEDS_PER_HOLDER = "UHQ_MaxSeedsPerHolder";
    public static final String UHQ_ERR_MIN_LOCK_EPOCHS = "UHQ_MinLockEpochs";
    public static final String UHQ_ERR_BATCH_TOO_LARGE = "UHQ_BatchTooLarge";
    public static final String UHQ_ERR_INVALID_WEIGHT = "UHQ_InvalidWeight";
    public static final String UHQ_ERR_NOT_ORACLE = "UHQ_NotOracle";
    public static final String UHQ_ERR_NOT_RELAY = "UHQ_NotRelay";

    // -------------------------------------------------------------------------
    // EVENT NAMES (unique signatures)
    // -------------------------------------------------------------------------

    public static final String EVT_JOY_PULSE_RECORDED = "JoyPulseRecorded";
    public static final String EVT_MOOD_SEED_PLANTED = "MoodSeedPlanted";
    public static final String EVT_CHEER_ORB_DISTRIBUTED = "CheerOrbDistributed";
    public static final String EVT_SEED_WITHDRAWN = "SeedWithdrawn";
    public static final String EVT_ALLOCATED_TO_TIER = "CheerAllocatedToTier";
    public static final String EVT_CURATOR_UPDATED = "JoyCuratorUpdated";
    public static final String EVT_VAULT_UPDATED = "CheerVaultUpdated";
    public static final String EVT_PROTOCOL_FEE_BASIS_SET = "ProtocolFeeBasisSet";
    public static final String EVT_GARDEN_PAUSED = "GardenPaused";
    public static final String EVT_GARDEN_UNPAUSED = "GardenUnpaused";
    public static final String EVT_TREASURY_WITHDRAWN = "SunshineTreasuryWithdrawn";
    public static final String EVT_TIER_WEIGHT_UPDATED = "TierWeightUpdated";
    public static final String EVT_SEED_BATCH_PLANTED = "MoodSeedPlantedBatch";
    public static final String EVT_SEED_BATCH_WITHDRAWN = "SeedWithdrawnBatch";
    public static final String EVT_EPOCH_ADVANCED = "EpochAdvanced";

    // -------------------------------------------------------------------------
    // STATE
    // -------------------------------------------------------------------------

    private final UbiquitousHappiness.JoyLedger ledger;
    private final UbiquitousHappiness.AccessControl access;
    private final UbiquitousHappiness.CheerHarvest harvest;
    private final UbiquitousHappiness.EventLog eventLog;
    private final long deployTimestampMs;
    private final String joyCurator;
    private final String cheerVault;
    private final String moodOracle;
    private final String sunshineTreasury;
    private final String pulseRelay;

    public UbiquitousHappiness() {
        this.joyCurator = JOY_CURATOR_HEX;
        this.cheerVault = CHEER_VAULT_HEX;
        this.moodOracle = MOOD_ORACLE_HEX;
        this.sunshineTreasury = SUNSHINE_TREASURY_HEX;
        this.pulseRelay = PULSE_RELAY_HEX;
        this.deployTimestampMs = System.currentTimeMillis();
        this.ledger = new UbiquitousHappiness.JoyLedger();
        this.access = new UbiquitousHappiness.AccessControl(joyCurator, cheerVault, moodOracle, sunshineTreasury, pulseRelay);
        this.harvest = new UbiquitousHappiness.CheerHarvest(ledger, access);
        this.eventLog = new UbiquitousHappiness.EventLog();
    }

    public UbiquitousHappiness(String joyCuratorAddr, String vaultAddr, String oracleAddr, String treasuryAddr, String relayAddr) {
        if (joyCuratorAddr == null || joyCuratorAddr.isEmpty()) throw new IllegalStateException(UHQ_ERR_ZERO_ADDRESS);
        if (vaultAddr == null || vaultAddr.isEmpty()) throw new IllegalStateException(UHQ_ERR_ZERO_ADDRESS);
        this.joyCurator = joyCuratorAddr;
        this.cheerVault = vaultAddr;
        this.moodOracle = oracleAddr != null ? oracleAddr : "";
        this.sunshineTreasury = treasuryAddr != null ? treasuryAddr : "";
        this.pulseRelay = relayAddr != null ? relayAddr : "";
        this.deployTimestampMs = System.currentTimeMillis();
        this.ledger = new UbiquitousHappiness.JoyLedger();
        this.access = new UbiquitousHappiness.AccessControl(joyCurator, cheerVault, moodOracle, sunshineTreasury, pulseRelay);
        this.harvest = new UbiquitousHappiness.CheerHarvest(ledger, access);
        this.eventLog = new UbiquitousHappiness.EventLog();
    }

    public UbiquitousHappiness.JoyLedger getLedger() { return ledger; }
    public UbiquitousHappiness.AccessControl getAccess() { return access; }
    public UbiquitousHappiness.CheerHarvest getHarvest() { return harvest; }
    public UbiquitousHappiness.EventLog getEventLog() { return eventLog; }
    public long getDeployTimestampMs() { return deployTimestampMs; }
    public String getJoyCurator() { return joyCurator; }
    public String getCheerVault() { return cheerVault; }
    public String getMoodOracle() { return moodOracle; }
    public String getSunshineTreasury() { return sunshineTreasury; }
    public String getPulseRelay() { return pulseRelay; }

    // -------------------------------------------------------------------------
    // MOOD SEED (per-user lock bucket)
    // -------------------------------------------------------------------------

    public static final class MoodSeed {
        private final String seedId;
        private final String ownerHex;
        private final int tierIndex;
        private final long unlockEpoch;
        private long principalWei;
        private long accruedCheerWei;
        private final long plantedAtEpoch;

        public MoodSeed(String seedId, String ownerHex, int tierIndex, long unlockEpoch, long principalWei) {
            this.seedId = Objects.requireNonNull(seedId);
            this.ownerHex = Objects.requireNonNull(ownerHex);
            this.tierIndex = tierIndex;
            this.unlockEpoch = unlockEpoch;
            this.principalWei = principalWei;
            this.accruedCheerWei = 0L;
            this.plantedAtEpoch = unlockEpoch - 1;
        }

        public String getSeedId() { return seedId; }
        public String getOwnerHex() { return ownerHex; }
        public int getTierIndex() { return tierIndex; }
        public long getUnlockEpoch() { return unlockEpoch; }
        public long getPrincipalWei() { return principalWei; }
        public long getAccruedCheerWei() { return accruedCheerWei; }
        public long getPlantedAtEpoch() { return plantedAtEpoch; }
        public void addPrincipal(long amount) { this.principalWei += amount; }
        public void addAccruedCheer(long amount) { this.accruedCheerWei += amount; }
        public void setPrincipal(long value) { this.principalWei = value; }
        public void setAccruedCheer(long value) { this.accruedCheerWei = value; }
    }

    // -------------------------------------------------------------------------
    // TIER CONFIG
    // -------------------------------------------------------------------------

    public static final class TierConfig {
        private final int tierIndex;
        private long lockEpochs;
        private long weight;

        public TierConfig(int tierIndex, long lockEpochs, long weight) {
            this.tierIndex = tierIndex;
            this.lockEpochs = lockEpochs;
            this.weight = weight;
        }

        public int getTierIndex() { return tierIndex; }
        public long getLockEpochs() { return lockEpochs; }
        public long getWeight() { return weight; }
        public void setLockEpochs(long v) { this.lockEpochs = v; }
        public void setWeight(long v) { this.weight = v; }
    }

    // -------------------------------------------------------------------------
    // JOY LEDGER (state storage)
    // -------------------------------------------------------------------------

    public static final class JoyLedger {
        private final Map<String, MoodSeed> seedsById = new ConcurrentHashMap<>();
        private final Map<String, List<String>> seedIdsByOwner = new ConcurrentHashMap<>();
        private final Map<Integer, TierConfig> tiersByIndex = new ConcurrentHashMap<>();
        private final AtomicLong currentEpoch = new AtomicLong(1L);
        private final AtomicLong totalPrincipal = new AtomicLong(0L);
        private final AtomicLong totalAccruedCheer = new AtomicLong(0L);
        private volatile long protocolFeeBasis = 25;
        private volatile boolean gardenPaused = false;
        private final AtomicLong seedIdCounter = new AtomicLong(1000L);
        private final List<String> seedIdOrder = Collections.synchronizedList(new ArrayList<>());

        public JoyLedger() {
            for (int i = 0; i < CHEER_MAX_MOOD_TIERS; i++) {
                long lock = CHEER_MIN_LOCK_EPOCHS * (1L << Math.min(i, 4));
                long w = 500 + (i * 200);
                tiersByIndex.put(i, new TierConfig(i, lock, Math.min(w, CHEER_MAX_WEIGHT)));
            }
        }

        public MoodSeed getSeed(String seedId) { return seedsById.get(seedId); }
        public TierConfig getTier(int index) { return tiersByIndex.get(index); }
        public long getCurrentEpoch() { return currentEpoch.get(); }
        public long getTotalPrincipal() { return totalPrincipal.get(); }
        public long getTotalAccruedCheer() { return totalAccruedCheer.get(); }
        public long getProtocolFeeBasis() { return protocolFeeBasis; }
        public boolean isGardenPaused() { return gardenPaused; }
        public void setProtocolFeeBasis(long basis) { this.protocolFeeBasis = basis; }
        public void setGardenPaused(boolean paused) { this.gardenPaused = paused; }
        public void advanceEpoch() { currentEpoch.incrementAndGet(); }
        public List<String> getSeedIdsForOwner(String ownerHex) {
            return seedIdsByOwner.computeIfAbsent(ownerHex, k -> Collections.synchronizedList(new ArrayList<>()));
        }

        public String nextSeedId() {
            long id = seedIdCounter.incrementAndGet();
            return "UHQ_SEED_" + Long.toHexString(id) + "_" + Long.toHexString(System.nanoTime() & 0xFFFF);
        }

        public MoodSeed plantSeed(String ownerHex, int tierIndex, long principalWei) {
            if (principalWei <= 0) throw new IllegalStateException(UHQ_ERR_ZERO_DEPOSIT);
            TierConfig tier = tiersByIndex.get(tierIndex);
            if (tier == null) throw new IllegalStateException(UHQ_ERR_INVALID_TIER);
            if (tier.getLockEpochs() < CHEER_MIN_LOCK_EPOCHS) throw new IllegalStateException(UHQ_ERR_MIN_LOCK_EPOCHS);
            List<String> owned = getSeedIdsForOwner(ownerHex);
            synchronized (owned) {
                if (owned.size() >= CHEER_MAX_SEEDS_PER_HOLDER) throw new IllegalStateException(UHQ_ERR_MAX_SEEDS_PER_HOLDER);
            }
            long unlockEpoch = currentEpoch.get() + tier.getLockEpochs();
            String seedId = nextSeedId();
            MoodSeed seed = new MoodSeed(seedId, ownerHex, tierIndex, unlockEpoch, principalWei);
            seedsById.put(seedId, seed);
            getSeedIdsForOwner(ownerHex).add(seedId);
            seedIdOrder.add(seedId);
            totalPrincipal.addAndGet(principalWei);
            return seed;
        }

        public void addPrincipalToSeed(String seedId, long amount) {
            MoodSeed seed = seedsById.get(seedId);
            if (seed == null) throw new IllegalStateException(UHQ_ERR_SEED_NOT_FOUND);
            if (amount <= 0) throw new IllegalStateException(UHQ_ERR_ZERO_DEPOSIT);
            seed.addPrincipal(amount);
            totalPrincipal.addAndGet(amount);
        }

        public void withdrawSeed(String seedId, String callerHex) {
            MoodSeed seed = seedsById.get(seedId);
            if (seed == null) throw new IllegalStateException(UHQ_ERR_SEED_NOT_FOUND);
            if (!seed.getOwnerHex().equalsIgnoreCase(callerHex)) throw new IllegalStateException(UHQ_ERR_NOT_SEED_OWNER);
            if (currentEpoch.get() < seed.getUnlockEpoch()) throw new IllegalStateException(UHQ_ERR_SEED_LOCKED);
            long p = seed.getPrincipalWei();
            long c = seed.getAccruedCheerWei();
            if (p == 0 && c == 0) throw new IllegalStateException(UHQ_ERR_WITHDRAW_ZERO);
            totalPrincipal.addAndGet(-p);
            totalAccruedCheer.addAndGet(-c);
            seedsById.remove(seedId);
            List<String> owned = getSeedIdsForOwner(callerHex);
            synchronized (owned) { owned.remove(seedId); }
            seedIdOrder.remove(seedId);
        }

        public void accrueCheerToSeed(String seedId, long amount) {
            MoodSeed seed = seedsById.get(seedId);
            if (seed == null) return;
            seed.addAccruedCheer(amount);
            totalAccruedCheer.addAndGet(amount);
        }

        public void setTierWeight(int tierIndex, long weight) {
            if (weight < 0 || weight > CHEER_MAX_WEIGHT) throw new IllegalStateException(UHQ_ERR_INVALID_WEIGHT);
            TierConfig t = tiersByIndex.get(tierIndex);
            if (t != null) t.setWeight(weight);
        }

        public int getTierCount() { return tiersByIndex.size(); }
        public int getSeedCount() { return seedsById.size(); }
        public List<String> getAllSeedIds() { return new ArrayList<>(seedIdOrder); }
    }

    // -------------------------------------------------------------------------
    // ACCESS CONTROL
    // -------------------------------------------------------------------------

    public static final class AccessControl {
        private final String joyCurator;
        private final String cheerVault;
        private final String moodOracle;
        private final String sunshineTreasury;
        private final String pulseRelay;

        public AccessControl(String joyCurator, String cheerVault, String moodOracle, String sunshineTreasury, String pulseRelay) {
            this.joyCurator = joyCurator;
            this.cheerVault = cheerVault;
            this.moodOracle = moodOracle;
            this.sunshineTreasury = sunshineTreasury;
            this.pulseRelay = pulseRelay;
        }

        public boolean isJoyCurator(String addr) { return addr != null && addr.equalsIgnoreCase(joyCurator); }
        public boolean isCheerVault(String addr) { return addr != null && addr.equalsIgnoreCase(cheerVault); }
        public boolean isMoodOracle(String addr) { return addr != null && addr.equalsIgnoreCase(moodOracle); }
        public boolean isSunshineTreasury(String addr) { return addr != null && addr.equalsIgnoreCase(sunshineTreasury); }
        public boolean isPulseRelay(String addr) { return addr != null && addr.equalsIgnoreCase(pulseRelay); }
        public String getJoyCurator() { return joyCurator; }
        public String getCheerVault() { return cheerVault; }
        public String getMoodOracle() { return moodOracle; }
        public String getSunshineTreasury() { return sunshineTreasury; }
        public String getPulseRelay() { return pulseRelay; }
    }

    // -------------------------------------------------------------------------
    // CHEER HARVEST (yield distribution)
    // -------------------------------------------------------------------------

    public static final class CheerHarvest {
        private final JoyLedger ledger;
        private final AccessControl access;

        public CheerHarvest(JoyLedger ledger, AccessControl access) {
            this.ledger = ledger;
            this.access = access;
        }

        public long distributeHarvest(long totalYieldWei, String callerHex) {
            if (!access.isJoyCurator(callerHex)) throw new IllegalStateException(UHQ_ERR_NOT_JOY_CURATOR);
            if (ledger.isGardenPaused()) throw new IllegalStateException(UHQ_ERR_GARDEN_PAUSED);
            if (totalYieldWei <= 0) throw new IllegalStateException(UHQ_ERR_HARVEST_ZERO);
            long feeBasis = ledger.getProtocolFeeBasis();
            if (feeBasis > CHEER_MAX_FEE_BASIS) throw new IllegalStateException(UHQ_ERR_FEE_BASIS_TOO_HIGH);
            long treasuryShare = (totalYieldWei * feeBasis) / CHEER_BASIS_DENOM;
            long toDistribute = totalYieldWei - treasuryShare;
            long totalWeight = 0L;
            for (int i = 0; i < ledger.getTierCount(); i++) {
                TierConfig t = ledger.getTier(i);
                if (t != null) totalWeight += t.getWeight();
            }
            if (totalWeight == 0) return treasuryShare;
            List<String> seedIds = ledger.getAllSeedIds();
            Map<Integer, Long> principalByTier = new HashMap<>();
            for (String sid : seedIds) {
                MoodSeed s = ledger.getSeed(sid);
                if (s != null && ledger.getCurrentEpoch() < s.getUnlockEpoch()) {
                    int ti = s.getTierIndex();
                    principalByTier.merge(ti, s.getPrincipalWei(), Long::sum);
                }
            }
            long tierDenom = 0L;
            for (Map.Entry<Integer, Long> e : principalByTier.entrySet()) {
                TierConfig t = ledger.getTier(e.getKey());
                if (t != null) tierDenom += t.getWeight() * Math.max(1, e.getValue());
            }
            if (tierDenom == 0) return treasuryShare;
            for (String sid : seedIds) {
                MoodSeed s = ledger.getSeed(sid);
                if (s == null || ledger.getCurrentEpoch() >= s.getUnlockEpoch()) continue;
                TierConfig t = ledger.getTier(s.getTierIndex());
                if (t == null) continue;
                long tierPrincipal = principalByTier.getOrDefault(s.getTierIndex(), 0L);
                if (tierPrincipal <= 0) continue;
                long share = (toDistribute * t.getWeight() * s.getPrincipalWei()) / tierDenom;
                if (share > 0) ledger.accrueCheerToSeed(sid, share);
            }
            return treasuryShare;
        }
    }

    // -------------------------------------------------------------------------
    // EVENT LOG
    // -------------------------------------------------------------------------

    public static final class EventEntry {
        public final String eventName;
        public final String payload;
        public final long timestampMs;

        public EventEntry(String eventName, String payload, long timestampMs) {
            this.eventName = eventName;
            this.payload = payload;
            this.timestampMs = timestampMs;
        }
    }

    public static final class EventLog {
        private final List<EventEntry> entries = Collections.synchronizedList(new ArrayList<>());
        private static final int MAX_ENTRIES = 10_000;

        public void emit(String eventName, String payload) {
            entries.add(new EventEntry(eventName, payload, System.currentTimeMillis()));
            while (entries.size() > MAX_ENTRIES) entries.remove(0);
        }

        public List<EventEntry> getRecent(int n) {
            int size = entries.size();
            if (n >= size) return new ArrayList<>(entries);
            return new ArrayList<>(entries.subList(size - n, size));
        }

        public int size() { return entries.size(); }
    }

    // -------------------------------------------------------------------------
    // PUBLIC API (EVM-style entrypoints)
    // -------------------------------------------------------------------------

    public MoodSeed plantMoodSeed(String ownerHex, int tierIndex, long principalWei) {
        if (ledger.isGardenPaused()) throw new IllegalStateException(UHQ_ERR_GARDEN_PAUSED);
        MoodSeed seed = ledger.plantSeed(ownerHex, tierIndex, principalWei);
        eventLog.emit(EVT_MOOD_SEED_PLANTED, String.format("owner=%s,seedId=%s,tier=%d,principal=%d,unlockEpoch=%d",
                ownerHex, seed.getSeedId(), tierIndex, principalWei, seed.getUnlockEpoch()));
        return seed;
    }

    public void addToSeed(String callerHex, String seedId, long amountWei) {
        if (ledger.isGardenPaused()) throw new IllegalStateException(UHQ_ERR_GARDEN_PAUSED);
        MoodSeed seed = ledger.getSeed(seedId);
        if (seed == null) throw new IllegalStateException(UHQ_ERR_SEED_NOT_FOUND);
        if (!seed.getOwnerHex().equalsIgnoreCase(callerHex)) throw new IllegalStateException(UHQ_ERR_NOT_SEED_OWNER);
        ledger.addPrincipalToSeed(seedId, amountWei);
        eventLog.emit(EVT_JOY_PULSE_RECORDED, String.format("owner=%s,seedId=%s,added=%d", callerHex, seedId, amountWei));
    }

    public void withdrawMoodSeed(String callerHex, String seedId) {
        if (ledger.isGardenPaused()) throw new IllegalStateException(UHQ_ERR_GARDEN_PAUSED);
        MoodSeed seed = ledger.getSeed(seedId);
        long p = seed != null ? seed.getPrincipalWei() : 0;
        long c = seed != null ? seed.getAccruedCheerWei() : 0;
        ledger.withdrawSeed(seedId, callerHex);
        eventLog.emit(EVT_SEED_WITHDRAWN, String.format("owner=%s,seedId=%s,principal=%d,cheer=%d", callerHex, seedId, p, c));
    }

    public long harvestAndDistribute(String curatorHex, long totalYieldWei) {
        long treasuryShare = harvest.distributeHarvest(totalYieldWei, curatorHex);
        eventLog.emit(EVT_CHEER_ORB_DISTRIBUTED, String.format("totalYield=%d,treasuryShare=%d", totalYieldWei, treasuryShare));
        return treasuryShare;
    }

    public void setProtocolFeeBasis(String curatorHex, long basis) {
        if (!access.isJoyCurator(curatorHex)) throw new IllegalStateException(UHQ_ERR_NOT_JOY_CURATOR);
        if (basis > CHEER_MAX_FEE_BASIS) throw new IllegalStateException(UHQ_ERR_FEE_BASIS_TOO_HIGH);
        long prev = ledger.getProtocolFeeBasis();
        ledger.setProtocolFeeBasis(basis);
        eventLog.emit(EVT_PROTOCOL_FEE_BASIS_SET, String.format("previous=%d,new=%d", prev, basis));
    }

    public void setTierWeight(String curatorHex, int tierIndex, long weight) {
        if (!access.isJoyCurator(curatorHex)) throw new IllegalStateException(UHQ_ERR_NOT_JOY_CURATOR);
        ledger.setTierWeight(tierIndex, weight);
        eventLog.emit(EVT_TIER_WEIGHT_UPDATED, String.format("tier=%d,weight=%d", tierIndex, weight));
    }

    public void pauseGarden(String curatorHex) {
        if (!access.isJoyCurator(curatorHex)) throw new IllegalStateException(UHQ_ERR_NOT_JOY_CURATOR);
        ledger.setGardenPaused(true);
        eventLog.emit(EVT_GARDEN_PAUSED, "by=" + curatorHex);
    }

    public void unpauseGarden(String curatorHex) {
        if (!access.isJoyCurator(curatorHex)) throw new IllegalStateException(UHQ_ERR_NOT_JOY_CURATOR);
        ledger.setGardenPaused(false);
        eventLog.emit(EVT_GARDEN_UNPAUSED, "by=" + curatorHex);
    }

    public void advanceEpoch(String oracleHex) {
        if (!access.isMoodOracle(oracleHex)) throw new IllegalStateException(UHQ_ERR_NOT_ORACLE);
        long prev = ledger.getCurrentEpoch();
        ledger.advanceEpoch();
        eventLog.emit(EVT_EPOCH_ADVANCED, String.format("previous=%d,new=%d", prev, ledger.getCurrentEpoch()));
    }

    public List<MoodSeed> plantMoodSeedBatch(String ownerHex, int[] tierIndices, long[] principalAmounts) {
        if (tierIndices == null || principalAmounts == null || tierIndices.length != principalAmounts.length)
            throw new IllegalStateException(UHQ_ERR_ARRAY_LENGTH_MISMATCH);
        if (tierIndices.length > CHEER_BATCH_SIZE) throw new IllegalStateException(UHQ_ERR_BATCH_TOO_LARGE);
        if (ledger.isGardenPaused()) throw new IllegalStateException(UHQ_ERR_GARDEN_PAUSED);
        List<MoodSeed> result = new ArrayList<>();
        for (int i = 0; i < tierIndices.length; i++) {
            result.add(ledger.plantSeed(ownerHex, tierIndices[i], principalAmounts[i]));
        }
        eventLog.emit(EVT_SEED_BATCH_PLANTED, String.format("owner=%s,count=%d", ownerHex, result.size()));
        return result;
    }

    public void withdrawMoodSeedBatch(String callerHex, List<String> seedIds) {
        if (seedIds == null || seedIds.size() > CHEER_BATCH_SIZE) throw new IllegalStateException(UHQ_ERR_BATCH_TOO_LARGE);
        if (ledger.isGardenPaused()) throw new IllegalStateException(UHQ_ERR_GARDEN_PAUSED);
        for (String seedId : seedIds) {
            MoodSeed s = ledger.getSeed(seedId);
            if (s != null && s.getOwnerHex().equalsIgnoreCase(callerHex) && ledger.getCurrentEpoch() >= s.getUnlockEpoch())
                ledger.withdrawSeed(seedId, callerHex);
        }
        eventLog.emit(EVT_SEED_BATCH_WITHDRAWN, "owner=" + callerHex + ",count=" + seedIds.size());
    }

    // -------------------------------------------------------------------------
    // VIEW FUNCTIONS
    // -------------------------------------------------------------------------

    public MoodSeed getSeed(String seedId) { return ledger.getSeed(seedId); }
    public List<String> getSeedIdsForOwner(String ownerHex) { return new ArrayList<>(ledger.getSeedIdsForOwner(ownerHex)); }
    public TierConfig getTier(int index) { return ledger.getTier(index); }
    public long getCurrentEpoch() { return ledger.getCurrentEpoch(); }
    public long getTotalPrincipal() { return ledger.getTotalPrincipal(); }
    public long getTotalAccruedCheer() { return ledger.getTotalAccruedCheer(); }
    public boolean isPaused() { return ledger.isGardenPaused(); }
    public long getProtocolFeeBasis() { return ledger.getProtocolFeeBasis(); }

    // -------------------------------------------------------------------------
    // SERIALIZATION (persist / restore for mainnet safety)
    // -------------------------------------------------------------------------

    public static final class Snapshot {
        public long deployTimestampMs;
        public long currentEpoch;
        public long totalPrincipal;
        public long totalAccruedCheer;
        public long protocolFeeBasis;
        public boolean gardenPaused;
        public List<MoodSeedSnapshot> seeds = new ArrayList<>();
        public Map<Integer, TierSnapshot> tiers = new HashMap<>();

        public static final class MoodSeedSnapshot {
            public String seedId, ownerHex;
            public int tierIndex;
            public long unlockEpoch, principalWei, accruedCheerWei, plantedAtEpoch;
        }

        public static final class TierSnapshot {
            public int tierIndex;
            public long lockEpochs, weight;
        }
    }

    public Snapshot takeSnapshot() {
        Snapshot s = new Snapshot();
        s.deployTimestampMs = deployTimestampMs;
        s.currentEpoch = ledger.getCurrentEpoch();
        s.totalPrincipal = ledger.getTotalPrincipal();
        s.totalAccruedCheer = ledger.getTotalAccruedCheer();
        s.protocolFeeBasis = ledger.getProtocolFeeBasis();
        s.gardenPaused = ledger.isGardenPaused();
        for (String seedId : ledger.getAllSeedIds()) {
            MoodSeed m = ledger.getSeed(seedId);
            if (m == null) continue;
            Snapshot.MoodSeedSnapshot ms = new Snapshot.MoodSeedSnapshot();
            ms.seedId = m.getSeedId();
            ms.ownerHex = m.getOwnerHex();
            ms.tierIndex = m.getTierIndex();
            ms.unlockEpoch = m.getUnlockEpoch();
            ms.principalWei = m.getPrincipalWei();
            ms.accruedCheerWei = m.getAccruedCheerWei();
            ms.plantedAtEpoch = m.getPlantedAtEpoch();
            s.seeds.add(ms);
        }
        for (int i = 0; i < ledger.getTierCount(); i++) {
            TierConfig t = ledger.getTier(i);
            if (t == null) continue;
            Snapshot.TierSnapshot ts = new Snapshot.TierSnapshot();
            ts.tierIndex = t.getTierIndex();
            ts.lockEpochs = t.getLockEpochs();
            ts.weight = t.getWeight();
            s.tiers.put(i, ts);
        }
        return s;
    }

    public String exportSnapshotJson() {
        Snapshot s = takeSnapshot();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"deployTimestampMs\":").append(s.deployTimestampMs);
        sb.append(",\"currentEpoch\":").append(s.currentEpoch);
        sb.append(",\"totalPrincipal\":").append(s.totalPrincipal);
        sb.append(",\"totalAccruedCheer\":").append(s.totalAccruedCheer);
        sb.append(",\"protocolFeeBasis\":").append(s.protocolFeeBasis);
        sb.append(",\"gardenPaused\":").append(s.gardenPaused);
        sb.append(",\"seeds\":[");
        for (int i = 0; i < s.seeds.size(); i++) {
            if (i > 0) sb.append(",");
            Snapshot.MoodSeedSnapshot m = s.seeds.get(i);
            sb.append("{\"seedId\":\"").append(escape(m.seedId)).append("\",\"ownerHex\":\"").append(escape(m.ownerHex)).append("\"");
            sb.append(",\"tierIndex\":").append(m.tierIndex).append(",\"unlockEpoch\":").append(m.unlockEpoch);
            sb.append(",\"principalWei\":").append(m.principalWei).append(",\"accruedCheerWei\":").append(m.accruedCheerWei).append("}");
        }
        sb.append("],\"tiers\":{");
        boolean first = true;
        for (Map.Entry<Integer, Snapshot.TierSnapshot> e : s.tiers.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            Snapshot.TierSnapshot t = e.getValue();
            sb.append("\"").append(e.getKey()).append("\":{\"lockEpochs\":").append(t.lockEpochs).append(",\"weight\":").append(t.weight).append("}");
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String escape(String x) {
        if (x == null) return "";
        return x.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // -------------------------------------------------------------------------
    // HASH / DOMAIN (mainnet-safe fingerprint)
    // -------------------------------------------------------------------------

    public byte[] getZinniaDomainHash() throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(ZINNIA_DOMAIN_HEX.getBytes(StandardCharsets.UTF_8));
        md.update(JOY_CURATOR_HEX.getBytes(StandardCharsets.UTF_8));
        md.update(Long.toString(deployTimestampMs).getBytes(StandardCharsets.UTF_8));
        return md.digest();
    }

    public String getZinniaDomainHashHex() {
        try {
            byte[] h = getZinniaDomainHash();
            StringBuilder sb = new StringBuilder(64);
            for (byte b : h) sb.append(String.format("%02x", b & 0xff));
            return "0x" + sb.toString();
        } catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }

    // -------------------------------------------------------------------------
    // TIER STATS AND AGGREGATES
    // -------------------------------------------------------------------------

    public static final class TierStats {
        public final int tierIndex;
        public final long totalPrincipal;
        public final long totalAccruedCheer;
        public final int activeSeedCount;
        public final long lockEpochs;
        public final long weight;

        public TierStats(int tierIndex, long totalPrincipal, long totalAccruedCheer, int activeSeedCount, long lockEpochs, long weight) {
            this.tierIndex = tierIndex;
            this.totalPrincipal = totalPrincipal;
            this.totalAccruedCheer = totalAccruedCheer;
            this.activeSeedCount = activeSeedCount;
            this.lockEpochs = lockEpochs;
            this.weight = weight;
        }
    }

    public List<TierStats> getTierStats() {
        List<TierStats> out = new ArrayList<>();
        long epoch = ledger.getCurrentEpoch();
        for (int i = 0; i < ledger.getTierCount(); i++) {
            TierConfig t = ledger.getTier(i);
            if (t == null) continue;
            long principal = 0, cheer = 0;
            int count = 0;
            for (String sid : ledger.getAllSeedIds()) {
                MoodSeed s = ledger.getSeed(sid);
                if (s != null && s.getTierIndex() == i && epoch < s.getUnlockEpoch()) {
                    principal += s.getPrincipalWei();
                    cheer += s.getAccruedCheerWei();
                    count++;
                }
            }
            out.add(new TierStats(i, principal, cheer, count, t.getLockEpochs(), t.getWeight()));
        }
        return out;
    }

    public Map<String, Long> getHolderBalances() {
        Map<String, Long> totalByOwner = new HashMap<>();
        for (String sid : ledger.getAllSeedIds()) {
            MoodSeed s = ledger.getSeed(sid);
            if (s == null) continue;
            String o = s.getOwnerHex();
            long v = s.getPrincipalWei() + s.getAccruedCheerWei();
            totalByOwner.merge(o, v, Long::sum);
        }
        return totalByOwner;
    }

    public List<MoodSeed> getSeedsForOwner(String ownerHex) {
        List<MoodSeed> out = new ArrayList<>();
        for (String sid : ledger.getSeedIdsForOwner(ownerHex)) {
            MoodSeed s = ledger.getSeed(sid);
            if (s != null) out.add(s);
        }
        return out;
    }

    public List<MoodSeed> getUnlockedSeedsForOwner(String ownerHex) {
        long epoch = ledger.getCurrentEpoch();
        return getSeedsForOwner(ownerHex).stream()
                .filter(s -> epoch >= s.getUnlockEpoch())
                .collect(Collectors.toList());
    }

    public List<MoodSeed> getLockedSeedsForOwner(String ownerHex) {
        long epoch = ledger.getCurrentEpoch();
        return getSeedsForOwner(ownerHex).stream()
                .filter(s -> epoch < s.getUnlockEpoch())
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // GAS / CYCLE ESTIMATION (EVM-style cost hints)
    // -------------------------------------------------------------------------

    public static final int COST_PLANT_BASE = 85_000;
    public static final int COST_ADD_TO_SEED = 45_000;
    public static final int COST_WITHDRAW = 55_000;
    public static final int COST_HARVEST_BASE = 120_000;
    public static final int COST_HARVEST_PER_SEED = 2_100;
    public static final int COST_ADVANCE_EPOCH = 28_000;
    public static final int COST_SET_FEE = 32_000;
    public static final int COST_PAUSE = 28_000;
    public static final int COST_BATCH_PLANT_PER = 72_000;

    public int estimatePlantGas() { return COST_PLANT_BASE; }
    public int estimateAddToSeedGas() { return COST_ADD_TO_SEED; }
    public int estimateWithdrawGas() { return COST_WITHDRAW; }
    public int estimateHarvestGas() {
        return COST_HARVEST_BASE + ledger.getSeedCount() * COST_HARVEST_PER_SEED;
    }
    public int estimateAdvanceEpochGas() { return COST_ADVANCE_EPOCH; }
    public int estimateBatchPlantGas(int count) {
        if (count <= 0 || count > CHEER_BATCH_SIZE) return 0;
        return COST_PLANT_BASE + (count - 1) * COST_BATCH_PLANT_PER;
    }

    // -------------------------------------------------------------------------
    // ADDRESS VALIDATION (mainnet-safe checks)
    // -------------------------------------------------------------------------

    public static boolean isValidEvmAddress(String addr) {
        if (addr == null) return false;
        String x = addr.startsWith("0x") ? addr.substring(2) : addr;
        if (x.length() != 40) return false;
        for (int i = 0; i < x.length(); i++) {
            char c = x.charAt(i);
            if (!Character.isDigit(c) && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) return false;
        }
        return true;
    }

    public void requireValidAddress(String addr) {
        if (!isValidEvmAddress(addr)) throw new IllegalStateException(UHQ_ERR_ZERO_ADDRESS);
    }

    // -------------------------------------------------------------------------
    // SNAPSHOT RESTORE (from JSON string)
    // -------------------------------------------------------------------------

    public static UbiquitousHappiness restoreFromSnapshot(String json, String joyCuratorAddr, String vaultAddr, String oracleAddr, String treasuryAddr, String relayAddr) {
        UbiquitousHappiness engine = new UbiquitousHappiness(joyCuratorAddr, vaultAddr, oracleAddr, treasuryAddr, relayAddr);
        UbiquitousHappiness.SnapshotLoader.load(json, engine.ledger);
        return engine;
    }

    public static final class SnapshotLoader {
        public static void load(String json, JoyLedger ledger) {
            int i = json.indexOf("\"currentEpoch\":");
            if (i >= 0) {
                int end = json.indexOf(",", i);
                if (end < 0) end = json.indexOf("}", i);
                String sub = json.substring(i + 15, end).trim();
                try {
                    long epoch = Long.parseLong(sub);
                    for (long e = ledger.getCurrentEpoch(); e < epoch; e++) ledger.advanceEpoch();
                } catch (NumberFormatException ignored) {}
            }
            int feeIdx = json.indexOf("\"protocolFeeBasis\":");
            if (feeIdx >= 0) {
                int end = json.indexOf(",", feeIdx);
                if (end < 0) end = json.indexOf("}", feeIdx);
                try {
                    long basis = Long.parseLong(json.substring(feeIdx + 18, end).trim());
                    ledger.setProtocolFeeBasis(basis);
                } catch (NumberFormatException ignored) {}
            }
            int pauseIdx = json.indexOf("\"gardenPaused\":true");
            if (pauseIdx >= 0) ledger.setGardenPaused(true);
            int seedsStart = json.indexOf("\"seeds\":[");
            if (seedsStart >= 0) {
                int depth = 0;
                int j = seedsStart + 9;
                while (j < json.length()) {
                    char c = json.charAt(j);
                    if (c == '[') depth++;
                    else if (c == ']') { depth--; if (depth == 0) break; }
                    j++;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // RATE LIMIT / THROTTLE (mainnet-safe abuse prevention)
    // -------------------------------------------------------------------------

    private final Map<String, Long> lastPlantByOwner = new ConcurrentHashMap<>();
    public static final long MIN_PLANT_INTERVAL_MS = 1_000;

    public boolean canPlantNow(String ownerHex) {
        Long last = lastPlantByOwner.get(ownerHex);
        if (last == null) return true;
        return System.currentTimeMillis() - last >= MIN_PLANT_INTERVAL_MS;
    }

    public void recordPlant(String ownerHex) {
        lastPlantByOwner.put(ownerHex, System.currentTimeMillis());
    }

    public MoodSeed plantMoodSeedWithThrottle(String ownerHex, int tierIndex, long principalWei) {
        if (!canPlantNow(ownerHex)) throw new IllegalStateException("UHQ_PlantThrottled");
        MoodSeed seed = plantMoodSeed(ownerHex, tierIndex, principalWei);
        recordPlant(ownerHex);
        return seed;
    }

    // -------------------------------------------------------------------------
    // EPOCH HISTORY (bounded ring for audits)
    // -------------------------------------------------------------------------

    public static final class EpochRecord {
        public final long epoch;
        public final long totalPrincipalAtEpoch;
        public final long totalCheerAtEpoch;
        public final long timestampMs;

        public EpochRecord(long epoch, long totalPrincipalAtEpoch, long totalCheerAtEpoch, long timestampMs) {
            this.epoch = epoch;
            this.totalPrincipalAtEpoch = totalPrincipalAtEpoch;
            this.totalCheerAtEpoch = totalCheerAtEpoch;
            this.timestampMs = timestampMs;
        }
