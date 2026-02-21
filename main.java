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
