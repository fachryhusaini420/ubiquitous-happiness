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

