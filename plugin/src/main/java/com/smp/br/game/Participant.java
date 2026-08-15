package com.smp.br.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Participant {

    private final UUID uuid;
    private final String name;

    private boolean alive = true;
    private boolean inBus;
    private boolean gliding;
    private boolean landed;
    private boolean spectating;
    private int kills;
    private int placement;
    private double damageDealt;
    private double damageTaken;
    private final List<String> victims = new ArrayList<>();

    public Participant(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public boolean alive() {
        return alive;
    }

    public void alive(boolean alive) {
        this.alive = alive;
    }

    public boolean inBus() {
        return inBus;
    }

    public void inBus(boolean inBus) {
        this.inBus = inBus;
    }

    public boolean gliding() {
        return gliding;
    }

    public void gliding(boolean gliding) {
        this.gliding = gliding;
    }

    public boolean landed() {
        return landed;
    }

    public void landed(boolean landed) {
        this.landed = landed;
    }

    public boolean spectating() {
        return spectating;
    }

    public void spectating(boolean spectating) {
        this.spectating = spectating;
    }

    public int kills() {
        return kills;
    }

    public void addKill(String victim) {
        kills++;
        victims.add(victim);
    }

    public List<String> victims() {
        return victims;
    }

    public int placement() {
        return placement;
    }

    public void placement(int placement) {
        this.placement = placement;
    }

    public double damageDealt() {
        return damageDealt;
    }

    public void addDamageDealt(double amount) {
        damageDealt += amount;
    }

    public double damageTaken() {
        return damageTaken;
    }

    public void addDamageTaken(double amount) {
        damageTaken += amount;
    }
}
