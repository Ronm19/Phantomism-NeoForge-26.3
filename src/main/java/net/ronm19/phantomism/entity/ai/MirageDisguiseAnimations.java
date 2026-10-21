package net.ronm19.phantomism.entity.ai;

public final class MirageDisguiseAnimations {
    private MirageDisguiseAnimations() {}
    public static String attack(String resource) {
        return switch (resource) {
            case "entity/mirage_phantom" -> "animation.mirage.strike";
            case "entity/ancient_phantom" -> "animation.ancient.strike";
            case "entity/angel_phantom" -> "animation.angel.smite";
            case "entity/blood_phantom" -> "animation.blood.strike";
            case "entity/chrono_phantom" -> "animation.chrono.bolt";
            case "entity/dark_phantom" -> "animation.dark.bolt";
            case "entity/demon_phantom" -> "animation.demon.bite";
            case "entity/echo_phantom" -> "animation.echo.strike";
            case "entity/end_phantom" -> "animation.end.bite";
            case "entity/golem_phantom" -> "animation.golem.hook";
            case "entity/gravity_phantom" -> "animation.gravity.bite";
            case "entity/husk_phantom" -> "animation.husk.bite";
            case "entity/omen_phantom" -> "animation.omen.bolt";
            case "entity/phantom_king" -> "animation.king.strike";
            case "entity/primordial_phantom" -> "animation.primordial.strike";
            case "entity/ravager_phantom" -> "animation.ravager.bite";
            case "entity/reaper_phantom" -> "animation.reaper.strike";
            case "entity/rift_phantom" -> "animation.rift.bolt";
            case "entity/skeleton_phantom" -> "animation.skeleton.bite";
            case "entity/spectral_phantom" -> "animation.spectral.lance";
            case "entity/vampire_phantom" -> "animation.vampire.bite";
            case "entity/void_phantom" -> "animation.void.bolt";
            case "entity/wither_phantom" -> "animation.wither.bite";
            case "entity/zombie_phantom" -> "animation.zombie.bite";
            default -> "animation.phantom.bite";
        };
    }
}
