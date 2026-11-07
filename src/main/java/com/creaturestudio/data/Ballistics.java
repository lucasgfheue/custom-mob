package com.creaturestudio.data;

/**
 * Hacia donde apuntar un proyectil para que llegue al objetivo. Simula el mismo vuelo que el proyectil de verdad
 * (frenado del aire, aceleracion opcional y gravedad, tick a tick) y busca el angulo que pasa por el objetivo:
 * el bajo (tiro recto) o el alto (en arco, como un mortero).
 */
public final class Ballistics {
    /** Gravedad por tick de los proyectiles que caen (como una flecha: 0,05; una bola de nieve: 0,03). */
    public static final double GRAVITY = 0.03;
    /** Frenado del aire por tick. */
    public static final double DRAG = 0.99;
    /** Aumento de velocidad por tick de los proyectiles que aceleran. */
    public static final double ACCELERATION = 1.06;
    /** Tope de velocidad de los que aceleran (veces la velocidad inicial). */
    public static final double MAX_BOOST = 3.0;
    /** Ticks de vida del proyectil (si no choca antes, desaparece). */
    public static final int MAX_LIFE = 200;

    private static final double MIN_ANGLE = Math.toRadians(-85.0);
    private static final double MAX_ANGLE = Math.toRadians(85.0);
    private static final int SAMPLES = 86;
    private static final int REFINE = 22;

    private Ballistics() {
    }

    /**
     * Direccion de disparo {x, y, z} (sin normalizar) hacia un objetivo a (dx, dy, dz) bloques, con la velocidad
     * inicial y la gravedad por tick. Con arc = true usa la trayectoria alta. Si el objetivo no se alcanza, apunta
     * al angulo que mas se acerca.
     */
    public static double[] aim(double dx, double dy, double dz, double speed, double gravity, boolean accelerate,
                               boolean arc) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (gravity <= 0.0 || horizontal < 0.5 || speed <= 0.0 || !Double.isFinite(horizontal + dy + speed)) {
            return new double[]{dx, dy, dz};
        }
        double low = Double.NaN;
        double high = Double.NaN;
        double best = Math.toRadians(45.0);
        double bestMiss = Double.POSITIVE_INFINITY;
        double prevA = Double.NaN;
        double prevF = Double.NaN;
        for (int i = 0; i <= SAMPLES; i++) {
            double a = MIN_ANGLE + (MAX_ANGLE - MIN_ANGLE) * i / SAMPLES;
            double f = miss(a, horizontal, dy, speed, gravity, accelerate);
            if (!Double.isNaN(f)) {
                if (Math.abs(f) < bestMiss) {
                    bestMiss = Math.abs(f); // el que mas se acerca (si ninguno llega justo)
                    best = a;
                }
                if (!Double.isNaN(prevF) && (prevF < 0.0) != (f < 0.0)) {
                    double root = refine(prevA, prevF, a, horizontal, dy, speed, gravity, accelerate);
                    if (Double.isNaN(low)) {
                        low = root;
                    }
                    high = root;
                }
            }
            prevA = a;
            prevF = f;
        }
        if (Double.isNaN(low)) {
            // Ningun angulo de la muestra cruza el objetivo: se busca el mejor cerca del que mas se acerco
            // (puede haber una ventana estrecha entre dos muestras, al limite del alcance)
            double step = (MAX_ANGLE - MIN_ANGLE) / SAMPLES;
            double peak = peak(best - step, best + step, horizontal, dy, speed, gravity, accelerate);
            double fPeak = miss(peak, horizontal, dy, speed, gravity, accelerate);
            if (!Double.isNaN(fPeak) && fPeak >= 0.0) {
                double fa = miss(best - step, horizontal, dy, speed, gravity, accelerate);
                double fb = miss(best + step, horizontal, dy, speed, gravity, accelerate);
                low = !Double.isNaN(fa) && fa < 0.0
                        ? refine(best - step, fa, peak, horizontal, dy, speed, gravity, accelerate) : peak;
                high = !Double.isNaN(fb) && fb < 0.0
                        ? refine(peak, fPeak, best + step, horizontal, dy, speed, gravity, accelerate) : peak;
            } else if (!Double.isNaN(fPeak) && Math.abs(fPeak) < bestMiss) {
                best = peak;
            }
        }
        double angle = arc ? high : low;
        if (Double.isNaN(angle)) {
            angle = best;
        }
        double cos = Math.cos(angle);
        return new double[]{dx / horizontal * cos, Math.sin(angle), dz / horizontal * cos};
    }

    /** Compatibilidad: sin aceleracion. */
    public static double[] aim(double dx, double dy, double dz, double speed, double gravity, boolean arc) {
        return aim(dx, dy, dz, speed, gravity, false, arc);
    }

    /** Angulo de [a, b] por el que el proyectil pasa mas alto (busqueda de la seccion aurea). */
    private static double peak(double a, double b, double horizontal, double dy, double speed, double gravity,
                               boolean accelerate) {
        double ratio = (Math.sqrt(5.0) - 1.0) / 2.0;
        double c = b - ratio * (b - a);
        double d = a + ratio * (b - a);
        double fc = height(c, horizontal, dy, speed, gravity, accelerate);
        double fd = height(d, horizontal, dy, speed, gravity, accelerate);
        for (int i = 0; i < 40; i++) {
            if (fc > fd) {
                b = d;
                d = c;
                fd = fc;
                c = b - ratio * (b - a);
                fc = height(c, horizontal, dy, speed, gravity, accelerate);
            } else {
                a = c;
                c = d;
                fc = fd;
                d = a + ratio * (b - a);
                fd = height(d, horizontal, dy, speed, gravity, accelerate);
            }
        }
        return (a + b) * 0.5;
    }

    private static double height(double angle, double horizontal, double dy, double speed, double gravity,
                                 boolean accelerate) {
        double f = miss(angle, horizontal, dy, speed, gravity, accelerate);
        return Double.isNaN(f) ? Double.NEGATIVE_INFINITY : f;
    }

    /** Biseccion entre dos angulos con distinto signo de error. */
    private static double refine(double a0, double f0, double a1, double horizontal, double dy, double speed,
                                 double gravity, boolean accelerate) {
        double lo = a0;
        double hi = a1;
        boolean loNegative = f0 < 0.0;
        for (int i = 0; i < REFINE; i++) {
            double mid = (lo + hi) * 0.5;
            double f = miss(mid, horizontal, dy, speed, gravity, accelerate);
            if (Double.isNaN(f)) {
                hi = mid; // no deberia pasar entre dos angulos que llegan; se acorta el intervalo
                continue;
            }
            if ((f < 0.0) == loNegative) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) * 0.5;
    }

    /**
     * Altura a la que pasa el proyectil al llegar a la distancia horizontal del objetivo, menos la del objetivo
     * (positivo = pasa por encima). NaN si no llega (cae demasiado o se acaba su vida antes).
     */
    public static double miss(double angle, double horizontal, double dy, double speed, double gravity,
                              boolean accelerate) {
        double vx = Math.cos(angle) * speed;
        double vy = Math.sin(angle) * speed;
        double x = 0.0;
        double y = 0.0;
        double max = speed * MAX_BOOST;
        double floor = Math.min(dy, 0.0) - 96.0;
        for (int t = 0; t < MAX_LIFE; t++) {
            double nx = x + vx;
            double ny = y + vy;
            if (nx >= horizontal) {
                double f = vx > 1.0e-9 ? (horizontal - x) / vx : 0.0;
                return y + vy * f - dy;
            }
            // mismo orden que el proyectil: se mueve, frena (o acelera) y cae
            double factor = accelerate ? ACCELERATION : DRAG;
            vx *= factor;
            vy *= factor;
            double len2 = vx * vx + vy * vy;
            if (len2 > max * max) {
                double k = max / Math.sqrt(len2);
                vx *= k;
                vy *= k;
            }
            vy -= gravity;
            x = nx;
            y = ny;
            if (y < floor || vx < 1.0e-4) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }
}
