package com.k.sosxbiomc.pit;

import com.k.sosxbiomc.SOSxBiomc;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(
        modid = SOSxBiomc.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class pitmanager extends SavedData {

    /** Gameplay policy: smaller blobs aren't pits. */
    public static final int MIN_PIT_SIZE = 4;

    /** One pit is revalidated every this many ticks (round-robin). */
    private static final int REVALIDATE_INTERVAL = 20;

    private static final String DATA_NAME = "sosxbiomc_pits";

    private final Map<UUID, pit> pits = new HashMap<>();
    private final Long2ObjectMap<UUID> blockToPit = new Long2ObjectOpenHashMap<>();
    private final Deque<UUID> revalidationQueue = new ArrayDeque<>();

    private final Deque<pitevent> pending = new ArrayDeque<>();
    private boolean flushing = false;
    private ServerLevel level;

    // ---------- access ----------

    public static pitmanager get(ServerLevel level) {
        pitmanager manager = level.getDataStorage().computeIfAbsent(
                pitmanager::load, pitmanager::new, DATA_NAME);

        manager.level = level;
        return manager;
    }

    public Optional<pit> getPit(UUID id) {
        return Optional.ofNullable(pits.get(id));
    }

    public Optional<pit> getPitAt(BlockPos pos) {
        long packedPos = pos.asLong();

        // First check the Blood Ether source block index.
        UUID id = blockToPit.get(packedPos);

        if (id != null) {
            Optional<pit> indexedPit = getPit(id);

            if (indexedPit.isPresent()) {
                return indexedPit;
            }
        }

        // Then check infected Flesh blocks.
        for (pit target : pits.values()) {
            if (target.getInfectedFleshBlocks().contains(packedPos)) {
                return Optional.of(target);
            }
        }

        return Optional.empty();
    }

    public Collection<pit> getPits() {
        return Collections.unmodifiableCollection(pits.values());
    }

    // ---------- automatic detection ----------

    /**
     * Checks notified positions for newly placed Blood Ether sources.
     */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = event.getPos();

        if (!pitdetection.isPitBlock(level, pos)) {
            return;
        }

        get(level).refreshAt(level, pos);
    }

    // ---------- entry points ----------

    /**
     * Call when something interacts with a possible pit block.
     * Creates, grows, merges, or splits pits as needed.
     */
    public Optional<pit> refreshAt(ServerLevel level, BlockPos pos) {
        UUID existing = blockToPit.get(pos.asLong());

        Set<UUID> affected = new HashSet<>();
        LongSet seeds = new LongOpenHashSet();

        if (existing != null) {
            affected.add(existing);
        } else {
            seeds.add(pos.asLong());
        }

        reconcile(level, affected, seeds);
        return getPitAt(pos);
    }

    /** Call every level tick (server side). */
    public void tick(ServerLevel level) {

        // Update pressure and process actions for every Pit.
        // pitresources handles its own 100-tick interval.
        for (pit target : new ArrayList<>(pits.values())) {
            pitresources.tick(target, level);
            pitactions.tick(target, level);
        }

        // Structural revalidation remains on its existing schedule.
        if (pits.isEmpty()
                || level.getGameTime() % REVALIDATE_INTERVAL != 0) {
            return;
        }

        if (revalidationQueue.isEmpty()) {
            revalidationQueue.addAll(pits.keySet());
        }

        UUID id = revalidationQueue.poll();

        if (id != null && pits.containsKey(id)) {
            reconcile(level, Set.of(id), LongSets.EMPTY_SET);
        }
    }

    // ---------- core logic ----------

    private void reconcile(ServerLevel level, Set<UUID> affected, LongSet extraSeeds) {
        Set<UUID> involved = new HashSet<>(affected);
        List<LongSet> components;

        while (true) {
            LongSet seeds = new LongOpenHashSet(extraSeeds);

            for (UUID id : involved) {
                pit known = pits.get(id);

                if (known != null) {
                    seeds.addAll(known.getBlocks());
                }
            }

            components = scanComponents(level, seeds);

            if (components == null) {
                return;
            }

            boolean grew = false;

            for (LongSet component : components) {
                for (long block : component) {
                    UUID owner = blockToPit.get(block);

                    if (owner != null && involved.add(owner)) {
                        grew = true;
                    }
                }
            }

            if (!grew) {
                break;
            }
        }

        // Step 1: discard undersized components.
        List<LongSet> survivors = new ArrayList<>();

        for (LongSet component : components) {
            if (component.size() >= MIN_PIT_SIZE) {
                survivors.add(component);
            }
        }

        survivors.sort((a, b) -> Integer.compare(b.size(), a.size()));

        // Skip rewriting an unchanged Pit.
        if (involved.size() == 1 && survivors.size() == 1) {
            pit only = pits.get(involved.iterator().next());

            if (only != null && only.getBlocks().equals(survivors.get(0))) {
                return;
            }
        }

        // Step 2: calculate old-Pit overlap with each surviving component.
        List<Map<UUID, Integer>> overlaps = new ArrayList<>();

        for (LongSet survivor : survivors) {
            Map<UUID, Integer> map = new HashMap<>();

            for (long block : survivor) {
                UUID owner = blockToPit.get(block);

                if (owner != null) {
                    map.merge(owner, 1, Integer::sum);
                }
            }

            overlaps.add(map);
        }

        // Step 3: assign identities.
        UUID[] ids = assignIdentities(overlaps);

        List<pit> created = new ArrayList<>();

        for (int i = 0; i < survivors.size(); i++) {
            created.add(new pit(ids[i], survivors.get(i)));
        }

        // Step 4: distribute old resources, intensity, and spreading state.
        for (UUID oldId : involved) {
            pit old = pits.get(oldId);

            if (old != null) {
                distribute(old, created, overlaps);
            }
        }

        // Step 5: commit the new state.
        Map<UUID, pit> olds = new HashMap<>();

        for (UUID oldId : involved) {
            pit old = pits.remove(oldId);

            if (old != null) {
                olds.put(oldId, old);

                for (long block : old.getBlocks()) {
                    blockToPit.remove(block);
                }
            }
        }

        for (pit fresh : created) {
            register(fresh);
        }

        // Remove fractional pressure tracking for identities that no
        // longer exist after this reconciliation.
        for (UUID oldId : olds.keySet()) {
            if (!pits.containsKey(oldId)) {
                pitresources.forgetPit(oldId);
            }
        }

        // Remove stale IDs from the revalidation queue.
        revalidationQueue.removeIf(id -> !pits.containsKey(id));

        setDirty();

        // Step 6: dispatch events after the state is consistent.
        queueTransitionEvents(level, olds, created, overlaps);
        flush();
    }

    /**
     * Works out what happened from the old-Pit/new-piece overlap graph.
     */
    private void queueTransitionEvents(
            ServerLevel level,
            Map<UUID, pit> olds,
            List<pit> created,
            List<Map<UUID, Integer>> overlaps
    ) {
        Map<UUID, List<pit>> fanOut = new HashMap<>();

        for (int i = 0; i < created.size(); i++) {
            for (UUID source : overlaps.get(i).keySet()) {
                fanOut.computeIfAbsent(source, k -> new ArrayList<>())
                        .add(created.get(i));
            }
        }

        for (Map.Entry<UUID, pit> entry : olds.entrySet()) {
            UUID oldId = entry.getKey();
            pit old = entry.getValue();

            List<pit> dests = fanOut.getOrDefault(
                    oldId, Collections.emptyList());

            if (dests.isEmpty()) {
                pending.add(new pitevent.destroyed(
                        level,
                        oldId,
                        old.getBlocks(),
                        new HashMap<>(old.getResources())
                ));
            } else if (dests.size() > 1) {
                pending.add(new pitevent.split(
                        level,
                        oldId,
                        new ArrayList<>(dests)
                ));
            }
        }

        for (int i = 0; i < created.size(); i++) {
            pit piece = created.get(i);
            Set<UUID> sources = overlaps.get(i).keySet();

            if (sources.isEmpty()) {
                pending.add(new pitevent.created(level, piece));
            } else if (sources.size() > 1) {
                Set<UUID> absorbed = new HashSet<>(sources);
                absorbed.remove(piece.getId());

                pending.add(new pitevent.merged(
                        level, piece, absorbed));
            } else {
                UUID source = sources.iterator().next();
                pit old = olds.get(source);

                boolean oneToOne = fanOut.get(source).size() == 1
                        && source.equals(piece.getId());

                if (oneToOne && old != null
                        && !old.getBlocks().equals(piece.getBlocks())) {

                    pending.add(new pitevent.resized(
                            level,
                            piece,
                            old.getSize(),
                            piece.getSize()
                    ));
                }
            }
        }
    }

    private void queueIntensityEvent(pit changed, int oldIntensity) {
        if (level == null) {
            return;
        }

        pending.add(new pitevent.intensitychanged(
                level,
                changed,
                oldIntensity,
                changed.getIntensity()
        ));

        flush();
    }

    private void flush() {
        if (flushing) {
            return;
        }

        flushing = true;

        try {
            pitevent next;

            while ((next = pending.poll()) != null) {
                MinecraftForge.EVENT_BUS.post(next);
            }
        } finally {
            flushing = false;
        }
    }

    // ---------- identity assignment ----------

    private static UUID[] assignIdentities(
            List<Map<UUID, Integer>> overlaps
    ) {
        List<claim> claims = new ArrayList<>();

        for (int i = 0; i < overlaps.size(); i++) {
            for (Map.Entry<UUID, Integer> entry : overlaps.get(i).entrySet()) {
                claims.add(new claim(
                        entry.getKey(),
                        i,
                        entry.getValue()
                ));
            }
        }

        claims.sort((a, b) -> {
            if (a.count != b.count) {
                return Integer.compare(b.count, a.count);
            }

            int byId = a.id.compareTo(b.id);

            if (byId != 0) {
                return byId;
            }

            return Integer.compare(a.piece, b.piece);
        });

        UUID[] result = new UUID[overlaps.size()];
        Set<UUID> taken = new HashSet<>();

        for (claim c : claims) {
            if (result[c.piece] != null || taken.contains(c.id)) {
                continue;
            }

            result[c.piece] = c.id;
            taken.add(c.id);
        }

        for (int i = 0; i < result.length; i++) {
            if (result[i] == null) {
                result[i] = UUID.randomUUID();
            }
        }

        return result;
    }

    // ---------- resource distribution ----------

    private void distribute(
            pit old,
            List<pit> created,
            List<Map<UUID, Integer>> overlaps
    ) {
        UUID oldId = old.getId();

        long total = 0;
        int best = -1;
        int bestCount = 0;

        for (int i = 0; i < created.size(); i++) {
            int count = overlaps.get(i).getOrDefault(oldId, 0);

            total += count;

            if (count > bestCount) {
                bestCount = count;
                best = i;
            }
        }

        if (best < 0 || total == 0) {
            return;
        }

        // Preserve the highest intensity across connected pieces.
        for (int i = 0; i < created.size(); i++) {
            if (overlaps.get(i).getOrDefault(oldId, 0) > 0) {
                pit piece = created.get(i);

                piece.setIntensity(Math.max(
                        piece.getIntensity(),
                        old.getIntensity()
                ));
            }
        }

        // Distribute resources proportionally.
        for (Map.Entry<ResourceLocation, Integer> entry
                : old.getResources().entrySet()) {

            int amount = entry.getValue();
            int given = 0;

            for (int i = 0; i < created.size(); i++) {
                int count = overlaps.get(i).getOrDefault(oldId, 0);

                if (count == 0) {
                    continue;
                }

                int share = (int) ((long) amount * count / total);

                created.get(i).addResource(entry.getKey(), share);
                given += share;
            }

            // Rounding remainder goes to the largest-overlap piece.
            created.get(best).addResource(
                    entry.getKey(),
                    amount - given
            );
        }

        // Preserve the center and frontier with the largest surviving piece.
        created.get(best).inheritSpreadState(old);
    }

    // ---------- component scanning ----------

    private static List<LongSet> scanComponents(
            ServerLevel level,
            LongSet seeds
    ) {
        List<LongSet> components = new ArrayList<>();
        LongSet claimed = new LongOpenHashSet();

        for (long seed : seeds) {
            if (claimed.contains(seed)) {
                continue;
            }

            BlockPos pos = BlockPos.of(seed);

            if (!level.hasChunkAt(pos)) {
                return null;
            }

            pitdetection.detectionresult result =
                    pitdetection.floodFill(level, pos);

            switch (result.getStatus()) {
                case COMPLETE -> {
                    components.add(result.getBlocks());
                    claimed.addAll(result.getBlocks());
                }

                case NOT_A_PIT -> {
                    // This block was drained.
                }

                default -> {
                    // TOO_LARGE or UNLOADED_BOUNDARY.
                    return null;
                }
            }
        }

        return components;
    }

    // ---------- registration ----------

    private void register(pit target) {
        pits.put(target.getId(), target);

        for (long block : target.getBlocks()) {
            blockToPit.put(block, target.getId());
        }

        target.setChangeListener(this::setDirty);
        target.setIntensityListener(this::queueIntensityEvent);
    }

    private static class claim {
        final UUID id;
        final int piece;
        final int count;

        claim(UUID id, int piece, int count) {
            this.id = id;
            this.piece = piece;
            this.count = count;
        }
    }

    // ---------- load conflict repair ----------

    private static class loadgroup {
        private UUID id;

        private final LongSet blocks = new LongOpenHashSet();
        private final LongSet infectedFlesh = new LongOpenHashSet();
        private final Map<ResourceLocation, Integer> resources = new HashMap<>();

        private final Deque<Long> spreadFrontier = new ArrayDeque<>();
        private final LongSet queuedSources = new LongOpenHashSet();

        private int intensity;

        private double centerX;
        private double centerY;
        private double centerZ;

        private loadgroup(pit source) {
            id = source.getId();

            blocks.addAll(source.getBlocks());
            infectedFlesh.addAll(source.getInfectedFleshBlocks());
            resources.putAll(source.getResources());

            intensity = source.getIntensity();

            centerX = source.getCenterX();
            centerY = source.getCenterY();
            centerZ = source.getCenterZ();

            for (long position : source.getSpreadFrontierSnapshot()) {
                addFrontierPosition(position);
            }
        }

        private void addFrontierPosition(long position) {
            if (queuedSources.add(position)) {
                spreadFrontier.addLast(position);
            }
        }

        private long[] getSpreadFrontierSnapshot() {
            long[] result = new long[spreadFrontier.size()];
            int index = 0;

            for (long position : spreadFrontier) {
                result[index++] = position;
            }

            return result;
        }

        private void merge(loadgroup other) {
            blocks.addAll(other.blocks);
            infectedFlesh.addAll(other.infectedFlesh);

            other.resources.forEach((type, amount) ->
                    resources.merge(type, amount, pitmanager::saturatedAdd)
            );

            intensity = Math.max(intensity, other.intensity);

            // Keep this group's original center because this group's
            // identity is retained during the merge.

            // Preserve this group's frontier first, then append the
            // other group's frontier without duplicate positions.
            for (long position : other.spreadFrontier) {
                addFrontierPosition(position);
            }
        }
    }

    private static boolean overlaps(LongSet first, LongSet second) {
        LongSet smaller = first.size() <= second.size() ? first : second;
        LongSet larger = smaller == first ? second : first;

        for (long block : smaller) {
            if (larger.contains(block)) {
                return true;
            }
        }

        return false;
    }

    private static int saturatedAdd(int first, int second) {
        long result = (long) first + second;

        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    // ---------- persistence ----------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();

        for (pit entry : pits.values()) {
            list.add(entry.save());
        }

        tag.put("Pits", list);
        return tag;
    }

    public static pitmanager load(CompoundTag tag) {
        pitmanager manager = new pitmanager();

        ListTag list = tag.getList("Pits", Tag.TAG_COMPOUND);
        List<loadgroup> groups = new ArrayList<>();
        boolean repairedData = false;

        // Read and repair saved Pits before registering anything.
        for (int i = 0; i < list.size(); i++) {
            pit loaded = pit.load(list.getCompound(i));
            loadgroup incoming = new loadgroup(loaded);

            boolean merged;

            do {
                merged = false;

                for (int j = 0; j < groups.size(); j++) {
                    loadgroup existing = groups.get(j);

                    if (incoming.id.equals(existing.id)
                            || overlaps(incoming.blocks, existing.blocks)) {

                        // The existing group's identity and center are retained.
                        incoming.id = existing.id;

                        // Merge the incoming group's data into the retained group.
                        existing.merge(incoming);

                        groups.remove(j);

                        // Continue with the combined group.
                        incoming = existing;

                        merged = true;
                        repairedData = true;
                        break;
                    }
                }
            } while (merged);

            groups.add(incoming);
        }

        // Register only after all ownership conflicts are resolved.
        for (loadgroup group : groups) {
            pit repaired = new pit(group.id, group.blocks);

            repaired.setIntensity(group.intensity);
            group.resources.forEach(repaired::addResource);
            repaired.addAllInfectedFleshBlocks(group.infectedFlesh);

            // Restore the original center and exact frontier rotation.
            repaired.restoreSpreadState(
                    group.centerX,
                    group.centerY,
                    group.centerZ,
                    group.getSpreadFrontierSnapshot()
            );

            manager.register(repaired);
        }

        if (repairedData) {
            manager.setDirty();
        }

        return manager;
    }
}