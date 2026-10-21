package net.ronm19.phantomism.entity.ai;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.ChronoPhantom;

public final class CooldownDiscipline {
    public enum Category { NORMAL, ULTIMATE, REVIVAL, LIFETIME, RECOVERY }
    public interface Participant { List<Slot> temporalCooldowns(); }
    public record Slot(Identifier id, Category category, int duration, int floor, IntSupplier remaining, IntConsumer update) {
        public static Slot normal(String name, int duration, IntSupplier remaining, IntConsumer update) {
            return new Slot(Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, name), Category.NORMAL, duration, 6, remaining, update);
        }
    }
    private CooldownDiscipline() {}
    public static boolean eligible(Slot slot) { return slot.category == Category.NORMAL && slot.duration >= 12 && slot.duration <= 2400 && slot.floor >= 6; }
    public static int assist(ChronoPhantom source, LivingEntity ally) {
        if (!source.combatEnabled() || !source.acceptsAlly(ally) || source.distanceToSqr(ally) > 24 * 24 || !(ally instanceof Participant participant)) { return 0; }
        long now = ally.level().getGameTime();
        var root = ally.getPersistentData().getCompoundOrEmpty("ChronoCooldownCredit");
        if (root.getLongOr("NextPulse", Long.MIN_VALUE) > now) { return 0; }
        int reduced = 0;
        for (Slot slot : participant.temporalCooldowns().stream().limit(24).toList()) {
            if (!eligible(slot)) { continue; }
            String key = slot.id.toString(); int remaining = Math.clamp(slot.remaining.getAsInt(), 0, slot.duration);
            var receipt = root.getCompoundOrEmpty(key); int previous = receipt.getIntOr("Remaining", 0), used = receipt.getIntOr("Credit", 0);
            if (remaining > previous || remaining == 0) { used = 0; }
            int credit = Math.max(0, Math.min(4, Math.min(slot.duration / 10 - used, remaining - slot.floor)));
            if (credit > 0) { slot.update.accept(remaining - credit); reduced += credit; used += credit; }
            receipt.putInt("Remaining", remaining - credit); receipt.putInt("Credit", used); root.put(key, receipt);
        }
        if (reduced > 0) { root.putLong("NextPulse", now + 40); }
        ally.getPersistentData().put("ChronoCooldownCredit", root); return reduced;
    }
}
