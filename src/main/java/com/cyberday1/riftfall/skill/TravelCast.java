package com.cyberday1.riftfall.skill;

import java.util.*;
import java.util.function.IntConsumer;

/** Deferred cast bookkeeping without an optional spell-engine dependency. One moving cast per caster. */
public final class TravelCast {
    private static final Map<UUID, Ticket> ACTIVE = new HashMap<>();
    private static final ThreadLocal<Ticket> IMPACT = new ThreadLocal<>();
    private TravelCast() { }
    public static boolean active(UUID caster) { return ACTIVE.containsKey(caster); }

    public static final class Ticket {
        final UUID caster;
        final String skill;
        final Set<UUID> hits = new HashSet<>();
        final List<IntConsumer> after = new ArrayList<>();
        final List<Runnable> before = new ArrayList<>();
        Ticket(UUID caster, String skill) { this.caster = caster; this.skill = skill; }
    }

    public static Ticket begin(UUID caster, String skill) {
        if (ACTIVE.containsKey(caster)) return null;
        Ticket ticket = new Ticket(caster, skill); ACTIVE.put(caster, ticket); return ticket;
    }

    public static boolean defer(UUID caster, String skill, IntConsumer action) {
        Ticket ticket = ACTIVE.get(caster);
        if (ticket == null || !ticket.skill.equals(skill)) return false;
        ticket.after.add(action); return true;
    }

    public static void impact(Ticket ticket, Runnable action) {
        Ticket previous = IMPACT.get(); IMPACT.set(ticket);
        try { action.run(); }
        finally { if (previous == null) IMPACT.remove(); else IMPACT.set(previous); }
    }

    public static boolean struck(UUID caster, UUID target) {
        Ticket ticket = IMPACT.get();
        return ticket != null && ticket.caster.equals(caster) && !caster.equals(target) && ticket.hits.add(target);
    }

    public static boolean beforeArrival(UUID caster, String skill, Runnable action) {
        Ticket ticket = IMPACT.get();
        if (ticket == null || !ticket.caster.equals(caster) || !ticket.skill.equals(skill)) return false;
        ticket.before.add(action); return true;
    }

    public static void finish(Ticket ticket, boolean arrived) {
        if (!ACTIVE.remove(ticket.caster, ticket) || !arrived) return;
        ticket.before.forEach(Runnable::run);
        ticket.after.forEach(action -> action.accept(ticket.hits.size()));
    }

    static void clear() { ACTIVE.clear(); IMPACT.remove(); }
}
