package com.creaturestudio.data;

/**
 * Un cubo del modelo. Todas las coordenadas estan en "pixeles" (1/16 de bloque) y son absolutas
 * en el espacio del modelo: Y hacia arriba, el suelo en Y=0 y el frente de la criatura hacia -Z.
 * La rotacion se aplica alrededor del pivote y se hereda a las piezas hijas.
 */
public class CreaturePart {
    public String name = "cubo";
    public int parent = -1;

    /** Esquina minima del cubo. */
    public float x = -2, y = 0, z = -2;
    /** Tamano del cubo. */
    public float w = 4, h = 4, d = 4;
    /** Punto de giro. */
    public float px = 0, py = 2, pz = 0;
    /** Rotacion en grados. */
    public float rx, ry, rz;

    /** Color RGB que tine la textura. */
    public int color = 0xFFFFFF;
    /** Sprite del atlas de bloques, p. ej. "minecraft:block/white_wool". */
    public String material = MaterialIds.SOLID;
    /** true = la textura entera se estira sobre cada cara; false = se repite cada 16 pixeles. */
    public boolean stretch;
    /** true = se dibuja con brillo maximo (ojos que brillan en la oscuridad). */
    public boolean glow;

    public PartAnim anim = PartAnim.NONE;
    public float animAmount = 1f;
    public float animSpeed = 1f;

    public CreaturePart() {
    }

    public CreaturePart copy() {
        CreaturePart c = new CreaturePart();
        c.name = name;
        c.parent = parent;
        c.x = x;
        c.y = y;
        c.z = z;
        c.w = w;
        c.h = h;
        c.d = d;
        c.px = px;
        c.py = py;
        c.pz = pz;
        c.rx = rx;
        c.ry = ry;
        c.rz = rz;
        c.color = color;
        c.material = material;
        c.stretch = stretch;
        c.glow = glow;
        c.anim = anim;
        c.animAmount = animAmount;
        c.animSpeed = animSpeed;
        return c;
    }

    /** Copia reflejada respecto al plano X=0 (para ojos, patas, alas...). */
    public CreaturePart mirroredX() {
        CreaturePart c = copy();
        c.x = -(x + w);
        c.px = -px;
        c.ry = -ry;
        c.rz = -rz;
        c.anim = anim.mirrored();
        c.name = mirrorName(name);
        return c;
    }

    public void translate(float dx, float dy, float dz) {
        x += dx;
        y += dy;
        z += dz;
        px += dx;
        py += dy;
        pz += dz;
    }

    public void centerPivot() {
        px = x + w / 2f;
        py = y + h / 2f;
        pz = z + d / 2f;
    }

    static String mirrorName(String n) {
        String[][] pairs = {
                {"izquierda", "derecha"},
                {"izquierdo", "derecho"},
                {"left", "right"}
        };
        for (String[] p : pairs) {
            if (n.contains(p[0])) {
                return n.replace(p[0], p[1]);
            }
            if (n.contains(p[1])) {
                return n.replace(p[1], p[0]);
            }
        }
        String result = n + " (espejo)";
        return result.length() > 24 ? result.substring(0, 24) : result;
    }

    // ---------------------------------------------------------------------------------------
    // Ayudantes para construir plantillas de forma compacta
    // ---------------------------------------------------------------------------------------

    public static CreaturePart box(String name, float x, float y, float z, float w, float h, float d) {
        CreaturePart p = new CreaturePart();
        p.name = name;
        p.x = x;
        p.y = y;
        p.z = z;
        p.w = w;
        p.h = h;
        p.d = d;
        p.centerPivot();
        return p;
    }

    public CreaturePart pivotAt(float px, float py, float pz) {
        this.px = px;
        this.py = py;
        this.pz = pz;
        return this;
    }

    public CreaturePart rotated(float rx, float ry, float rz) {
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        return this;
    }

    public CreaturePart colored(int rgb) {
        this.color = rgb & 0xFFFFFF;
        return this;
    }

    public CreaturePart textured(String material) {
        this.material = material;
        return this;
    }

    public CreaturePart animated(PartAnim anim) {
        this.anim = anim;
        return this;
    }

    public CreaturePart animated(PartAnim anim, float amount) {
        this.anim = anim;
        this.animAmount = amount;
        return this;
    }

    public CreaturePart childOf(int parent) {
        this.parent = parent;
        return this;
    }

    public CreaturePart stretched() {
        this.stretch = true;
        return this;
    }

    public CreaturePart glowing() {
        this.glow = true;
        return this;
    }
}
