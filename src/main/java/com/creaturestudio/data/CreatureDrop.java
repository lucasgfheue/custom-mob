package com.creaturestudio.data;

/** Un objeto que la criatura puede soltar al morir, con su cantidad y su probabilidad. */
public class CreatureDrop {
    public String item = "";
    public int min = 1;
    public int max = 1;
    /** Probabilidad en % (1-100). */
    public int chance = 100;

    public CreatureDrop() {
    }

    public CreatureDrop(String item, int min, int max, int chance) {
        this.item = item;
        this.min = min;
        this.max = max;
        this.chance = chance;
    }

    public CreatureDrop copy() {
        return new CreatureDrop(item, min, max, chance);
    }
}
