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

    /**
     * Pintura a mano: un color ARGB por texel (alfa 0 = sin pintar, se ve la textura de debajo),
     * con el esquema de {@link PaintLayout}. Puede ser null.
     */
    public int[] paint;
    /** Resolucion de la pintura en texels (ancho, alto y fondo). */
    public int pw;
    public int ph;
    public int pd;

    /**
     * Sitio de la pieza en una textura (skin) con el formato de caja de Minecraft: esquina (uvU, uvV), o -1
     * si no tiene. uvMirror: la pieza usa esa zona reflejada (como las patas del otro lado en Minecraft).
     */
    public int uvU = -1;
    public int uvV = -1;
    public boolean uvMirror;
    public int uvW;
    public int uvH;
    public int uvD;

    /** Tamano de la pieza en la skin (texels de ancho, alto y fondo; 0 en piezas planas como las aletas). */
    public boolean hasUv() {
        if (uvU < 0 || uvV < 0 || uvW < 0 || uvH < 0 || uvD < 0
                || uvW > PaintLayout.MAX_DIM || uvH > PaintLayout.MAX_DIM || uvD > PaintLayout.MAX_DIM) {
            return false;
        }
        return (uvW > 0 ? 1 : 0) + (uvH > 0 ? 1 : 0) + (uvD > 0 ? 1 : 0) >= 2;
    }

    /** Quita el sitio en la skin. */
    public void clearUv() {
        uvU = -1;
        uvV = -1;
        uvMirror = false;
        uvW = 0;
        uvH = 0;
        uvD = 0;
    }

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
        c.paint = paint == null ? null : paint.clone();
        c.pw = pw;
        c.ph = ph;
        c.pd = pd;
        c.uvU = uvU;
        c.uvV = uvV;
        c.uvMirror = uvMirror;
        c.uvW = uvW;
        c.uvH = uvH;
        c.uvD = uvD;
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
        if (validPaint()) {
            c.paint = mirroredPaint();
        }
        c.uvMirror = !uvMirror;
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

    // ---------------------------------------------------------------------------------------
    // Pintura a mano
    // ---------------------------------------------------------------------------------------

    /** Texels de pintura para un tamano dado (uno por pixel del modelo, entre 1 y el maximo). */
    public static int paintDim(float size) {
        return Math.max(1, Math.min(PaintLayout.MAX_DIM, (int) Math.ceil(size - 1.0e-4f)));
    }

    public boolean validPaint() {
        return paint != null && pw >= 1 && ph >= 1 && pd >= 1
                && pw <= PaintLayout.MAX_DIM && ph <= PaintLayout.MAX_DIM && pd <= PaintLayout.MAX_DIM
                && paint.length == PaintLayout.imageW(pw, pd) * PaintLayout.imageH(ph, pd);
    }

    /** true si hay al menos un texel pintado. */
    public boolean hasPaint() {
        if (!validPaint()) {
            return false;
        }
        for (int c : paint) {
            if ((c >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Crea la capa de pintura vacia si no existe. */
    public void ensurePaint() {
        if (validPaint()) {
            return;
        }
        pw = paintDim(w);
        ph = paintDim(h);
        pd = paintDim(d);
        paint = new int[PaintLayout.imageW(pw, pd) * PaintLayout.imageH(ph, pd)];
    }

    public void clearPaint() {
        paint = null;
        pw = 0;
        ph = 0;
        pd = 0;
    }

    public int texel(int face, int i, int j) {
        if (!validPaint()) {
            return 0;
        }
        int[] r = PaintLayout.region(face, pw, ph, pd);
        if (i < 0 || j < 0 || i >= r[2] || j >= r[3]) {
            return 0;
        }
        return paint[(r[1] + j) * PaintLayout.imageW(pw, pd) + r[0] + i];
    }

    public void setTexel(int face, int i, int j, int argb) {
        if (!validPaint()) {
            return;
        }
        int[] r = PaintLayout.region(face, pw, ph, pd);
        if (i >= 0 && j >= 0 && i < r[2] && j < r[3]) {
            paint[(r[1] + j) * PaintLayout.imageW(pw, pd) + r[0] + i] = argb;
        }
    }

    /** Cambia la resolucion de la pintura conservando lo pintado (cada cara se recorta o crece desde su esquina). */
    public void remapPaint(int npw, int nph, int npd) {
        if (!validPaint()) {
            return;
        }
        int oldW = PaintLayout.imageW(pw, pd);
        int newW = PaintLayout.imageW(npw, npd);
        int[] out = new int[newW * PaintLayout.imageH(nph, npd)];
        for (int f = 0; f < PaintLayout.FACES; f++) {
            int[] ro = PaintLayout.region(f, pw, ph, pd);
            int[] rn = PaintLayout.region(f, npw, nph, npd);
            int cw = Math.min(ro[2], rn[2]);
            int ch = Math.min(ro[3], rn[3]);
            for (int j = 0; j < ch; j++) {
                for (int i = 0; i < cw; i++) {
                    out[(rn[1] + j) * newW + rn[0] + i] = paint[(ro[1] + j) * oldW + ro[0] + i];
                }
            }
        }
        paint = out;
        pw = npw;
        ph = nph;
        pd = npd;
    }

    /**
     * Llamar despues de cambiar el tamano. Si la pintura iba a un texel por pixel, se adapta al tamano nuevo;
     * si no (por ejemplo, la textura de un modelo de Minecraft), simplemente se estira.
     */
    public void onResized(float oldW, float oldH, float oldD) {
        if (!validPaint()) {
            return;
        }
        if (pw == paintDim(oldW) && ph == paintDim(oldH) && pd == paintDim(oldD)) {
            int npw = paintDim(w);
            int nph = paintDim(h);
            int npd = paintDim(d);
            if (npw != pw || nph != ph || npd != pd) {
                remapPaint(npw, nph, npd);
            }
        }
    }

    /** La pintura reflejada en X: izquierda y derecha se intercambian y cada cara se voltea. */
    private int[] mirroredPaint() {
        int width = PaintLayout.imageW(pw, pd);
        int[] out = new int[paint.length];
        for (int f = 0; f < PaintLayout.FACES; f++) {
            int[] dst = PaintLayout.region(f, pw, ph, pd);
            int[] src = PaintLayout.region(PaintLayout.mirrored(f), pw, ph, pd);
            for (int j = 0; j < dst[3]; j++) {
                for (int i = 0; i < dst[2]; i++) {
                    out[(dst[1] + j) * width + dst[0] + i] = paint[(src[1] + j) * width + src[0] + (src[2] - 1 - i)];
                }
            }
        }
        return out;
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
